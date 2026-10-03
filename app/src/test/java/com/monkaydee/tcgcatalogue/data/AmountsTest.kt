package com.monkaydee.tcgcatalogue.data

import com.monkaydee.tcgcatalogue.data.remote.Amounts
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountsTest {
    @Test fun amounts() {
        assertEquals(1249.0, Amounts.parse("1.249,00")!!, 0.0)
        assertEquals(1249.0, Amounts.parse("1,249.00")!!, 0.0)
        assertEquals(64.9, Amounts.parse("64,90")!!, 0.0)
        assertEquals(71.5, Amounts.parse("71.50")!!, 0.0)
    }
}
