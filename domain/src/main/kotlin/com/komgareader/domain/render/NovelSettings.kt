package com.komgareader.domain.render

/**
 * Persistierte Roman-Typografie als reine Primitive (Strings/Floats), wie sie in
 * den App-Settings liegen. Engine- und UI-neutral; [toReflowConfig] ist der reine
 * Mapper auf die [ReflowConfig], die die Reflow-Engine (Naht B) versteht.
 *
 * Die Defaults entsprechen den Settings-Defaults (em 1.0, lineHeight 1.0, Rand
 * NORMAL, Schrift = [NovelFonts.DEFAULT] "DejaVu Sans", Ausrichtung JUSTIFY,
 * Trennung aus).
 */
data class NovelSettings(
    val fontSizeEm: Float = 1.0f,
    val lineHeight: Float = 1.0f,
    val marginPreset: String = MARGIN_NORMAL,
    val fontFamily: String = NovelFonts.DEFAULT,
    val textAlign: String = "JUSTIFY",
    val hyphenationLang: String = "",
    val fontWeight: Int = FONT_WEIGHT_DEFAULT,
) {
    /** Reiner Mapper: persistierte Primitive → [ReflowConfig] (engine-neutral). */
    fun toReflowConfig(): ReflowConfig = ReflowConfig(
        fontSizeEm = fontSizeEm,
        lineHeight = lineHeight,
        margin = marginFor(marginPreset),
        fontFamily = fontFamily,
        textAlign = if (textAlign == "LEFT") TextAlign.LEFT else TextAlign.JUSTIFY,
        hyphenation = hyphenationLang.ifBlank { null }
            ?.let { Hyphenation.Language(it) }
            ?: Hyphenation.Off,
        fontWeight = fontWeight.coerceIn(FONT_WEIGHT_MIN, FONT_WEIGHT_MAX),
    )

    companion object {
        const val MARGIN_NARROW = "NARROW"
        const val MARGIN_SNUG = "SNUG"
        const val MARGIN_NORMAL = "NORMAL"
        const val MARGIN_RELAXED = "RELAXED"
        const val MARGIN_WIDE = "WIDE"
        const val MARGIN_XWIDE = "XWIDE"

        /**
         * Ordered list of margin preset keys, ascending in page margin. Single source of truth
         * for the margin slider (the slider's position indexes into this list). Every entry maps
         * to a crengine-listed px in [marginFor]; landmarks ([MARGIN_LANDMARKS]) get named ticks.
         */
        val MARGIN_STEPS: List<String> = listOf(
            MARGIN_NARROW, MARGIN_SNUG, MARGIN_NORMAL, MARGIN_RELAXED, MARGIN_WIDE, MARGIN_XWIDE,
        )

        /**
         * The named landmark steps (Narrow / Normal / Wide / Extra-wide) — a subset of
         * [MARGIN_STEPS] that carry a label on the slider; the in-between steps (Snug, Relaxed)
         * are unlabelled intermediates.
         */
        val MARGIN_LANDMARKS: Set<String> = setOf(
            MARGIN_NARROW, MARGIN_NORMAL, MARGIN_WIDE, MARGIN_XWIDE,
        )

        /** Schrift-Grundstärke (crengine `font.face.base.weight`): 400 = normal, höher = dicker. */
        const val FONT_WEIGHT_DEFAULT = 400
        const val FONT_WEIGHT_MIN = 400
        const val FONT_WEIGHT_MAX = 900
        const val FONT_WEIGHT_STEP = 100

        /** Schriftgröße (em): Minimum, Maximum, Schrittweite — SSOT für Reader-Panel + Settings. */
        const val FONT_SIZE_MIN = 0.7f
        const val FONT_SIZE_MAX = 2.5f
        const val FONT_SIZE_STEP = 0.1f

        /** Zeilenabstand (em-Multiplikator): Minimum, Maximum, Schrittweite. */
        const val LINE_HEIGHT_MIN = 0.8f
        const val LINE_HEIGHT_MAX = 2.0f
        const val LINE_HEIGHT_STEP = 0.1f

        /**
         * Preset-String → konkrete [Margins]; unbekannt fällt auf NORMAL zurück.
         *
         * Die px-Werte müssen in der crengine-ng-Erlaubnisliste liegen
         * ({…,12,…,20,25,…,40,50,…}, siehe `LVDocView::propsUpdateDefaults`):
         * `limitValueList` setzt einen NICHT gelisteten Wert still auf den Default (8) zurück,
         * sodass z. B. 24/48 beide auf 8 kollabierten und der Rand-Regler wirkungslos blieb.
         * Confirmed-listed: 12, 20, 25, 40, 50 → NARROW < SNUG < NORMAL < RELAXED < WIDE.
         *
         * XWIDE = 60 is the *probable* next listed step but NOT confirmed here: verify on device
         * that 60 is crengine-listed; if it silently collapses to the default (8), drop XWIDE to a
         * confirmed-listed value (e.g. reuse 50, or another value once verified). Do NOT invent
         * unlisted intermediates between the confirmed values.
         */
        fun marginFor(preset: String): Margins = when (preset) {
            MARGIN_NARROW -> Margins(12, 12, 12, 12)
            MARGIN_SNUG -> Margins(20, 20, 20, 20)
            MARGIN_RELAXED -> Margins(40, 40, 40, 40)
            MARGIN_WIDE -> Margins(50, 50, 50, 50)
            MARGIN_XWIDE -> Margins(60, 60, 60, 60)
            else -> Margins.NORMAL
        }

        /**
         * Reverse of [marginFor]: the [MARGIN_STEPS] entry whose [marginFor] equals [margins], else
         * [MARGIN_NORMAL]. Each step has a distinct px, so the px→preset round-trip is lossless.
         * A missing case here was the bug where SNUG/RELAXED/XWIDE round-tripped to NORMAL and the
         * margin slider snapped back to Normal instead of holding the picked step.
         */
        fun presetForMargin(margins: Margins): String =
            MARGIN_STEPS.firstOrNull { marginFor(it) == margins } ?: MARGIN_NORMAL
    }
}
