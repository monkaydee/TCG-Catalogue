package com.monkaydee.tcgcatalogue

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.monkaydee.tcgcatalogue.scan.SharedPhotos
import com.monkaydee.tcgcatalogue.ui.AppNav
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        val repository = (application as TcgApp).repository
        setContent {
            TcgTheme {
                AppNav(repository)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Photos shared to the app ("Share → TCG Catalogue") go to the import screen. */
    private fun handleShare(intent: Intent?) {
        if (intent?.type?.startsWith("image/") != true) return
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        SharedPhotos.offer(uris)
    }
}
