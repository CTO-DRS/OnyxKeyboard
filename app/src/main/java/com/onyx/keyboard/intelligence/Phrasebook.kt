package com.onyx.keyboard.intelligence

/**
 * Offline AR<->EN phrasebook powering the "Instant translate" panel.
 * 100% local — no network calls. Curated for the most frequent daily needs.
 */
object Phrasebook {

    // Arabic -> English
    val pairs: List<Pair<String, String>> = listOf(
        "مرحبا" to "Hello",
        "أهلا وسهلا" to "Welcome",
        "السلام عليكم" to "Peace be upon you",
        "صباح الخير" to "Good morning",
        "مساء الخير" to "Good evening",
        "تصبح على خير" to "Good night",
        "شكرا" to "Thank you",
        "شكرا جزيلا" to "Thank you very much",
        "عفوا" to "You're welcome / Excuse me",
        "من فضلك" to "Please",
        "آسف" to "Sorry",
        "نعم" to "Yes",
        "لا" to "No",
        "ربما" to "Maybe",
        "بالتأكيد" to "Certainly",
        "كيف حالك" to "How are you?",
        "بخير" to "I'm fine",
        "الحمد لله" to "Praise be to God / I'm well",
        "ما اسمك" to "What is your name?",
        "اسمي" to "My name is",
        "تشرفت بمعرفتك" to "Nice to meet you",
        "إلى اللقاء" to "Goodbye",
        "مع السلامة" to "Farewell",
        "أراك لاحقا" to "See you later",
        "أحبك" to "I love you",
        "أشتاق إليك" to "I miss you",
        "مبروك" to "Congratulations",
        "كل عام وأنتم بخير" to "Happy holidays",
        "عيد سعيد" to "Happy Eid",
        "مساعدة" to "Help",
        "أحتاج مساعدة" to "I need help",
        "أين" to "Where",
        "متى" to "When",
        "لماذا" to "Why",
        "كيف" to "How",
        "ماذا" to "What",
        "من" to "Who",
        "الآن" to "Now",
        "لاحقا" to "Later",
        "اليوم" to "Today",
        "غدا" to "Tomorrow",
        "أمس" to "Yesterday",
        "هاتف" to "Phone",
        "حاسوب" to "Computer",
        "رسالة" to "Message",
        "بريد إلكتروني" to "Email",
        "إنترنت" to "Internet",
        "كلمة سر" to "Password",
        "حساب" to "Account",
        "موقع" to "Website / Location",
        "تطبيق" to "Application",
        "صورة" to "Photo",
        "فيديو" to "Video",
        "ملف" to "File",
        "مجلد" to "Folder",
        "تنزيل" to "Download",
        "تحميل" to "Upload",
        "حفظ" to "Save",
        "فتح" to "Open",
        "إغلاق" to "Close",
        "بحث" to "Search",
        "إرسال" to "Send",
        "حذف" to "Delete",
        "تعديل" to "Edit",
        "نسخ" to "Copy",
        "لصق" to "Paste",
        "ترجمة" to "Translation",
        "لغة" to "Language",
        "عربي" to "Arabic",
        "إنجليزي" to "English",
        "كتاب" to "Book",
        "قراءة" to "Reading",
        "كتابة" to "Writing",
        "مدرسة" to "School",
        "جامعة" to "University",
        "عمل" to "Work / Job",
        "اجتماع" to "Meeting",
        "مشروع" to "Project",
        "تقرير" to "Report",
        "موعد" to "Appointment",
        "وقت" to "Time",
        "ساعة" to "Hour / Clock",
        "دقيقة" to "Minute",
        "يوم" to "Day",
        "أسبوع" to "Week",
        "شهر" to "Month",
        "سنة" to "Year",
        "طعام" to "Food",
        "ماء" to "Water",
        "قهوة" to "Coffee",
        "شاي" to "Tea",
        "مطعم" to "Restaurant",
        "فندق" to "Hotel",
        "مطار" to "Airport",
        "سيارة" to "Car",
        "طريق" to "Road",
        "مدينة" to "City",
        "بلد" to "Country",
        "عائلة" to "Family",
        "صديق" to "Friend",
        "حب" to "Love",
        "سلام" to "Peace",
        "سؤال" to "Question",
        "جواب" to "Answer",
        "فكرة" to "Idea",
        "خبر" to "News",
        "معلومة" to "Information",
        "دراسة" to "Study",
        "امتحان" to "Exam",
        "نجاح" to "Success",
        "تفضل" to "Go ahead / Here you go",
        "انتبه" to "Watch out",
        "حسنا" to "Okay",
        "هيا" to "Let's go",
        "بطيء" to "Slow",
        "سريع" to "Fast",
        "جميل" to "Beautiful",
        "رائع" to "Wonderful",
        "ممتاز" to "Excellent",
        "صعب" to "Difficult",
        "سهل" to "Easy",
        "جديد" to "New",
        "قديم" to "Old",
        "كبير" to "Big",
        "صغير" to "Small",
    )

    private val arIndex: Map<String, String> = pairs.toMap()
    private val enIndex: Map<String, String> = pairs.associate { (ar, en) -> en.lowercase() to ar }

    /** Searches both directions; returns "source → target" pairs. */
    fun search(queryRaw: String, limit: Int = 12): List<Pair<String, String>> {
        val q = queryRaw.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Pair<String, String>>(limit)
        // Exact hits first
        arIndex[q]?.let { out.add(q to it) }
        enIndex[q]?.let { out.add(it to q) }
        // Prefix / contains scan
        for ((ar, en) in pairs) {
            if (out.size >= limit) break
            val arl = ar.lowercase()
            if (arl != q && (arl.startsWith(q) || arl.contains(q))) out.add(ar to en)
            if (out.size >= limit) break
            val enl = en.lowercase()
            if (enl != q && (enl.startsWith(q) || enl.contains(q))) out.add(ar to en)
        }
        return out.distinct().take(limit)
    }
}
