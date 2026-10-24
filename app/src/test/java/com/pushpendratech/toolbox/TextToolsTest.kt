package com.pushpendratech.toolbox

import com.pushpendratech.toolbox.core.TextTools
import org.junit.Assert.assertEquals
import org.junit.Test

class TextToolsTest {
    @Test fun countBasic() {
        val c = TextTools.count("Hello world.\nSecond line here")
        assertEquals(5, c.words); assertEquals(2, c.lines); assertEquals(29, c.charsWithSpaces); assertEquals(25, c.charsNoSpaces)
    }
    @Test fun countEmpty() { val c = TextTools.count(""); assertEquals(0, c.words); assertEquals(0, c.lines) }
    @Test fun titleCase() { assertEquals("Hello Big World", TextTools.toTitleCase("hello big WORLD")) }
    @Test fun sentenceCase() { assertEquals("Hello. How are you? Fine.", TextTools.toSentenceCase("hello. how are you? fine.")) }
    @Test fun dedupKeepsFirstAndOrder() { assertEquals("a\nb\nc", TextTools.removeDuplicateLines("a\nb\na\nc\nb")) }
    @Test fun dedupIgnoreCase() { assertEquals("Apple", TextTools.removeDuplicateLines("Apple\napple\nAPPLE", ignoreCase = true)) }
    @Test fun removeSpacesCollapses() { assertEquals("a b\nc", TextTools.removeExtraSpaces("  a   b  \n  c ")) }
    @Test fun reverseModes() {
        assertEquals("cba", TextTools.reverseCharacters("abc"))
        assertEquals("b\na", TextTools.reverseLines("a\nb"))
        assertEquals("three two one", TextTools.reverseWords("one two three"))
    }
    @Test fun slug() {
        assertEquals("hello-world-2026", TextTools.toSlug("  Héllo, World! 2026 "))
        assertEquals("gst-calculator-india", TextTools.toSlug("GST Calculator (India)"))
    }
    @Test fun findReplaceCounts() {
        val r = TextTools.findReplace("Cat cat CAT", "cat", "dog", ignoreCase = true); assertEquals(3, r.replacements); assertEquals("dog dog dog", r.text)
        val w = TextTools.findReplace("cat scatter cat", "cat", "dog", wholeWord = true); assertEquals(2, w.replacements)
    }
}
