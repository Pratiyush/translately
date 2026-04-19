package io.translately.service.keys

import io.translately.data.entity.Key
import io.translately.data.entity.KeyState
import io.translately.service.orgs.OrgException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import jakarta.transaction.Transactional

/**
 * Use-case entry point for key search (T206).
 *
 * Composes a native Postgres query over the FTS artefacts landed in V4:
 *
 *   * `keys.search_vector` — generated `tsvector` on `key_name + description`
 *     (see `V4__keys_fts_trigram.sql`). Matched via `plainto_tsquery('simple',
 *     ?)` so callers can pass a raw phrase without caring about tsquery
 *     syntax. `ts_rank` is surfaced as `matchRank`.
 *   * `translations.value` — trigram GIN index for fuzzy substring search.
 *     When FTS on the key side finds nothing we fall back to a similarity
 *     (`%`) match over the translations, so "welcom" still surfaces a
 *     "Welcome" translation body.
 *
 * All filter predicates compose independently so the UI can combine a
 * free-text query with namespace, tag-set, and state filters. Tag filtering
 * uses `HAVING COUNT(DISTINCT tag.id) = :required` to enforce intersection
 * (all requested tags must be present).
 *
 * Membership gating follows the project's convention — non-members see
 * `NOT_FOUND` via [OrgException.NotFound] rather than leaking project
 * existence. Scope enforcement stays at the JAX-RS resource when T207
 * wires the HTTP surface; this service only asserts the membership rule.
 */
@ApplicationScoped
open class KeySearchService(
    private val em: EntityManager,
) {
    /**
     * Search keys inside a project. Returns hits in rank order (highest
     * first) when a query is given, otherwise in `updated_at DESC` order.
     *
     * Non-member callers see `NOT_FOUND` — same shape as an unknown
     * project, so the server never leaks project existence.
     */
    @Transactional
    open fun search(
        callerExternalId: String,
        q: KeySearchQuery,
    ): List<KeySearchHit> {
        val projectId = resolveProjectId(callerExternalId, q.projectSlugOrId)

        val namespaceId = q.namespaceSlug?.let { resolveNamespaceId(projectId, it) }
        // Namespace filter that matches nothing is a definitive empty
        // result — avoids the more expensive key-level query.
        if (q.namespaceSlug != null && namespaceId == null) return emptyList()

        val tagIds = resolveTagIds(projectId, q.tagSlugs)
        // Tag filter that can't be satisfied — short-circuit identically.
        if (q.tagSlugs.isNotEmpty() && tagIds.size != q.tagSlugs.size) return emptyList()

        val trimmedQuery = q.query?.trim()?.takeIf(String::isNotEmpty)
        val filters = Filters(projectId, namespaceId, tagIds, q.state, q.limit, q.offset)

        val ftsHits = runSearch(filters, trimmedQuery, useTrigram = false)
        if (ftsHits.isNotEmpty() || trimmedQuery == null) return ftsHits

        // Fall through to trigram on translation bodies.
        return runSearch(filters, trimmedQuery, useTrigram = true)
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private data class Filters(
        val projectId: Long,
        val namespaceId: Long?,
        val tagIds: List<Long>,
        val state: KeyState?,
        val limit: Int,
        val offset: Int,
    )

    /** Returns the internal `projects.id` for a project the caller can see; else NOT_FOUND. */
    private fun resolveProjectId(
        callerExternalId: String,
        projectSlugOrId: String,
    ): Long {
        val projKey = projectSlugOrId.trim().lowercase()
        val row =
            em
                .createQuery(
                    """
                    SELECT p.id FROM Project p
                    JOIN OrganizationMember m ON m.organization.id = p.organization.id
                    WHERE (p.slug = :projKey OR p.externalId = :projKey)
                      AND p.deletedAt IS NULL
                      AND m.user.externalId = :uid
                    """.trimIndent(),
                    java.lang.Long::class.java,
                ).setParameter("projKey", projKey)
                .setParameter("uid", callerExternalId)
                .resultList
                .firstOrNull() ?: throw OrgException.NotFound("Project")
        return row.toLong()
    }

    private fun resolveNamespaceId(
        projectId: Long,
        slug: String,
    ): Long? {
        val normalized = slug.trim().lowercase()
        return em
            .createQuery(
                """
                SELECT n.id FROM Namespace n
                WHERE n.project.id = :pid AND n.slug = :slug
                """.trimIndent(),
                java.lang.Long::class.java,
            ).setParameter("pid", projectId)
            .setParameter("slug", normalized)
            .resultList
            .firstOrNull()
            ?.toLong()
    }

    private fun resolveTagIds(
        projectId: Long,
        slugs: Set<String>,
    ): List<Long> {
        if (slugs.isEmpty()) return emptyList()
        val normalized = slugs.map { it.trim().lowercase() }
        return em
            .createQuery(
                """
                SELECT t.id FROM Tag t
                WHERE t.project.id = :pid AND t.slug IN :slugs
                """.trimIndent(),
                java.lang.Long::class.java,
            ).setParameter("pid", projectId)
            .setParameter("slugs", normalized)
            .resultList
            .map { it.toLong() }
    }

    /**
     * Single-query shape: project `(k.id, rank)` tuples through the native
     * query, then hydrate [Key] entities in `id` order via a separate JPQL
     * `IN (:ids)` fetch — avoids the double-query drift where the rank
     * list and entity list could disagree on order.
     */
    private fun runSearch(
        f: Filters,
        query: String?,
        useTrigram: Boolean,
    ): List<KeySearchHit> {
        val sql = buildSearchSql(f, query != null, useTrigram)
        val native = em.createNativeQuery(sql)
        bindCommonParams(native, f, query)

        @Suppress("UNCHECKED_CAST")
        val rows = native.resultList as List<Array<Any?>>
        if (rows.isEmpty()) return emptyList()

        val idsInOrder = rows.map { (it[0] as Number).toLong() }
        val ranks = rows.map { (it[1] as? Number)?.toFloat() ?: 0f }

        // Fetch-join namespace + project so callers can read the basic
        // metadata outside the service's transaction without tripping
        // Hibernate's lazy-init guard.
        val entities =
            em
                .createQuery(
                    """
                    SELECT DISTINCT k FROM Key k
                    JOIN FETCH k.namespace
                    JOIN FETCH k.project
                    WHERE k.id IN :ids
                    """.trimIndent(),
                    Key::class.java,
                ).setParameter("ids", idsInOrder)
                .resultList
                .associateBy { it.id }

        return idsInOrder.mapIndexedNotNull { idx, id ->
            entities[id]?.let { KeySearchHit(key = it, matchRank = ranks[idx]) }
        }
    }

    private fun buildSearchSql(
        f: Filters,
        hasQuery: Boolean,
        useTrigram: Boolean,
    ): String =
        buildString {
            append("SELECT k.id, ${rankExpr(hasQuery, useTrigram)} AS rank FROM keys k ")
            if (hasQuery && useTrigram) append("JOIN translations t ON t.key_id = k.id ")
            append("WHERE k.project_id = :pid AND k.soft_deleted_at IS NULL ")
            append(filterClauses(f, hasQuery, useTrigram))
            append(tagIntersectionClause(f.tagIds))
            // `translations JOIN` can fan out one key into many rows — collapse with GROUP BY.
            if (hasQuery && useTrigram) append("GROUP BY k.id, k.updated_at ")
            append(if (hasQuery) "ORDER BY rank DESC, k.id ASC " else "ORDER BY k.updated_at DESC, k.id ASC ")
            append("LIMIT :lim OFFSET :off")
        }

    private fun rankExpr(
        hasQuery: Boolean,
        useTrigram: Boolean,
    ): String =
        when {
            hasQuery && !useTrigram -> "ts_rank(k.search_vector, plainto_tsquery('simple', :q))"
            // word_similarity(query, text) is designed for "find the query inside
            // a longer text" — much better fit than raw similarity() for the
            // translation-body fallback, and still GIN-accelerated by gin_trgm_ops.
            hasQuery && useTrigram -> "MAX(word_similarity(:q, t.value))"
            else -> "0.0"
        }

    private fun filterClauses(
        f: Filters,
        hasQuery: Boolean,
        useTrigram: Boolean,
    ): String =
        buildString {
            if (f.namespaceId != null) append("AND k.namespace_id = :nsid ")
            if (f.state != null) append("AND k.state = :state ")
            // `<%` is the word-similarity operator — true when the query is
            // "close enough" as a word-level substring (pg_trgm default 0.6
            // word-similarity threshold). Uses the gin_trgm_ops GIN index.
            if (hasQuery && useTrigram) append("AND :q <% t.value ")
            if (hasQuery && !useTrigram) append("AND k.search_vector @@ plainto_tsquery('simple', :q) ")
        }

    private fun tagIntersectionClause(tagIds: List<Long>): String =
        if (tagIds.isEmpty()) {
            ""
        } else {
            "AND k.id IN (SELECT kt.key_id FROM key_tags kt " +
                "WHERE kt.tag_id IN (:tagIds) " +
                "GROUP BY kt.key_id " +
                "HAVING COUNT(DISTINCT kt.tag_id) = :tagCount) "
        }

    private fun bindCommonParams(
        q: Query,
        f: Filters,
        query: String?,
    ) {
        q.setParameter("pid", f.projectId)
        if (f.namespaceId != null) q.setParameter("nsid", f.namespaceId)
        if (f.state != null) q.setParameter("state", f.state.name)
        if (query != null) q.setParameter("q", query)
        if (f.tagIds.isNotEmpty()) {
            q.setParameter("tagIds", f.tagIds)
            q.setParameter("tagCount", f.tagIds.size.toLong())
        }
        q.setParameter("lim", f.limit)
        q.setParameter("off", f.offset)
    }
}

// ----------------------------------------------------------------------
// DTOs
// ----------------------------------------------------------------------

/**
 * Search input composed by the UI / API. Every filter is independent; pass
 * only what the caller supplied. `query` is matched against the FTS
 * vector; falls through to trigram on `translations.value` when FTS has
 * no hits. `tagSlugs` is an AND-intersection (a key must carry every tag).
 */
data class KeySearchQuery(
    val projectSlugOrId: String,
    val query: String? = null,
    val namespaceSlug: String? = null,
    val tagSlugs: Set<String> = emptySet(),
    val state: KeyState? = null,
    val limit: Int = DEFAULT_LIMIT,
    val offset: Int = 0,
) {
    companion object {
        const val DEFAULT_LIMIT: Int = 50
    }
}

/**
 * One search hit. `matchRank` is Postgres `ts_rank` on FTS hits and
 * `similarity()` on trigram fallbacks; both are in `[0.0, 1.0]` with
 * higher = better. When no query was supplied it is `0f`.
 */
data class KeySearchHit(
    val key: Key,
    val matchRank: Float,
)
