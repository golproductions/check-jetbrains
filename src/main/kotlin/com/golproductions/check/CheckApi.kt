package com.golproductions.check

import java.net.HttpURLConnection
import java.net.URI

data class CheckResult(val verdict: String, val reason: String?)

object CheckApi {
    private const val API = "https://triage.golproductions.com/preflight"
    private const val INSTANT = "https://triage.golproductions.com/instant-key"
    private const val CHANNEL = "jetbrains"
    private const val VERSION = "1.0.16"
    private const val TIMEOUT = 5000

    // On Windows, bare "bash" is a trap: PATH can resolve to WSL's
    // System32\bash.exe, a different operating system. Locate Git Bash
    // explicitly; if it cannot be located, abstain.
    private fun bashPath(): String? {
        val osName = (System.getProperty("os.name") ?: "").lowercase()
        if (!osName.contains("win")) return "bash"
        val candidates = listOfNotNull(
            System.getenv("CLAUDE_CODE_GIT_BASH_PATH"),
            "C:/Program Files/Git/bin/bash.exe",
            "C:/Program Files/Git/usr/bin/bash.exe",
            "C:/Program Files (x86)/Git/bin/bash.exe"
        )
        for (c in candidates) {
            try { if (java.io.File(c).exists()) return c } catch (e: Exception) {}
        }
        return null
    }

    // The ONE local check: `bash -n` parses without executing; its verdict is
    // the shell's own ground truth. The client holds no other opinion about
    // command structure (no word extraction, no binary probing, no quoting
    // model); everything else is the server's verdict, relayed. A string =
    // the shell's own error, verbatim, deny with it. null = clean parse OR
    // no bash located; both mean the same thing to the caller: ask the server.
    private fun syntaxError(command: String): String? {
        return try {
            val bash = bashPath() ?: return null
            val p = ProcessBuilder(bash, "-n")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            p.outputStream.use { it.write(command.toByteArray(Charsets.UTF_8)) }
            if (!p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly(); return null
            }
            val stderr = p.errorStream.bufferedReader().use { it.readText() }
            if (p.exitValue() == 0) {
                val warn = stderr.lines().firstOrNull { Regex("here-document.*end-of-file", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                return warn?.replace(Regex("^[^:]*bash[^:]*:\\s*", RegexOption.IGNORE_CASE), "")?.trim()
            }
            val msg = stderr.lines().firstOrNull { it.isNotBlank() } ?: "syntax error"
            msg.replace(Regex("^[^:]*bash[^:]*:\\s*", RegexOption.IGNORE_CASE), "").trim()
        } catch (e: Exception) { null }
    }

    fun validate(command: String): CheckResult {
        var clientId = CheckSettings.getInstance().clientId

        // No key yet: mint one instantly. No email, no browser, no paste.
        if (clientId.isBlank()) {
            clientId = mintInstantKey()
                ?: return CheckResult("error", "Could not activate Check. Check your connection and try again.")
        }

        // Syntax first: the shell's own parser speaks before the server does.
        val synErr = syntaxError(command)
        if (synErr != null) {
            return CheckResult("invalid", "syntax error. $synErr")
        }

        val conn = URI(API).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-GOL-CLIENT-ID", clientId)
        conn.setRequestProperty("User-Agent", "jetbrains/$VERSION")
        conn.connectTimeout = TIMEOUT
        conn.readTimeout = TIMEOUT
        conn.doOutput = true

        val body = """{"command":${escapeJson(command)},"platform":"jetbrains","channel":"$CHANNEL","v":"$VERSION"}"""
        conn.outputStream.use { it.write(body.toByteArray()) }

        // Non-2xx responses carry no `verdict`; the body lands on errorStream.
        // Read the right stream so a billing/limit reply never throws.
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""

        if (code == 402) {
            val upgrade = extractJsonString(response, "upgrade")
                ?: "Top up at https://www.golproductions.com/console.html"
            return CheckResult("invalid", "You've used all 120 free checks for today. Credits never expire. $upgrade")
        }

        val verdict = extractJsonString(response, "verdict") ?: "error"
        val reason = extractJsonString(response, "reason") ?: extractJsonString(response, "error")
        return CheckResult(verdict, reason)
    }

    // One free GOL Client ID per machine, shared by every Check client
    // (npm hook, MCP, editors). All clients read and write this file.
    private val machineKeyFile = java.io.File(System.getProperty("user.home"), ".check/key")

    private fun readMachineKey(): String? {
        return try {
            val k = machineKeyFile.readText(Charsets.UTF_8).trim()
            k.ifEmpty { null }
        } catch (e: Exception) { null }
    }

    private fun writeMachineKey(key: String) {
        try {
            machineKeyFile.parentFile?.mkdirs()
            machineKeyFile.writeText(key, Charsets.UTF_8)
        } catch (e: Exception) {}
    }

    // Resolve the machine key: another Check client may already have minted one.
    // Otherwise mint (same fingerprint recipe everywhere, so the server returns
    // the same key even in a race). Persists to settings and the machine key file.
    private fun mintInstantKey(): String? {
        readMachineKey()?.let {
            CheckSettings.getInstance().clientId = it
            return it
        }
        return try {
            val conn = URI(INSTANT).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "jetbrains/$VERSION")
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.doOutput = true

            val body = """{"fingerprint":"${deviceFingerprint()}","channel":"$CHANNEL"}"""
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode !in 200..299) return null
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val id = extractJsonString(response, "client_id") ?: return null
            CheckSettings.getInstance().clientId = id
            writeMachineKey(id)
            id
        } catch (e: Exception) {
            null
        }
    }

    // Stable anonymous device fingerprint: one-way SHA-256 of coarse machine
    // facts. The recipe (hostname|platform|arch|username, Node.js-style tokens)
    // is shared by every Check client so all tools on one machine resolve to
    // the same free key.
    private fun deviceFingerprint(): String {
        val host = try { java.net.InetAddress.getLocalHost().hostName } catch (e: Exception) { "" }
        val osName = (System.getProperty("os.name") ?: "").lowercase()
        val platform = when {
            osName.contains("win") -> "win32"
            osName.contains("mac") || osName.contains("darwin") -> "darwin"
            else -> "linux"
        }
        val archRaw = (System.getProperty("os.arch") ?: "").lowercase()
        val arch = when {
            archRaw.contains("aarch64") || archRaw.contains("arm64") -> "arm64"
            else -> "x64"
        }
        val user = System.getProperty("user.name") ?: ""
        val seed = listOf(host, platform, arch, user).joinToString("|")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun escapeJson(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val match = Regex(pattern).find(json) ?: return null
        return match.groupValues[1]
    }
}
