package com.monkaydee.tcgcatalogue.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.monkaydee.tcgcatalogue.data.CardRepository
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.attempt

/**
 * Identifies the cards in one photo: read the text, find card numbers, look them up, check them
 * against the printed name, and fall back to the picture. Photo import and the recognition test
 * (golden set) both use this, so what is measured is what users get.
 */
class PhotoIdentifier(private val context: Context, private val repo: CardRepository) {

    /** One card found in a photo, with how it was found. */
    data class Found(
        val hit: ScanHit?,
        val grade: GradeInfo?,
        /** The card cut out of the photo, for telling alt arts apart. */
        val picture: Bitmap?,
        /** Everything read on the card. */
        val texts: List<String>,
        /** Best first; empty when nothing matched. */
        val candidates: List<CardCandidate>,
        /** True when the cards come from the picture alone (always shown for review). */
        val byPicture: Boolean,
        /** True when the picture search couldn't run (index not downloaded, offline). */
        val pictureUnavailable: Boolean = false,
    )

    /** Text and number hits of a photo; throws when the photo can't be opened. */
    suspend fun read(photo: Uri, filter: Game?): PhotoRecognizer.Result {
        val enabled = repo.settings.current().enabledGames
        return PhotoRecognizer.recognize(context, photo) { lines ->
            CardTextParser.parseAll(lines, filter, repo.indexMatchers(enabled)).filter { filter != null || it.game in enabled }
        }
    }

    /** Everything in one go (the recognition test uses this). */
    suspend fun identify(photo: Uri, filter: Game? = null): List<Found> {
        val read = read(photo, filter)
        if (read.hits.isEmpty()) return listOf(byPicture(photo, read.texts, filter, null).copy(grade = read.grade))
        return read.hits.map { hit -> lookUp(photo, hit, read.grade, read.picture, read.texts, filter) }
    }

    /** A number hit looked up and checked against the printed name; the picture when it leads nowhere. */
    suspend fun lookUp(photo: Uri, hit: ScanHit, grade: GradeInfo?, picture: Bitmap?, texts: List<String>, filter: Game?): Found {
        val candidates = VisualMatcher.rank(context, picture, repo.checkedByName(repo.resolve(hit), texts), VisualMatcher.Source.PHOTO)
        if (candidates.isNotEmpty()) return Found(hit, grade, picture, texts, candidates, byPicture = false)
        // The number led nowhere (misread, or a print the databases don't have): try the picture.
        return byPicture(photo, texts, filter, hit).copy(grade = grade)
    }

    /** The card looked up by its picture, limited to the game its small print names. */
    suspend fun byPicture(photo: Uri, texts: List<String>, filter: Game?, hit: ScanHit?): Found {
        val games = filter?.let { setOf(it) } ?: repo.settings.current().enabledGames
        val result = attempt {
            val small = PhotoRecognizer.loadSmall(context, photo)
            val crops = PictureSearch.crops(small, fromCamera = false)
            val printed = CardTextParser.gameFromPrint(texts)?.takeIf { it in games }
            repo.candidatesFromPicture(PictureSearch.find(context, crops, printed?.let { setOf(it) } ?: games), texts)
                .map { it.copy(language = it.language ?: hit?.language ?: CardTextParser.detectLanguage(texts)) }
        }
        return Found(hit, null, null, texts, result.getOrDefault(emptyList()), byPicture = true, pictureUnavailable = result.isFailure)
    }
}
