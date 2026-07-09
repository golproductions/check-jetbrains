package com.golproductions.check

import java.net.HttpURLConnection
import java.net.URI

data class CheckResult(val verdict: String, val reason: String?)

object CheckApi {
    private const val API = "https://triage.golproductions.com/preflight"
    private const val INSTANT = "https://triage.golproductions.com/instant-key"
    private const val CHANNEL = "jetbrains"
    private const val VERSION = "1.0.18"
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

        // Syntax check and server round trip share no data: run them
        // concurrently (same as the npm hook). The HTTP call fires on a
        // background thread while the shell parses; the shell's syntax
        // verdict has absolute priority, so if bash refuses the grammar the
        // in-flight server call is abandoned (harmless) and we deny locally.
        val firstBody = """{"command":${escapeJson(command)},"platform":"jetbrains","channel":"$CHANNEL","v":"$VERSION","probe_ok":1}"""
        val httpFuture = java.util.concurrent.CompletableFuture.supplyAsync { postPreflight(clientId, firstBody) }
        val synErr = syntaxError(command)
        if (synErr != null) {
            return CheckResult("invalid", "syntax error. $synErr")
        }
        var reply = await(httpFuture)

        if (reply.code == 200 && extractJsonString(reply.body, "verdict") == "probe") {
            val words = extractJsonStringArray(reply.body, "probe").take(8).filter { it.isNotEmpty() && it.length <= 200 }
            // Every word asked at once: N probes cost one probe's time.
            val answers = words.map { w -> java.util.concurrent.CompletableFuture.supplyAsync { Pair(w, shellType(w)) } }
                .map { await(it) }
            val probes = StringBuilder()
            val whys = StringBuilder()
            for ((w, t) in answers) {
                if (t == null) continue
                if (probes.isNotEmpty()) probes.append(",")
                probes.append("${escapeJson(w)}:${t.first}")
                if (!t.first && t.second != null) {
                    if (whys.isNotEmpty()) whys.append(",")
                    whys.append("${escapeJson(w)}:${escapeJson(t.second!!)}")
                }
            }
            reply = postPreflight(clientId, """{"command":${escapeJson(command)},"platform":"jetbrains","channel":"$CHANNEL","v":"$VERSION","probe_ok":1,"probe":{$probes},"probe_why":{$whys}}""")
        }

        if (reply.code == 402) {
            val upgrade = extractJsonString(reply.body, "upgrade")
                ?: "Top up at https://www.golproductions.com/console.html"
            return CheckResult("invalid", "You've used all 120 free checks for today. Credits never expire. $upgrade")
        }

        val verdict = extractJsonString(reply.body, "verdict") ?: "error"
        val reason = extractJsonString(reply.body, "reason") ?: extractJsonString(reply.body, "error")
        return CheckResult(verdict, reason)
    }

    // Unwrap a concurrent result so the caller's try/catch sees the real
    // cause (an IOException from the HTTP call), not a CompletableFuture
    // ExecutionException wrapper. Behavior stays identical to the old serial
    // path where postPreflight threw straight out of validate().
    private fun <T> await(f: java.util.concurrent.CompletableFuture<T>): T {
        try {
            return f.get()
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        }
    }

    private data class Reply(val code: Int, val body: String)

    private fun postPreflight(clientId: String, body: String): Reply {
        val conn = URI(API).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-GOL-CLIENT-ID", clientId)
        conn.setRequestProperty("User-Agent", "jetbrains/$VERSION")
        conn.connectTimeout = TIMEOUT
        conn.readTimeout = TIMEOUT
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray()) }
        // Non-2xx responses carry no `verdict`; the body lands on errorStream.
        // Read the right stream so a billing/limit reply never throws.
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return Reply(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
    }

    // The other half of the rod: the server names a word, this asks the
    // shell about exactly that word. The word travels as a positional
    // argument, never shell-interpreted; the answer is the shell's own.
    // Pair(exists, whyOrNull). null = no bash located, abstain.
    private fun shellType(word: String): Pair<Boolean, String?>? {
        return try {
            val bash = bashPath() ?: return null
            val p = ProcessBuilder(bash, "-c", "type -- \"\$1\"", "check", word)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly(); return null
            }
            if (p.exitValue() == 0) return Pair(true, null)
            val stderr = p.errorStream.bufferedReader().use { it.readText() }
            val line = stderr.lines().firstOrNull { it.contains(word) } ?: ""
            val why = if (line.isNotEmpty()) line.substring(line.indexOf(word)).trim() else "$word: not found"
            Pair(false, why.ifEmpty { "$word: not found" })
        } catch (e: Exception) { null }
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

    private fun extractJsonStringArray(json: String, key: String): List<String> {
        val m = Regex("\"$key\"\\s*:\\s*\\[([^\\]]*)\\]").find(json) ?: return emptyList()
        return Regex("\"([^\"]*)\"").findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
    }
}
