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
 * A target language enabled for a [Project]. The project's base language tag
 * (on [Project.baseLanguageTag]) is not stored as a ProjectLanguage row; it's
 * implicit. Only *additional* target languages live here.
 */
@Entity
@Table(
    name = "project_languages",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_project_languages_project_tag", columnNames = ["project_id", "language_tag"]),
    ],
    indexes = [
        Index(name = "idx_project_languages_project", columnList = "project_id"),
    ],
)
class ProjectLanguage : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = ForeignKey(name = "fk_project_languages_project"))
    lateinit var project: Project

    /** BCP 47 language tag, e.g. `de`, `fr-CA`, `pt-BR`. */
    @Column(name = "language_tag", nullable = false, length = 32)
    var languageTag: String = ""

    /** Display name in the target language itself — e.g. "Deutsch", "Français". */
    @Column(name = "name", nullable = false, length = 64)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 8)
    var direction: LanguageDirection = LanguageDirection.LTR
}
