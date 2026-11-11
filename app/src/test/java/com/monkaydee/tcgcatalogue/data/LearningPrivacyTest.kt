package com.monkaydee.tcgcatalogue.data

import org.junit.Assert.*
import org.junit.Test

class LearningPrivacyTest {
    @Test fun uploadsContainStableFingerprintsNotReadableOcr() {
        val hash=SharedLearning.readHash("ＳＶＰ １２３ • ピカチュウ")
        assertEquals(hash,SharedLearning.readHash("svp123ピカチュウ"))
        assertTrue(hash.matches(Regex("[a-f0-9]{64}")))
        assertFalse(hash.contains("ピカチュウ"))
        assertFalse(SharedLearning.PHOTOS_ENABLED)
    }
}
