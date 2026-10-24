package com.pushpendratech.toolbox.core

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import kotlin.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Video + Audio tools, 100% on-device.
 * Strategy (documented, no fake operations):
 *  - Copy operations (trim / mute / extract-audio / merge) use MediaExtractor+MediaMuxer — no re-encode, fast, lossless.
 *    Muxer output container is MP4, so audio-only outputs are .m4a (AAC/MP3 track copied as-is where the muxer accepts it).
 *  - Re-encode operations (compress / change resolution) use AndroidX Media3 Transformer (Apache-2.0, Google) — genuine transcoding.
 *  - Android has NO built-in MP3 *encoder*, and FFmpeg's MP3 encoder (libmp3lame) brings GPL/LGPL distribution duties we avoid.
 *    Therefore "Video to MP3" extracts the audio track losslessly to .m4a and the UI says so plainly — it never writes a fake .mp3.
 *  - WAV/PCM operations (volume, speed-by-resampling, convert) decode with MediaCodec to PCM, process samples, and write real WAV or AAC(.m4a).
 */
object MediaTools {

    data class MediaInfo(
        val durationMs: Long = 0, val width: Int = 0, val height: Int = 0, val bitrate: Int = 0,
        val mime: String = "", val sampleRate: Int = 0, val channels: Int = 0, val rotation: Int = 0, val sizeBytes: Long = 0
    )

    fun inspect(file: File): MediaInfo {
        val r = MediaMetadataRetriever(); var info = MediaInfo(sizeBytes = file.length())
        try {
            r.setDataSource(file.absolutePath)
            fun s(k: Int) = r.extractMetadata(k)
            info = info.copy(
                durationMs = s(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                width = s(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = s(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                bitrate = s(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0,
                mime = s(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "",
                rotation = s(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                sampleRate = s(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            )
        } finally { r.release() }
        // Sample-rate/channels are more reliable from the extractor track format.
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i); val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio") && info.sampleRate == 0) {
                    info = info.copy(
                        sampleRate = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0,
                        channels = if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0
                    )
                }
            }
        } catch (e: Exception) { /* keep retriever info */ } finally { ex.release() }
        return info
    }

    fun frameAt(file: File, timeMs: Long, maxDim: Int = 1280): Bitmap {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val bmp = r.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: throw IllegalStateException("No frame found at that time. Try a slightly different second.")
            if (maxOf(bmp.width, bmp.height) <= maxDim) return bmp
            val s = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt().coerceAtLeast(1), (bmp.height * s).roundToInt().coerceAtLeast(1), true)
            if (scaled !== bmp) bmp.recycle(); return scaled
        } finally { r.release() }
    }

    /**
     * Lossless stream-copy engine. outputFormat is MediaMuxer.OutputFormat (MP4 for video, MP4 also used for .m4a audio).
     * trackFilter receives track mime, returns true to keep. Timestamps are re-based to zero.
     */
    fun muxCopy(input: File, out: File, startMs: Long = 0, endMs: Long = Long.MAX_VALUE, keepTrack: (String) -> Boolean) {
        val extractor = MediaExtractor(); extractor.setDataSource(input.absolutePath)
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val trackMap = HashMap<Int, Int>(); var maxDurationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i); val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (!keepTrack(mime)) continue
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    val d = format.getLong(MediaFormat.KEY_DURATION)
                    val clipped = minOf(d, endMs * 1000) - startMs * 1000
                    if (clipped > maxDurationUs) maxDurationUs = clipped
                    format.setLong(MediaFormat.KEY_DURATION, maxOf(0, clipped))
                }
                // Muxer rejects some optional keys:
                try { format.setString(MediaFormat.KEY_FRAME_RATE, null) } catch (e: Exception) { /* optional key — safe to ignore */ }
                trackMap[i] = muxer.addTrack(format)
            }
            require(trackMap.isNotEmpty()) { "No matching track found in this file (it may be unsupported or have no such stream)." }
            muxer.start()
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024); val info = MediaCodec.BufferInfo()
            for ((srcTrack, _) in trackMap) {
                extractor.selectTrack(srcTrack)
                if (startMs > 0) extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                var basePts: Long = -1
                while (true) {
                    val size = extractor.readSampleData(buffer, 0); if (size < 0) break
                    val pts = extractor.sampleTime; if (pts < 0) break
                    if (pts > endMs * 1000) break
                    if (basePts < 0) basePts = pts
                    info.offset = 0; info.size = size; info.presentationTimeUs = pts - basePts
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(trackMap.getValue(srcTrack), buffer, info)
                    extractor.advance()
                }
                extractor.unselectTrack(srcTrack)
            }
        } finally { try { muxer.stop() } catch (_: Exception) {}; muxer.release(); extractor.release() }
        if (out.length() == 0L) { out.delete(); throw IllegalStateException("Processing produced an empty file — the source format may not be copyable into MP4.") }
    }

    fun trimVideo(input: File, out: File, startMs: Long, endMs: Long) { require(endMs > startMs) { "End time must be after start time" }; muxCopy(input, out, startMs, endMs) { true } }
    fun muteVideo(input: File, out: File) = muxCopy(input, out) { it.startsWith("video") }
    fun extractAudio(input: File, out: File) = muxCopy(input, out) { it.startsWith("audio") }
    fun trimAudio(input: File, out: File, startMs: Long, endMs: Long) { require(endMs > startMs) { "End time must be after start time" }; muxCopy(input, out, startMs, endMs) { it.startsWith("audio") } }

    /** Merge same-codec audio files by appending samples with shifted timestamps (MP4/.m4a output). Throws a clear error if codecs/container differ too much. */
    fun mergeAudio(inputs: List<File>, out: File) {
        require(inputs.size >= 2) { "Select at least 2 audio files" }
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            var muxTrack = -1; var offsetUs = 0L; val buffer = ByteBuffer.allocate(2 * 1024 * 1024); val info = MediaCodec.BufferInfo()
            for (file in inputs) {
                val ex = MediaExtractor(); ex.setDataSource(file.absolutePath)
                try {
                    var audioIdx = -1; var format: MediaFormat? = null
                    for (i in 0 until ex.trackCount) { val f = ex.getTrackFormat(i); if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio")) { audioIdx = i; format = f; break } }
                    require(audioIdx >= 0 && format != null) { "${file.name} has no audio track" }
                    if (muxTrack < 0) { try { format.setString(MediaFormat.KEY_FRAME_RATE, null) } catch (e: Exception) { /* optional key — safe to ignore */ }; muxTrack = muxer.addTrack(format); muxer.start() }
                    ex.selectTrack(audioIdx); var lastPts = 0L
                    while (true) {
                        val size = ex.readSampleData(buffer, 0); if (size < 0) break
                        val pts = ex.sampleTime; if (pts < 0) break
                        info.offset = 0; info.size = size; info.presentationTimeUs = offsetUs + pts
                        info.flags = 0; lastPts = pts
                        muxer.writeSampleData(muxTrack, buffer, info); ex.advance()
                    }
                    offsetUs += lastPts + 20_000 // ~one frame gap
                } finally { ex.release() }
            }
        } finally { try { muxer.stop() } catch (_: Exception) {}; muxer.release() }
        if (out.length() == 0L) { out.delete(); throw IllegalStateException("Merge failed — the files may use different codecs. Convert them to M4A first, then merge.") }
    }

    // ---- Media3 Transformer: genuine re-encode for compress + resolution ----
    // Threading rule (learned from a real device crash, 2026-10-23): a Media3
    // Transformer is bound to its application thread — it must be CREATED,
    // STARTED and CANCELLED on the main thread. Calling it from Dispatchers.IO
    // throws IllegalStateException("Transformer is accessed on the wrong
    // thread"), and a cancel posted from a worker thread crashed the app.
    // So the whole interaction runs inside withContext(Dispatchers.Main);
    // the coroutine merely SUSPENDS there (main thread stays free) while
    // Transformer does the heavy work on its own internal threads.
    @OptIn(UnstableApi::class)
    suspend fun transcode(context: Context, input: File, out: File, videoBitrateBps: Int? = null, targetHeight: Int? = null, removeAudio: Boolean = false) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val mediaItem = MediaItem.fromUri(Uri.fromFile(input))
                val edited = EditedMediaItem.Builder(mediaItem).setRemoveAudio(removeAudio).apply {
                    if (targetHeight != null) setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(targetHeight))))
                }.build()
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
                // call itself is posted back to the main thread.
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

    // ---- PCM / WAV engine for audio convert, volume, speed ----
    data class Pcm(val samples: ShortArray, val sampleRate: Int, val channels: Int)

    fun decodeToPcm(input: File): Pcm {
        val extractor = MediaExtractor(); extractor.setDataSource(input.absolutePath)
        var track = -1; var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) { val f = extractor.getTrackFormat(i); if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio")) { track = i; format = f; break } }
        require(track >= 0 && format != null) { "No audio track found in this file" }
        extractor.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE); val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        if (mime == "audio/raw" || input.extension.equals("wav", true)) { extractor.release(); return readWav(input) }
        val codec = MediaCodec.createDecoderByType(mime); codec.configure(format, null, null, 0); codec.start()
        val outSamples = ArrayList<Short>(sampleRate * 10)
        val timeout = 10_000L; var inputDone = false; var outputDone = false
        val inBufInfo = MediaCodec.BufferInfo(); val outInfo = MediaCodec.BufferInfo()
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(timeout)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!; val size = extractor.readSampleData(buf, 0)
                        if (size < 0) { codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(outInfo, timeout)
                if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    buf.position(outInfo.offset); buf.limit(outInfo.offset + outInfo.size)
                    val shorts = ShortArray(outInfo.size / 2); buf.asShortBuffer().get(shorts); shorts.forEach { outSamples.add(it) }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (outInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally { codec.stop(); codec.release(); extractor.release() }
        return Pcm(outSamples.toShortArray(), sampleRate, channels)
    }

    fun writeWav(pcm: Pcm, out: File) {
        FileOutputStream(out).use { fos ->
            val dataBytes = pcm.samples.size * 2
            fun wInt(v: Int) { fos.write(v and 0xFF); fos.write((v shr 8) and 0xFF); fos.write((v shr 16) and 0xFF); fos.write((v shr 24) and 0xFF) }
            fun wShort(v: Int) { fos.write(v and 0xFF); fos.write((v shr 8) and 0xFF) }
            fos.write("RIFF".toByteArray()); wInt(36 + dataBytes); fos.write("WAVEfmt ".toByteArray())
            wInt(16); wShort(1); wShort(pcm.channels); wInt(pcm.sampleRate); wInt(pcm.sampleRate * pcm.channels * 2); wShort(pcm.channels * 2); wShort(16)
            fos.write("data".toByteArray()); wInt(dataBytes)
            val bytes = ByteArray(pcm.samples.size * 2); ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm.samples)
            fos.write(bytes)
        }
    }

    fun readWav(file: File): Pcm {
        FileInputStream(file).use { fis ->
            val all = fis.readBytes(); require(all.size > 44 && String(all, 0, 4) == "RIFF") { "Not a valid WAV file" }
            fun u16(o: Int) = (all[o].toInt() and 0xFF) or ((all[o + 1].toInt() and 0xFF) shl 8)
            fun u32(o: Int) = (all[o].toLong() and 0xFF) or ((all[o + 1].toLong() and 0xFF) shl 8) or ((all[o + 2].toLong() and 0xFF) shl 16) or ((all[o + 3].toLong() and 0xFF) shl 24)
            val channels = u16(22); val sampleRate = u32(24).toInt()
            var dataOffset = 12; var dataSize = 0
            while (dataOffset + 8 <= all.size) { val id = String(all, dataOffset, 4); val sz = u32(dataOffset + 4).toInt(); if (id == "data") { dataSize = sz; dataOffset += 8; break }; dataOffset += 8 + sz }
            require(dataSize > 0) { "WAV file has no audio data" }
            val samples = ShortArray(dataSize / 2)
            ByteBuffer.wrap(all, dataOffset, dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
            return Pcm(samples, sampleRate, channels)
        }
    }

    /** Volume: multiplies PCM samples by gain, hard-clamps to avoid clipping distortion beyond full scale. */
    fun changeVolume(input: File, out: File, gain: Float) {
        val pcm = decodeToPcm(input)
        val outSamples = ShortArray(pcm.samples.size) { i -> (pcm.samples[i] * gain).roundToInt().coerceIn(-32768, 32767).toShort() }
        writeWav(Pcm(outSamples, pcm.sampleRate, pcm.channels), out)
    }

    /** Speed by resampling — pitch shifts with speed (chipmunk/slow-mo effect). Stated plainly in UI; true pitch-preserving stretch needs a DSP library we do not bundle. */
    fun changeSpeed(input: File, out: File, speed: Float) {
        require(speed in 0.25f..4f) { "Speed must be between 0.25× and 4×" }
        val pcm = decodeToPcm(input)
        val frames = pcm.samples.size / pcm.channels; val newFrames = (frames / speed).toInt().coerceAtLeast(1)
        val outSamples = ShortArray(newFrames * pcm.channels)
        for (f in 0 until newFrames) {
            val srcFrame = (f * speed).toInt().coerceAtMost(frames - 1)
            for (c in 0 until pcm.channels) outSamples[f * pcm.channels + c] = pcm.samples[srcFrame * pcm.channels + c]
        }
        writeWav(Pcm(outSamples, pcm.sampleRate, pcm.channels), out)
    }

    /** Convert: target "wav" => decoded PCM WAV. Target "m4a" => PCM re-encoded to AAC via MediaCodec (below). */
    fun convertAudio(context: Context, input: File, out: File, targetExt: String) {
        when (targetExt.lowercase()) {
            "wav" -> writeWav(decodeToPcm(input), out)
            "m4a" -> encodePcmToAac(decodeToPcm(input), out)
            else -> throw IllegalArgumentException("Only WAV and M4A conversion are supported on-device (no MP3 encoder exists in Android).")
        }
    }

    private fun encodePcmToAac(pcm: Pcm, out: File) {
        val format = MediaFormat.createAudioFormat(MimeTypes.AUDIO_AAC, pcm.sampleRate, pcm.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
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
}
