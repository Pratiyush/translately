package io.translately.service.translations

import com.ibm.icu.text.MessagePattern
import jakarta.enterprise.context.ApplicationScoped
import java.util.Locale

/**
 * Parses and validates ICU MessageFormat source strings (T203).
 *
 * This is a pure service-layer utility: no database, no HTTP, no CDI
 * dependencies beyond the [ApplicationScoped] marker so consumers can
 * `@Inject` it. The class is deliberately thread-safe and cache-free —
 * `MessagePattern` is cheap to construct and binding it to a shared
 * instance would require external synchronisation.
 *
 * ### Consumers
 *  - Editor autosave (T207) — validates each keystroke batch on the
 *    webapp's PUT path before persisting.
 *  - JSON importer (T301) — validates every incoming translation value
 *    so bad ICU never reaches the database.
 *
 * ### What the validator checks
 *  - **Syntax.** The full ICU MessageFormat grammar (arguments, nested
 *    plural/select branches, apostrophe escapes). We use [MessagePattern]
 *    rather than `MessageFormat` because MessagePattern's parse
 *    exceptions carry a source index we can turn into a line + column.
 *  - **Plural / select completeness.** CLDR requires every plural,
 *    selectordinal and select argument to carry an `other` branch.
 *    MessagePattern already enforces this at parse time — a missing
 *    `other` surfaces as an `IllegalArgumentException` — and we map that
 *    into a structured [ValidationError] alongside other syntax faults.
 *  - **Argument types.** `SIMPLE` arguments (`{n, number}`, `{d, date}`,
 *    etc.) must name a type the ICU runtime actually supports. An
 *    unknown type like `{x, bogusType}` parses cleanly at the grammar
 *    level but blows up at format time; catching it here lets editors
 *    mark the problem as the user types.
 *
 * ### What the validator does not check (on purpose)
 *  - Placeholder / argument name consistency across translations — that
 *    belongs on the key-level diff, not a per-cell validator.
 *  - Plural branch coverage for a specific locale's CLDR keywords (e.g.
 *    Russian rejecting a message that only supplies `one` + `other`
 *    even though Russian needs `many`). Authors frequently ship under-
 *    specified plural sets during translation; surfacing that as an
 *    ERROR would gate save on every half-finished cell. Revisit as a
 *    WARNING once the Severity pipeline lights up in T207.
 */
@ApplicationScoped
open class IcuValidator {
    /**
     * Parse [source] as ICU MessageFormat and return a structured
     * [ValidationResult]. The [locale] is accepted because a future
     * WARNING tier will diff the user's branches against the locale's
     * CLDR plural keywords; today it's unused and parse behaviour is
     * locale-independent.
     *
     * An empty or blank source is valid — the [io.translately.data.entity.TranslationState]
     * enum on the Translation entity gates export, not the validator.
     */
    fun validate(
        source: String,
        @Suppress("UNUSED_PARAMETER") locale: Locale,
    ): ValidationResult {
        if (source.isBlank()) {
            return ValidationResult(errors = emptyList())
        }

        val pattern =
            try {
                MessagePattern(MessagePattern.ApostropheMode.DOUBLE_OPTIONAL).parse(source)
            } catch (ex: IllegalArgumentException) {
                return ValidationResult(errors = listOf(parseErrorFrom(source, ex)))
            }

        val errors = mutableListOf<ValidationError>()
        collectSimpleArgTypeErrors(pattern, source, errors)
        return ValidationResult(errors = errors)
    }

    /**
     * Walk the parsed Part stream and flag SIMPLE arguments whose type
     * name is not a built-in ICU type (`number`, `date`, ...).
     * MessagePattern accepts any identifier at this slot; the error only
     * surfaces at format time, so we guard the editor here.
     *
     * The other validation concerns (missing `other`, grammar faults)
     * are already handled by the MessagePattern parser itself.
     */
    private fun collectSimpleArgTypeErrors(
        pattern: MessagePattern,
        source: String,
        errors: MutableList<ValidationError>,
    ) {
        val count = pattern.countParts()
        var i = 0
        while (i < count) {
            val part = pattern.getPart(i)
            if (part.type == MessagePattern.Part.Type.ARG_START &&
                part.argType == MessagePattern.ArgType.SIMPLE
            ) {
                validateSimpleArg(pattern, source, i, errors)
            }
            i += 1
        }
    }

    private fun validateSimpleArg(
        pattern: MessagePattern,
        source: String,
        argStartIdx: Int,
        errors: MutableList<ValidationError>,
    ) {
        val limit = pattern.getLimitPartIndex(argStartIdx)
        var j = argStartIdx + 1
        while (j <= limit) {
            val p = pattern.getPart(j)
            if (p.type == MessagePattern.Part.Type.ARG_TYPE) {
                val typeName = pattern.getSubstring(p)
                if (typeName !in SIMPLE_ARG_TYPES) {
                    errors +=
                        positionError(
                            source = source,
                            charIndex = pattern.getPart(argStartIdx).index,
                            message = "Unknown argument type '$typeName' — expected one of $SIMPLE_ARG_TYPES.",
                        )
                }
                return
            }
            j += 1
        }
    }

    /**
     * ICU parse messages come in two shapes:
     *  - `Bad argument syntax: [at pattern index N] ...` — has an offset
     *  - `Unmatched '{' braces in message "..."` — no offset
     *
     * For the second shape we fall back to the last `{` or `}` in the
     * source so the editor still lands the caret somewhere useful.
     */
    private fun parseErrorFrom(
        source: String,
        ex: IllegalArgumentException,
    ): ValidationError {
        val message = ex.message ?: "Failed to parse ICU MessageFormat source."
        val idx =
            extractIndex(message)
                ?: source.lastIndexOfAny(BRACE_CHARS).takeIf { it >= 0 }
                ?: 0
        return positionError(
            source = source,
            charIndex = idx.coerceAtLeast(0).coerceAtMost(source.length),
            message = message,
        )
    }

    private fun extractIndex(message: String): Int? {
        val match = INDEX_RE.find(message) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun positionError(
        source: String,
        charIndex: Int,
        message: String,
    ): ValidationError {
        val (line, col) = lineCol(source, charIndex)
        return ValidationError(line = line, col = col, message = message, severity = Severity.ERROR)
    }

    private fun lineCol(
        source: String,
        charIndex: Int,
    ): Pair<Int, Int> {
        val bounded = charIndex.coerceIn(0, source.length)
        var line = 1
        var col = 1
        for (i in 0 until bounded) {
            if (source[i] == '\n') {
                line += 1
                col = 1
            } else {
                col += 1
            }
        }
        return line to col
    }

    companion object {
        /**
         * ICU's built-in simple argument types. Extensions are possible in
         * theory via a `FormatFactory`, but Translately doesn't ship any —
         * an unknown type here is always a user mistake.
         */
        private val SIMPLE_ARG_TYPES: Set<String> =
            setOf("number", "date", "time", "spellout", "ordinal", "duration")

        /**
         * ICU error messages encode the source offset as `at pattern index N`
         * or, on older branches, `at index N` / `at position N`. Accept all
         * three so we survive ICU upgrades without behaviour drift.
         */
        private val INDEX_RE = Regex("""at (?:pattern index|index|position) (\d+)""")

        private val BRACE_CHARS = charArrayOf('{', '}')
    }
}

/** Severity tier for a [ValidationError]. */
enum class Severity { ERROR, WARNING }

/**
 * A single validation finding. [line] and [col] are 1-based, newline-
 * aware positions into the source string — suitable to pass straight
 * into CodeMirror 6's linter surface.
 */
data class ValidationError(
    val line: Int,
    val col: Int,
    val message: String,
    val severity: Severity,
)

/**
 * Outcome of [IcuValidator.validate]. `ok` is derived from [errors] so
 * callers never see a `ok=true` result with errors attached (or vice-
 * versa).
 */
data class ValidationResult(
    val errors: List<ValidationError>,
) {
    val ok: Boolean get() = errors.isEmpty()
}
