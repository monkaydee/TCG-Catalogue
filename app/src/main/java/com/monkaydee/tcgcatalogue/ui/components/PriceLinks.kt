package com.monkaydee.tcgcatalogue.ui.components

import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import java.net.URLEncoder

/** Search links for a card on the price sites, opened in the phone's browser. */
object PriceLinks {
    fun forCard(c: OwnedCard, currency: String): List<Pair<String, String>> {
        val number = when (c.game) {
            Game.POKEMON -> c.number.substringBefore('/').trimStart('0').ifEmpty { "0" }
            Game.MAGIC -> c.number.substringAfter(' ')
            else -> c.number
        }
        val name = c.name.substringBefore(" - ").replace(Regex("""\s*\([^)]*\)"""), "").trim()
        val grade = if (c.graded) listOfNotNull(c.grader?.takeUnless { it == "Other" }, c.grade, c.gradeQualifier).joinToString(" ") else null
        val gameWord = when (c.game) {
            Game.POKEMON -> "pokemon"
            Game.ONE_PIECE -> "one piece"
            Game.MAGIC -> "mtg"
            Game.DRAGON_BALL_FW, Game.DRAGON_BALL_SUPER -> "dragon ball"
            Game.UNION_ARENA -> "union arena"
            Game.WEISS_SCHWARZ -> "weiss schwarz"
            Game.NARUTO -> "naruto"
        }
        val ebayHost = if (currency == "EUR") "www.ebay.de" else "www.ebay.com"
        val ebayQuery = listOfNotNull(name, number, grade ?: gameWord).joinToString(" ")
        val links = mutableListOf(
            "eBay sold" to "https://$ebayHost/sch/i.html?_nkw=${enc(ebayQuery)}&LH_Sold=1&LH_Complete=1&_sop=13",
            "PriceCharting" to "https://www.pricecharting.com/search-products?type=prices&q=${enc("$name ${c.setName} $number")}",
        )
        cardmarketGame(c.game)?.let { g ->
            links += "Cardmarket" to "https://www.cardmarket.com/en/$g/Products/Search?searchString=${enc(if (c.game == Game.ONE_PIECE) c.number else name)}"
        }
        val tcgplayerId = c.cardId.toLongOrNull()?.takeIf { c.game.indexed }
        links += "TCGplayer" to (tcgplayerId?.let { "https://www.tcgplayer.com/product/$it" }
            ?: "https://www.tcgplayer.com/search/all/product?q=${enc("$name $number")}")
        return links
    }

    private fun cardmarketGame(game: Game) = when (game) {
        Game.POKEMON -> "Pokemon"
        Game.ONE_PIECE -> "OnePiece"
        Game.MAGIC -> "Magic"
        Game.DRAGON_BALL_SUPER, Game.DRAGON_BALL_FW -> "DragonBallSuper"
        Game.WEISS_SCHWARZ -> "WeissSchwarz"
        else -> null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
