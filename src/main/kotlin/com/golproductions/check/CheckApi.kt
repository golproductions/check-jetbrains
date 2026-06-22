package com.golproductions.check

import java.net.HttpURLConnection
import java.net.URI

data class CheckResult(val verdict: String, val reason: String?)

object CheckApi {
    private const val API = "https://triage.golproductions.com/preflight"
    private const val VERSION = "1.0.0"
    private const val TIMEOUT = 5000

    fun validate(command: String): CheckResult {
        val clientId = CheckSettings.getInstance().clientId
        if (clientId.isBlank()) {
            return CheckResult("error", "No Client ID set. Get one at golproductions.com/check.html")
        }

        val conn = URI(API).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-GOL-CLIENT-ID", clientId)
        conn.setRequestProperty("User-Agent", "jetbrains/$VERSION")
        conn.connectTimeout = TIMEOUT
        conn.readTimeout = TIMEOUT
        conn.doOutput = true

        val body = """{"command":${escapeJson(command)},"platform":"jetbrains","v":"$VERSION"}"""
        conn.outputStream.use { it.write(body.toByteArray()) }

        val response = conn.inputStream.bufferedReader().readText()
        val verdict = extractJsonString(response, "verdict") ?: "error"
        val reason = extractJsonString(response, "reason")
        return CheckResult(verdict, reason)
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
