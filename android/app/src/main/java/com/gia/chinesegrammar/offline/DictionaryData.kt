package com.gia.chinesegrammar.offline

import com.gia.chinesegrammar.data.local.DictionaryEntryEntity

/**
 * Bundled starter dictionary (HSK1–3 core). Meanings written for this project — no third-party
 * dictionary content redistributed. Full CC-CEDICT import is a user-initiated optional download
 * (CC BY-SA 4.0) handled in ModelManager/docs, not bundled.
 */
object DictionaryData {
    data class Entry(val pinyin: String, val pos: String, val vi: String, val en: String, val example: String, val hsk: Int)

    val words: Map<String, Entry> = mapOf(
        "我" to Entry("wǒ", "pron", "tôi, mình", "I/me", "我是学生。", 1),
        "你" to Entry("nǐ", "pron", "bạn", "you", "你好吗？", 1),
        "他" to Entry("tā", "pron", "anh ấy", "he", "他是老师。", 1),
        "她" to Entry("tā", "pron", "cô ấy", "she", "她在学习。", 1),
        "我们" to Entry("wǒmen", "pron", "chúng tôi, chúng ta", "we", "我们一起去。", 1),
        "学生" to Entry("xuésheng", "n", "học sinh, sinh viên", "student", "我是学生。", 1),
        "老师" to Entry("lǎoshī", "n", "giáo viên", "teacher", "老师很好。", 1),
        "中国" to Entry("Zhōngguó", "n", "Trung Quốc", "China", "我去中国。", 1),
        "学习" to Entry("xuéxí", "v", "học tập", "to study", "我学习汉语。", 1),
        "汉语" to Entry("Hànyǔ", "n", "tiếng Trung", "Chinese language", "汉语不难。", 1),
        "喜欢" to Entry("xǐhuan", "v", "thích", "to like", "我喜欢茶。", 1),
        "吃" to Entry("chī", "v", "ăn", "to eat", "我吃饭。", 1),
        "喝" to Entry("hē", "v", "uống", "to drink", "他喝水。", 1),
        "去" to Entry("qù", "v", "đi", "to go", "我去学校。", 2),
        "来" to Entry("lái", "v", "đến", "to come", "他来了。", 1),
        "看" to Entry("kàn", "v", "xem, nhìn", "to look/read", "我看书。", 1),
        "书" to Entry("shū", "n", "sách", "book", "这本书很好。", 1),
        "朋友" to Entry("péngyou", "n", "bạn bè", "friend", "他是我的朋友。", 1),
        "今天" to Entry("jīntiān", "n", "hôm nay", "today", "今天很热。", 1),
        "明天" to Entry("míngtiān", "n", "ngày mai", "tomorrow", "明天见。", 1),
        "昨天" to Entry("zuótiān", "n", "hôm qua", "yesterday", "昨天下雨了。", 2),
        "时间" to Entry("shíjiān", "n", "thời gian", "time", "我没有时间。", 2),
        "工作" to Entry("gōngzuò", "v/n", "công việc; làm việc", "work", "他工作很忙。", 2),
        "公司" to Entry("gōngsī", "n", "công ty", "company", "我在公司工作。", 2),
        "学校" to Entry("xuéxiào", "n", "trường học", "school", "学校很大。", 1),
        "家" to Entry("jiā", "n", "nhà, gia đình", "home/family", "我回家了。", 1),
        "人" to Entry("rén", "n", "người", "person", "很多人在这里。", 1),
        "很" to Entry("hěn", "adv", "rất", "very", "今天很热。", 1),
        "不" to Entry("bù", "adv", "không", "not", "我不去。", 1),
        "没" to Entry("méi", "adv", "chưa/không (phủ định 有/quá khứ)", "not (have/perfective)", "我没吃饭。", 2),
        "都" to Entry("dōu", "adv", "đều", "all/both", "我们都是学生。", 2),
        "也" to Entry("yě", "adv", "cũng", "also", "我也去。", 2),
        "在" to Entry("zài", "prep/v", "ở; đang", "at; progressive", "他在家学习。", 1),
        "和" to Entry("hé", "conj", "và", "and", "我和他去。", 1),
        "的" to Entry("de", "part", "của (định ngữ)", "possessive/attributive particle", "我的书", 1),
        "了" to Entry("le", "part", "đã (hoàn thành) / rồi (thay đổi trạng thái)", "perfective/change-of-state particle", "我吃了饭。", 1),
        "过" to Entry("guo", "part", "đã từng (kinh nghiệm)", "experiential particle", "我去过中国。", 2),
        "着" to Entry("zhe", "part", "đang (trạng thái tiếp diễn)", "durative particle", "门开着。", 3),
        "把" to Entry("bǎ", "prep", "cấu trúc 把: đưa tân ngữ lên trước động từ", "ba-construction marker", "我把书看完了。", 3),
        "被" to Entry("bèi", "prep", "bị/được (câu bị động)", "passive marker", "书被他拿走了。", 3),
        "比" to Entry("bǐ", "prep", "hơn (so sánh)", "comparative marker", "今天比昨天热。", 2),
        "是" to Entry("shì", "v", "là", "to be", "我是学生。", 1),
        "有" to Entry("yǒu", "v", "có", "to have", "我有时间。", 1),
        "想" to Entry("xiǎng", "v", "muốn; nghĩ", "to want/think", "我想去中国。", 2),
        "会" to Entry("huì", "v", "biết (kỹ năng); sẽ", "can (learned skill); will", "我会说汉语。", 2),
        "能" to Entry("néng", "v", "có thể", "can (ability/permission)", "我能来。", 2),
        "热" to Entry("rè", "adj", "nóng", "hot", "今天很热。", 1),
        "大" to Entry("dà", "adj", "to, lớn", "big", "学校很大。", 1),
        "好" to Entry("hǎo", "adj", "tốt, hay", "good", "这本书很好。", 1),
        "忙" to Entry("máng", "adj", "bận", "busy", "他很忙。", 2),
        "完" to Entry("wán", "v", "xong (bổ ngữ kết quả)", "to finish (result complement)", "我看完了。", 2),
        "饭" to Entry("fàn", "n", "cơm, bữa ăn", "meal/rice", "我吃饭。", 1),
        "水" to Entry("shuǐ", "n", "nước", "water", "他喝水。", 1),
        "茶" to Entry("chá", "n", "trà", "tea", "我喜欢茶。", 1),
        "门" to Entry("mén", "n", "cửa", "door", "门开着。", 2),
        "开" to Entry("kāi", "v", "mở", "to open", "门开着。", 2),
        "请" to Entry("qǐng", "v", "xin mời", "please", "请坐。", 1),
        "谢谢" to Entry("xièxie", "v", "cảm ơn", "thank you", "谢谢你。", 1),
        "再见" to Entry("zàijiàn", "v", "tạm biệt", "goodbye", "明天见，再见。", 1),
        "吗" to Entry("ma", "part", "không? (trợ từ nghi vấn)", "yes-no question particle", "你好吗？", 1),
        "呢" to Entry("ne", "part", "còn... thì sao? (trợ từ)", "follow-up question particle", "你呢？", 2),
        "个" to Entry("gè", "mw", "cái, người (lượng từ chung)", "general measure word", "一个人", 1),
        "这" to Entry("zhè", "pron", "này, đây", "this", "这本书很好。", 1),
        "那" to Entry("nà", "pron", "kia, đó", "that", "那个人是谁？", 1),
        "谁" to Entry("shéi", "pron", "ai", "who", "他是谁？", 1),
        "什么" to Entry("shénme", "pron", "cái gì", "what", "这是什么？", 1),
        "怎么" to Entry("zěnme", "pron", "như thế nào; sao", "how/why", "你怎么去？", 2),
        "因为" to Entry("yīnwèi", "conj", "bởi vì", "because", "因为下雨，我没去。", 3),
        "所以" to Entry("suǒyǐ", "conj", "cho nên", "therefore", "下雨了，所以我没去。", 3),
        "但是" to Entry("dànshì", "conj", "nhưng", "but", "很难，但是我喜欢。", 3),
        "如果" to Entry("rúguǒ", "conj", "nếu", "if", "如果下雨，我就不去。", 3)
    )

    /** Single-character pinyin fallback for characters not covered by a dictionary word. */
    val charPinyin: Map<Char, String> = mapOf(
        '我' to "wǒ", '你' to "nǐ", '他' to "tā", '她' to "tā", '学' to "xué", '生' to "shēng", '老' to "lǎo", '师' to "shī",
        '中' to "zhōng", '国' to "guó", '汉' to "hàn", '语' to "yǔ", '喜' to "xǐ", '欢' to "huan", '吃' to "chī", '喝' to "hē",
        '去' to "qù", '来' to "lái", '看' to "kàn", '书' to "shū", '朋' to "péng", '友' to "yǒu", '今' to "jīn", '天' to "tiān",
        '明' to "míng", '昨' to "zuó", '时' to "shí", '间' to "jiān", '工' to "gōng", '作' to "zuò", '司' to "sī", '校' to "xiào",
        '家' to "jiā", '人' to "rén", '很' to "hěn", '不' to "bù", '没' to "méi", '都' to "dōu", '也' to "yě", '在' to "zài",
        '和' to "hé", '的' to "de", '了' to "le", '过' to "guo", '着' to "zhe", '把' to "bǎ", '被' to "bèi", '比' to "bǐ",
        '是' to "shì", '有' to "yǒu", '想' to "xiǎng", '会' to "huì", '能' to "néng", '热' to "rè", '大' to "dà", '好' to "hǎo",
        '忙' to "máng", '完' to "wán", '饭' to "fàn", '水' to "shuǐ", '茶' to "chá", '门' to "mén", '开' to "kāi", '这' to "zhè",
        '那' to "nà", '谁' to "shéi", '什' to "shén", '么' to "me", '怎' to "zěn", '因' to "yīn", '为' to "wèi", '所' to "suǒ",
        '以' to "yǐ", '但' to "dàn", '如' to "rú", '果' to "guǒ", '请' to "qǐng", '谢' to "xiè", '再' to "zài", '见' to "jiàn",
        '吗' to "ma", '呢' to "ne", '个' to "gè", '雨' to "yǔ", '下' to "xià", '坐' to "zuò", '走' to "zǒu", '拿' to "ná"
    )

    val pronouns = setOf("我", "你", "他", "她", "我们", "这", "那", "谁", "什么")
    val verbs = setOf("学习", "喜欢", "吃", "喝", "去", "来", "看", "工作", "是", "有", "想", "会", "能", "开", "拿", "走", "坐", "下")

    fun toRoomEntities(): List<DictionaryEntryEntity> = words.map { (w, e) ->
        DictionaryEntryEntity(word = w, pinyin = e.pinyin, pos = e.pos, meaningVi = e.vi, meaningEn = e.en, example = e.example, hskLevel = e.hsk)
    }
}
