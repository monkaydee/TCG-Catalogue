package com.monkaydee.tcgcatalogue.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import com.monkaydee.tcgcatalogue.ui.components.PsaDisplayLabel
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.grade.AutomaticPreGrade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** A short presentation, never a real certification slab or an actual PSA grade. */
@Composable
internal fun PreGradeReveal(photo: Bitmap, title: String, result: AutomaticPreGrade.Result, onContinue: () -> Unit,
                            playAnimation: Boolean = true) {
    var settled by rememberSaveable { mutableStateOf(!playAnimation) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var hint by remember { mutableStateOf(false) }
    val entrance = remember { Animatable(if (settled) 1f else 0f) }
    val paper = remember { Animatable(if (revealed) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val infinite = rememberInfiniteTransition(label = "pull-hint")
    val pulse by infinite.animateFloat(-1f, 1f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "synchronized-pull")
    LaunchedEffect(Unit) {
        if (!settled) { entrance.animateTo(1f, tween(2800, easing = FastOutSlowInEasing)); settled = true }
        delay(2200); hint = true
    }
    fun reveal() {
        if (!settled || revealed) return
        revealed = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { paper.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF153F43), Color(0xFF080F14))))) {
        val width = minOf(maxWidth * .76f, (maxHeight - 180.dp).coerceAtLeast(240.dp) * .64f, 310.dp)
        val density = LocalDensity.current
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height * .44f)
            val radius = size.minDimension * (.2f + .25f * entrance.value)
            repeat(16) { index ->
                val angle = index * Math.PI / 8 + entrance.value * .4
                val start = Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
                val end = Offset(center.x + cos(angle).toFloat() * (radius + 14), center.y + sin(angle).toFloat() * (radius + 14))
                drawLine(Color(0xFF7DF3DF).copy(alpha = .18f), start, end, 2f)
            }
            drawCircle(Color(0xFF6DDACF).copy(alpha = .07f), radius * .92f, center)
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(if (result.scope == AutomaticPreGrade.Scope.CENTERING_ONLY) R.string.pre_quick_center_only else R.string.pre_quick_front_only), color = Color(0xFF9CECDF), style = MaterialTheme.typography.labelLarge)
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        Box(Modifier.align(Alignment.Center).offset(y = (-16).dp).width(width).aspectRatio(.64f)
            .graphicsLayer {
                rotationY = -360f * (1 - entrance.value)
                rotationX = (1 - entrance.value) * 18
                rotationZ = (1 - entrance.value) * -9
                cameraDistance = 14 * density.density
                scaleX = .72f + .28f * entrance.value; scaleY = scaleX
            }.shadow(24.dp, RoundedCornerShape(22.dp)).testTag("pregrade_reveal_slab")) {
            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(width * .065f))
                .background(Brush.linearGradient(listOf(Color(0xFFA5BDC6), Color(0xFFEBF7FC), Color(0xFF6B8A96), Color(0xFFD3EDF1))))
                .border(3.dp, Color(0xFFCDDDE3), RoundedCornerShape(width * .065f)).padding(width * .045f),
                verticalArrangement = Arrangement.spacedBy(width * .065f)) {
                PsaDisplayLabel(title, "CardNavo · photo estimate", null, width, null, display = true) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (revealed) Text(result.grade.toString(), color = Color(0xFF16262B),
                            style = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold, fontSize = (width.value * .15f).sp),
                            modifier = Modifier.testTag("pregrade_revealed_grade"))
                        if (paper.value < 1) {
                            var dragging by remember { mutableStateOf(false) }
                            var dragProgress by remember { mutableFloatStateOf(0f) }
                            val pixels = with(density) { (width * .27f).toPx() }
                            val description = stringResource(R.string.pre_quick_paper)
                            Box(Modifier.fillMaxSize().graphicsLayer {
                                translationX = paper.value * pixels * 1.8f + if (settled && !dragging && !revealed) pulse * 2 * density.density else 0f
                                rotationZ = -4f + if (settled && !dragging && !revealed) pulse * 1.2f else 0f
                                alpha = 1 - paper.value
                            }.shadow(4.dp, RoundedCornerShape(2.dp)).background(Color(0xFFF5EDD9))
                                .border(1.dp, Color(0xFFCFC4AC), RoundedCornerShape(2.dp))
                                .testTag("pregrade_paper")
                                .semantics { contentDescription = description; onClick(description) { reveal(); true } }
                                .pointerInput(settled, revealed) {
                                    detectHorizontalDragGestures(onDragStart = { dragging = true; dragProgress = paper.value }, onDragCancel = {
                                        dragging = false; scope.launch { paper.animateTo(0f, spring()) }
                                    }, onDragEnd = {
                                        dragging = false
                                        if (dragProgress >= .3f) reveal() else scope.launch { paper.animateTo(0f, spring()) }
                                    }) { change, amount ->
                                        if (settled && !revealed) {
                                            change.consume()
                                            dragProgress = (dragProgress + amount / (pixels * 1.8f)).coerceIn(0f, .9f)
                                            val next = dragProgress
                                            scope.launch { paper.snapTo(next) }
                                        }
                                    }
                                }, contentAlignment = Alignment.Center) {
                                Text("?", color = Color(0xFF9D8F73), fontSize = (width.value * .09f).sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Image(photo.asImageBitmap(), null, Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(width * .025f)).border(2.dp, Color(0xFF859FA8), RoundedCornerShape(width * .025f)),
                    contentScale = ContentScale.Fit)
            }
            if (settled && !revealed && hint) Row(
                Modifier.align(Alignment.TopEnd).offset(x = -width * .045f, y = width * .325f)
                    .width(width * .25f).height(width * .06f)
                    .graphicsLayer { translationX = pulse * 3 * density.density }
                    .testTag("pregrade_pull_hint"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pre_quick_pull), color = Color(0xFF173F45),
                    style = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif,
                        fontSize = (width.value * .037f).sp, fontWeight = FontWeight.Bold))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Color(0xFF173F45), modifier = Modifier.size(width * .055f))
            }
            // Light sweep across the plastic during the short spin.
            if (!settled) Box(Modifier.fillMaxSize().graphicsLayer { alpha = .22f * (1 - entrance.value) }
                .background(Brush.linearGradient(listOf(Color.Transparent, Color.White, Color.Transparent))))
        }
        if (!settled) Text(stringResource(R.string.pre_quick_rotating), Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            color = Color.White, style = MaterialTheme.typography.bodySmall)
        AnimatedVisibility(revealed, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 64.dp),
            enter = fadeIn() + slideInVertically { it / 2 }) {
            Button(onContinue, Modifier.testTag("pregrade_reveal_continue")) {
                Text(stringResource(if (result.grade >= 9) R.string.pre_quick_nice else R.string.pre_quick_meeh))
            }
        }
        Text(stringResource(R.string.pre_quick_short_notice), Modifier.align(Alignment.BottomCenter).padding(16.dp),
            color = Color(0xFFBDCECE), style = MaterialTheme.typography.labelSmall)
    }
}
