package gg.opgg.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A related card, with the reason it surfaced. */
data class RelatedCard(
    val card: Card?,
    val normalId: String,
    val archetypeCount: Int = 0,
    /** 0..1 inclusion rate across the archetypes that contain the seed. */
    val coScore: Double = 0.0,
    val basis: Basis = Basis.META,
) {
    enum class Basis { META, CREW }
}

/**
 * Related-card index, built from `getmetasnapshot`.
 *
 * Two tiers, because the meta snapshot only covers the staples: roughly 446 of
 * 3,063 cards appear in it, so anything else has to fall back to crew.
 *
 * Ids in the snapshot are PRINTING ids, so everything is normalised through
 * printingToNormal first; otherwise OP06-038_r1 and OP06-038 would be treated
 * as different cards.
 */
class RelatedIndex private constructor(
    private val archetypesOf: Map<String, List<String>>,
    private val coScores: Map<String, Map<String, Double>>,
    private val weights: Map<String, Double>,
    private val crews: Map<String, String>,
    /** Colour components per card; multicolour cards carry both, e.g. {Red, Green}. */
    private val colors: Map<String, Set<String>>,
) {

    val coveredCards: Int get() = archetypesOf.size

    fun archetypeCount(normalId: String): Int = archetypesOf[normalId]?.size ?: 0

    /** Mean inclusion rate of a card across the whole snapshot, 0..1. */
    fun inclusionRate(normalId: String): Double? = weights[normalId]

    /**
     * Related cards for [normalId], best first.
     *
     * [allCards] is the loaded database, used to resolve ids to real cards and
     * to find crew-mates. [limit] caps the result.
     */
    fun related(
        normalId: String,
        allCards: List<Card>,
        limit: Int = 6,
    ): List<RelatedCard> {
        val metaPartners = coScores[normalId]
        if (!metaPartners.isNullOrEmpty()) {
            return metaPartners.entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { (id, score) ->
                    RelatedCard(
                        card = allCards.firstOrNull { it.key == id },
                        normalId = id,
                        archetypeCount = archetypeCount(id),
                        coScore = score,
                        basis = RelatedCard.Basis.META,
                    )
                }
        }
        // Fallback: same type AND overlapping colour. Crew is the only field
        // that meaningfully partitions the card pool (175 groups, median size
        // 7), but a "same crew" list used to mix every colour in it; the
        // related section is more useful when it stays inside the seed's
        // colour. Colours are compared per component: multicolour leaders
        // store "Red/Green", and an exact string match would leave them with
        // only the handful of cards carrying the identical combination. A
        // colourless seed keeps the type-only behaviour, so nothing loses its
        // list over a blank field.
        val crew = crews[normalId] ?: return emptyList()
        val color = colors[normalId]
        return allCards.asSequence()
            .filter { it.key != normalId }
            .filter { crews[it.key] == crew }
            .filter { color.isNullOrEmpty() || colors[it.key].orEmpty().any(color::contains) }
            .sortedWith(
                compareByDescending<Card> { weights[it.key] ?: -1.0 }
                    .thenBy { it.idNormal },
            )
            .take(limit)
            .map {
                RelatedCard(
                    card = it,
                    normalId = it.key,
                    archetypeCount = archetypeCount(it.key),
                    coScore = weights[it.key] ?: 0.0,
                    basis = RelatedCard.Basis.CREW,
                )
            }
            .toList()
    }

    /**
     * How many related cards there are in total.
     *
     * [related] caps its result at 6 for the preview, so its size is the number
     * of cards *shown*, not the number that exist. The button on the card page
     * printed that capped number, so every card with more than six crew-mates
     * read "See 6 cards from this crew" - the count was the preview length, not
     * the size of the crew.
     *
     * Counts without sorting or allocating: a map lookup for the meta tier, one
     * scan for the crew tier. The basis is chosen exactly as [related] chooses
     * it, so the count and the list always agree.
     */
    fun relatedCount(normalId: String, allCards: List<Card>): Int {
        val metaPartners = coScores[normalId]
        if (!metaPartners.isNullOrEmpty()) return metaPartners.size
        val crew = crews[normalId] ?: return 0
        val color = colors[normalId]
        var n = 0
        for (c in allCards) {
            if (c.key == normalId) continue
            if (crews[c.key] == crew && (color.isNullOrEmpty() || colors[c.key].orEmpty().any(color::contains))) n++
        }
        return n
    }

    companion object {
        fun build(
            snapshot: MetaSnapshot,
            printingToNormal: Map<String, String>,
            allCards: List<Card>,
        ): RelatedIndex {
            val crews = HashMap<String, String>()
            val colors = HashMap<String, Set<String>>()
            for (c in allCards) {
                val crew = c.Type?.split('/')?.firstOrNull()?.trim()
                if (!crew.isNullOrEmpty()) crews[c.key] = crew
                val color = c.Color?.trim()
                if (!color.isNullOrEmpty()) {
                    // "Red/Green" is two colours, not one value; a whole-string
                    // comparison would hide single-colour crew-mates from every
                    // multicolour card.
                    val parts = color.split('/')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                    if (parts.isNotEmpty()) colors[c.key] = parts
                }
            }

            val archetypesOf = HashMap<String, MutableList<String>>()
            val weights = HashMap<String, MutableList<Double>>()
            // Per archetype: canonical id -> inclusion weight.
            val perArchetype = ArrayList<Pair<String, Map<String, Double>>>()

            for (arch in snapshot.archetypes) {
                val name = arch.name ?: arch.id?.toString() ?: continue
                val inThis = LinkedHashMap<String, Double>()
                for (entry in arch.cards) {
                    val printing = entry.id ?: continue
                    val normal = printingToNormal[printing] ?: printing
                    if (inThis.containsKey(normal)) continue
                    inThis[normal] = entry.weight ?: 0.0
                    archetypesOf.getOrPut(normal) { ArrayList() }.add(name)
                    entry.weight?.let { weights.getOrPut(normal) { ArrayList() }.add(it) }
                }
                perArchetype.add(name to inThis)
            }

            // Co-occurrence: for each card, sum the inclusion weight of every
            // other card across the archetypes that contain it. The seed is
            // never a partner of itself.
            val coScores = HashMap<String, MutableMap<String, Double>>()
            for ((_, mine) in perArchetype) {
                for (seed in mine.keys) {
                    val acc = coScores.getOrPut(seed) { HashMap() }
                    for ((other, w) in mine) {
                        if (other == seed) continue
                        acc[other] = (acc[other] ?: 0.0) + w
                    }
                }
            }

            val meanWeights = HashMap<String, Double>()
            for ((k, list) in weights) {
                if (list.isNotEmpty()) meanWeights[k] = list.average()
            }

            return RelatedIndex(
                archetypesOf = archetypesOf.mapValues { it.value.toList() },
                coScores = coScores.mapValues { it.value.toMap() },
                weights = meanWeights,
                crews = crews,
                colors = colors,
            )
        }
    }
}
