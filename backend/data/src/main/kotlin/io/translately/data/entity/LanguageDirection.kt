package io.translately.data.entity

/**
 * Text direction of a [ProjectLanguage]. Derived from the language tag at
 * creation time (Arabic, Hebrew, Persian, Urdu → RTL; everything else → LTR)
 * but stored explicitly so operators can override for uncommon dialects.
 */
enum class LanguageDirection {
    LTR,
    RTL,
}
