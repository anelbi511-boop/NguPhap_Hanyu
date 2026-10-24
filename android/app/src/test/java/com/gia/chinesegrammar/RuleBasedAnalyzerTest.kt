package com.gia.chinesegrammar

import com.gia.chinesegrammar.offline.RuleBasedAnalyzer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RuleBasedAnalyzerTest {
    private val analyzer = RuleBasedAnalyzer()

    @Test fun segmentation_usesLongestMatch_andPreservesOffsets() {
        val text = "我是学生。"
        val tokens = analyzer.segment(text)
        assertEquals(listOf("我", "是", "学生", "。"), tokens.map { it.text })
        tokens.forEach { t -> assertEquals(t.text, text.substring(t.start, t.end)) }
        assertEquals("xuésheng", tokens[2].pinyin)
    }

    @Test fun segmentation_multiCharWordsNotSplit() {
        val tokens = analyzer.segment("我们学习汉语")
        assertTrue(tokens.any { it.text == "我们" }); assertTrue(tokens.any { it.text == "学习" }); assertTrue(tokens.any { it.text == "汉语" })
    }

    @Test fun roles_subjectPredicateObject() = runTest {
        val r = analyzer.analyze("我学习汉语。", 3)
        val byText = r.tokens.associateBy { it.text }
        assertEquals("subject", byText["我"]!!.role)
        assertEquals("predicate", byText["学习"]!!.role)
        assertEquals("object", byText["汉语"]!!.role)
    }

    @Test fun patterns_detectLeGuoBaBeiBi() = runTest {
        // patterns are GrammarPatternDto objects (server contract), not strings.
        assertTrue(analyzer.analyze("我吃了饭。", 3).patterns.any { it.name.startsWith("了") })
        assertTrue(analyzer.analyze("我去过中国。", 3).patterns.any { it.name.startsWith("过") })
        assertTrue(analyzer.analyze("我把书看完了。", 4).patterns.any { it.name.contains("把") })
        assertTrue(analyzer.analyze("今天比昨天热。", 2).patterns.any { it.name.startsWith("比") })
        assertTrue(analyzer.analyze("门开着。", 3).patterns.any { it.name.startsWith("着") })
        val p = analyzer.analyze("我把书看完了。", 4).patterns.first { it.name.contains("把") }
        assertTrue(p.descriptionVi.isNotBlank())
        assertTrue(p.confidence in 0.0..1.0)
    }

    @Test fun offline_neverFabricatesDependencies_andWarns() = runTest {
        val r = analyzer.analyze("我是学生。", 1)
        assertTrue("offline must not invent a parse tree", r.dependencies.isEmpty())
        assertTrue(r.warnings.isNotEmpty())
        assertEquals("offline-rule", r.provenance.engine)
        assertTrue(r.uncertainty in 0.0..1.0)
    }

    @Test fun rejectsEmptyInput() = runTest {
        var threw = false
        try { analyzer.analyze("   ", 1) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
    }
}
