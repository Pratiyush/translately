package io.translately.app.auth

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Tiny Mailpit HTTP-API reader used to pull the raw token out of the
 * verification / reset emails during integration tests. Mailpit exposes
 * messages at `/api/v1/messages` and a single message's text body at
 * `/api/v1/message/{ID}/part/...`; we use the simpler `/api/v1/message/{ID}`
 * endpoint that returns `Text` inline.
 */
class MailpitClient(
    private val baseUrl: String,
) {
    private val http =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    /** Most recent message addressed to [toEmail], polled up to ~10 s. */
    fun waitForMessage(
        toEmail: String,
        timeoutMs: Long = 10_000,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val id = findLatestMessageId(toEmail)
            if (id != null) return fetchTextBody(id)
            Thread.sleep(POLL_INTERVAL_MS)
        }
        error("No message for $toEmail arrived in Mailpit within ${timeoutMs}ms")
    }

    private fun findLatestMessageId(toEmail: String): String? {
        val body = get("/api/v1/messages")
        val messages = JsonObject(body).getJsonArray("messages") ?: JsonArray()
        for (i in 0 until messages.size()) {
            val m = messages.getJsonObject(i)
            val tos = m.getJsonArray("To") ?: continue
            for (j in 0 until tos.size()) {
                val addr = tos.getJsonObject(j).getString("Address") ?: continue
                if (addr.equals(toEmail, ignoreCase = true)) return m.getString("ID")
            }
        }
        return null
    }

    private fun fetchTextBody(id: String): String {
        val body = get("/api/v1/message/$id")
        val msg = JsonObject(body)
        val text = msg.getString("Text")
        if (!text.isNullOrBlank()) return text
        val html = msg.getString("HTML").orEmpty()
        return html
    }

    private fun get(path: String): String {
        val req = HttpRequest.newBuilder(URI.create("${baseUrl.trimEnd('/')}$path")).GET().build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        check(resp.statusCode() in 200..299) { "Mailpit returned ${resp.statusCode()} for $path" }
        return resp.body()
    }

    companion object {
        private const val POLL_INTERVAL_MS: Long = 200
    }
}
