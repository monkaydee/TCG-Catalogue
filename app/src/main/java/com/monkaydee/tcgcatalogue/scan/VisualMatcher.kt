package com.monkaydee.tcgcatalogue.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

/**
 * Picks the printing a scan shows when printings share a card number (One Piece alt arts,
 * parallel and special versions of the indexed games, same-size Pokémon sets): the scanned
 * picture is searched for the card and compared with each printing's image (see [CardVision]).
 * A special printing is only preselected when the picture really shows it; when it can't be told
 * apart from another printing that is worth a lot more or less, the card is marked to be checked.
 */
object VisualMatcher {
    /** Where the scanned picture comes from: how big the card is in it. */
    enum class Source(val maxSide: Int, val minCardHeight: Double) {
        /** The area around the camera's card guide: the card fills most of it. */
        CAMERA(320, 0.45),

        /** A whole photo: the card can be anywhere and smaller. */
        PHOTO(420, 0.22),
    }

    /** Printings within this price ratio count as the same when they can't be told apart. */
    private const val SAME_PRICE = 1.6

    suspend fun rank(context: Context, scene: Bitmap?, candidates: List<CardCandidate>, source: Source): List<CardCandidate> {
        if (candidates.isEmpty()) return candidates
        // The pictures to compare: per printing when printings have their own picture.
        val options = candidates.flatMapIndexed { i, c ->
            val own = c.variants.filter { it.imageUrl != null }.distinctBy { it.imageUrl }
            if (own.size > 1) own.map { Option(i, it.key, it.imageUrl!!) } else listOfNotNull(c.imageUrl?.let { Option(i, null, it) })
        }.take(16)
        if (options.size < 2) return candidates
        if (scene == null) return markUnchecked(candidates)
        val pixels = withContext(Dispatchers.Default) { scaled(scene, source.maxSide) }
        val loaded = coroutineScope {
            options.map { o -> async { load(context, o.url)?.let { b -> withContext(Dispatchers.Default) { CardVision.reference(scaled(b, 180)) } } } }.awaitAll()
        }
        val usable = options.indices.filter { loaded[it] != null }
        if (usable.size < 2) return markUnchecked(candidates)
        val refs = usable.map { loaded[it]!! }
        val matches = withContext(Dispatchers.Default) { CardVision.scores(pixels, refs, source.minCardHeight) } ?: return markUnchecked(candidates)
        val decision = CardVision.decide(matches, refs) ?: return markUnchecked(candidates)
        val chosen = options[usable[decision.index]]
        val alike = decision.lookAlikes.map { options[usable[it]] }
        // Unsure only matters when the printings it could also be are worth clearly more or less.
        val check = !decision.sure && priceSpread(candidates, alike) > SAME_PRICE

        return candidates.mapIndexed { i, c ->
            if (i != chosen.candidate) return@mapIndexed c
            c.copy(
                // The picture moves the matching card to the top when it is sure.
                score = c.score + if (decision.sure) 1.0 else 0.0,
                preferredVariant = chosen.variant ?: c.preferredVariant,
                printingCheck = check,
            )
        }.sortedByDescending { it.score }
    }

    /** Without a usable picture, printings that look different can't be preselected safely. */
    private fun markUnchecked(candidates: List<CardCandidate>) = candidates.map { c ->
        if (c.variants.mapNotNull { it.imageUrl }.distinct().size > 1 && c.preferredVariant == null) c.copy(printingCheck = true) else c
    }

    /** Ratio of the highest to the lowest price of the printings in [options]. */
    private fun priceSpread(candidates: List<CardCandidate>, options: List<Option>): Double {
        val prices = options.map { o ->
            val c = candidates[o.candidate]
            val v = o.variant?.let { k -> c.variants.firstOrNull { it.key == k } } ?: c.defaultVariant
            v.prices.values.maxOrNull() ?: return Double.MAX_VALUE
        }
        val low = prices.min()
        return if (low <= 0) Double.MAX_VALUE else prices.max() / low
    }

    /** True when the printing of [c] isn't known for sure and the user should pick it. */
    fun needsChoice(c: CardCandidate): Boolean = c.printingCheck

    private class Option(val candidate: Int, val variant: String?, val url: String)

    private suspend fun load(context: Context, url: String): Bitmap? = withTimeoutOrNull(10_000) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(240, 336)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withTimeoutOrNull null
        (result.drawable as? BitmapDrawable)?.bitmap
    }

    /** [b] at most [maxSide] pixels on its longest side, as pixels for [CardVision]. */
    private fun scaled(b: Bitmap, maxSide: Int): Pixels {
        val scale = maxSide.toFloat() / max(b.width, b.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true) else b
        val argb = IntArray(small.width * small.height)
        small.getPixels(argb, 0, small.width, 0, 0, small.width, small.height)
        val out = Pixels(small.width, small.height, argb)
        if (small !== b) small.recycle()
        return out
    }
}
