package com.gia.chinesegrammar.util

import com.gia.chinesegrammar.data.remote.TokenDto

/**
 * Token-offset alignment utilities. Offsets are UTF-16 char indices — identical semantics
 * in Kotlin String and Python str for BMP characters; supplementary-plane chars are handled
 * by validating against the source text rather than assuming.
 */
object TokenAlignment {
    /** True when every token's [start,end) slice equals its text and tokens are ordered, non-overlapping. */
    fun validate(text: String, tokens: List<TokenDto>): List<String> {
        val warnings = mutableListOf<String>()
        var prevEnd = 0
        tokens.forEachIndexed { i, t ->
            when {
                t.start < 0 || t.end > text.length || t.start >= t.end ->
                    warnings += "Token $i ('${t.text}') có offset không hợp lệ [${t.start},${t.end})"
                text.substring(t.start, t.end) != t.text ->
                    warnings += "Token $i ('${t.text}') không khớp văn bản gốc tại [${t.start},${t.end})"
                t.start < prevEnd -> warnings += "Token $i chồng lấn token trước"
            }
            prevEnd = maxOf(prevEnd, t.end)
        }
        return warnings
    }

    /** Rebuild offsets by locating each token sequentially in [text]; returns null if any token cannot be found in order. */
    fun realign(text: String, rawTokens: List<TokenDto>): List<TokenDto>? {
        val out = ArrayList<TokenDto>(rawTokens.size)
        var cursor = 0
        for (t in rawTokens) {
            val idx = text.indexOf(t.text, startIndex = cursor)
            if (idx < 0) return null
            out += t.copy(start = idx, end = idx + t.text.length)
            cursor = idx + t.text.length
        }
        return out
    }
}
