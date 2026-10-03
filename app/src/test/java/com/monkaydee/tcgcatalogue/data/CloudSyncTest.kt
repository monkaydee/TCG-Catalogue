package com.monkaydee.tcgcatalogue.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncTest {
    private fun backup(savedAt: Long = 0, device: String = "", cards: Int = 0) = Backup(
        cards = emptyList(), snapshots = emptyList(), savedAt = savedAt, device = device, version = 2 + cards,
    )

    @Test fun fingerprintIgnoresWhenAndWhere() {
        assertEquals(CloudSync.fingerprint(backup(1, "a")), CloudSync.fingerprint(backup(2, "b")))
        assertNotEquals(CloudSync.fingerprint(backup(1, "a")), CloudSync.fingerprint(backup(1, "a", cards = 1)))
    }

    @Test fun newerOnlyFromAnotherPhone() {
        assertTrue(CloudSync.isNewerFromOtherPhone(backup(10, "other"), "me", 5))
        assertFalse(CloudSync.isNewerFromOtherPhone(backup(10, "me"), "me", 5))
        assertFalse(CloudSync.isNewerFromOtherPhone(backup(5, "other"), "me", 5))
        assertFalse(CloudSync.isNewerFromOtherPhone(backup(3, "other"), "me", 5))
    }

    @Test fun parseEmptyFileIsFresh() {
        assertNull(CloudSync.parse(ByteArray(0)))
        assertNull(CloudSync.parse("  \n".toByteArray()))
    }

    @Test fun parseRoundTrips() {
        val text = CloudSync.json.encodeToString(Backup.serializer(), backup(42, "dev"))
        val parsed = CloudSync.parse(text.toByteArray())!!
        assertEquals(42L, parsed.savedAt)
        assertEquals("dev", parsed.device)
    }

    @Test(expected = CloudException::class) fun parseGarbageThrows() {
        CloudSync.parse("not json".toByteArray())
    }
}
