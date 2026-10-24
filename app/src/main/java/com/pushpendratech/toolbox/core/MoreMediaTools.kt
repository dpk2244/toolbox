package com.pushpendratech.toolbox.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * v7 expansion batch (media): video speed / rotate-flip / aspect-crop via
 * Media3 Transformer effects, plus audio compress (bitrate) and fade in/out
 * on the app's existing PCM engine.
 *
 * Threading rule (the project's hard-learned lesson, mirrored exactly from
 * MediaTools.transcode): a Transformer is bound to its application thread —
 * it is created, started and cancelled on the MAIN thread; the coroutine
 * merely suspends there, and cancellation is posted back to the main thread.
 */
object MoreMediaTools {

    @OptIn(UnstableApi::class)
    suspend fun transcodeEffects(
        context: Context, input: File, out: File,
        videoEffects: List<Effect> = emptyList(),
        audioProcessors: List<AudioProcessor> = emptyList(),
        videoBitrateBps: Int? = null
    ) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val mediaItem = MediaItem.fromUri(Uri.fromFile(input))
                val edited = EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(audioProcessors, videoEffects))
                    .build()
                val encoderFactory = DefaultEncoderFactory.Builder(context.applicationContext).apply {
                    if (videoBitrateBps != null) setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBitrateBps).build())
                }.build()
                val transformer = Transformer.Builder(context.applicationContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) { if (cont.isActive) cont.resume(Unit) }
                        override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                            if (cont.isActive) cont.resumeWithException(IllegalStateException("Video processing failed (${exception.errorCodeName}). The format may be unsupported on this device."))
                        }
                    }).build()
                // Cancellation can be signalled from ANY thread, so the cancel
                // call itself is posted back to the main thread (see MediaTools).
                val mainHandler = Handler(Looper.getMainLooper())
                cont.invokeOnCancellation { mainHandler.post { runCatching { transformer.cancel() } } }
                try {
                    transformer.start(edited, out.absolutePath)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        }
        if (out.length() == 0L) { out.delete(); throw IllegalStateException("Video processing produced an empty file.") }
    }

    /**
     * Speed change the way Media3 1.5.1 intends it for Transformer:
     * [Effects.createExperimentalSpeedChangingEffect] returns a MATCHED pair —
     * an audio processor that re-times the sound and a video effect that
     * re-times the picture at the same constant rate — so A/V stay in sync.
     * The result is genuinely re-encoded; duration becomes ~original / speed.
     */
    @OptIn(UnstableApi::class)
    suspend fun changeVideoSpeed(context: Context, input: File, out: File, speed: Float) {
        require(speed in 0.25f..4f) { "Speed must be between 0.25× and 4×" }
        val provider = object : SpeedProvider {
            override fun getSpeed(timeUs: Long): Float = speed
            override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
        }
        val pair = Effects.createExperimentalSpeedChangingEffect(provider)
        transcodeEffects(context, input, out, videoEffects = listOf(pair.second), audioProcessors = listOf(pair.first))
    }

    /** Rotation (90/180/270) and mirror flips via ScaleAndRotateTransformation (a matrix effect — the encoder output really is rotated/flipped). */
    @OptIn(UnstableApi::class)
    suspend fun rotateFlipVideo(context: Context, input: File, out: File, rotationDegrees: Float, flipHorizontal: Boolean, flipVertical: Boolean) {
        val effect = ScaleAndRotateTransformation.Builder()
            .setScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f)
            .setRotationDegrees(rotationDegrees)
            .build()
        transcodeEffects(context, input, out, videoEffects = listOf(effect))
    }

    /** Centre-crop to a target aspect ratio (9:16, 1:1, 4:5, 16:9…) via Presentation LAYOUT_SCALE_TO_FIT_WITH_CROP. */
    @OptIn(UnstableApi::class)
    suspend fun cropToAspect(context: Context, input: File, out: File, aspectWidth: Float, aspectHeight: Float) {
        val effect = Presentation.createForAspectRatio(aspectWidth / aspectHeight, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        transcodeEffects(context, input, out, videoEffects = listOf(effect))
    }

    // ---- Audio: parameterized AAC encode (the pattern of MediaTools' private encoder, bitrate exposed) ----
    fun encodePcmToAac(pcm: MediaTools.Pcm, out: File, bitrateBps: Int) {
        val format = MediaFormat.createAudioFormat(MimeTypes.AUDIO_AAC, pcm.sampleRate, pcm.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MimeTypes.AUDIO_AAC); codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1; var muxStarted = false
        val info = MediaCodec.BufferInfo(); var inputDone = false; var outputDone = false; var sampleIndex = 0; var ptsUs = 0L
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!; buf.clear()
                        val remaining = pcm.samples.size - sampleIndex
                        if (remaining <= 0) { codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else {
                            val chunkShorts = minOf(buf.remaining() / 2, remaining); val bytes = ByteArray(chunkShorts * 2)
                            ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm.samples, sampleIndex, chunkShorts)
                            buf.put(bytes); codec.queueInputBuffer(inIdx, 0, bytes.size, ptsUs, 0)
                            ptsUs += (chunkShorts / pcm.channels) * 1_000_000L / pcm.sampleRate; sampleIndex += chunkShorts
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = muxer.addTrack(codec.outputFormat); muxer.start(); muxStarted = true }
                else if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    if (info.size > 0 && muxStarted) { buf.position(info.offset); buf.limit(info.offset + info.size); muxer.writeSampleData(track, buf, info) }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally { codec.stop(); codec.release(); if (muxStarted) try { muxer.stop() } catch (_: Exception) {}; muxer.release() }
    }

    private fun downmixToMono(pcm: MediaTools.Pcm): MediaTools.Pcm {
        if (pcm.channels != 2) return pcm
        val frames = pcm.samples.size / 2
        val out = ShortArray(frames) { i -> ((pcm.samples[i * 2].toInt() + pcm.samples[i * 2 + 1].toInt()) / 2).toShort() }
        return MediaTools.Pcm(out, pcm.sampleRate, 1)
    }

    /** Integer-factor (2×) downsample by averaging frame pairs — used for the optional "half sample rate" step. */
    private fun halveSampleRate(pcm: MediaTools.Pcm): MediaTools.Pcm {
        val frames = pcm.samples.size / pcm.channels
        val outFrames = frames / 2
        val out = ShortArray(outFrames * pcm.channels)
        for (f in 0 until outFrames) {
            for (c in 0 until pcm.channels) {
                val a = pcm.samples[(2 * f) * pcm.channels + c].toInt()
                val b = pcm.samples[(2 * f + 1) * pcm.channels + c].toInt()
                out[f * pcm.channels + c] = ((a + b) / 2).toShort()
            }
        }
        return MediaTools.Pcm(out, pcm.sampleRate / 2, pcm.channels)
    }

    /**
     * Compress = decode to PCM, optionally downmix to mono and/or halve the
     * sample rate, then re-encode to AAC at [bitrateBps]. Returns real
     * before/after byte sizes — if the source was already smaller, the caller
     * reports that instead of claiming a reduction.
     */
    fun compressAudio(input: File, out: File, bitrateBps: Int, toMono: Boolean, halfRate: Boolean): Pair<Long, Long> {
        var pcm = MediaTools.decodeToPcm(input)
        if (toMono) pcm = downmixToMono(pcm)
        if (halfRate && pcm.sampleRate >= 16_000) pcm = halveSampleRate(pcm)
        encodePcmToAac(pcm, out, bitrateBps)
        if (out.length() == 0L) { out.delete(); throw IllegalStateException("Audio compression produced an empty file.") }
        return input.length() to out.length()
    }

    /** Linear gain ramps at the start/end of the PCM, written as WAV (same convention as the volume/speed tools). */
    fun fadeAudio(input: File, out: File, fadeInSec: Double, fadeOutSec: Double) {
        require(fadeInSec >= 0 && fadeOutSec >= 0) { "Fade times cannot be negative" }
        val pcm = MediaTools.decodeToPcm(input)
        val frames = pcm.samples.size / pcm.channels
        val inFrames = (fadeInSec * pcm.sampleRate).toInt().coerceIn(0, frames)
        val outFrames = (fadeOutSec * pcm.sampleRate).toInt().coerceIn(0, frames)
        val samples = pcm.samples.copyOf()
        for (f in 0 until frames) {
            var gain = 1.0
            if (inFrames > 0 && f < inFrames) gain = minOf(gain, f.toDouble() / inFrames)
            if (outFrames > 0 && f >= frames - outFrames) gain = minOf(gain, (frames - 1 - f).toDouble() / outFrames)
            if (gain != 1.0) {
                for (c in 0 until pcm.channels) {
                    val idx = f * pcm.channels + c
                    samples[idx] = (samples[idx] * gain).roundToInt().coerceIn(-32768, 32767).toShort()
                }
            }
        }
        MediaTools.writeWav(MediaTools.Pcm(samples, pcm.sampleRate, pcm.channels), out)
    }
}
