package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.remote.EbayApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EbayTest {
    @Test fun parsesGermanAndEnglishSoldListings() {
        val blocks = listOf(
            "Shop on eBay\nBrand New\n$20.00",
            "Verkauft  1. Okt 2026\nMisty's Psyduck 193/182 CGC 9 Mint Destined Rivals Pokemon\nNeu (Sonstige)\nEUR 64,90\n+EUR 5,99 Versand",
            "Sold  Sep 30, 2026\nCGC 9 MISTY'S PSYDUCK 193/182 ILLUSTRATION RARE\nPre-Owned\n$71.50\n+$4.95 delivery",
            "Verkauft  28. Sep 2026\nMistys Enton 193/182 CGC 9.5 Gem Mint\nEUR 1.249,00",
        )
        val sales = EbayApi.parseSales(blocks)
        assertEquals(3, sales.size)
        assertEquals(64.90, sales[0].price, 0.001)
        assertEquals("EUR", sales[0].currency)
        assertEquals(71.50, sales[1].price, 0.001)
        assertEquals("USD", sales[1].currency)
        assertEquals(1249.0, sales[2].price, 0.001)
    }

    @Test fun onlySameCardSameGrade() {
        assertTrue(EbayApi.matches("Misty's Psyduck 193/182 CGC 9 Mint", "193", "CGC", "9", null))
        assertFalse(EbayApi.matches("Mistys Enton 193/182 CGC 9.5 Gem Mint", "193", "CGC", "9", null))
        assertFalse(EbayApi.matches("Misty's Psyduck 193/182 PSA 9", "193", "CGC", "9", null))
        assertFalse(EbayApi.matches("Misty's Psyduck 045/182 CGC 9", "193", "CGC", "9", null))
        assertFalse(EbayApi.matches("Lot of 3 Misty's Psyduck 193 CGC 9", "193", "CGC", "9", null))
        assertTrue(EbayApi.matches("PSA 10 GEM MINT Zekrom 115/113", "115", "PSA", "10", null))
        assertTrue(EbayApi.matches("Beckett 9.5 Gem Mint Zekrom 115/113", "115", "BGS", "9.5", null))
        assertFalse(EbayApi.matches("BGS 10 Black Label Zekrom 115/113", "115", "BGS", "10", null))
        assertTrue(EbayApi.matches("BGS 10 Black Label Zekrom 115/113", "115", "BGS", "10", "Black Label"))
    }

    @Test fun amounts() {
        assertEquals(1249.0, EbayApi.parseAmount("1.249,00")!!, 0.0)
        assertEquals(1249.0, EbayApi.parseAmount("1,249.00")!!, 0.0)
        assertEquals(64.9, EbayApi.parseAmount("64,90")!!, 0.0)
        assertEquals(71.5, EbayApi.parseAmount("71.50")!!, 0.0)
    }
}
