package com.monkaydee.tcgcatalogue.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProTest {
    private fun bought(product: String = Pro.PRODUCT_ID, paid: Boolean = true, pending: Boolean = false, acknowledged: Boolean = true) =
        Pro.Bought(listOf(product), paid, pending, acknowledged, token = "t")

    @Test fun buildsWithoutThePaywallLeaveEveryToolOpen() {
        val open = Pro.State(enforced = false)
        assertTrue(ProFeature.entries.all { open.allows(it) })
        assertTrue(open.canCreateBinder(50))
    }

    @Test fun paywallLocksToolsUntilProIsOwned() {
        val free = Pro.State(enforced = true)
        assertTrue(ProFeature.entries.none { free.allows(it) })
        val owner = free.copy(owned = true)
        assertTrue(ProFeature.entries.all { owner.allows(it) })
    }

    @Test fun freeUsersGetOneOwnBinder() {
        val free = Pro.State(enforced = true)
        assertTrue(free.canCreateBinder(0))
        assertFalse(free.canCreateBinder(Pro.FREE_BINDERS))
        assertTrue(free.copy(owned = true).canCreateBinder(20))
    }

    @Test fun paidPurchaseUnlocksAndIsAcknowledgedOnce() {
        val fresh = Pro.resolve(remembered = false, storeAnswered = true, purchases = listOf(bought(acknowledged = false)))
        assertTrue(fresh.owned); assertEquals(1, fresh.toAcknowledge.size)
        val later = Pro.resolve(remembered = true, storeAnswered = true, purchases = listOf(bought()))
        assertTrue(later.owned); assertTrue(later.toAcknowledge.isEmpty())
    }

    @Test fun pendingPaymentDoesNotUnlock() {
        val r = Pro.resolve(remembered = false, storeAnswered = true, purchases = listOf(bought(paid = false, pending = true, acknowledged = false)))
        assertFalse(r.owned); assertTrue(r.pending); assertTrue(r.toAcknowledge.isEmpty())
    }

    @Test fun refundLocksAgainWhenTheStoreAnswers() {
        val r = Pro.resolve(remembered = true, storeAnswered = true, purchases = emptyList())
        assertFalse(r.owned)
    }

    @Test fun offlineKeepsTheLastKnownAnswer() {
        assertTrue(Pro.resolve(remembered = true, storeAnswered = false, purchases = emptyList()).owned)
        assertFalse(Pro.resolve(remembered = false, storeAnswered = false, purchases = emptyList()).owned)
    }

    @Test fun otherProductsDoNotUnlock() {
        val r = Pro.resolve(remembered = false, storeAnswered = true, purchases = listOf(bought(product = "something_else")))
        assertFalse(r.owned); assertFalse(r.pending)
    }
}
