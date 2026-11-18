package gg.opgg.android

import gg.opgg.android.data.Card
import gg.opgg.android.data.MetaArchetype
import gg.opgg.android.data.MetaCardEntry
import gg.opgg.android.data.MetaSnapshot
import gg.opgg.android.data.RelatedCard
import gg.opgg.android.data.RelatedIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedIndexTest {

    private fun c(id: String, normal: String? = null, crew: String? = null, color: String? = null) = Card(
        id = id, idNormal = normal ?: id, Type = crew, Color = color,
    )

    // OP1-001 and OP1-001_r1 are the same card; the snapshot uses the reprint id.
    private val cards = listOf(
        c("OP1-001", crew = "Straw Hat Crew"),
        c("OP1-001_r1", normal = "OP1-001", crew = "Straw Hat Crew"),
        c("OP1-002", crew = "Straw Hat Crew"),
        c("OP1-003", crew = "Red Dog Pirates"),
        c("OP2-001", crew = "Straw Hat Crew"),
    )
    private val printingToNormal = cards.associate { (it.id ?: "") to (it.idNormal ?: "") }

    private val snapshot = MetaSnapshot(
        archetypes = listOf(
            MetaArchetype(
                name = "Luffy",
                cards = listOf(
                    MetaCardEntry("OP1-001_r1", "Luffy", "4", 1.0),
                    MetaCardEntry("OP1-002", "Zoro", "4", 1.0),
                    MetaCardEntry("OP1-003", "Buggy", "1", 0.2),
                ),
            ),
            MetaArchetype(
                name = "Zoro",
                cards = listOf(
                    MetaCardEntry("OP1-001", "Luffy", "4", 0.9),
                    MetaCardEntry("OP1-002", "Zoro", "4", 0.8),
                ),
            ),
        ),
    )

    private val index = RelatedIndex.build(snapshot, printingToNormal, cards)

    @Test
    fun printingIdsNormaliseToOneCard() {
        // OP1-001 appears as _r1 in one archetype and bare in another; that is
        // still one card, appearing in 2 archetypes.
        assertEquals(2, index.archetypeCount("OP1-001"))
    }

    @Test
    fun coOccurrenceRanksTheMostCommonPartnerFirst() {
        val related = index.related("OP1-001", cards)
        assertEquals("OP1-002", related.first().normalId)
        assertTrue(related.all { it.basis == RelatedCard.Basis.META })
    }

    @Test
    fun aCardIsNeverItsOwnPartner() {
        val related = index.related("OP1-001", cards)
        assertFalse(related.any { it.normalId == "OP1-001" })
    }

    @Test
    fun inclusionRateIsTheMeanAcrossArchetypes() {
        // Luffy is in 1.0 and 0.9 -> 0.95
        assertEquals(0.95, index.inclusionRate("OP1-001")!!, 0.0001)
    }

    @Test
    fun cardsOutsideTheMetaFallBackToCrew() {
        val related = index.related("OP2-001", cards)
        assertTrue(related.isNotEmpty())
        assertTrue("fallback must be labelled, not passed off as meta", related.all { it.basis == RelatedCard.Basis.CREW })
        assertFalse(related.any { it.normalId == "OP2-001" })
    }

    @Test
    fun crewFallbackPrefersCardsThatAppearInMeta() {
        val related = index.related("OP2-001", cards)
        // OP1-001 and OP1-002 are in the snapshot; OP1-001_r1 is the same card.
        assertTrue(related.first().normalId.startsWith("OP1-"))
    }

    @Test
    fun relatedResolvesRealCardObjects() {
        val related = index.related("OP1-001", cards)
        assertTrue(related.first().card != null)
        assertEquals("OP1-002", related.first().card!!.key)
    }

    @Test
    fun aCardWithNoCrewAndNoMetaReturnsNothing() {
        val orphan = listOf(c("ZZ-001"))
        val idx = RelatedIndex.build(MetaSnapshot(), emptyMap(), orphan)
        assertTrue(idx.related("ZZ-001", orphan).isEmpty())
    }

    @Test
    fun emptySnapshotIsHandled() {
        val idx = RelatedIndex.build(MetaSnapshot(), printingToNormal, cards)
        assertEquals(0, idx.coveredCards)
        assertTrue(idx.related("OP1-001", cards).isNotEmpty()) // crew fallback still works
    }

    @Test
    fun limitIsRespected() {
        assertTrue(index.related("OP1-001", cards, limit = 1).size == 1)
    }

    @Test
    fun countIsTheTotalNotThePreviewLength() {
        // The card page's button used to print related().size, which is capped
        // at six, so every card with a big crew read "See 6 cards from this
        // crew" however many crew-mates it had.
        val crew = List(20) { c("X-%03d".format(it), crew = "Big Crew") }
        val seed = c("X-SEED", crew = "Big Crew")
        val pool = crew + seed
        val idx = RelatedIndex.build(MetaSnapshot(), emptyMap(), pool)

        assertEquals(20, idx.relatedCount("X-SEED", pool))
        // The preview is still capped, and the count is not derived from it.
        assertEquals(6, idx.related("X-SEED", pool).size)
        assertTrue(idx.relatedCount("X-SEED", pool) > idx.related("X-SEED", pool).size)
    }

    @Test
    fun countMatchesTheFullListForTheMetaTier() {
        // The button and the list screen must agree on the number.
        val all = index.related("OP1-001", cards, Int.MAX_VALUE)
        assertEquals(all.size, index.relatedCount("OP1-001", cards))
    }

    @Test
    fun countMatchesTheFullListForTheCrewTier() {
        val all = index.related("OP2-001", cards, Int.MAX_VALUE)
        assertEquals(all.size, index.relatedCount("OP2-001", cards))
    }

    @Test
    fun countOfACardWithNoCrewAndNoMetaIsZero() {
        val orphan = listOf(c("ZZ-001"))
        val idx = RelatedIndex.build(MetaSnapshot(), emptyMap(), orphan)
        assertEquals(0, idx.relatedCount("ZZ-001", orphan))
    }

    // ---- colour rule ---------------------------------------------------------

    @Test
    fun crewFallbackKeepsOnlyTheSeedsColour() {
        val pool = listOf(
            c("A-001", crew = "Navy", color = "Red"),
            c("A-002", crew = "Navy", color = "Red"),
            c("A-003", crew = "Navy", color = "Blue"),
            c("A-SEED", crew = "Navy", color = "Red"),
        )
        val idx = RelatedIndex.build(MetaSnapshot(), emptyMap(), pool)
        val related = idx.related("A-SEED", pool)
        assertEquals(setOf("A-001", "A-002"), related.map { it.normalId }.toSet())
        // The button count and the list must agree, as with every other tier.
        assertEquals(2, idx.relatedCount("A-SEED", pool))
    }

    @Test
    fun aColourlessSeedStillGetsItsWholeCrew() {
        // Blank colour fields exist; the colour rule must not empty those lists.
        val pool = listOf(
            c("B-001", crew = "Film", color = "Green"),
            c("B-002", crew = "Film"),
            c("B-SEED", crew = "Film"),
        )
        val idx = RelatedIndex.build(MetaSnapshot(), emptyMap(), pool)
        assertEquals(setOf("B-001", "B-002"), idx.related("B-SEED", pool).map { it.normalId }.toSet())
        assertEquals(2, idx.relatedCount("B-SEED", pool))
    }

    @Test
    fun multicolourSeedSeesEveryColourItContains() {
        // Leaders store "Red/Green" in one field. An exact string match left
        // them with only the other cards carrying the identical combination,
        // which read as nonsense: no crew-mates at all.
        val pool = listOf(
            c("C-001", crew = "Supernovas", color = "Red"),
            c("C-002", crew = "Supernovas", color = "Green"),
            c("C-003", crew = "Supernovas", color = "Blue"),
            c("C-004", crew = "Supernovas", color = "Red/Green"),
            c("C-SEED", crew = "Supernovas", color = "Red/Green"),
        )
        val idx = RelatedIndex.build(MetaSnapshot(), emptyMap(), pool)
        val ids = idx.related("C-SEED", pool).map { it.normalId }.toSet()
        assertEquals(setOf("C-001", "C-002", "C-004"), ids)
        assertEquals(3, idx.relatedCount("C-SEED", pool))
    }

    @Test
    fun singleColourSeedMatchesMulticolourPartners() {
        // Symmetric: a Red card shares a colour with a Red/Green crew-mate.
        val pool = listOf(
            c("D-001", crew = "Navy", color = "Red"),
            c("D-002", crew = "Navy", color = "Red/Green"),
            c("D-003", crew = "Navy", color = "Blue"),
            c("D-SEED", crew = "Navy", color = "Red"),
        )
        val idx = RelatedIndex.build(MetaSnapshot(), emptyMap(), pool)
        assertEquals(setOf("D-001", "D-002"), idx.related("D-SEED", pool).map { it.normalId }.toSet())
    }
}
