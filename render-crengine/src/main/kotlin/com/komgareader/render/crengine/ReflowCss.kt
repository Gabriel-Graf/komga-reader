package com.komgareader.render.crengine

import com.komgareader.domain.render.Hyphenation
import com.komgareader.domain.render.ReflowConfig
import com.komgareader.domain.render.TextAlign

/**
 * Übersetzt einen engine-neutralen [ReflowConfig] in das crengine-ng-Layout.
 *
 * Zwei Ausgaben, weil crengine-ng zwei Mechanismen kennt:
 *  - [toProperties]: die per `propsApply` gesetzten Engine-Properties. Die
 *    Schlüssel sind die echten `PROP_*`-Konstanten aus
 *    `crengine-ng/include/lvdocviewprops.h` — nicht erfunden.
 *  - [toUserCss]: text-align ist in crengine-ng kein Property, sondern ein
 *    CSS-Stil (Default-Stylesheet: `body { text-align: justify }`). Eine
 *    abweichende Ausrichtung wird daher als User-Stylesheet-Schnipsel gesetzt.
 *
 * Reine Funktionen ohne Seiteneffekte: trivial unit-testbar (TDD).
 */
object ReflowCss {

    // PROP_*-Konstanten aus crengine-ng/include/lvdocviewprops.h:
    private const val PROP_INTERLINE_SPACE = "crengine.interline.space"
    private const val PROP_PAGE_MARGIN_TOP = "crengine.page.margin.top"
    private const val PROP_PAGE_MARGIN_BOTTOM = "crengine.page.margin.bottom"
    private const val PROP_PAGE_MARGIN_LEFT = "crengine.page.margin.left"
    private const val PROP_PAGE_MARGIN_RIGHT = "crengine.page.margin.right"
    private const val PROP_FONT_FACE = "font.face.default"
    private const val PROP_HYPHENATION_DICT = "crengine.hyphenation.directory"
    private const val PROP_TEXTLANG_MAIN_LANG = "crengine.textlang.main.lang"
    private const val PROP_TEXTLANG_HYPHENATION_ENABLED = "crengine.textlang.hyphenation.enabled"

    // Dictionary-IDs aus crengine-ng/include/crhyphman.h:
    private const val HYPH_DICT_ID_NONE = "@none"
    private const val HYPH_DICT_ID_ALGORITHM = "@algorithm"

    /**
     * Echte TeX-Muster-Wörterbücher (crengine-ng `share/crengine-ng/hyph/`,
     * gebündelt als App-Assets). Die Dictionary-ID ist der Dateiname des
     * `.pattern`-Files — crengine-ng aktiviert ihn über die Dictionary-Liste,
     * die [CrengineNative.nativeInit] aus dem entpackten Pattern-Verzeichnis lädt.
     *
     * Nur Sprachen mit gebündeltem Muster-Wörterbuch; alle anderen fallen auf
     * [HYPH_DICT_ID_ALGORITHM] (generische, regelbasierte Trennung) zurück.
     */
    private val PATTERN_DICTS: Map<String, String> = mapOf(
        "de" to "hyph-de-1996.pattern",
        "en" to "hyph-en-us.pattern",
    )

    /**
     * Engine-Properties für [cfg]. [ReflowConfig.fontSizeEm] ist bewusst nicht
     * enthalten — die Schriftgröße in Pixeln wird beim Anwenden aus der em-Größe
     * und der Viewport-Geometrie berechnet und über `setFontSize` gesetzt, nicht
     * über eine Property.
     */
    fun toProperties(cfg: ReflowConfig): Map<String, String> {
        val props = LinkedHashMap<String, String>()
        props[PROP_INTERLINE_SPACE] = interlineSpacePercent(cfg.lineHeight).toString()
        props[PROP_PAGE_MARGIN_TOP] = cfg.margin.top.toString()
        props[PROP_PAGE_MARGIN_BOTTOM] = cfg.margin.bottom.toString()
        props[PROP_PAGE_MARGIN_LEFT] = cfg.margin.left.toString()
        props[PROP_PAGE_MARGIN_RIGHT] = cfg.margin.right.toString()
        props[PROP_FONT_FACE] = cfg.fontFamily
        applyHyphenation(props, cfg.hyphenation)
        return props
    }

    /**
     * User-Stylesheet für die in crengine-ng nicht als Property abbildbaren
     * Stile (derzeit nur text-align). Wird zusätzlich zum Default-Stylesheet
     * über `setStyleSheet` gesetzt.
     */
    fun toUserCss(cfg: ReflowConfig): String {
        val align = when (cfg.textAlign) {
            TextAlign.JUSTIFY -> "justify"
            TextAlign.LEFT -> "left"
        }
        return "body { text-align: $align }\n"
    }

    private fun interlineSpacePercent(lineHeight: Float): Int =
        Math.round(lineHeight * 100)

    private fun applyHyphenation(props: MutableMap<String, String>, hyphenation: Hyphenation) {
        when (hyphenation) {
            is Hyphenation.Off -> {
                props[PROP_HYPHENATION_DICT] = HYPH_DICT_ID_NONE
                props[PROP_TEXTLANG_HYPHENATION_ENABLED] = "0"
            }
            is Hyphenation.Language -> {
                // Für DE/EN das echte TeX-Muster-Wörterbuch aktivieren; sonst
                // bleibt es bei der generischen algorithmischen Trennung.
                props[PROP_HYPHENATION_DICT] =
                    PATTERN_DICTS[hyphenation.lang] ?: HYPH_DICT_ID_ALGORITHM
                props[PROP_TEXTLANG_MAIN_LANG] = hyphenation.lang
                props[PROP_TEXTLANG_HYPHENATION_ENABLED] = "1"
            }
        }
    }
}
