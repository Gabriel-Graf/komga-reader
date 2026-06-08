package com.komgareader.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeReflowDoc : ReflowableDocument {
    var lastCfg: ReflowConfig? = null
    var anchor = "a0"
    var page = 0
    override fun applyLayout(cfg: ReflowConfig) { lastCfg = cfg }
    override fun chapters() = listOf(Chapter("K1", "a0", 0))
    override fun currentAnchor() = anchor
    override fun currentPage() = page
    override fun seekToAnchor(a: String) { anchor = a }
    override fun seekToProgress(fraction: Float) { anchor = "p$fraction" }
    override fun search(query: String) = listOf(SearchHit("a0", query))
    override fun pageCount() = 3
    override fun pageSize(index: Int) = PageSize(100, 100)
    override fun renderPage(index: Int, zoom: Float, rotation: Int) = RenderedPage(1, 1, intArrayOf(0))
    override fun close() {}
}

class ReflowableDocumentContractTest {
    @Test fun `applyLayout merkt sich config, seek setzt anchor`() {
        val d = FakeReflowDoc()
        d.applyLayout(ReflowConfig.DEFAULT.copy(fontSizeEm = 1.4f))
        assertEquals(1.4f, d.lastCfg!!.fontSizeEm)
        d.seekToAnchor("a2"); assertEquals("a2", d.currentAnchor())
    }
}
