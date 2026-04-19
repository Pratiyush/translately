package io.translately.app.keys

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.translately.app.auth.PostgresAndMailpitResource
import io.translately.data.entity.KeyState
import io.translately.service.keys.KeySearchQuery
import io.translately.service.keys.KeySearchService
import io.translately.service.orgs.OrgException
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for the key search surface (T206). Exercises
 * [KeySearchService] against a seeded Phase 2 fixture on a real Postgres
 * container, so FTS + trigram indexes are the actual query backend.
 *
 * Each test seeds a fresh org/project via [KeysTestHelpers] so suffixes
 * keep slugs globally unique across parallel runs.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresAndMailpitResource::class, restrictToAnnotatedClass = true)
open class KeySearchServiceIT {
    @Inject
    lateinit var helpers: KeysTestHelpers

    @Inject
    lateinit var searchService: KeySearchService

    private fun seed(): SeededKeysFixture {
        val suffix = System.nanoTime().toString().takeLast(9)
        return helpers.seedFixture(
            orgSlug = "keys-$suffix",
            projectSlug = "app-$suffix",
            email = "keys-$suffix@example.com",
        )
    }

    @Test
    fun `free-text match hits key_name and ranks unrelated rows zero`() {
        val fx = seed()

        val hits =
            searchService.search(
                fx.ownerExternalId,
                KeySearchQuery(projectSlugOrId = fx.projectSlug, query = "login"),
            )

        // The two `login.*` keys should hit; unrelated keys should be absent.
        assertTrue(hits.isNotEmpty(), "expected at least one hit for 'login'")
        val names = hits.map { it.key.keyName }.toSet()
        assertTrue(names.contains("login.title"), "expected login.title in hits, got $names")
        assertTrue(names.contains("login.button"), "expected login.button in hits, got $names")
        assertFalse(names.contains("billing.invoice"), "unrelated key billing.invoice should not match")
        hits.forEach { assertTrue(it.matchRank > 0f, "FTS hits must have matchRank > 0") }
    }

    @Test
    fun `namespace filter restricts results to the matching namespace`() {
        val fx = seed()

        val hits =
            searchService.search(
                fx.ownerExternalId,
                KeySearchQuery(
                    projectSlugOrId = fx.projectSlug,
                    namespaceSlug = fx.iosNamespaceSlug,
                ),
            )

        assertEquals(4, hits.size)
        hits.forEach {
            assertEquals(fx.iosNamespaceSlug, it.key.namespace.slug)
        }
    }

    @Test
    fun `single-tag filter returns only keys carrying that tag`() {
        val fx = seed()

        val hits =
            searchService.search(
                fx.ownerExternalId,
                KeySearchQuery(
                    projectSlugOrId = fx.projectSlug,
                    tagSlugs = setOf(fx.tagAuthSlug),
                ),
            )

        val names = hits.map { it.key.keyName }.toSet()
        assertEquals(setOf("login.title", "login.button", "password.reset"), names)
    }

    @Test
    fun `tag intersection requires every tag to be present`() {
        val fx = seed()

        val hits =
            searchService.search(
                fx.ownerExternalId,
                KeySearchQuery(
                    projectSlugOrId = fx.projectSlug,
                    tagSlugs = setOf(fx.tagAuthSlug, fx.tagImportantSlug),
                ),
            )

        // Only login.title carries BOTH `auth` and `important`.
        assertEquals(1, hits.size)
        assertEquals("login.title", hits[0].key.keyName)
    }

    @Test
    fun `state filter narrows to the selected lifecycle state`() {
        val fx = seed()

        val reviewHits =
            searchService.search(
                fx.ownerExternalId,
                KeySearchQuery(projectSlugOrId = fx.projectSlug, state = KeyState.REVIEW),
            )
        assertEquals(1, reviewHits.size)
        assertEquals("welcome.greeting", reviewHits[0].key.keyName)

        val doneHits =
            searchService.search(
                fx.ownerExternalId,
                KeySearchQuery(projectSlugOrId = fx.projectSlug, state = KeyState.DONE),
            )
        assertEquals(1, doneHits.size)
        assertEquals("logout.confirm", doneHits[0].key.keyName)
    }

    @Test
    fun `limit and offset produce stable pagination`() {
        val fx = seed()

        val page1 =
            searchService.search(
                fx.ownerExternalId,
                KeySearchQuery(projectSlugOrId = fx.projectSlug, limit = 4, offset = 0),
            )
        val page2 =
            searchService.search(
                fx.ownerExternalId,
                KeySearchQuery(projectSlugOrId = fx.projectSlug, limit = 4, offset = 4),
            )
        val page3 =
            searchService.search(
                fx.ownerExternalId,
                KeySearchQuery(projectSlugOrId = fx.projectSlug, limit = 4, offset = 8),
            )

        assertEquals(4, page1.size)
        assertEquals(4, page2.size)
        assertEquals(2, page3.size)

        // No overlap across pages.
        val ids = page1.map { it.key.id } + page2.map { it.key.id } + page3.map { it.key.id }
        assertEquals(ids.size, ids.toSet().size, "paged results should not repeat")
    }

    @Test
    fun `trigram fallback surfaces translation bodies when FTS has no key hit`() {
        val fx = seed()

        // "welcom" does not appear in any key_name or description, but it
        // is a trigram-similar substring of "Welcome to Translately" on the
        // `welcome.greeting` key's `en` translation.
        val hits =
            searchService.search(
                fx.ownerExternalId,
                KeySearchQuery(projectSlugOrId = fx.projectSlug, query = "welcom"),
            )

        assertTrue(hits.isNotEmpty(), "trigram fallback should match translation values")
        assertTrue(
            hits.any { it.key.keyName == "welcome.greeting" },
            "welcome.greeting should surface via translation-body trigram match",
        )
    }

    @Test
    fun `non-member caller sees NOT_FOUND on the target project`() {
        val fx = seed()
        val outsider = helpers.seedVerifiedUser("outsider-${System.nanoTime()}@example.com")

        val ex =
            assertThrows(OrgException.NotFound::class.java) {
                searchService.search(
                    outsider,
                    KeySearchQuery(projectSlugOrId = fx.projectSlug),
                )
            }
        assertEquals("NOT_FOUND", ex.code)
    }

    @Test
    fun `empty query string is treated as 'no query' and returns every key`() {
        val fx = seed()

        val hits =
            searchService.search(
                fx.ownerExternalId,
                KeySearchQuery(projectSlugOrId = fx.projectSlug, query = "   "),
            )

        assertEquals(10, hits.size)
        hits.forEach { assertEquals(0f, it.matchRank) }
    }
}
