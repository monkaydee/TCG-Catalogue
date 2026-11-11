package com.monkaydee.tcgcatalogue

import com.monkaydee.tcgcatalogue.data.CardIdentity
import com.monkaydee.tcgcatalogue.data.db.*
import org.junit.Assert.*
import org.junit.Test

class CardIdentityTest {
    private val slab = OwnedCard(id=1,game=Game.POKEMON,cardId="x",variant="normal",variantLabel="Normal",name="Test",number="1",setId="s",setName="Set",condition="PSA 10",grader="PSA",grade="10",certNumber=" 123456 ",addedAt=1)
    @Test fun oldBackupCertificateUsesSameIdentityAsDatabaseMigration() {
        val restored=CardIdentity.restored(slab)
        assertEquals("cert:123456",restored.copyKey)
        assertEquals(slab.quantity,restored.quantity)
    }
    @Test fun uncertifiedLegacySlabsRemainDistinctAndRepeatedRestoreIsStable() {
        val one=slab.copy(certNumber=null)
        val two=one.copy(id=2,addedAt=2)
        assertNotEquals(CardIdentity.restored(one).copyKey,CardIdentity.restored(two).copyKey)
        assertEquals(CardIdentity.restored(one),CardIdentity.restored(one))
    }
    @Test fun modernIdentitiesAndRawCollectionGroupingArePreserved() {
        assertEquals("slab:existing",CardIdentity.restored(slab.copy(copyKey="slab:existing")).copyKey)
        assertEquals("",CardIdentity.restored(slab.copy(grader=null,grade=null,certNumber=null)).copyKey)
    }
}
