package com.userhunter.app.checker

import org.junit.Assert.assertEquals
import org.junit.Test

class TikTokPageParserTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture: $name" }
            .readText()

    @Test
    fun embeddedUserDataDetectsTakenProfile() {
        val parsed = TikTokPageParser.parse(200, fixture("tiktok_taken.html"), "tiktok")
        assertEquals(PageClassification.TAKEN, parsed.classification)
    }

    @Test
    fun pageTextAndEmbeddedStatusDetectNotFoundProfile() {
        val parsed = TikTokPageParser.parse(200, fixture("tiktok_not_found.html"), "name123")
        assertEquals(PageClassification.NOT_FOUND, parsed.classification)
    }

    @Test
    fun captchaIsClassifiedAsBlocked() {
        val parsed = TikTokPageParser.parse(200, fixture("tiktok_captcha.html"), "name123")
        assertEquals(PageClassification.BLOCKED, parsed.classification)
    }

    @Test
    fun ambiguousHtmlRemainsUnknown() {
        val parsed = TikTokPageParser.parse(200, fixture("tiktok_unknown.html"), "name123")
        assertEquals(PageClassification.UNKNOWN, parsed.classification)
    }

    @Test
    fun notFoundHttpStatusNeedsConfirmingPayloadMarker() {
        val ambiguous = TikTokPageParser.parse(404, "<html>not-found</html>", "name123")
        assertEquals(PageClassification.UNKNOWN, ambiguous.classification)

        val confirmed = TikTokPageParser.parse(404, "<html>Couldn't find this account</html>", "name123")
        assertEquals(PageClassification.NOT_FOUND, confirmed.classification)
    }
}
