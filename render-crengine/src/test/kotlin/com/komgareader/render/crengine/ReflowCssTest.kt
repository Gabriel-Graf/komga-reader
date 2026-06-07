package com.komgareader.render.crengine

import com.komgareader.domain.render.Hyphenation
import com.komgareader.domain.render.Margins
import com.komgareader.domain.render.ReflowConfig
import com.komgareader.domain.render.TextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-Kotlin Mapping ReflowConfig -> crengine-ng-Property-Keys. Die hier
 * geprüften Schlüssel sind die echten PROP_*-Konstanten aus
 * crengine-ng/include/lvdocviewprops.h (nicht erfunden):
 *   crengine.interline.space, crengine.page.margin.*, font.face.default,
 *   crengine.hyphenation.directory, crengine.textlang.*.
 */
class ReflowCssTest {

    @Test
    fun lineHeight_wird_als_interline_space_prozent_abgebildet() {
        val props = ReflowCss.toProperties(ReflowConfig(lineHeight = 1.0f))
        assertEquals("100", props["crengine.interline.space"])
    }

    @Test
    fun lineHeight_eineinhalb_ergibt_150_prozent() {
        val props = ReflowCss.toProperties(ReflowConfig(lineHeight = 1.5f))
        assertEquals("150", props["crengine.interline.space"])
    }

    @Test
    fun margins_werden_auf_die_vier_seiten_props_in_pixeln_abgebildet() {
        val props = ReflowCss.toProperties(
            ReflowConfig(margin = Margins(top = 10, bottom = 20, left = 30, right = 40)),
        )
        assertEquals("10", props["crengine.page.margin.top"])
        assertEquals("20", props["crengine.page.margin.bottom"])
        assertEquals("30", props["crengine.page.margin.left"])
        assertEquals("40", props["crengine.page.margin.right"])
    }

    @Test
    fun fontFamily_wird_zur_default_font_face() {
        val props = ReflowCss.toProperties(ReflowConfig(fontFamily = "DejaVu Sans"))
        assertEquals("DejaVu Sans", props["font.face.default"])
    }

    @Test
    fun hyphenation_off_setzt_dictionary_auf_none() {
        val props = ReflowCss.toProperties(ReflowConfig(hyphenation = Hyphenation.Off))
        assertEquals("@none", props["crengine.hyphenation.directory"])
    }

    @Test
    fun hyphenation_sprache_aktiviert_algorithmische_trennung_und_setzt_sprache() {
        val props = ReflowCss.toProperties(
            ReflowConfig(hyphenation = Hyphenation.Language("de")),
        )
        assertEquals("@algorithm", props["crengine.hyphenation.directory"])
        assertEquals("de", props["crengine.textlang.main.lang"])
        assertEquals("1", props["crengine.textlang.hyphenation.enabled"])
    }

    @Test
    fun hyphenation_off_aktiviert_keine_sprachtrennung() {
        val props = ReflowCss.toProperties(ReflowConfig(hyphenation = Hyphenation.Off))
        assertEquals("0", props["crengine.textlang.hyphenation.enabled"])
    }

    @Test
    fun textAlign_ist_keine_property_sondern_css() {
        // text-align ist in crengine-ng CSS-gesteuert, keine PROP_-Konstante.
        val props = ReflowCss.toProperties(ReflowConfig(textAlign = TextAlign.LEFT))
        assertFalse(props.keys.any { it.contains("align") })
    }

    @Test
    fun textAlign_justify_ergibt_justify_css_auf_body() {
        val css = ReflowCss.toUserCss(ReflowConfig(textAlign = TextAlign.JUSTIFY))
        assertTrue(css.contains("text-align: justify"), "war: $css")
    }

    @Test
    fun textAlign_left_ergibt_left_css_auf_body() {
        val css = ReflowCss.toUserCss(ReflowConfig(textAlign = TextAlign.LEFT))
        assertTrue(css.contains("text-align: left"), "war: $css")
    }
}
