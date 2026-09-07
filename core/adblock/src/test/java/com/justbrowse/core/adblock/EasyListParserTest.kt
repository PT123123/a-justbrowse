package com.justbrowse.core.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EasyListParserTest {

    @Test
    fun `parse comment returns Noop`() {
        val rule = EasyListParser.parseLine("! This is a comment")
        assertTrue(rule is FilterRule.Noop)
    }

    @Test
    fun `parse empty line returns Noop`() {
        val rule = EasyListParser.parseLine("")
        assertTrue(rule is FilterRule.Noop)
    }

    @Test
    fun `parse basic block rule`() {
        val rule = EasyListParser.parseLine("/ads/banner.gif") as FilterRule.BlockRule
        assertEquals("/ads/banner.gif", rule.pattern)
        assertFalse(rule.isException)
    }

    @Test
    fun `parse domain anchor rule`() {
        val rule = EasyListParser.parseLine("||doubleclick.net^") as FilterRule.BlockRule
        assertTrue(rule.isDomainAnchor)
        assertEquals("||doubleclick.net^", rule.pattern)
    }

    @Test
    fun `parse exception rule`() {
        val rule = EasyListParser.parseLine("@@||example.com^") as FilterRule.Exception
        assertTrue(rule.rule.isException)
    }

    @Test
    fun `parse element hiding rule`() {
        val rule = EasyListParser.parseLine("example.com##.ad-banner") as FilterRule.ElementHidingRule
        assertEquals(listOf("example.com"), rule.domains)
        assertEquals(".ad-banner", rule.selector)
    }

    @Test
    fun `parse global element hiding rule`() {
        val rule = EasyListParser.parseLine("##.adsbygoogle") as FilterRule.ElementHidingRule
        assertTrue(rule.domains.isEmpty())
        assertEquals(".adsbygoogle", rule.selector)
    }

    @Test
    fun `parse rule with options`() {
        val rule = EasyListParser.parseLine("||ads.com^$script,third-party") as FilterRule.BlockRule
        assertTrue(rule.options.requestTypes.contains("script"))
        assertTrue(rule.options.thirdParty)
    }

    @Test
    fun `block rule matches url`() {
        val rule = FilterRule.BlockRule(pattern = "doubleclick", isDomainAnchor = true)
        assertTrue(rule.matches("https://doubleclick.net/ads/banner.gif"))
        assertFalse(rule.matches("https://example.com/"))
    }

    @Test
    fun `exception rule overrides block`() {
        val engine = AdBlockEngine.fromRulesText("""
            ||ads.com^
            @@||ads.com/safe^
        """.trimIndent())

        assertTrue(engine.shouldBlock("https://ads.com/tracker.gif"))
        assertFalse(engine.shouldBlock("https://ads.com/safe/image.png"))
    }

    @Test
    fun `element hiding css generated correctly`() {
        val engine = AdBlockEngine.fromRulesText("""
            ##.ad
            ##.adsbygoogle
            example.com##.sponsored
        """.trimIndent())

        val globalCss = engine.getElementHidingCss("any.com")
        assertTrue(globalCss.contains(".ad{"))
        assertTrue(globalCss.contains(".adsbygoogle{"))

        val domainCss = engine.getElementHidingCss("example.com")
        assertTrue(domainCss.contains(".sponsored{"))
    }

    @Test
    fun `parse multiple rules`() {
        val input = """
            ! Comment
            ||doubleclick.net^
            ||google-analytics.com^
            ##.ad
            ##.adsbygoogle
            @@||safe.com^
        """.trimIndent()

        val rules = EasyListParser.parse(input)
        assertEquals(6, rules.size)  // 5 + 1 noop for comment
    }
}
