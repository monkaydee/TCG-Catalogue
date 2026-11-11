package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import org.junit.Assert.*
import org.junit.Test

class PriceAvailabilityTest {
    private val card = OwnedCard(game=Game.POKEMON,cardId="x",variant="holo",variantLabel="Holo",name="Card",number="1",setId="s",setName="Set",priceCurrency="EUR")
    @Test fun missingAndInvalidQuotesStayUnknownButManualZeroIsReal() {
        assertNull(Money.unitOrNull(card,"EUR",0.9))
        assertEquals("—",Money.unitText(card.copy(price=0.0),"EUR",0.9))
        assertNull(Money.unitOrNull(card.copy(price=Double.NaN),"EUR",0.9))
        assertEquals(0.0,Money.unitOrNull(card.copy(manualPrice=0.0,manualCurrency="EUR"),"EUR",0.9)!!,0.0)
    }
    @Test fun totalsExposeMissingCopiesInsteadOfInventingAZeroValuation() {
        assertNull(Money.coverage(listOf(card.copy(quantity=3)),"EUR",0.9).amount)
        val partial=Money.coverage(listOf(card.copy(quantity=3),card.copy(price=10.0,quantity=2)),"EUR",0.9)
        assertEquals(20.0,partial.amount!!,0.0);assertEquals(3,partial.missingCopies)
        assertEquals(0.0,Money.coverage(emptyList(),"EUR",0.9).amount!!,0.0)
    }
}
