package com.monkaydee.tcgcatalogue.scan

/** A line of OCR text with its position, in fractions (0..1) of the analysed frame. */
data class OcrLine(val text: String, val top: Float = 0f, val height: Float = 0f)

sealed interface ScanHit {
    /** Stable identity used to decide when the same card has been read several frames in a row. */
    val key: String

    data class OnePiece(val code: String) : ScanHit {
        override val key get() = "op:$code"
    }

    /**
     * A Pokémon collector number such as 025/165 or TG05/TG30.
     * [number] keeps the printed prefix ("TG05"); [total] is the printed set size (165).
     */
    data class Pokemon(val number: String, val total: Int, val nameGuess: String?) : ScanHit {
        override val key get() = "pkm:$number/$total"
    }
}

enum class GameFilter { AUTO, POKEMON, ONE_PIECE }

/**
 * Turns OCR output from a card photo into the identifiers printed on the card:
 * the One Piece card code (bottom right, "OP05-060") or the Pokémon collector
 * number (bottom left, "025/165").
 */
object CardTextParser {
    // OCR often reads 0 as O and vice versa; accept both and normalise afterwards.
    private val onePieceCode = Regex("""(?<![A-Z0-9])(PRB|[O0]P|ST|EB)\s?([0-9OoIl]{2})\s?[-–—]\s?([0-9OoIl]{3})(?![0-9])""")
    private val onePiecePromo = Regex("""(?<![A-Z0-9])P\s?[-–—]\s?([0-9Oo]{3})(?![0-9])""")
    private val pokemonNumber = Regex("""(?<![A-Za-z0-9/])([A-Z]{0,3})\s?(\d{1,3})\s?/\s?([A-Z]{0,3})\s?(\d{2,3})(?![0-9])""")

    private val nameStopWords = setOf(
        "BASIC", "STAGE", "HP", "TRAINER", "SUPPORTER", "ITEM", "ENERGY", "STADIUM", "EVOLVES",
        "POKEMON", "POKÉMON", "TOOL", "ABILITY", "WEAKNESS", "RESISTANCE", "RETREAT", "ILLUS",
    )

    fun parse(lines: List<OcrLine>, filter: GameFilter = GameFilter.AUTO): ScanHit? {
        if (filter != GameFilter.POKEMON) {
            findOnePiece(lines)?.let { return it }
            if (filter == GameFilter.ONE_PIECE) return null
        }
        return findPokemon(lines)
    }

    fun findOnePiece(lines: List<OcrLine>): ScanHit.OnePiece? {
        for (line in lines) {
            val text = line.text.uppercase()
            onePieceCode.find(text)?.let { m ->
                val prefix = m.groupValues[1].replace('0', 'O')
                return ScanHit.OnePiece("$prefix${digits(m.groupValues[2])}-${digits(m.groupValues[3])}")
            }
        }
        for (line in lines) {
            onePiecePromo.find(line.text.uppercase())?.let { m ->
                return ScanHit.OnePiece("P-${digits(m.groupValues[1])}")
            }
        }
        return null
    }

    fun findPokemon(lines: List<OcrLine>): ScanHit.Pokemon? {
        // The collector number is printed near the bottom; prefer the lowest match.
        val matches = lines.sortedByDescending { it.top }.flatMap { line ->
            pokemonNumber.findAll(line.text).mapNotNull { m ->
                val prefix = m.groupValues[1]
                val number = m.groupValues[2]
                val totalPrefix = m.groupValues[3]
                val total = m.groupValues[4].toIntOrNull() ?: return@mapNotNull null
                if (total < 10) return@mapNotNull null
                // "TG05/TG30": both prefixes should agree; a lone prefix is usually OCR noise.
                if (prefix.isNotEmpty() && totalPrefix.isNotEmpty() && prefix != totalPrefix) return@mapNotNull null
                val printed = if (prefix.isNotEmpty() && prefix == totalPrefix) prefix + number else number
                // A regular card number is never wildly bigger than the set (secret rares go up to ~1.6x).
                if (prefix.isEmpty() && number.toInt() > total * 2) return@mapNotNull null
                printed to total
            }.toList()
        }
        val (number, total) = matches.firstOrNull() ?: return null
        return ScanHit.Pokemon(number, total, guessName(lines))
    }

    /** The card name is the tallest plain-text line in the top part of the card. */
    fun guessName(lines: List<OcrLine>): String? = lines
        .filter { it.top < 0.35f }
        .map { it.copy(text = cleanName(it.text)) }
        .filter { l ->
            l.text.length in 3..30 &&
                l.text.count { it.isLetter() } >= 3 &&
                l.text.uppercase().split(' ').none { it in nameStopWords }
        }
        .maxByOrNull { it.height }
        ?.text

    private fun cleanName(raw: String) = raw
        .replace(Regex("""\b\d{2,3}\s?HP\b|\bHP\s?\d{2,3}\b""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[^\p{L}\p{N} .'’:-]"""), "")
        .trim()

    private fun digits(s: String) = s.uppercase().replace('O', '0').replace('I', '1').replace('L', '1')

    /** Similarity of two names in 0..1, tolerant to OCR errors (normalised Levenshtein). */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        if (x.contains(y) || y.contains(x)) return 0.9
        val d = levenshtein(x, y)
        return 1.0 - d.toDouble() / maxOf(x.length, y.length)
    }

    private fun normalize(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
