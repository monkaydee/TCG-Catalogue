package com.monkaydee.tcgcatalogue.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.monkaydee.tcgcatalogue.R
import androidx.compose.ui.window.Dialog
import com.monkaydee.tcgcatalogue.grade.PreGrader
import com.monkaydee.tcgcatalogue.grade.SurfaceFinding
import com.monkaydee.tcgcatalogue.scan.PhotoRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun SurfaceInspectionPanel(side: PreGrader.Side, label: String, onCamera: () -> Unit, onChange: (PreGrader.Side) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch(Dispatchers.Main.immediate) {
            busy = true
            val path = withContext(Dispatchers.IO) { runCatching {
                val photo = PhotoRecognizer.loadSmall(context, uri, 4000)
                try {
                    val dir = File(context.cacheDir, "pregrade").apply { mkdirs() }
                    val file = File.createTempFile("surface-", ".png", dir)
                    file.outputStream().use { check(photo.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    file.absolutePath
                } finally { photo.recycle() }
            }.getOrNull() }
            busy = false
            if (path != null) { onChange(side.copy(surfacePhotos = (side.surfacePhotos + path).takeLast(4), surfaceFinding = SurfaceFinding.NOT_REVIEWED)); error = null }
            else error = context.getString(R.string.pre_surface_error)
        }
    }
    Text(stringResource(R.string.pre_surface_label, label), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.pre_surface_help), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.pre_surface_count, side.surfacePhotos.size), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCamera, enabled = !busy) { Text(stringResource(R.string.pre_surface_capture)) }
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Text(stringResource(R.string.pre_surface_upload)) }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        side.surfacePhotos.forEachIndexed { index, path ->
            OutlinedButton(onClick = { viewing = path }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.pre_surface_view, index + 1)) }
        }
    }
    for ((finding, text) in listOf(
        SurfaceFinding.NOT_REVIEWED to stringResource(R.string.wear_not_reviewed),
        SurfaceFinding.CLEAR_IN_PHOTOS to stringResource(R.string.pre_surface_clear),
        SurfaceFinding.SCRATCHES to stringResource(R.string.pre_surface_scratches),
        SurfaceFinding.DENT_OR_CREASE to stringResource(R.string.pre_surface_dent),
    )) {
        FilterChip(selected = side.surfaceFinding == finding, enabled = finding == SurfaceFinding.NOT_REVIEWED || side.surfacePhotos.size >= 2,
            onClick = { onChange(side.copy(surfaceFinding = finding)) }, label = { Text(text) })
    }
    Text(stringResource(R.string.pre_surface_scope), style = MaterialTheme.typography.bodySmall)
    viewing?.let { path ->
        Dialog(onDismissRequest = { viewing = null }) {
            Surface {
                Column(Modifier.padding(12.dp)) {
                    val image = remember(path) { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 }) }
                    // Drawing layers may outlive the dialog composition; UI images are GC-owned.
                    var zoom by remember(path) { mutableFloatStateOf(1f) }
                    var pan by remember(path) { mutableStateOf(Offset.Zero) }
                    image?.let { bitmap ->
                        Box(Modifier.fillMaxWidth().height(400.dp).clipToBounds().pointerInput(path) {
                            detectTransformGestures { _, movement, scale, _ -> zoom = (zoom * scale).coerceIn(1f, 8f); pan = if (zoom == 1f) Offset.Zero else pan + movement }
                        }) { Image(bitmap.asImageBitmap(), "Surface lighting view", Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y), contentScale = ContentScale.Fit) }
                    }
                    TextButton(onClick = { viewing = null }) { Text(stringResource(R.string.wear_done)) }
                    TextButton(onClick = { onChange(side.copy(surfacePhotos = side.surfacePhotos - path, surfaceFinding = SurfaceFinding.NOT_REVIEWED)); viewing = null }) { Text(stringResource(R.string.pre_surface_remove)) }
                }
            }
        }
    }
}
