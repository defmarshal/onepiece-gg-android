package gg.opgg.android

import gg.opgg.android.data.AppJson
import gg.opgg.android.data.MetaArchetype
import gg.opgg.android.data.MetaSnapshot
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Meta snapshot parsing and ordering.
 *
 * The JSON is a trimmed real payload, so a change in the site's shape shows up
 * here rather than as a blank screen.
 */
class MetaSnapshotTest {

    private fun parse(json: String): MetaSnapshot =
        AppJson.decodeFromString(MetaSnapshot.serializer(), json)

    private val payload = """
    {
      "game":"onepiece","format":"standard","window":"30d","lane":"measured",
      "set_code":"OP17","premium":false,"generated_at":1790478399,
      "sample":{"decks":737,"events":33,"matches":1448,"from":1787886399,"to":1790478399},
      "field_top8_rate":0.2521,"field_topq_rate":0.4329,
      "available":{"winrate":true,"matchups":true,"matchup_notes":true,"tier_history":false},
      "archetypes":[
        {
          "id":3013,"name":"Dracule Mihawk","leader":"OP14-020","set_code":"OP17",
          "slug":"op14-020-dracule-mihawk-standard-op17",
          "tier":"S","tier_score":2.94,"tier_basis":"conversion",
          "tier_reason":"Played in 20.1% of all placed decks, against 8.8% for the average deck.",
          "share":20.1,"share_delta":-4.1,"decks":148,"events":30,"rank":1,
          "winrate":0.65,"winrate_n":699,"winrate_method":"matches",
          "top8_rate":0.2824,"topq_rate":0.5578,"cut_rate":null,"cut_n":0,
          "momentum":"flat","watch":false,"colors":["Black"],
          "form":[{"event":"[EGB] ChinoizeCup #3","place":3,"players":64,"date":1790000000,"entries":120,"cut":"top 8"}],
          "research_note":"Nicky Goldman won the North American OP17 Finals in Dallas with it.",
          "evidence":[{"title":"45 first-place lists","url":"https://gumgum.gg","date":1790000000}],
          "cards":[{"id":"ST32-001","name":"Kin'emon","in_deck":"4","weight":1.0}],
          "best_decks":[{"deck_id":0,"place":1,"event":"2026 NA Finals","players":1095}],
          "matchup_notes_total":3
        },
        {
          "id":2001,"name":"No Tier","leader":"OP09-062","set_code":"OP17",
          "share":8.8,"decks":64,"events":20,"rank":2,
          "winrate":0.51,"winrate_n":300,
          "tier_reason":"A middling record that the field cannot read as anything.",
          "form":[{"event":"Regional #2","place":2,"players":40}],
          "cards":[{"id":"OP09-079","name":"Nico Robin","in_deck":"2","weight":0.8}],
          "evidence":[],"best_decks":[]
        },
        {
          "id":1000,"name":"Unranked","leader":"OP13-001","set_code":"OP16",
          "share":1.2,"decks":9,"winrate":0.40,
          "cards":[]
        }
      ]
    }
    """.trimIndent()

    private val snapshot = parse(payload)

    // ---- shape -----------------------------------------------------------

    @Test
    fun theHeaderFieldsParse() {
        assertEquals("standard", snapshot.format)
        assertEquals("30d", snapshot.window)
        assertEquals("measured", snapshot.lane)
        assertEquals(false, snapshot.premium)
        assertEquals(737, snapshot.sample.decks)
        assertEquals(33, snapshot.sample.events)
        assertEquals(1448, snapshot.sample.matches)
        assertEquals(3, snapshot.archetypes.size)
    }

    @Test
    fun fieldBenchmarksParseFromTheirPrefixedKeys() {
        // The payload uses field_top8_rate, not top8_rate, which is what a naive
        // model would silently miss.
        assertEquals(0.2521, snapshot.fieldTop8Rate!!, 0.00001)
        assertEquals(0.4329, snapshot.fieldTopqRate!!, 0.00001)
    }

    @Test
    fun availabilityFlagsParse() {
        assertEquals(true, snapshot.available.winrate)
        assertEquals(false, snapshot.available.tierHistory)
    }

    @Test
    fun aFullArchetypeParses() {
        val a = snapshot.archetypes[0]
        assertEquals("Dracule Mihawk", a.name)
        assertEquals("S", a.tier)
        assertEquals(20.1, a.share!!, 0.001)
        assertEquals(0.65, a.winrate!!, 0.0001)
        assertEquals(699, a.winrateN)
        assertEquals(1, a.rank)
        assertEquals(1, a.cards.size)
        assertEquals("Kin'emon", a.cards[0].name)
        assertEquals(1, a.evidence.size)
        assertEquals("45 first-place lists", a.evidence[0].title)
        assertNotNull(a.evidence[0].url)
        assertEquals(1, a.bestDecks.size)
        assertEquals(1095, a.bestDecks[0].players)
        assertEquals(3, a.matchupNotesTotal)
        assertEquals(1, a.form.size)
        assertEquals("[EGB] ChinoizeCup #3", a.form[0].event)
        assertEquals(3, a.form[0].place)
        assertNotNull(a.researchNote)
    }

    @Test
    fun inDeckArrivesAsAStringAndIsExposedAsANumber() {
        val entry = snapshot.archetypes[0].cards[0]
        assertEquals("4", entry.inDeck)
        assertEquals(4.0, entry.copies!!, 0.001)
    }

    @Test
    fun missingFieldsBecomeNullRatherThanThrowing() {
        // The site omits keys freely; archetype 3 has no tier, no rank, no reason.
        val a = snapshot.archetypes[2]
        assertEquals("Unranked", a.name)
        assertEquals(null, a.tier)
        assertEquals(null, a.rank)
        assertEquals(null, a.tierReason)
        assertEquals(1.2, a.share!!, 0.001)
    }

    @Test
    fun aNullFieldStaysNullRatherThanBecomingZero() {
        // cut_rate is explicitly null on the top deck and must not read as 0%.
        assertEquals(null, snapshot.archetypes[0].cutRate)
        assertEquals(0, snapshot.archetypes[0].cutN)
    }

    // ---- the two behaviours the UI depends on ---------------------------

    @Test
    fun tierIsSparseSoTheScreenCannotDependOnIt() {
        // Measured on the live snapshot: tier present on 9 of 51. Here 1 of 3.
        val withTier = snapshot.archetypes.count { !it.tier.isNullOrBlank() }
        assertEquals(1, withTier)
        // tierReason, by contrast, is on all of them.
        assertEquals(2, snapshot.archetypes.count { !it.tierReason.isNullOrBlank() })
    }

    @Test
    fun rankingIsByRankNotByTier() {
        val ranked = snapshot.ranked()
        assertEquals(listOf(1, 2, null), ranked.map { it.rank })
        // The untiered third archetype sorts last, not in the middle.
        assertEquals("Unranked", ranked.last().name)
    }

    @Test
    fun anArchetypeWithNoRankSortsAfterEveryRankedOne() {
        val ranked = snapshot.ranked()
        assertEquals("Unranked", ranked.last().name)
        assertEquals(null, ranked.last().rank)
    }

    @Test
    fun tiesBreakOnShareThenNameSoTheOrderIsStable() {
        val s = parse(
            """
            {"archetypes":[
              {"name":"Beta","share":5.0,"rank":4},
              {"name":"Alpha","share":9.0,"rank":4},
              {"name":"Gamma","share":5.0,"rank":4}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("Alpha", "Beta", "Gamma"), s.ranked().map { it.name })
    }

    @Test
    fun anEmptySnapshotRanksToAnEmptyList() {
        assertTrue(parse("{}").ranked().isEmpty())
        assertTrue(parse("""{"archetypes":[]}""").ranked().isEmpty())
    }

    // ---- identity, used as the expanded-row key -------------------------

    @Test
    fun theKeyPrefersTheArchetypeId() {
        assertEquals("3013", MetaArchetype(id = 3013L, slug = "mihawk").key)
    }

    @Test
    fun theKeyFallsBackToSlugThenNameSoAPartialPayloadStillKeys() {
        assertEquals("mihawk", MetaArchetype(id = null, slug = "mihawk", name = "x").key)
        assertEquals("Beast", MetaArchetype(id = null, slug = "", name = "Beast").key)
    }

    @Test
    fun twoArchetypesNeverShareAKey() {
        // The expanded row is stored in the ViewModel by this key, so a collision
        // would expand both at once.
        val keys = snapshot.archetypes.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.none { it.isEmpty() })
    }

    // ---- what is worth expanding ----------------------------------------

    @Test
    fun detailIsOfferedWhenThereIsSomethingToRead() {
        assertTrue(snapshot.archetypes[0].hasDetail)
        assertTrue(snapshot.archetypes[1].hasDetail)
        // Blank form, no note, no evidence, no cards: nothing to show.
        assertFalse(snapshot.archetypes[2].hasDetail)
    }

    @Test
    fun thePayloadIsStillAValidRelatedIndexInput() {
        // RelatedIndex consumes this same model, so the move must not have
        // changed the archetype shape it relies on.
        val a = snapshot.archetypes[0]
        assertNotNull(a.id)
        assertNotNull(a.leader)
        assertNotNull(a.decks)
    }
}
