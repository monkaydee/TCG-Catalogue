package com.monkaydee.tcgcatalogue

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.monkaydee.tcgcatalogue.scan.SharedPhotos
import com.monkaydee.tcgcatalogue.ui.AppNav
import com.monkaydee.tcgcatalogue.ui.theme.LocalLook
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        val repository = (application as TcgApp).repository
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use the whole screen, also next to the camera cut-out.
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lifecycleScope.launch {
            repository.settings.flow.map { it.fullScreen }.distinctUntilChanged().collect {
                fullScreen = it
                applyFullScreen()
            }
        }
        setContent {
            val settings by repository.settings.flow.collectAsState(initial = null)
            // Wait for the stored look, so the app doesn't flash in the default theme first.
            val look = settings?.look ?: return@setContent
            TcgTheme(look) {
                val dark = LocalLook.current.dark
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                AppNav(repository)
            }
        }
    }

    private var fullScreen = true

    /** Hides the status and navigation bars; a swipe from the edge shows them for a moment. */
    private fun applyFullScreen() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullScreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs, the photo picker or the keyboard can bring the bars back.
        if (hasFocus) applyFullScreen()
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
