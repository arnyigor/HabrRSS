package com.arny.habrrss

import com.arny.habrrss.data.remote.habr.daily.HabrPiniaStateExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HabrPiniaStateExtractorTest {

    @Test
    fun extractsJsonObjectIgnoringTrailingScriptSuffix() {
        val html = """<script>window.__PINIA_STATE__={"a":1};(function(){})();</script>"""

        val state = HabrPiniaStateExtractor.extract(html)

        assertEquals("""{"a":1}""", state)
    }

    @Test
    fun extractsNestedObjectsAndBracesInsideStrings() {
        val html = """<script>window.__PINIA_STATE__={"a":{"b":"} {"},"c":[{"d":"x"}]}</script>"""

        val state = HabrPiniaStateExtractor.extract(html)

        assertEquals("""{"a":{"b":"} {"},"c":[{"d":"x"}]}""", state)
    }

    @Test
    fun honorsEscapedQuotesAndBackslashesInsideString() {
        val html = """<script>window.__PINIA_STATE__={"a":"x \" y \\ z","b":1}</script>"""

        val state = HabrPiniaStateExtractor.extract(html)

        assertEquals("""{"a":"x \" y \\ z","b":1}""", state)
    }

    @Test
    fun skipsWhitespaceAfterEqualsSign() {
        val html = """<script>window.__PINIA_STATE__= {"a":1}</script>"""

        val state = HabrPiniaStateExtractor.extract(html)

        assertEquals("""{"a":1}""", state)
    }

    @Test
    fun returnsNullWhenMarkerIsMissing() {
        assertNull(HabrPiniaStateExtractor.extract("<html><body>no state here</body></html>"))
    }

    @Test
    fun returnsNullWhenValueIsNotAnObject() {
        assertNull(HabrPiniaStateExtractor.extract("<script>window.__PINIA_STATE__=[1,2,3];</script>"))
        assertNull(HabrPiniaStateExtractor.extract("<script>window.__PINIA_STATE__=\"str\";</script>"))
    }

    @Test
    fun returnsNullWhenJsonIsUnclosed() {
        assertNull(HabrPiniaStateExtractor.extract("<script>window.__PINIA_STATE__={\"a\":1</script>"))
    }
}
