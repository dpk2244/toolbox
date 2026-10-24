package com.pushpendratech.toolbox.core

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max

/**
 * Minimal animated-GIF writer (GIF89a + LZW), written for this project.
 * Uses a fixed 256-colour global palette built from a NeuQuant-style simple quantizer:
 * to keep this dependency-free and robust we use a uniform 3-3-2 palette (good for
 * short preview GIFs, documented limitation — colours are approximate).
 */
class AnimatedGifEncoder(private val out: OutputStream) {
    private var width = 0; private var height = 0; private var delayMs = 100; private var started = false
    fun setDelay(ms: Int) { delayMs = ms }
    fun start(w: Int, h: Int) {
        width = w; height = h
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(w); writeShort(h)
        out.write(byteArrayOf(0xF7.toByte(), 0, 0)) // GCT present, 256 colours (3-3-2)
        for (r in 0..7) for (g in 0..7) for (b in 0..3) {
            out.write(r * 255 / 7); out.write(g * 255 / 7); out.write(b * 255 / 3)
        }
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 11)); out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(3, 1, 0, 0, 0)) // loop forever
        started = true
    }
    fun addFrame(bitmap: Bitmap) {
        check(started) { "Call start() first" }
        val scaled = if (bitmap.width != width || bitmap.height != height) Bitmap.createScaledBitmap(bitmap, width, height, true) else bitmap
        // Graphic Control Extension
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0)); writeShort(max(2, delayMs / 10)); out.write(byteArrayOf(0, 0))
        out.write(0x2C); writeShort(0); writeShort(0); writeShort(width); writeShort(height); out.write(0)
        val pixels = IntArray(width * height); scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val indices = ByteArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]; val r = (android.graphics.Color.red(c) shr 5); val g = (android.graphics.Color.green(c) shr 5); val b = (android.graphics.Color.blue(c) shr 6)
            indices[i] = ((r shl 5) or (g shl 2) or b).toByte()
        }
        Lzw(out).encode(indices, 8)
        if (scaled !== bitmap) scaled.recycle()
    }
    fun finish() { out.write(0x3B); out.flush() }
    private fun writeShort(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

    /** Standard GIF LZW compressor. */
    private class Lzw(private val out: OutputStream) {
        fun encode(indices: ByteArray, minCodeSize: Int) {
            out.write(minCodeSize)
            val dict = HashMap<Int, Int>(); var nextCode = 0; var codeSize = 0
            val clear = 1 shl minCodeSize; val eoi = clear + 1
            fun reset() { dict.clear(); for (i in 0 until clear) dict[i] = i; nextCode = eoi + 1; codeSize = minCodeSize + 1 }
            reset()
            val buffer = ArrayList<Byte>(); var bitBuffer = 0; var bitCount = 0
            fun emit(code: Int) {
                bitBuffer = bitBuffer or (code shl bitCount); bitCount += codeSize
                while (bitCount >= 8) { buffer.add((bitBuffer and 0xFF).toByte()); bitBuffer = bitBuffer shr 8; bitCount -= 8 }
                if (buffer.size >= 254) { out.write(buffer.size); buffer.forEach { out.write(it.toInt()) }; buffer.clear() }
            }
            emit(clear)
            var current = indices[0].toInt() and 0xFF
            for (i in 1 until indices.size) {
                val k = indices[i].toInt() and 0xFF; val key = (current shl 8) or k
                val existing = dict[key]
                if (existing != null) current = existing else {
                    emit(current)
                    if (nextCode < 4096) { dict[key] = nextCode++; if (nextCode - 1 == (1 shl codeSize) && codeSize < 12) codeSize++ }
                    else { emit(clear); reset() }
                    current = k
                }
            }
            emit(current); emit(eoi)
            if (bitCount > 0) buffer.add((bitBuffer and 0xFF).toByte())
            if (buffer.isNotEmpty()) { out.write(buffer.size); buffer.forEach { out.write(it.toInt()) } }
            out.write(0)
        }
    }
}

object GifMaker {
    /** Frames sampled evenly from [startMs, startMs+durationMs]. Warns via return value if output is large. */
    fun makeGif(frames: List<Bitmap>, outFile: File, frameDelayMs: Int): Long {
        require(frames.isNotEmpty()) { "No frames could be extracted from that segment" }
        FileOutputStream(outFile).use { fos ->
            val enc = AnimatedGifEncoder(fos); enc.setDelay(frameDelayMs)
            enc.start(frames[0].width, frames[0].height)
            frames.forEach { enc.addFrame(it) }; enc.finish()
        }
        return outFile.length()
    }
}
