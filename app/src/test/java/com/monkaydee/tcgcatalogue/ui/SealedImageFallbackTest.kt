package com.monkaydee.tcgcatalogue.ui

import com.monkaydee.tcgcatalogue.ui.screens.sealedImageCandidates
import org.junit.Assert.*
import org.junit.Test

class SealedImageFallbackTest {
    @Test fun retriesOnlySameProductOnItsOriginalTrustedCdn() {
        val choices=sealedImageCandidates("https://tcgplayer-cdn.tcgplayer.com/product/647175_in_400x400.jpg")
        assertEquals(3,choices.size)
        assertTrue(choices.all { it.startsWith("https://tcgplayer-cdn.tcgplayer.com/product/647175_") })
        assertEquals(listOf("https://i.ebayimg.com/images/german-box.jpg"),sealedImageCandidates("https://i.ebayimg.com/images/german-box.jpg"))
        assertEquals(emptyList<String>(),sealedImageCandidates(null))
    }
}
