package com.komgareader.app.ci

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Spec §9 Block A — gemischte Quellenarten live (Komga-REST + OPDS gegen dieselbe CI-Instanz A).
 * Löst `MixedSourcesLiveTest` ab (gegen die dedizierte CI-Topologie statt der Dev-Komga).
 */
@RunWith(AndroidJUnit4::class)
class BlockAMixedSourcesTest {

    private lateinit var stack: CiSourceStack

    @Before fun setUp() { stack = CiSourceStack() }
    @After fun tearDown() { stack.close() }

    /** A5: Komga-REST + OPDS gleichzeitig → beide aggregiert, beide browsebar, verschiedene IDs. */
    @Test fun a5_komga_rest_und_opds_gemischt() = runTest {
        stack.register(CiKomga.A, CiKomga.A_OPDS)
        val all = stack.activeSource.all()
        assertTrue("Mind. 2 Quellen erwartet, war ${all.size}", all.size >= 2)
        assertTrue("Komga-REST muss dabei sein", all.any { it.kind == SourceKind.KOMGA })
        assertTrue("OPDS muss dabei sein", all.any { it.kind == SourceKind.OPDS })

        val komga = all.first { it.kind == SourceKind.KOMGA }
        val opds = all.first { it.kind == SourceKind.OPDS }
        assertNotEquals("Quellen müssen verschiedene IDs haben", komga.id, opds.id)

        val opdsItems = opds.browse(0, SourceFilter()).items
        assertTrue("OPDS-Katalog muss mind. ein Werk liefern", opdsItems.isNotEmpty())
    }

    /**
     * A6 (Lackmustest): dieselbe agnostische Operation (`browse` über `BrowsableSource`) liefert
     * für KOMGA UND OPDS ein nicht-leeres Ergebnis — ohne quellen-spezifischen Code-Pfad.
     */
    @Test fun a6_lackmustest_agnostisch_ueber_browsable_source() = runTest {
        stack.register(CiKomga.A, CiKomga.A_OPDS)
        val all = stack.activeSource.all()
        // Identischer Aufruf, egal welche konkrete Quelle dahinter steckt.
        val agnostic: (BrowsableSource) -> Int = { /* keine Typprüfung! */ 0 }
        for (s in all) {
            val items = s.browse(0, SourceFilter()).items
            assertTrue("Quelle ${s.kind} muss agnostisch browsebar sein", items.isNotEmpty())
            agnostic(s)
        }
    }
}
