package com.pushpendratech.toolbox.core

import java.text.Normalizer

/** Pure, Android-free text logic — fully unit-tested on the JVM. Counting rules documented per function. */
object TextTools {

    data class Counts(
        val words: Int, val charsWithSpaces: Int, val charsNoSpaces: Int,
        val lines: Int, val sentences: Int, val paragraphs: Int, val readingMinutes: Double
    )

    /** Words = maximal runs separated by whitespace. Chars counted as Unicode code points are NOT used;
     *  chars = String.length (UTF-16 units), stated in UI. Lines = split on \n (empty text = 0 lines). */
    fun count(text: String): Counts {
        if (text.isEmpty()) return Counts(0, 0, 0, 0, 0, 0, 0.0)
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val noSpaces = text.count { !it.isWhitespace() }
        val lines = text.split("\n").size
        val sentences = text.split(Regex("[.!?]+")).count { it.isNotBlank() }
        val paragraphs = text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }
        // Average reading speed 200 wpm.
        return Counts(words, text.length, noSpaces, lines, sentences, paragraphs, if (words == 0) 0.0 else words / 200.0)
    }

    fun toUpper(t: String) = t.uppercase()
    fun toLower(t: String) = t.lowercase()
    fun toTitleCase(t: String): String {
        val sb = StringBuilder(); var capNext = true
        for (ch in t.lowercase()) {
            sb.append(if (capNext && ch.isLetter()) ch.uppercaseChar() else ch)
            capNext = ch.isWhitespace()
        }
        return sb.toString()
    }
    fun toSentenceCase(t: String): String {
        val sb = StringBuilder(); var capNext = true
        for (ch in t.lowercase()) {
            sb.append(if (capNext && ch.isLetter()) ch.uppercaseChar() else ch)
            if (ch.isLetter()) capNext = false
            if (ch == '.' || ch == '!' || ch == '?') capNext = true
        }
        return sb.toString()
    }
    fun toAlternating(t: String): String {
        val sb = StringBuilder(); var up = true
        for (ch in t) { if (ch.isLetter()) { sb.append(if (up) ch.uppercaseChar() else ch.lowercaseChar()); up = !up } else sb.append(ch) }
        return sb.toString()
    }

    /** Removes duplicate lines, keeps first occurrence, preserves order. */
    fun removeDuplicateLines(text: String, ignoreCase: Boolean = false, trimForCompare: Boolean = true): String {
        val seen = HashSet<String>(); val out = ArrayList<String>()
        for (line in text.split("\n")) {
            var key = if (trimForCompare) line.trim() else line
            if (ignoreCase) key = key.lowercase()
            if (seen.add(key)) out.add(line)
        }
        return out.joinToString("\n")
    }

    /** Trims each line, collapses repeated spaces/tabs to one, optionally removes blank lines. Never touches single intentional spaces inside a line beyond collapsing repeats (option-controlled). */
    fun removeExtraSpaces(text: String, collapseInner: Boolean = true, removeBlankLines: Boolean = false): String {
        var lines = text.split("\n").map { line ->
            var l = line.trim()
            if (collapseInner) l = l.replace(Regex("[ \\t]+"), " ")
            l
        }
        if (removeBlankLines) lines = lines.filter { it.isNotEmpty() }
        return lines.joinToString("\n")
    }

    fun reverseCharacters(t: String) = t.reversed()
    fun reverseLines(t: String) = t.split("\n").reversed().joinToString("\n")
    fun reverseWords(t: String) = t.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.reversed().joinToString(" ")

    /** Slug rules: NFKD-normalize, strip diacritics, lowercase, any run of non [a-z0-9] -> single '-', trim '-'. Non-Latin scripts are dropped after normalization (documented limitation). */
    fun toSlug(text: String, separator: Char = '-'): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKD).replace(Regex("\\p{Mn}+"), "")
        return normalized.lowercase().replace(Regex("[^a-z0-9]+"), separator.toString()).trim(separator)
    }

    data class ReplaceResult(val text: String, val replacements: Int)
    fun findReplace(text: String, find: String, replace: String, ignoreCase: Boolean = false, wholeWord: Boolean = false): ReplaceResult {
        if (find.isEmpty()) return ReplaceResult(text, 0)
        if (!wholeWord) {
            var count = 0; var idx = 0; val hay = if (ignoreCase) text.lowercase() else text; val needle = if (ignoreCase) find.lowercase() else find
            while (true) { val i = hay.indexOf(needle, idx); if (i < 0) break; count++; idx = i + needle.length }
            val regex = Regex(Regex.escape(find), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
            return ReplaceResult(regex.replace(text, Regex.escapeReplacement(replace)), count)
        }
        val regex = Regex("\\b" + Regex.escape(find) + "\\b", if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        val matches = regex.findAll(text).count()
        return ReplaceResult(regex.replace(text, Regex.escapeReplacement(replace)), matches)
    }
}
