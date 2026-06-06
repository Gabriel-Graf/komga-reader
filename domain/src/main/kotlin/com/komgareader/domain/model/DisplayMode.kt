package com.komgareader.domain.model

/**
 * App-weiter Anzeige-/Geräte-Modus. Bestimmt, ob für E-Ink optimiert wird
 * (kein Free-Scroll, Frame-Sprünge, keine Animationen, später Farbfilter etc.)
 * oder ob die Smartphone-Variante mit Smooth-Scroll/Animationen läuft.
 */
enum class DisplayMode { EINK, SMARTPHONE }
