package com.pushpendratech.toolbox.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pushpendratech.toolbox.data.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v1.4.0 sensor batch — Pushpendra's pick from the competitor study (SHZ's most-loved
 * tools are its sensor tools). Wiring mirrors the other batches: one line in
 * MainActivity's tool route BEFORE the category dispatch:
 *   tool.id in SENSOR_TOOL_IDS -> SensorToolScreen(tool, isFav, toggle, back)
 * Permissions: Level and Compass need none. Sound Meter uses RECORD_AUDIO (already in
 * the manifest for the Voice Recorder), asked only when the tool is opened.
 */
val SENSOR_TOOL_IDS: Set<String> = setOf(
    "sensor_level", "sensor_compass", "sensor_sound",
    "sensor_light", "sensor_metal", "sensor_pressure"
)

@Composable
fun SensorToolScreen(tool: Tool, isFav: Boolean, onToggleFav: () -> Unit, onBack: () -> Unit) {
    ToolScaffold(tool, isFav, onToggleFav, onBack) {
        when (tool.id) {
            "sensor_level" -> SpiritLevelBody()
            "sensor_compass" -> CompassBody()
            "sensor_sound" -> SoundMeterBody()
            "sensor_light" -> LightMeterBody()
            "sensor_metal" -> MetalDetectorBody()
            "sensor_pressure" -> BarometerBody()
            else -> Text("Unknown tool: ${tool.id}")
        }
    }
}

@Composable
private fun SpiritLevelBody() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accel = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    if (accel == null) {
        NoteCard("This device does not have an accelerometer, so the Spirit Level cannot run.")
        return
    }
    var tiltX by remember { mutableStateOf(0f) } // left-right lean, degrees (negative = right edge down)
    var tiltY by remember { mutableStateOf(0f) } // top-bottom lean, degrees
    var bubbleX by remember { mutableStateOf(0f) } // raw gravity components for the bubble
    var bubbleY by remember { mutableStateOf(0f) }
    DisposableEffect(accel) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val gx = e.values[0]; val gy = e.values[1]; val gz = e.values[2]
                val nx = Math.toDegrees(atan2(gx.toDouble(), gz.toDouble())).toFloat()
                val ny = Math.toDegrees(atan2(gy.toDouble(), gz.toDouble())).toFloat()
                tiltX = tiltX * 0.8f + nx * 0.2f
                tiltY = tiltY * 0.8f + ny * 0.2f
                bubbleX = bubbleX * 0.8f + gx * 0.2f
                bubbleY = bubbleY * 0.8f + gy * 0.2f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }
    val isLevel = abs(tiltX) < 0.6f && abs(tiltY) < 0.6f
    val ringColor = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Canvas(Modifier.size(280.dp)) {
            val c = center
            val r = size.minDimension / 2f
            drawCircle(color = ringColor, radius = r - 2.dp.toPx(), center = c, style = Stroke(width = 3.dp.toPx()))
            drawCircle(color = ringColor.copy(alpha = 0.5f), radius = r * 0.42f, center = c, style = Stroke(width = 1.5.dp.toPx()))
            drawLine(ringColor.copy(alpha = 0.5f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.5.dp.toPx())
            drawLine(ringColor.copy(alpha = 0.5f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.5.dp.toPx())
            drawCircle(color = primary, radius = 12.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
            val maxOff = r * 0.8f
            val bx = c.x + (bubbleX / 9.81f).coerceIn(-1f, 1f) * maxOff
            val by = c.y - (bubbleY / 9.81f).coerceIn(-1f, 1f) * maxOff
            drawCircle(color = if (isLevel) primary else primary.copy(alpha = 0.75f), radius = 17.dp.toPx(), center = Offset(bx, by))
            drawCircle(color = Color.White.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = Offset(bx - 5.dp.toPx(), by - 5.dp.toPx()))
        }
        if (isLevel) {
            Text("LEVEL ✓", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Left – Right", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.1f°".format(abs(tiltX)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Top – Bottom", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.1f°".format(abs(tiltY)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        NoteCard("Place the phone flat on the surface you want to check. A thick back cover can tilt the reading slightly.")
    }
}

@Composable
private fun CompassBody() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    // v1.4.2 rewrite (Pushpendra's device report: "compass nahi kaam kar raha" — the needle
    // sat pinned near N). v1.4.0 fused accelerometer + magnetometer by hand with heavy
    // double smoothing, which lagged badly and pinned whenever the magnetometer reported
    // low accuracy indoors. Now: prefer the phone's own fused ROTATION_VECTOR sensor
    // (system-level accel+gyro+mag fusion — what production compass apps use); the hand
    // fusion stays only as a fallback for phones without it, with lighter smoothing and
    // first-reading seeding so it starts true instead of crawling up from zero.
    val rotVec = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val accel = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val mag = remember { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    if (rotVec == null && (accel == null || mag == null)) {
        NoteCard("This device does not have a compass (magnetometer) sensor, so the Compass cannot run.")
        return
    }
    var azimuth by remember { mutableStateOf(0f) }
    var lowAccuracy by remember { mutableStateOf(false) }
    DisposableEffect(rotVec, accel, mag) {
        var sinS = 0.0
        var cosS = 1.0
        var seeded = false
        fun onAzimuth(degIn: Float) {
            val rad = Math.toRadians(degIn.toDouble())
            if (!seeded) { sinS = sin(rad); cosS = cos(rad); seeded = true }
            else { sinS = sinS * 0.55 + sin(rad) * 0.45; cosS = cosS * 0.55 + cos(rad) * 0.45 }
            var deg = Math.toDegrees(atan2(sinS, cosS)).toFloat()
            if (deg < 0) deg += 360f
            azimuth = deg
        }
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var haveGravity = false
        var haveMag = false
        var gravitySeeded = false
        var magSeeded = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val r = FloatArray(9)
                        val orient = FloatArray(3)
                        SensorManager.getRotationMatrixFromVector(r, e.values)
                        SensorManager.getOrientation(r, orient)
                        onAzimuth(Math.toDegrees(orient[0].toDouble()).toFloat())
                    }
                    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD -> {
                        val isAccel = e.sensor.type == Sensor.TYPE_ACCELEROMETER
                        val target = if (isAccel) gravity else geomagnetic
                        if ((isAccel && !gravitySeeded) || (!isAccel && !magSeeded)) {
                            e.values.copyInto(target)
                            if (isAccel) gravitySeeded = true else magSeeded = true
                        } else {
                            for (i in 0..2) target[i] = target[i] * 0.5f + e.values[i] * 0.5f
                        }
                        if (isAccel) haveGravity = true else haveMag = true
                        if (!haveGravity || !haveMag) return
                        val r = FloatArray(9)
                        val orient = FloatArray(3)
                        if (SensorManager.getRotationMatrix(r, null, gravity, geomagnetic)) {
                            SensorManager.getOrientation(r, orient)
                            onAzimuth(Math.toDegrees(orient[0].toDouble()).toFloat())
                        }
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {
                if (s?.type == Sensor.TYPE_MAGNETIC_FIELD || s?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    lowAccuracy = a == SensorManager.SENSOR_STATUS_UNRELIABLE || a == SensorManager.SENSOR_STATUS_ACCURACY_LOW
                }
            }
        }
        if (rotVec != null) {
            sensorManager.registerListener(listener, rotVec, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val dirName = names[(((azimuth + 22.5f) / 45f).toInt()) % 8]
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.size(300.dp)) {
                val c = center
                val r = size.minDimension / 2f
                drawCircle(color = onSurface.copy(alpha = 0.25f), radius = r - 2.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
                withTransform({ rotate(-azimuth, c) }) {
                    for (deg in 0 until 360 step 15) {
                        val rad = Math.toRadians(deg.toDouble())
                        val long = deg % 90 == 0
                        val inner = if (long) r * 0.82f else r * 0.89f
                        drawLine(
                            color = if (deg == 0) Color.Red else onSurface.copy(alpha = 0.7f),
                            start = Offset(c.x + (inner * sin(rad)).toFloat(), c.y - (inner * cos(rad)).toFloat()),
                            end = Offset(c.x + (r * 0.96f * sin(rad)).toFloat(), c.y - (r * 0.96f * cos(rad)).toFloat()),
                            strokeWidth = if (long) 3.dp.toPx() else 1.5.dp.toPx()
                        )
                    }
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 15.dp.toPx()
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    val letters = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)
                    for ((letter, deg) in letters) {
                        val rad = Math.toRadians(deg)
                        val lx = c.x + (r * 0.66f * sin(rad)).toFloat()
                        val ly = c.y - (r * 0.66f * cos(rad)).toFloat() + 7.dp.toPx()
                        paint.color = if (letter == "N") Color.Red.toArgb() else onSurface.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(letter, lx, ly, paint)
                    }
                }
                // fixed needle marker at the top (the direction the phone faces)
                drawLine(color = primary, start = Offset(c.x, c.y - r - 6.dp.toPx()), end = Offset(c.x, c.y - r + 10.dp.toPx()), strokeWidth = 4.dp.toPx())
            }
        }
        Text("${azimuth.toInt()}° $dirName", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        if (lowAccuracy) {
            NoteCard("Compass accuracy is low right now — wave the phone in a figure-8 (∞) motion a few times to calibrate it.")
        }
        NoteCard("Shows magnetic north. Keep away from metal, magnets, speakers and phone covers with magnetic clasps for a true reading.")
    }
}

@Composable
private fun SoundMeterBody() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var running by remember { mutableStateOf(false) }
    var db by remember { mutableStateOf(0f) }
    var minDb by remember { mutableStateOf<Float?>(null) }
    var maxDb by remember { mutableStateOf<Float?>(null) }
    var avgDb by remember { mutableStateOf<Float?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var recorder by remember { mutableStateOf<AudioRecord?>(null) }
    val scope = rememberCoroutineScope()

    fun releaseRecorder() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
    }
    fun stopMeter() {
        job?.cancel(); job = null
        running = false
        releaseRecorder()
    }
    fun startMeter() {
        note = null
        minDb = null; maxDb = null; avgDb = null; db = 0f
        val rate = 44100
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf * 2, 4096))
        } catch (e: Exception) {
            note = "Could not open the microphone: ${e.message ?: "unavailable"}"
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            note = "The microphone is unavailable right now — it may be in use by another app."
            return
        }
        recorder = rec
        running = true
        var sum = 0.0
        var count = 0L
        job = scope.launch(Dispatchers.IO) {
            val buf = ShortArray(2048)
            try {
                rec.startRecording()
                while (isActive) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var acc = 0.0
                    for (i in 0 until n) acc += buf[i].toDouble() * buf[i].toDouble()
                    val rms = sqrt(acc / n)
                    // dBFS from the sample level, + a typical phone-mic offset ≈ dB SPL estimate.
                    val v = (20.0 * log10(max(rms, 1.0) / 32768.0) + 90.0).toFloat().coerceIn(0f, 130f)
                    db = if (count == 0L) v else db * 0.6f + v * 0.4f
                    sum += v; count++
                    avgDb = (sum / count).toFloat()
                    minDb = minDb?.let { minOf(it, v) } ?: v
                    maxDb = maxDb?.let { maxOf(it, v) } ?: v
                }
            } catch (e: Exception) {
                note = "Meter stopped: ${e.message ?: "microphone error"}"
                running = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) startMeter() else note = "Mic permission is required for the Sound Meter. You can allow it from the button below or in system Settings → Apps."
    }
    DisposableEffect(Unit) { onDispose { job?.cancel(); runCatching { recorder?.stop() }; runCatching { recorder?.release() } } }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!hasPermission) {
            NoteCard("The Sound Meter needs microphone permission. It only measures how loud it is — nothing is ever recorded or saved.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
        } else {
            Text("${db.toInt()}", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
            Text("decibels (dB) — right now", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val frac = (db / 120f).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth(frac).height(18.dp)
                        .background(if (db > 85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatCol("Min", minDb?.let { "${it.toInt()} dB" } ?: "—")
                StatCol("Average", avgDb?.let { "${it.toInt()} dB" } ?: "—")
                StatCol("Max", maxDb?.let { "${it.toInt()} dB" } ?: "—")
            }
            Button(onClick = { if (running) stopMeter() else startMeter() }) {
                Text(if (running) "Stop" else "Start measuring")
            }
            NoteCard("Rough guide: whisper ≈ 30 dB · normal talk ≈ 60 dB · traffic ≈ 85 dB · very loud ≈ 100+ dB. Prolonged sound above 85 dB can harm hearing.")
        }
        note?.let { NoteCard(it) }
        NoteCard("Estimate only — phone microphones are not calibrated like a professional sound-level meter, so treat the number as a close guide, not a lab reading.")
    }
}

@Composable
private fun LightMeterBody() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val light = remember { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }
    if (light == null) {
        NoteCard("This device does not have a light sensor, so the Light Meter cannot run.")
        return
    }
    var lux by remember { mutableStateOf(0f) }
    DisposableEffect(light) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { lux = lux * 0.6f + e.values[0] * 0.4f }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensorManager.registerListener(listener, light, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }
    val label = when {
        lux < 1f -> "Near darkness"
        lux < 10f -> "Very dark"
        lux < 50f -> "Dim room"
        lux < 200f -> "Normal room light"
        lux < 500f -> "Bright office / classroom"
        lux < 1000f -> "Very bright"
        lux < 10000f -> "Daylight (in shade)"
        else -> "Direct sunlight"
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("💡", style = MaterialTheme.typography.displayMedium)
        Text("${lux.toInt()}", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        Text("lux — $label", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val frac = (sqrt(lux) / sqrt(10000f)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(frac).height(18.dp).background(Color(0xFFFFB300)))
        }
        NoteCard("Point the top edge of the phone (where the light sensor sits, near the front camera) towards the light. Rough guide: living room ≈ 50–200 lux · office ≈ 300–500 · a sunny day outside ≈ 10,000 or more.")
    }
}

@Composable
private fun MetalDetectorBody() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val mag = remember { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    if (mag == null) {
        NoteCard("This device does not have a magnetometer, so the Metal Detector cannot run.")
        return
    }
    // v1.4.2 improvement (Pushpendra's device finding): small iron only nudges the total
    // field a little, so the old absolute ">100 µT" alert almost never fired. Now the tool
    // learns the resting field (baseline) when it opens and alerts on the RISE above it —
    // a +22 µT rise is a real signal even when the total still looks "normal".
    var field by remember { mutableStateOf(0f) } // total magnetic field, µT
    var baseline by remember { mutableStateOf(0f) } // resting field; 0 = still learning
    var peakRise by remember { mutableStateOf(0f) }
    DisposableEffect(mag) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val total = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                field = if (field == 0f) total else field * 0.7f + total * 0.3f
                if (baseline == 0f) {
                    baseline = field // first stable reading becomes the reference
                } else {
                    val riseNow = field - baseline
                    if (riseNow > peakRise) peakRise = riseNow
                    // Slow drift only while nothing is detected, so moving to a different
                    // spot does not leave a stale baseline behind.
                    if (riseNow < 10f) baseline = baseline * 0.995f + field * 0.005f
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensorManager.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }
    val rise = if (baseline > 0f) field - baseline else 0f
    val detected = rise > 22f
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator }
    androidx.compose.runtime.LaunchedEffect(detected) {
        if (detected) runCatching {
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(70, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("🧲", style = MaterialTheme.typography.displayMedium)
        Text("${field.toInt()} µT", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        if (detected) {
            Text("⚠ METAL NEARBY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        } else if (baseline == 0f) {
            Text("Learning the normal field here…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Normal — just Earth's magnetic field", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val frac = (rise / 120f).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth(frac).height(18.dp)
                    .background(if (detected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCol("Rise above normal", "+${rise.toInt().coerceAtLeast(0)} µT")
            StatCol("Peak rise", "+${peakRise.toInt()} µT")
        }
        Button(onClick = { baseline = field; peakRise = 0f }) { Text("Re-learn normal (metal away)") }
        NoteCard("When this tool opens it first learns the normal field around you, then watches for a RISE — iron and steel disturb the field, magnets disturb it the most. Move the phone slowly, 1–2 cm from the surface; the back-top senses the strongest on most phones. Small iron raises the field only a little — that is normal physics — and non-magnetic metals (aluminium, copper, gold, silver) cannot be detected by any phone.")
    }
}

@Composable
private fun BarometerBody() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val pressure = remember { sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) }
    if (pressure == null) {
        NoteCard("This device does not have a barometer (air pressure) sensor — many phones skip it — so the Barometer cannot run.")
        return
    }
    var hpa by remember { mutableStateOf(0f) }
    DisposableEffect(pressure) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { hpa = if (hpa == 0f) e.values[0] else hpa * 0.8f + e.values[0] * 0.2f }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensorManager.registerListener(listener, pressure, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }
    val altitude = if (hpa > 0f) SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hpa) else 0f
    val weather = when {
        hpa >= 1022f -> "High pressure — usually clear, settled weather"
        hpa in 1f..1005f -> "Low pressure — weather may change, rain is likelier"
        hpa > 0f -> "Normal pressure"
        else -> "Reading…"
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("🌡️", style = MaterialTheme.typography.displayMedium)
        Text(if (hpa > 0f) "%.1f".format(hpa) else "—", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        Text("hPa — air pressure", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCol("Estimated altitude", if (hpa > 0f) "${altitude.toInt()} m" else "—")
            StatCol("Sea-level standard", "1013.25 hPa")
        }
        Text(weather, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        NoteCard("Air pressure changes with the weather, so the altitude is an estimate worked out from standard sea-level pressure — the same trick weather apps and watches use.")
    }
}

@Composable
private fun StatCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
