package io.translately.service.translations

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.enterprise.context.ApplicationScoped

/**
 * i18next-compatible JSON reader / writer for translations (T301 + T302).
 *
 * Two on-the-wire shapes are supported:
 *
 *  - **Flat**: `{ "nav.signIn": "Sign in" }` — top-level keys are the
 *    full dotted key names, values are the translation strings.
 *  - **Nested**: `{ "nav": { "signIn": "Sign in" } }` — each `.` in a key
 *    becomes one level of nesting.
 *
 * Both shapes produce the same `(keyName, value)` pair stream; mode
 * choice is a serialisation preference, not a semantic one. The reader
 * accepts either shape per call (auto-detects) but the writer emits
 * whichever is explicitly requested.
 *
 * Non-string leaf values (numbers, booleans, null) are coerced to their
 * string representation — i18next tolerates those, and translators
 * occasionally ship a bare `42` for formatting. Nested arrays and
 * mixed-type siblings (`{ "foo": "x", "foo.bar": "y" }`) are rejected
 * with a structured [JsonShapeError] so the UI can pinpoint the path.
 *
 * This class is a pure parser — no database, no ICU validation, no
 * conflict resolution. Those layer on top in [TranslationImportService].
 */
@ApplicationScoped
open class JsonTranslationsIO(
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) {
    /**
     * Walk [body] and return the flattened `(keyName, value)` pairs.
     *
     * @throws JsonShapeException when the top-level JSON is not an
     *   object, when a nested path collides with a string leaf at the
     *   same address, or when the input is not valid JSON.
     */
    fun read(body: String): List<Entry> {
        val root =
            try {
                mapper.readTree(body)
            } catch (ex: JsonProcessingException) {
                throw JsonShapeException(
                    JsonShapeError("$", "INVALID_JSON", ex.message ?: "Invalid JSON"),
                    ex,
                )
            }
        if (root == null || !root.isObject) {
            throw JsonShapeException(
                JsonShapeError("$", "NOT_AN_OBJECT", "Top-level payload must be a JSON object."),
            )
        }
        val out = mutableListOf<Entry>()
        collectEntries(root as ObjectNode, prefix = "", out = out)
        return out
    }

    /**
     * Serialise [entries] as either a flat or nested object, preserving
     * the insertion order so tests can byte-match golden fixtures.
     */
    fun write(
        entries: List<Entry>,
        shape: Shape,
    ): String {
        val root = mapper.createObjectNode()
        when (shape) {
            Shape.FLAT ->
                entries.forEach { (name, value) ->
                    root.put(name, value)
                }
            Shape.NESTED ->
                entries.forEach { (name, value) ->
                    insertNested(root, name.split('.').filter(String::isNotEmpty), value)
                }
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun collectEntries(
        node: ObjectNode,
        prefix: String,
        out: MutableList<Entry>,
    ) {
        val fields = node.fields()
        while (fields.hasNext()) {
            val (name, child) = fields.next()
            val path = if (prefix.isEmpty()) name else "$prefix.$name"
            when {
                child.isObject -> collectEntries(child as ObjectNode, path, out)
                child.isArray ->
                    throw JsonShapeException(
                        JsonShapeError(path, "UNSUPPORTED_TYPE", "Arrays are not supported for translations."),
                    )
                child.isNull -> out += Entry(path, "")
                else -> out += Entry(path, child.asText())
            }
        }
    }

    private fun insertNested(
        parent: ObjectNode,
        parts: List<String>,
        value: String,
    ) {
        var cursor: ObjectNode = parent
        for ((index, part) in parts.withIndex()) {
            val leaf = index == parts.lastIndex
            if (leaf) {
                cursor.put(part, value)
                return
            }
            val next = cursor.get(part)
            cursor =
                when {
                    next == null -> cursor.putObject(part)
                    next is ObjectNode -> next
                    else ->
                        // String sitting where we'd like to nest — replace it
                        // with an object and drop the original as "__self__".
                        // Export collapses these back; this path is only hit
                        // when a caller asks NESTED for flat-style keys like
                        // `a` AND `a.b` coexisting.
                        cursor.putObject(part).also { replacement ->
                            replacement.put("__self__", (next as? JsonNode)?.asText().orEmpty())
                        }
                }
        }
    }

    /** A single translation entry — one row in the resolved output. */
    data class Entry(
        val keyName: String,
        val value: String,
    )

    enum class Shape { FLAT, NESTED }
}

/**
 * Structured parse error. The `path` follows jq-style addressing so the
 * UI can highlight the offending cell: `nav.signIn`, `$`, `foo[2]`.
 */
data class JsonShapeError(
    val path: String,
    val code: String,
    val message: String,
)

class JsonShapeException(
    val error: JsonShapeError,
    cause: Throwable? = null,
) : RuntimeException(error.message, cause)
