package com.monkaydee.tcgcatalogue.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * How far the phone is tilted, each axis in -1..1 (x: left/right, y: towards/away from you,
 * relative to how it is being held). Read it only while drawing so tilting never recomposes.
 *
 * Uses the gravity sensor (or the accelerometer), only while the screen is resumed. Phones
 * without one get a slow automatic sweep; with animations turned off it stays still.
 */
@Composable
fun rememberDeviceTilt(): State<Offset> {
    val context = LocalContext.current
    val sensors = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember { sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val still = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    return when {
        still -> remember { mutableStateOf(Offset(0.3f, -0.2f)) }
        sensors == null || sensor == null -> autoSweep()
        else -> sensorTilt(sensors, sensor)
    }
}

@Composable
private fun autoSweep(): State<Offset> {
    val phase = rememberInfiniteTransition(label = "holoSweep")
        .animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "holoPhase")
    return remember(phase) { derivedStateOf { Offset(sin(phase.value) * 0.7f, cos(phase.value) * 0.35f) } }
}

@Composable
private fun sensorTilt(sensors: SensorManager, sensor: Sensor): State<Offset> {
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(sensors, sensor, lifecycle) {
        var gx = 0f
        var gy = 0f
        var baseY = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // Low-pass filter: smooth out hand jitter (and, for the accelerometer, movement).
                gx += (e.values[0] / SensorManager.GRAVITY_EARTH - gx) * 0.12f
                gy += (e.values[1] / SensorManager.GRAVITY_EARTH - gy) * 0.12f
                // Forward/back tilt is measured from a slowly following rest position, so it
                // works whether the phone is held upright or almost flat.
                if (baseY.isNaN()) baseY = gy
                baseY += (gy - baseY) * 0.008f
                val next = Offset((-gx * 1.8f).coerceIn(-1f, 1f), ((gy - baseY) * 3f).coerceIn(-1f, 1f))
                if ((next - tilt.value).getDistance() > 0.004f) tilt.value = next
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                Lifecycle.Event.ON_PAUSE -> sensors.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sensors.unregisterListener(listener)
        }
    }
    return tilt
}

/**
 * Shows [content] (a card or slab) as a physical object: a soft shadow, a slight 3D lean and a
 * faint holographic sheen that slides across with [tilt]. Everything happens in the draw/layer
 * phase, so it costs one gradient pass per frame and no recomposition.
 */
@Composable
fun HoloCard(
    tilt: State<Offset>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .graphicsLayer {
                val t = tilt.value
                rotationY = t.x * 5f
                rotationX = -t.y * 4f
                cameraDistance = 16f * density
                shadowElevation = 14.dp.toPx()
                this.shape = shape
                clip = true
                ambientShadowColor = Color.Black.copy(alpha = 0.35f)
                spotShadowColor = Color.Black.copy(alpha = 0.5f)
            }
            .drawWithContent {
                drawContent()
                val t = tilt.value
                val w = size.width
                val h = size.height
                // Rainbow foil band along the diagonal, moved by the tilt.
                val centre = Offset(w * (0.5f + t.x * 0.55f), h * (0.5f + t.y * 0.45f))
                val diagonal = hypot(w, h)
                val dir = Offset(w / diagonal, h / diagonal) * (diagonal * 0.45f)
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.32f to Color(0x2600E5FF),
                        0.5f to Color(0x30FFFFFF),
                        0.68f to Color(0x26FF4FD8),
                        0.84f to Color(0x14FFE57F),
                        1f to Color.Transparent,
                        start = centre - dir,
                        end = centre + dir,
                    ),
                )
                // Soft light spot, moving the opposite way like a reflection.
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x24FFFFFF), Color.Transparent),
                        center = Offset(w * (0.5f - t.x * 0.4f), h * (0.3f - t.y * 0.3f)),
                        radius = w * 0.75f,
                    ),
                )
            },
    ) {
        content()
    }
}
