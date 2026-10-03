package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.monkaydee.tcgcatalogue.ui.theme.LocalLook
import java.io.File

/**
 * A screen's background: the user's own picture (dimmed with the background colour so text
 * stays readable) or the plain theme background.
 */
@Composable
fun Backdrop(picture: String?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(bg)) {
        if (picture != null) {
            AsyncImage(model = File(picture), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(bg.copy(alpha = LocalLook.current.imageDim)))
        }
        content()
    }
}

/** Top bar colours from the look; see-through over a background picture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarColors(overPicture: Boolean = false): TopAppBarColors {
    val look = LocalLook.current
    val container = if (overPicture) look.topBar.copy(alpha = 0.82f) else look.topBar
    return TopAppBarDefaults.topAppBarColors(
        containerColor = container,
        scrolledContainerColor = container,
        titleContentColor = look.onTopBar,
        navigationIconContentColor = look.onTopBar,
        actionIconContentColor = look.onTopBar,
    )
}

/** Screens drawn over a background picture leave their own background transparent. */
val Transparent = Color.Transparent
