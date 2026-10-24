package com.gia.chinesegrammar

import com.gia.chinesegrammar.data.remote.TokenDto
import com.gia.chinesegrammar.util.TokenAlignment
import org.junit.Assert.*
import org.junit.Test

class TokenAlignmentTest {
    @Test fun validate_acceptsExactOffsets() {
        val text = "我学习汉语"
        val tokens = listOf(TokenDto("我", 0, 1), TokenDto("学习", 1, 3), TokenDto("汉语", 3, 5))
        assertTrue(TokenAlignment.validate(text, tokens).isEmpty())
    }

    @Test fun validate_flagsMismatchAndOverlap() {
        val text = "我学习"
        val bad = listOf(TokenDto("我", 0, 1), TokenDto("学习", 0, 2))
        val w = TokenAlignment.validate(text, bad)
        assertTrue(w.any { it.contains("không khớp") || it.contains("chồng lấn") })
    }

    @Test fun realign_fixesShiftedOffsets() {
        val text = "  我 学习 汉语"
        val raw = listOf(TokenDto("我", 0, 1), TokenDto("学习", 0, 2), TokenDto("汉语", 0, 2))
        val fixed = TokenAlignment.realign(text.trim().replace(" ", ""), raw)
        assertNotNull(fixed)
        assertTrue(TokenAlignment.validate("我学习汉语", fixed!!).isEmpty())
    }

    @Test fun realign_returnsNullWhenTokenMissing() {
        assertNull(TokenAlignment.realign("我是学生", listOf(TokenDto("老师", 0, 2))))
    }

    @Test fun backupImport_validation() {
        val good = """{"schema":"chinese-grammar-backup-v1","vocab":[{"word":"学习","pinyin":"xuéxí","meaningVi":"học","example":"","hskLevel":1}],"historyTexts":[]}"""
        val items = com.gia.chinesegrammar.util.ExportImport.importVocab(good)
        assertEquals(1, items.size); assertEquals("学习", items[0].word)
        assertThrows(IllegalArgumentException::class.java) { com.gia.chinesegrammar.util.ExportImport.importVocab("{\"schema\":\"other\"}") }
        assertThrows(IllegalArgumentException::class.java) { com.gia.chinesegrammar.util.ExportImport.importVocab("not json") }
    }
}
