package com.onyx.keyboard.intelligence

/**
 * Built-in offline lexicons. Small, curated, and kept fully on-device.
 * The user's own learned words (Room) always rank above these.
 */
object ArabicLexicon {
    val words: List<String> = listOf(
        "في", "من", "على", "إلى", "عن", "هذا", "هذه", "ذلك", "التي", "الذي",
        "كان", "كانت", "يكون", "أن", "إن", "أو", "و", "ثم", "لكن", "حتى",
        "إذا", "كل", "بعض", "غير", "بين", "عند", "عندما", "اليوم", "أمس", "غدا",
        "الآن", "أيضا", "جدا", "فقط", "ربما", "نعم", "لا", "مرحبا", "السلام", "عليكم",
        "شكرا", "عفوا", "أهلا", "سهلا", "صباح", "المساء", "خير", "صديق", "عزيزي", "عزيزتي",
        "ماذا", "كيف", "لماذا", "أين", "متى", "ما", "هل", "تأكيد", "حسنا", "يلا",
        "انطلاق", "حاسوب", "هاتف", "رسالة", "ملف", "صورة", "فيديو", "موقع", "إنترنت", "برنامج",
        "تطبيق", "شاشة", "لوحة", "مفاتيح", "كلمة", "جملة", "لغة", "عربية", "إنجليزية", "ترجمة",
        "كتاب", "قراءة", "كتابة", "ذكاء", "مستقبل", "نور", "نجم", "سماء", "بحر", "نار",
        "ماء", "هواء", "أرض", "شمس", "قمر", "حب", "سلام", "أمل", "حلم", "نجاح",
        "طاقة", "سرعة", "قوة", "انطلق", "أنجز", "أرسل", "احفظ", "افتح", "أغلق", "ابحث",
        "عذرا", "فضلك", "تفضل", "ممكن", "عليك", "عليها", "عليه", "أنا", "أنت", "نحن",
        "هم", "هي", "هو", "لي", "لك", "لنا", "جميل", "رائع", "ممتاز", "مثير",
        "سؤال", "جواب", "فكرة", "مشروع", "وقت", "ساعة", "دقيقة", "يوم", "أسبوع", "شهر",
        "سنة", "عمل", "بيت", "مدرسة", "جامعة", "طريق", "مدينة", "بلد", "عالم", "حياة"
    )

    /**
     * Sentence-starter words shown as next-word predictions when a NEW
     * sentence begins (after a terminator / newline) and the user has no
     * learned continuation for the context. Ordered by natural frequency.
     */
    val starters: List<String> = listOf(
        "في", "من", "على", "إلى", "هذا", "هذه", "أنا", "أنت",
        "نحن", "كل", "عندما", "اليوم", "شكرا", "كيف", "ثم", "لكن"
    )
}

object EnglishLexicon {
    val words: List<String> = listOf(
        "the", "and", "for", "you", "are", "with", "this", "that", "have", "from",
        "not", "was", "were", "they", "will", "would", "could", "should", "there", "their",
        "about", "which", "when", "what", "where", "how", "than", "then", "them", "these",
        "those", "because", "been", "before", "after", "again", "other", "some", "such", "only",
        "own", "same", "too", "very", "just", "now", "new", "like", "love", "thanks",
        "hello", "please", "sorry", "welcome", "good", "morning", "night", "yes", "maybe", "okay",
        "cool", "nice", "great", "perfect", "awesome", "beautiful", "happy", "sad", "angry", "tired",
        "hungry", "work", "home", "school", "time", "life", "world", "people", "friend", "family",
        "today", "tomorrow", "yesterday", "always", "never", "often", "soon", "late", "fast", "slow",
        "easy", "hard", "big", "small", "more", "less", "best", "better", "worse", "first",
        "last", "next", "open", "save", "send", "search", "close", "write", "read", "type",
        "word", "sentence", "language", "arabic", "english", "translate", "future", "light", "dark", "star",
        "water", "fire", "earth", "sun", "moon", "peace", "hope", "dream", "success", "power",
        "speed", "strength", "energy", "message", "file", "photo", "video", "website", "internet", "program",
        "app", "screen", "keyboard", "phone", "computer", "idea", "question", "answer", "project", "minute",
        "hour", "day", "week", "month", "year", "city", "street", "country", "road", "door"
    )

    /** Sentence starters for next-word prediction at a fresh sentence. */
    val starters: List<String> = listOf(
        "the", "i", "you", "we", "it", "and", "but", "this",
        "that", "please", "thanks", "hello", "today", "just", "now", "how"
    )
}
