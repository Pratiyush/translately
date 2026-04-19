package io.translately.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * Translation cell — one per `(key, languageTag)`. The `value` column
 * stores the ICU MessageFormat source that the T203 validator accepts.
 *
 * Language is kept as a plain BCP-47 string rather than a foreign key to
 * [ProjectLanguage], so a project can soft-delete a language without
 * cascading through every translation. Validity of the tag is enforced at
 * the service layer against the project's configured languages.
 *
 * `author` points at the user who last edited the row. Nullable because
 * imports can create rows without an edit attribution.
 */
@Entity
@Table(
    name = "translations",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_translations_key_language", columnNames = ["key_id", "language_tag"]),
    ],
    indexes = [
        Index(name = "idx_translations_key", columnList = "key_id"),
        Index(name = "idx_translations_state", columnList = "state"),
    ],
)
class Translation : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key_id", nullable = false, foreignKey = ForeignKey(name = "fk_translations_key"))
    lateinit var key: Key

    /** BCP-47 tag, e.g. `en`, `en-US`, `de`. Must match a configured [ProjectLanguage]. */
    @Column(name = "language_tag", nullable = false, length = 32)
    var languageTag: String = ""

    /** ICU MessageFormat source. Empty for EMPTY-state rows. */
    @Column(name = "value", nullable = false, columnDefinition = "TEXT")
    var value: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    var state: TranslationState = TranslationState.EMPTY

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id", foreignKey = ForeignKey(name = "fk_translations_author"))
    var author: User? = null
}
