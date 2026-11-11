package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import java.util.UUID

/** Old JSON backups predate physical-slab identity, even when the database has already upgraded. */
object CardIdentity {
    fun restored(card: OwnedCard): OwnedCard {
        if (!card.graded) return card.copy(copyKey = "")
        if (card.copyKey.isNotBlank()) return card
        val certificate = card.certNumber?.trim()?.takeIf { it.isNotEmpty() }
        val legacy = listOf(card.game.name, card.cardId, card.variant, card.condition, card.language, card.addedAt, card.id).joinToString("|")
        return card.copy(copyKey = certificate?.let { "cert:$it" }
            ?: "slab:restored:${UUID.nameUUIDFromBytes(legacy.toByteArray(Charsets.UTF_8))}")
    }
}
