package com.monkaydee.tcgcatalogue.scan

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Photos shared to the app from other apps, waiting to be picked up by the import screen. */
object SharedPhotos {
    private val _pending = MutableStateFlow<List<Uri>>(emptyList())
    val pending = _pending.asStateFlow()

    fun offer(uris: List<Uri>) {
        if (uris.isNotEmpty()) _pending.value = _pending.value + uris
    }

    fun take(): List<Uri> = _pending.getAndUpdate { emptyList() }
}
