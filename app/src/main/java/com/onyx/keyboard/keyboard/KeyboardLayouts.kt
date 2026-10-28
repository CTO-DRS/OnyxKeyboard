package com.onyx.keyboard.keyboard

/**
 * Physical key layouts. Rows are plain label lists; special keys
 * (shift / backspace / space / enter / mode toggles) are rendered by the UI
 * around them, mirroring real IME layout structure.
 */
object KeyboardLayouts {

    private val arabicRows: List<List<String>> = listOf(
        listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "د"),
        listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
        listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ"),
    )

    /** Shifted Arabic: punctuation + harakat + Arabic-Indic digits. */
    private val arabicAltRows: List<List<String>> = listOf(
        listOf("أ", "إ", "آ", "؛", "،", "؟", "!", ":", "\"", "/", "٪"),
        listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠"),
        listOf("َ", "ُ", "ِ", "ّ", "ْ", "(", ")", "-", "_", "+"),
    )

    private val englishRows: List<List<String>> = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m"),
    )

    /** Page 1: digits row + top symbols. */
    val symbolRows: List<List<String>> = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "\$", "_", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?", "=", "%"),
    )

    /** Page 2: extended symbols. */
    val symbolRowsAlt: List<List<String>> = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
        listOf("£", "€", "¥", "^", "°", "{", "}", "\\", "[", "]"),
        listOf("<", ">", "\$", "«", "»", "…", "≈", "±", "µ", "∞"),
    )

    /** Optional digits row shown above the letters when the user enables it. */
    val numberRow: List<String> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    // ------------------------------------------------------- advanced numpad

    /**
     * Slim function row of the advanced numpad (rendered at reduced height).
     * "ABC" is the exit key back to the letters page; the rest commit
     * directly through [numpadCommit].
     */
    val numpadSlim: List<String> = listOf("ABC", "√", "x²", "π", "%", "(", ")")

    /**
     * Main digit/operator grid. Each row is rendered with fixed extras:
     * row 0 gets a backspace icon appended, row 1 and 2 are plain, and the
     * final utility row (0 / . / space / enter) is composed by the UI.
     */
    val numpadRows: List<List<String>> = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×", "+"),
        listOf("1", "2", "3", "−", "="),
    )

    /**
     * Maps an on-screen numpad label to the text it actually commits.
     * Mostly identity — "x²" commits the superscript and "−" falls back to
     * the ASCII hyphen-minus that text fields universally accept.
     */
    fun numpadCommit(label: String): String = when (label) {
        "x²" -> "²"
        "−" -> "-"
        else -> label
    }

    /**
     * The un-shifted physical letter grid for [lang] — exposed so the glide
     * matcher can derive key adjacency (the "letter chain" geometry) from
     * the ONE source of truth instead of a hand-copied map.
     */
    fun baseLetterRows(lang: Lang): List<List<String>> = when (lang) {
        Lang.AR -> arabicRows
        Lang.EN -> englishRows
    }

    fun letterRows(lang: Lang, shift: ShiftState): List<List<String>> = when (lang) {
        Lang.AR -> if (shift == ShiftState.OFF) arabicRows else arabicAltRows
        Lang.EN -> if (shift == ShiftState.OFF) englishRows else {
            englishRows.map { row -> row.map { it.uppercase() } }
        }
    }
}

/**
 * Emoji catalog grouped by category, fully offline. Every emoji carries a
 * set of ARABIC keywords powering the panel's live search (matched through
 * [ArabicSearch.normalize] so hamza/taa-marbuta/diacritic variants all hit).
 * Categories provide their own label keywords so a category name ("قلوب",
 * "طعام") surfaces the whole shelf. Two new shelves vs. the original pack:
 * «أشخاص» and «أعلام».
 */
object EmojiCatalog {

    /** One searchable emoji: the character plus Arabic keywords. */
    data class EmojiEntry(val e: String, val kw: List<String>)

    /** A shelf: display label, label keywords (searchable), entries. */
    data class Category(
        val label: String,
        val labelKw: List<String>,
        val entries: List<EmojiEntry>,
    ) {
        /** Plain characters in shelf order (rendering / recents compat). */
        val emojis: List<String> get() = entries.map { it.e }
    }

    /** Helper: split a space-separated keyword blob once, at load. */
    private fun E(e: String, kws: String): EmojiEntry = EmojiEntry(e, kws.split(' '))

    val categories: List<Category> = listOf(
        Category("وجوه", listOf("وجه", "تعبير", "ابتسامة", "ضحك"), listOf(
            E("😀", "ابتسامة فرح سعادة"),
            E("😃", "ابتسامة فرح سعادة"),
            E("😄", "ضحك فرح عيون"),
            E("😁", "ضحك عيون مبتسمة"),
            E("😆", "ضحك ضيقة"),
            E("😅", "عرق ضحك تمرين"),
            E("😂", "دموع ضحك قهقهة"),
            E("🤣", "ضحك أرضا قهقهة"),
            E("🙂", "ابتسامة خفيفة"),
            E("😉", "غمزة"),
            E("😊", "خجل ابتسامة ود"),
            E("😍", "عيون حب اعجاب"),
            E("🥰", "حب قلوب سعادة"),
            E("😘", "قبلة حب"),
            E("😜", "لسان غمزة مرح"),
            E("🤪", "مجنون لسان عبث"),
            E("🤗", "عناق حضن"),
            E("🤔", "تفكير شك"),
            E("🤨", "حاجب شك سؤال"),
            E("😐", "محايد ساكن"),
            E("😑", "صامت عيون ممل"),
            E("🙄", "عيون دحرجة ضجر"),
            E("😏", "ماكرة دلال"),
            E("😴", "نوم نعاس"),
            E("😌", "ارتياح هدوء"),
            E("😔", "حزن خيبة"),
            E("🙃", "مقلوب سخافة"),
            E("😲", "دهشة مفاجأة"),
            E("☹️", "حزن عبوس"),
            E("😖", "حيرة إزعاج"),
            E("😢", "بكاء دمعة حزن"),
            E("😭", "بكاء دموع صراخ"),
            E("😤", "عتاب أنف تحد"),
            E("😠", "غضب زعل"),
            E("🤯", "انفجار عقل صدمة"),
            E("😬", "تشنج أسنان حرج"),
            E("😰", "خوف قلق توتر"),
            E("🥵", "حرارة عرق صيف"),
            E("🥶", "برودة تجمد شتاء"),
            E("😳", "خجل احمرار"),
            E("🤤", "شهية جوع لسان"),
            E("😵", "دوخة إغماء"),
            E("🥴", "دوخة سكر تشوش"),
            E("🤢", "غثيان قرف مرض"),
            E("🤧", "عطس زكام"),
            E("😷", "كمامة مرض وقاية"),
            E("🤑", "مال طمع فم"),
            E("🤠", "قبعة راعي"),
            E("😈", "شيطان عبث"),
            E("💀", "جمجمة ميت"),
            E("👻", "شبح خيال"),
            E("👽", "فضائي كوكب"),
            E("🤖", "روبوت آلة"),
            E("💩", "روث سخافة"),
        )),
        Category("قلوب", listOf("قلب", "حب", "قلوب", "احتفال", "نجوم"), listOf(
            E("❤️", "قلب حب أحمر"),
            E("🧡", "قلب برتقالي"),
            E("💛", "قلب أصفر"),
            E("💚", "قلب أخضر"),
            E("💙", "قلب أزرق"),
            E("💜", "قلب بنفسجي"),
            E("🖤", "قلب أسود"),
            E("🤍", "قلب أبيض"),
            E("🤎", "قلب بني"),
            E("💔", "قلب مكسور انفصال"),
            E("❣️", "قلب تعجب"),
            E("💕", "قلوب حب"),
            E("💞", "قلوب دوارة حب"),
            E("💓", "قلب نابض حب"),
            E("💗", "قلب نابض لمعان"),
            E("💖", "قلب لامع حب"),
            E("💘", "قلب سهم عشق"),
            E("💝", "قلب شريط هدية"),
            E("💟", "قلب زخرفة"),
            E("✨", "نجوم لمعان بريق"),
            E("⭐", "نجمة"),
            E("🌟", "نجمة لمعان تالق"),
            E("💫", "دوامة نجم دوار"),
            E("🔥", "نار حريق رائع"),
            E("💥", "انفجار دوي"),
            E("⚡", "برق صاعقة سرعة"),
            E("🎉", "حفلة انفجار احتفال"),
            E("🎊", "منشور احتفال عيد"),
            E("🥳", "احتفال حفلة فرح"),
            E("🎁", "هدية عيد"),
        )),
        Category("إيماءات", listOf("ايماءة", "يد", "اشارة", "اصابع"), listOf(
            E("👍", "اعجاب موافقة جيد"),
            E("👎", "استنكار رفض سيء"),
            E("👌", "تمام ممتاز دقة"),
            E("✌️", "سلام نصر"),
            E("🤞", "حظ تداخل أصابع"),
            E("🤟", "احبك لغة إشارة"),
            E("🤘", "صخور مراوغة"),
            E("🤙", "اتصال اتصلي"),
            E("👈", "لفت يسار"),
            E("👉", "لفت يمين"),
            E("👆", "فوق لفت"),
            E("👇", "أسفل لفت"),
            E("☝️", "اشارة صب"),
            E("✋", "يد كف وقف"),
            E("🤚", "كف مرفوع يد"),
            E("🖐️", "أصابع كف يد"),
            E("🖖", "فولكان تحية"),
            E("👋", "تحية سلام وداع"),
            E("🤝", "مصافحة اتفاق"),
            E("🙏", "شكر دعاء امتنان"),
            E("💪", "قوة عضلة رياضة"),
            E("🦾", "ذراع صناعية قوة"),
            E("✍️", "كتابة خط"),
            E("👏", "تصفيق تشجيع"),
            E("🙌", "احتفال أيدي فرح"),
            E("👐", "فتح أيدي حضن"),
            E("🤲", "دعاء كفوف"),
            E("🫶", "قلب أيدي حب"),
            E("🤳", "سيلفي تصوير"),
            E("👊", "قبضة ضرب"),
        )),
        Category("أشخاص", listOf("شخص", "ناس", "انسان", "مهنة"), listOf(
            E("👶", "طفل رضيع"),
            E("👦", "ولد صبي"),
            E("👧", "بنت فتاة"),
            E("🧑", "شخص"),
            E("👨", "رجل"),
            E("👩", "امرأة"),
            E("🧓", "مسن شيخ"),
            E("👴", "جد مسن"),
            E("👵", "جدة مسنة"),
            E("👮", "شرطي أمن"),
            E("👷", "عامل بناء خوذة"),
            E("🤴", "أمير ملك"),
            E("👸", "أميرة تاج"),
            E("🦸", "بطل خارق"),
            E("🦹", "شرير خارق"),
            E("🧙", "ساحر سحر"),
            E("🧚", "جني خرافة"),
            E("🎅", "سانتا هدية عيد"),
            E("🤶", "سانتا امرأة عيد"),
            E("🧕", "محجبة حجاب"),
            E("👳", "عمامة"),
            E("🤰", "حامل حمل"),
            E("🕵️", "محقق تجسس"),
            E("💃", "راقصة رقص"),
            E("🕺", "راقص رقص"),
        )),
        Category("حيوانات", listOf("حيوان", "حيوانات", "طبيعة"), listOf(
            E("🐶", "كلب جراء"),
            E("🐱", "قطة قط"),
            E("🐭", "فأر"),
            E("🐹", "همستر"),
            E("🐰", "أرنب"),
            E("🦊", "ثعلب"),
            E("🐻", "دب"),
            E("🐼", "باندا دب"),
            E("🐨", "كوالا"),
            E("🐯", "نمر"),
            E("🦁", "أسد"),
            E("🐮", "بقرة"),
            E("🐷", "خنزير"),
            E("🐸", "ضفدع"),
            E("🐵", "قرد"),
            E("🐔", "دجاجة"),
            E("🐧", "بطريق"),
            E("🐦", "طائر عصفور"),
            E("🦅", "نسر صقر"),
            E("🦉", "بومة"),
            E("🐺", "ذئب"),
            E("🐴", "حصان"),
            E("🦄", "وحيد قرن خرافة"),
            E("🐝", "نحلة عسل"),
            E("🦋", "فراشة"),
            E("🐌", "حلزون"),
            E("🐞", "خنفساء دعسوقة"),
            E("🐢", "سلحفاة"),
            E("🐍", "ثعبان حية"),
            E("🐙", "أخطبوط بحر"),
        )),
        Category("طعام", listOf("طعام", "اكل", "شرب", "فواكه"), listOf(
            E("🍏", "تفاح أخضر"),
            E("🍎", "تفاح"),
            E("🍐", "كمثرى"),
            E("🍊", "برتقال"),
            E("🍋", "ليمون"),
            E("🍌", "موز"),
            E("🍉", "بطيخ"),
            E("🍇", "عنب"),
            E("🍓", "فراولة"),
            E("🫐", "توت"),
            E("🍈", "شمام"),
            E("🍒", "كرز"),
            E("🍑", "خوخ"),
            E("🥭", "مانجا"),
            E("🍍", "أناناس"),
            E("🥥", "جوز هند"),
            E("🥝", "كيوي"),
            E("🍅", "طماطم بندورة"),
            E("🥑", "أفوكادو"),
            E("🍆", "باذنجان"),
            E("🥕", "جزر"),
            E("🌽", "ذرة"),
            E("🌶️", "فلفل حار"),
            E("🍞", "خبز"),
            E("🥐", "كرواسون معجنات"),
            E("🥖", "خبز فرنسي"),
            E("🧀", "جبن"),
            E("🍳", "بيض مقلاة طبخ"),
            E("🍔", "برجر سندويتش"),
            E("🍟", "بطاطس مقلية"),
            E("🍕", "بيتزا"),
            E("🌮", "تاكو"),
            E("🌯", "لفة شاورما"),
            E("🥗", "سلطة"),
            E("🍝", "باستا معكرونة"),
            E("🍜", "نودلز رامن"),
            E("🍣", "سوشي"),
            E("🍤", "جمبري روبيان"),
            E("🍦", "آيس كريم مثلجات"),
            E("🍩", "دونات حلقى"),
            E("🍪", "بسكويت كعكة"),
            E("🎂", "كعكة عيد ميلاد"),
            E("🍰", "قطعة كعكة"),
            E("🍫", "شوكولاتة"),
            E("🍿", "فشار سينما"),
            E("☕", "قهوة"),
            E("🍵", "شاي"),
            E("🧃", "عصير"),
        )),
        Category("أنشطة", listOf("نشاط", "رياضة", "موسيقى", "لعب"), listOf(
            E("⚽", "كرة قدم"),
            E("🏀", "كرة سلة"),
            E("🏈", "رغبي"),
            E("⚾", "بيسبول"),
            E("🎾", "تنس"),
            E("🏐", "كرة طائرة"),
            E("🎱", "بلياردو"),
            E("🏓", "بينج بونج"),
            E("🏸", "تنس ريشة"),
            E("🎮", "ألعاب فيديو جيم"),
            E("🎲", "نرد"),
            E("🎯", "هدف دارة"),
            E("🎬", "سينما كليبر فيلم"),
            E("🎵", "نوتة موسيقى"),
            E("🎶", "نوتات موسيقى"),
            E("🎤", "مايك غناء"),
            E("🎧", "سماعات"),
            E("🎸", "قيثارة"),
            E("🎹", "بيانو"),
            E("🥁", "طبلة"),
            E("🏆", "كأس جائزة فوز"),
            E("🥇", "ميدالية ذهب"),
            E("🎨", "ألوان فن رسم"),
            E("🎭", "مسرح تمثيل"),
            E("🎪", "سيرك"),
            E("🎰", "قمار آلة"),
            E("🧩", "بازل أحجية"),
            E("♟️", "شطرنج"),
            E("🎳", "بولينج"),
            E("🏃", "جري ركض"),
        )),
        Category("سفر", listOf("سفر", "مواصلات", "طقس", "عطلات"), listOf(
            E("🚗", "سيارة"),
            E("✈️", "طائرة سفر"),
            E("🚀", "صاروخ فضاء"),
            E("🛸", "طبق طائر"),
            E("⛵", "قارب شراع"),
            E("🚲", "دراجة"),
            E("🚂", "قطار"),
            E("🚌", "حافلة باص"),
            E("🗺️", "خريطة"),
            E("🏝️", "جزيرة شواطئ"),
            E("🌋", "بركان"),
            E("🏔️", "جبل ثلج"),
            E("🌊", "موجة بحر"),
            E("🌧️", "مطر غيمة"),
            E("⛅", "شمس غيوم جزئية"),
            E("🌈", "قوس قزح"),
            E("☀️", "شمس صيف"),
            E("🌙", "هلال قمر ليل"),
            E("☁️", "غيمة"),
            E("❄️", "ثلج ندفة شتاء"),
            E("🌪️", "إعصار عاصفة"),
            E("🌍", "أرض كوكب عالم"),
            E("🏕️", "تخييم خيمة"),
            E("🏙️", "مدينة ناطحات"),
            E("🛣️", "طريق"),
            E("🚦", "إشارة ضوء"),
            E("⛽", "محطة وقود بنزين"),
            E("🎡", "عجلة ملاهي"),
            E("🎢", "قطار ملاهي"),
            E("🗼", "برج"),
        )),
        Category("أشياء", listOf("اشياء", "ادوات", "اجهزة", "مكتب"), listOf(
            E("📱", "هاتف جوال"),
            E("💻", "لابتوب حاسوب"),
            E("⌨️", "لوحة مفاتيح"),
            E("🖥️", "شاشة كمبيوتر"),
            E("🖱️", "فأرة جهاز"),
            E("💾", "قرص حفظ"),
            E("📷", "كاميرا تصوير"),
            E("📸", "كاميرا فلاش"),
            E("📹", "كاميرا فيديو"),
            E("🔍", "بحث عدسة"),
            E("💡", "مصباح فكرة"),
            E("🔦", "كشاف ضوء"),
            E("💸", "نقود مال"),
            E("💳", "بطاقة دفع"),
            E("💎", "ماسة ألماس"),
            E("⚙️", "ترس إعدادات"),
            E("🔧", "مفتاح ربط"),
            E("🔨", "مطرقة"),
            E("🧲", "مغناطيس"),
            E("🧪", "تجربة كيمياء"),
            E("🧬", "جينات وراثة"),
            E("🔭", "تلسكوب نجوم"),
            E("🌡️", "ميزان حرارة"),
            E("⏰", "منبه ساعة"),
            E("⌛", "ساعة رملية"),
            E("📅", "تقويم تاريخ"),
            E("📌", "دبوس تثبيت"),
            E("📎", "مشبك ورق"),
            E("✂️", "مقص قطع"),
            E("📚", "كتب مكتبة"),
            E("📖", "كتاب مفتوح قراءة"),
            E("✏️", "قلم رصاص"),
            E("🖊️", "قلم حبر"),
            E("🗒️", "مفكرة ورق"),
            E("🔒", "قفل مقفل أمان"),
            E("🔑", "مفتاح"),
            E("🧿", "عين زرقاء حظ"),
            E("🔮", "كرة تنبؤ غيب"),
            E("🛠️", "أدوات صيانة"),
        )),
        Category("رموز", listOf("رموز", "علامات", "اشارات", "اشكال"), listOf(
            E("✅", "صح تم تأكيد"),
            E("❌", "غلط خطأ رفض"),
            E("❓", "سؤال استفهام"),
            E("❗", "تعجب تنبيه"),
            E("⚠️", "تحذير خطر"),
            E("♻️", "تدوير بيئة"),
            E("✔️", "صح علامة"),
            E("✖️", "ضرب غلط"),
            E("➕", "زائد جمع"),
            E("➖", "ناقص طرح"),
            E("➗", "قسمة تقسيم"),
            E("💠", "زخرفة معين"),
            E("🔴", "دائرة حمراء"),
            E("🟠", "دائرة برتقالية"),
            E("🟡", "دائرة صفراء"),
            E("🟢", "دائرة خضراء"),
            E("🔵", "دائرة زرقاء"),
            E("🟣", "دائرة بنفسجية"),
            E("⚫", "دائرة سوداء"),
            E("⚪", "دائرة بيضاء"),
            E("🔺", "مثلث أحمر أعلى"),
            E("🔻", "مثلث أحمر أسفل"),
            E("🔶", "معين برتقالي"),
            E("🔷", "معين أزرق"),
            E("🔱", "شنكة رمز"),
            E("♠️", "بستوني ورق"),
            E("♥️", "قلب ورق"),
            E("♦️", "ديناري ورق"),
            E("♣️", "سباتي ورق"),
            E("🚩", "علم أحمر"),
        )),
        Category("أعلام", listOf("اعلام", "علم", "دول"), listOf(
            E("🇸🇦", "علم السعودية"),
            E("🇦🇪", "علم الإمارات"),
            E("🇪🇬", "علم مصر"),
            E("🇶🇦", "علم قطر"),
            E("🇰🇼", "علم الكويت"),
            E("🇧🇭", "علم البحرين"),
            E("🇴🇲", "علم عمان"),
            E("🇯🇴", "علم الأردن"),
            E("🇱🇧", "علم لبنان"),
            E("🇸🇾", "علم سوريا"),
            E("🇮🇶", "علم العراق"),
            E("🇾🇪", "علم اليمن"),
            E("🇵🇸", "علم فلسطين"),
            E("🇲🇦", "علم المغرب"),
            E("🇩🇿", "علم الجزائر"),
            E("🇹🇳", "علم تونس"),
            E("🇱🇾", "علم ليبيا"),
            E("🇸🇩", "علم السودان"),
            E("🇹🇷", "علم تركيا"),
            E("🇺🇸", "علم أمريكا"),
            E("🇬🇧", "علم بريطانيا"),
            E("🇫🇷", "علم فرنسا"),
            E("🇩🇪", "علم ألمانيا"),
            E("🇮🇳", "علم الهند"),
            E("🇵🇰", "علم باكستان"),
            E("🇯🇵", "علم اليابان"),
            E("🇨🇳", "علم الصين"),
            E("🇧🇷", "علم البرازيل"),
        )),
    )

    /** Every character in the catalog, shelf order (dedup-checked by tests). */
    val all: List<String> get() = categories.flatMap { it.emojis }

    /**
     * ARABIC EMOJI SEARCH: every normalized query token must hit at least
     * one keyword of the emoji OR a keyword of its shelf label. Results are
     * capped (UI grid comfort) and ordered by shelf order so runs stay
     * stable. Empty/blank query → empty list (caller shows recents).
     */
    fun search(query: String, limit: Int = 80): List<String> {
        val tokens = ArabicSearch.tokens(query)
        if (tokens.isEmpty()) return emptyList()
        val out = ArrayList<String>(minOf(limit, 64))
        for (cat in categories) {
            for (entry in cat.entries) {
                if (ArabicSearch.matches(entry.kw + cat.labelKw, tokens)) {
                    out.add(entry.e)
                    if (out.size >= limit) return out
                }
            }
        }
        return out
    }
}
