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
 *
 * Hinweis zur Trennung: PROP_HYPHENATION_DICT IST "crengine.hyphenation.directory"
 * (lvdocviewprops.h:82) — trotz des Namens der Property, die das aktive Wörterbuch
 * WÄHLT (ihr Wert geht an HyphDictionaryList::activate(id)). Eine separate
 * "crengine.hyphenation.dict"-Property existiert in crengine-ng nicht.
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
    fun hyphenation_de_aktiviert_das_echte_deutsche_pattern_woerterbuch() {
        // Deutsch -> echtes TeX-Muster-Wörterbuch (hyph-de-1996.pattern), nicht @algorithm.
        val props = ReflowCss.toProperties(
            ReflowConfig(hyphenation = Hyphenation.Language("de")),
        )
        assertEquals("hyph-de-1996.pattern", props["crengine.hyphenation.directory"])
        assertEquals("de", props["crengine.textlang.main.lang"])
        assertEquals("1", props["crengine.textlang.hyphenation.enabled"])
    }

    @Test
    fun hyphenation_en_aktiviert_das_echte_englische_pattern_woerterbuch() {
        // Englisch -> echtes TeX-Muster-Wörterbuch (hyph-en-us.pattern), nicht @algorithm.
        val props = ReflowCss.toProperties(
            ReflowConfig(hyphenation = Hyphenation.Language("en")),
        )
        assertEquals("hyph-en-us.pattern", props["crengine.hyphenation.directory"])
        assertEquals("en", props["crengine.textlang.main.lang"])
        assertEquals("1", props["crengine.textlang.hyphenation.enabled"])
    }

    @Test
    fun hyphenation_unbekannte_sprache_faellt_auf_algorithmische_trennung_zurueck() {
        // Für Sprachen ohne gebündeltes Muster-Wörterbuch bleibt der generische
        // @algorithm-Pfad erhalten (immer noch echte Trennung, nur geringere Qualität).
        val props = ReflowCss.toProperties(
            ReflowConfig(hyphenation = Hyphenation.Language("fr")),
        )
        assertEquals("@algorithm", props["crengine.hyphenation.directory"])
        assertEquals("fr", props["crengine.textlang.main.lang"])
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

    @Test
    fun fontWeight_wird_auf_die_font_base_weight_property_abgebildet() {
        val props = ReflowCss.toProperties(ReflowConfig(fontWeight = 600))
        assertEquals("600", props["font.face.base.weight"])
    }

    @Test
    fun fontWeight_normal_400_ist_die_default_basisstaerke() {
        val props = ReflowCss.toProperties(ReflowConfig(fontWeight = 400))
        assertEquals("400", props["font.face.base.weight"])
    }

    @Test
    fun kapitel_beginnen_per_docfragment_pagebreak_auf_neuer_seite() {
        // Kapitel sollen oben auf einer neuen Seite starten. crengine bricht zuverlässig am
        // EPUB-Datei-Anfang (DocFragment) um — markup-unabhängig, anders als ein h-Tag-Selektor.
        val css = ReflowCss.toUserCss(ReflowConfig())
        assertTrue(css.contains("DocFragment"), "DocFragment-Selektor fehlt: $css")
        assertTrue(css.contains("page-break-before: always"), "war: $css")
    }
}
