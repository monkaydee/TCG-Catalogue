package com.monkaydee.tcgcatalogue.scan

import com.monkaydee.tcgcatalogue.data.db.Game

/** A line of OCR text with its position, in fractions (0..1) of the analysed frame. */
data class OcrLine(val text: String, val top: Float = 0f, val height: Float = 0f)

sealed interface ScanHit {
    val game: Game

    /** Stable identity used to decide when the same card has been read several frames in a row. */
    val key: String

    data class OnePiece(val code: String) : ScanHit {
        override val game get() = Game.ONE_PIECE
        override val key get() = "op:$code"
    }

    /**
     * A Pokémon collector number such as 025/165 or TG05/TG30.
     * [number] keeps the printed prefix ("TG05"); [total] is the printed set size (165).
     */
    data class Pokemon(val number: String, val total: Int, val nameGuess: String?) : ScanHit {
        override val game get() = Game.POKEMON
        override val key get() = "pkm:$number/$total"
    }

    /** Magic: set code + collector number ("DMU", "107") or, failing that, the card name. */
    data class Magic(val set: String?, val number: String?, val nameGuess: String?) : ScanHit {
        override val game get() = Game.MAGIC
        override val key get() = "mtg:${set ?: "?"}/${number ?: nameGuess}"
    }

    /** A card code found in a game's card index ("FB01-139", "UE01BT/BLC-1-001", "HOL/W91-001"). */
    data class Indexed(override val game: Game, val code: String) : ScanHit {
        override val key get() = "idx:${game.name}:$code"
    }
}

/** What a grading-company label on a slab says. Any part can be missing if it wasn't readable. */
data class GradeInfo(
    val grader: String?,
    val grade: String?,
    /** "Black Label" (BGS) or "Pristine" (CGC) */
    val qualifier: String? = null,
    val cert: String? = null,
) {
    /** "PSA 10", "BGS 10 Black Label", "CGC 9.5" */
    val label: String get() = listOfNotNull(grader ?: "Graded", grade, qualifier).joinToString(" ")
}

/**
 * Turns OCR output from a card photo into the identifiers printed on the card:
 * the One Piece card code (bottom right, "OP05-060"), the Pokémon collector
 * number (bottom left, "025/165"), the Magic set code and collector number,
 * or a code from one of the indexed games. Also reads grading-company slab labels.
 */
object CardTextParser {
    // OCR often reads 0 as O and vice versa; accept both and normalise afterwards.
    private val onePieceCode = Regex("""(?<![A-Z0-9])(PRB|[O0]P|ST|EB)\s?([0-9OoIl]{2})\s?[-–—]\s?([0-9OoIl]{3})(?![0-9])""")
    private val onePiecePromo = Regex("""(?<![A-Z0-9])P\s?[-–—]\s?([0-9Oo]{3})(?![0-9])""")
    private val pokemonNumber = Regex("""(?<![A-Za-z0-9/])([A-Z]{0,3})\s?(\d{1,3})\s?/\s?([A-Z]{0,3})\s?(\d{2,3})(?![0-9])""")

    // Magic: "DMU • EN" next to the collector number "0107 M" or "107/281 M".
    private val magicSetLine = Regex("""(?<![A-Z0-9])([A-Z0-9]{3,5})\s*[•·∙*°.]\s*(EN|DE|FR|IT|ES|PT|JA|JP|KO|RU|ZHS|ZHT|PH|CS|CT)\b""")
    private val magicNumber = Regex("""(?<![\d/])(\d{1,4})(?:\s*/\s*\d{1,4})?\s+([CURMSLTP])\b""")

    // Codes of the indexed games: "FB01-139", "UE01BT/BLC-1-001", "HOL/W91-001SP", "BT1-001".
    private val codeToken = Regex("""[A-Z0-9]+(?:[/\-_][A-Z0-9]+)+""")

    private val nameStopWords = setOf(
        "BASIC", "STAGE", "HP", "TRAINER", "SUPPORTER", "ITEM", "ENERGY", "STADIUM", "EVOLVES",
        "POKEMON", "POKÉMON", "TOOL", "ABILITY", "WEAKNESS", "RESISTANCE", "RETREAT", "ILLUS",
    )

    /** Most likely single card in the frame (live camera scan). */
    fun parse(lines: List<OcrLine>, filter: Game? = null, indexes: Map<Game, (String) -> Boolean> = emptyMap()): ScanHit? =
        parseAll(lines, filter, indexes).firstOrNull()

    /**
     * Every card in a photo, e.g. a binder page or several cards on a table.
     * [filter] limits detection to one game (null = recognise any game).
     * [indexes] tells, per indexed game, whether a code exists in that game's card index.
     * Pokémon name guesses are only kept when there is a single card, since a
     * name can't be matched to the right number when several cards are visible.
     */
    fun parseAll(lines: List<OcrLine>, filter: Game? = null, indexes: Map<Game, (String) -> Boolean> = emptyMap()): List<ScanHit> {
        fun wants(g: Game) = filter == null || filter == g

        if (wants(Game.MAGIC)) {
            findMagic(lines, nameFallback = filter == Game.MAGIC)?.let { return listOf(it) }
        }
        val codes = buildList {
            if (wants(Game.ONE_PIECE)) addAll(allOnePiece(lines))
            indexes.filterKeys { wants(it) }.forEach { (game, contains) -> addAll(findIndexed(lines, game, contains)) }
        }
        if (codes.isNotEmpty() || filter != null && filter != Game.POKEMON) return codes
        val numbers = allPokemonNumbers(lines)
        val name = if (numbers.size == 1) guessName(lines) else null
        return numbers.map { (number, total) -> ScanHit.Pokemon(number, total, name) }
    }

    fun findOnePiece(lines: List<OcrLine>): ScanHit.OnePiece? = allOnePiece(lines).firstOrNull()

    fun findPokemon(lines: List<OcrLine>): ScanHit.Pokemon? {
        val (number, total) = allPokemonNumbers(lines).firstOrNull() ?: return null
        return ScanHit.Pokemon(number, total, guessName(lines))
    }

    private fun allOnePiece(lines: List<OcrLine>): List<ScanHit.OnePiece> {
        val codes = lines.flatMap { line ->
            onePieceCode.findAll(line.text.uppercase()).map { m ->
                val prefix = m.groupValues[1].replace('0', 'O')
                ScanHit.OnePiece("$prefix${digits(m.groupValues[2])}-${digits(m.groupValues[3])}")
            }.toList()
        }
        // Promo codes ("P-001") are short and easy to misread, so only look for them when nothing else matched.
        val found = codes.ifEmpty {
            lines.flatMap { line ->
                onePiecePromo.findAll(line.text.uppercase()).map { m -> ScanHit.OnePiece("P-${digits(m.groupValues[1])}") }.toList()
            }
        }
        return found.distinct()
    }

    /** Collector numbers as (printed number, set size), lowest on the card first. */
    private fun allPokemonNumbers(lines: List<OcrLine>): List<Pair<String, Int>> =
        lines.sortedByDescending { it.top }.flatMap { line ->
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
        }.distinct()

    /**
     * Magic cards print "DMU • EN" with the collector number on the line above or beside it.
     * With [nameFallback] (the user chose Magic) a card without a readable set line is looked up by name.
     */
    fun findMagic(lines: List<OcrLine>, nameFallback: Boolean = false): ScanHit.Magic? {
        val setLine = lines.firstNotNullOfOrNull { l -> magicSetLine.find(l.text.uppercase())?.let { l to it.groupValues[1] } }
        if (setLine == null) {
            return if (nameFallback) magicName(lines)?.let { ScanHit.Magic(null, null, it) } else null
        }
        val (line, set) = setLine
        val number = lines
            .sortedBy { kotlin.math.abs(it.top - line.top) }
            .firstNotNullOfOrNull { l -> magicNumber.find(l.text.uppercase())?.groupValues?.get(1) }
            ?.trimStart('0')?.ifEmpty { "0" }
        return ScanHit.Magic(set.lowercase(), number, magicName(lines))
    }

    /** The Magic card name is the first line on the card. */
    private fun magicName(lines: List<OcrLine>): String? = lines
        .filter { it.top < 0.2f && it.text.count(Char::isLetter) >= 3 }
        .minByOrNull { it.top }
        ?.text?.replace(Regex("""[^\p{L} ,'’-]"""), "")?.trim()?.takeIf { it.length >= 3 }

    /** Codes in [lines] that exist in [game]'s card index, tolerating OCR's O/0 and spacing mistakes. */
    fun findIndexed(lines: List<OcrLine>, game: Game, contains: (String) -> Boolean): List<ScanHit.Indexed> =
        lines.flatMap { line ->
            val text = line.text.uppercase().replace(Regex("""\s*([/\-–_])\s*"""), "$1").replace('–', '-')
            codeToken.findAll(text).mapNotNull { m -> candidates(m.value).firstOrNull(contains) }.toList()
        }.distinct().map { ScanHit.Indexed(game, it) }

    private fun candidates(token: String): List<String> {
        val zeroed = token.replace('O', '0')
        val trimmed = token.trimEnd { it.isLetter() }
        return listOf(token, zeroed, trimmed, trimmed.replace('O', '0')).distinct()
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

    // ---- Grading-company slab labels ----

    /** Grading companies and the words that identify their labels. */
    private val graders = listOf(
        "PSA" to Regex("""\bPSA\b"""),
        "BGS" to Regex("""\bBECKETT\b|\bBGS\b"""),
        "CGC" to Regex("""\bCGC\b"""),
        "SGC" to Regex("""\bSGC\b"""),
        "TAG" to Regex("""\bTAG\s*GRAD|\bTAGGRADING\b|^TAG$"""),
        "ACE" to Regex("""\bACE\s*GRADING\b"""),
        "AOG" to Regex("""\bAOG\b"""),
        "GSG" to Regex("""\bGSG\b"""),
        "PI" to Regex("""\bPI\s*GRADING\b|\bPIGRADING\b"""),
        "PCA" to Regex("""\bPCA\b"""),
        "PGS" to Regex("""\bPGS\b"""),
        "HGA" to Regex("""\bHGA\b"""),
        "MNT" to Regex("""\bMNT\s*GRADING\b"""),
    )

    private const val GRADE = """(10|[1-9](?:\.5)?)"""
    private const val WORDS = """GEM\s*MT|GEM\s*MINT|GEM|PRISTINE|BLACK\s*LABEL|MINT\+?|NM-MT\+?|NM\+?|EX-MT\+?|EX\+?|VG-EX\+?|VG\+?|GOOD\+?|FAIR|FR|PR|POOR"""
    private val gradeAfterWords = Regex("""(?<![A-Z])($WORDS)\s*$GRADE(?![\d.])""")
    private val gradeBeforeWords = Regex("""(?<![\d.])$GRADE\s*($WORDS)(?![A-Z])""")
    private val bareGrade = Regex("""^$GRADE$""")
    private val cert = Regex("""(?<!\d)(\d{7,12})(?!\d)""")
    private val subgradeWords = Regex("""CENTER|CORNER|EDGE|SURFACE""")

    /** Reads the grading company, grade and cert number from a slab label, or null for a raw card. */
    fun parseGrade(lines: List<OcrLine>): GradeInfo? {
        val texts = lines.map { it.text.uppercase().trim() }
        val grader = graders.firstOrNull { (_, re) -> texts.any { re.containsMatchIn(it) } }?.first
        var grade: String? = null
        var words = ""
        for (t in texts) {
            if (subgradeWords.containsMatchIn(t)) continue
            val m = gradeAfterWords.find(t)?.let { it.groupValues[2] to it.groupValues[1] }
                ?: gradeBeforeWords.find(t)?.let { it.groupValues[1] to it.groupValues[2] }
            if (m != null) {
                grade = m.first
                words = m.second.replace(Regex("""\s+"""), " ")
                break
            }
        }
        // BGS and CGC print the grade as a big number on its own.
        if (grade == null && grader != null) {
            grade = texts.firstNotNullOfOrNull { t -> bareGrade.find(t)?.groupValues?.get(1) }
        }
        if (grader == null && grade == null) return null
        val all = texts.joinToString(" ")
        val qualifier = when {
            grade == "10" && (all.contains("BLACK LABEL") || grader == "BGS" && words == "PRISTINE") -> "Black Label"
            grade == "10" && grader == "CGC" && words == "PRISTINE" -> "Pristine"
            else -> null
        }
        val company = when {
            qualifier == "Black Label" -> "BGS"
            grader != null -> grader
            // "GEM MT 10" is PSA's wording; other companies write "GEM MINT".
            words.startsWith("GEM MT") -> "PSA"
            else -> null
        }
        return GradeInfo(company, grade, qualifier, texts.firstNotNullOfOrNull { cert.find(it)?.groupValues?.get(1) })
    }

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
