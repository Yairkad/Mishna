package app.mishna.core.content

class Seder(val name: String, val tractates: List<Tractate>)

class Tractate(val name: String, val chapterSizes: IntArray) {
    val size: Int get() = chapterSizes.sum()
}

/** A single mishna located in the study order. [perek] and [mishna] are 1-based. */
data class MishnaRef(
    val globalIndex: Int,
    val seder: String,
    val tractate: String,
    val perek: Int,
    val mishna: Int,
)
