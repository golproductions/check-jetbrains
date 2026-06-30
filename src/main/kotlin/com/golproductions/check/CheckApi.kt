package com.golproductions.check

import java.net.HttpURLConnection
import java.net.URI

data class CheckResult(val verdict: String, val reason: String?)

object CheckApi {
    private const val API = "https://triage.golproductions.com/preflight"
    private const val INSTANT = "https://triage.golproductions.com/instant-key"
    private const val CHANNEL = "jetbrains"
    private const val VERSION = "1.0.2"
    private const val TIMEOUT = 5000

    fun validate(command: String): CheckResult {
        var clientId = CheckSettings.getInstance().clientId

        // No key yet: mint one instantly. No email, no browser, no paste.
        if (clientId.isBlank()) {
            clientId = mintInstantKey()
                ?: return CheckResult("error", "Could not activate Check. Check your connection and try again.")
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

    // Mint a free key with no signup. Returns the client_id (also persisted) or null.
    private fun mintInstantKey(): String? {
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
            id
        } catch (e: Exception) {
            null
        }
    }

    // Stable anonymous device fingerprint: one-way SHA-256 of coarse machine facts.
    // No personal data. The server uses it only to rate-limit free-key minting.
    private fun deviceFingerprint(): String {
        val host = try { java.net.InetAddress.getLocalHost().hostName } catch (e: Exception) { "" }
        val seed = listOf(
            System.getProperty("os.name") ?: "",
            System.getProperty("os.arch") ?: "",
            System.getProperty("user.name") ?: "",
            host
        ).joinToString("|")
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
