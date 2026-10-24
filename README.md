# Onyx لوحة مفاتيح — V 1.0.0

لوحة مفاتيح Android حقيقية بتصميم **Glassmorphism مستقبلي (2035)** — أرقام داكنة، نيون بنفسجي، هولوجرام أزرق، وجسيمات ضوئية متحركة.

> هذه ليست نموذجاً أوضّاحياً: `InputMethodService` فعلي مسجّل في النظام، محرّك ذكاء محلي بالكامل، قاعدة بيانات Room مشفّرة، ووحدة C++ أصلية (NDK).

---

## المزايا

| # | الميزة | التفاصيل |
|---|--------|----------|
| 1 | **لوحة مفاتيح نظام حقيقية** | `OnyxIME` (InputMethodService) مع واجهة Jetpack Compose كاملة |
| 2 | **عربية + إنجليزية** | تخطيط عربي قياسي + QWERTY، تبديل فوري، حروف مدّ وعلامات عبر Shift |
| 3 | **تصميم نيون زجاجي** | مفاتيح متوهجة، توهج عند الضغط، تدرجات، جسيمات ضوئية |
| 4 | **شريط أدوات ذكي** | اقتراحات مباشرة + رموز + ترجمة فورية + حافظة + GIF + إملاء صوتي + إعدادات سريعة |
| 5 | **ذكاء محلي** | اقتراح كلمات، تصحيح عربي (مسافة تعديل ≤ 2 عبر C++)، تعلّم أسلوبك، اختصارات ذكية |
| 6 | **ملفات شخصية** | ملفات متعددة، ذاكرة منفصلة لكل ملف، قاموس مخصص قابل للتحرير |
| 7 | **نظام ثيمات** | 6 ثيمات مدمجة (Onyx Neon, Cyber Hologram, Emerald Pulse, Rose Circuit, Solar Flare, Midnight Glass) + حفظ تلقائي |
| 8 | **خصوصية مطلقة** | لا تحليلات ولا مزامنة؛ الحافظة مشفّرة AES-256/GCM عبر Android Keystore |
| 9 | **إحصاءات الاستخدام** | ضغطات/كلمات/رموز/جلسات صوتية يومية + إجمالي تراكمي |
| 10 | **وحدة C++ أصلية** | Levenshtein محدود + تطبيع عربي + هاش سريع (CMake/NDK) |

---

## البنية التقنية

- **Kotlin 2.0 + Jetpack Compose (BOM 2024.10) + Material 3**
- **MVVM + Kotlin Coroutines / Flow**
- **Room** (الملفات، الكلمات، الثيمات، الاختصارات، الحافظة، الإحصاءات)
- **NDK / C++17 / CMake** — `app/src/main/cpp/onyx_native.cpp`
- **minSdk 26 / targetSdk 35 / AGP 8.7.3 / Gradle 8.9**

### شجرة المشروع

```
OnyxKeyboard/
├── app/src/main/
│   ├── AndroidManifest.xml          ← تسجيل الخدمة BIND_INPUT_METHOD
│   ├── cpp/onyx_native.cpp          ← وحدة C++ (تصحيح + تطبيع + هاش)
│   ├── res/xml/method.xml           ← وصفيات IME (عربي/إنجليزي)
│   └── java/com/onyx/keyboard/
│       ├── OnyxApp.kt               ← تهيئة التطبيق و AppGraph
│       ├── MainActivity.kt          ← لوحة القيادة
│       ├── service/OnyxIME.kt       ← خدمة الإدخال (القلب)
│       ├── keyboard/                ← واجهة لوحة المفاتيح
│       │   ├── OnyxInputView.kt     ← جسر Compose داخل IME
│       │   ├── OnyxKeyboardController.kt
│       │   ├── OnyxKeyboardScreen.kt
│       │   ├── KeyboardLayouts.kt   ← تخطيطات AR/EN/رموز
│       │   ├── KeyButton.kt         ← مفتاح نيون متوهج
│       │   └── *.kt                 ← اللوحات: رموز/حافظة/ترجمة/GIF/صوت/إعدادات
│       ├── intelligence/            ← المحرك الذكي المحلي
│       ├── data/                    ← Room + المستودع + التشفير
│       └── ui/                      ← شاشات التطبيق والمكونات
```

---

## خطوات البناء على Android Studio

1. افتح **Android Studio Koala (2024.1) أو أحدث** مع **JDK 17**.
2. من **SDK Manager** ثبّت: **Android SDK 35**، **NDK (Side by side)**، **CMake 3.22.1**.
3. **File → Open** واختر مجلد `OnyxKeyboard`.
4. انتظر مزامنة Gradle (أول مزامنة تنزّل الاعتماديات).
5. اضغط **Run** على جهاز حقيقي أو محاكي (API 26+).

أو من الطرفية:

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## التفعيل

> ✅ **تم التحقق من البناء فعلياً (2026-09-16)** — راجع قسم «سجل البناء الموثّق» أدناه.

1. افتح التطبيق → اضغط **"التفعيل من إعدادات النظام"**.
2. فعّل مفتاح **Onyx لوحة مفاتيح**.
3. ارجع للتطبيق → اضغط **"اختر Onyx الآن"** واخترها كلوحة افتراضية.
4. ابدأ الكتابة في أي تطبيق.

## ملاحظات هندسية

- **الترجمة الفورية**: معجم محلي ثنائي الاتجاه (~120 مدخلاً) — تعمل بلا إنترنت نهائياً.
- **GIF**: لوحة اختيارية معطلة افتراضياً (تحترم الخصوصية). تستخدم مفتاح Giphy التجريبي العام — استبدل `API_KEY` في `GiphyClient.kt` بمفتاحك للإنتاج. الإدراج عبر `commitContent` مع سقوط آمن إلى رابط نصي.
- **الإملاء الصوتي**: عبر `SpeechRecognizer` النظامي؛ يتطلب صلاحية الميكروفون (تُمنح من شاشة الخصوصية).
- **التشفير**: محتوى الحافظة يُشفَّر قبل دخول Room بمفتاح AES-256/GCM محفوظ في Android Keystore ولا يغادر العتاد.
- **تطبيع عربي C++**: يوحّد أ/إ/آ→ا، ى→ي، ة→ه ويحذف الحركات قبل حساب مسافة التعديل — لمطابقة إملائية ذكية.
- **إضافة ثيم جديد**: أدخل صف `ThemeEntity` جديداً في `defaultThemes()` أو أضف جدول تعديل مستخدم لاحقاً — الواجهة تقرأ الألوان ديناميكياً.

---

## سجل البناء الموثّق (Build Verification Log)

تم بناء المشروع **فعلياً** في بيئة Linux (Debian 13) بتاريخ 2026-09-16:

| البند | القيمة |
|-------|--------|
| JDK | OpenJDK 21.0.12 (Adoptium, مع jlink) |
| Android SDK | platform-tools + platforms;android-35 + build-tools;35.0.0 |
| NDK / CMake | 27.0.12077973 / 3.22.1 |
| Gradle | 8.9 (wrapper) |
| النتيجة | **BUILD SUCCESSFUL** — :app:assembleDebug و :app:assembleRelease |

**المخرجات:**

| الملف | الحجم | ملاحظات |
|-------|-------|---------|
| `OnyxKeyboard-V1.0.0-debug.apk` | ~19.5 MB | قابل للتثبيت مباشرة، مع libonyx_native.so لثلاث معمارات |
| `OnyxKeyboard-V1.0.0-release.apk` | **2.3 MB** | R8 + ShrinkResources + موقّعة v2/v3 |
| `OnyxKeyboard-V1.0.0-source.zip` | 0.9 MB | المشروع كاملاً جاهز للفتح في Android Studio |

**التوقيع (Release):**
- Keystore: `onyx-release.keystore` (مضمّن في المشروع — **استبدله بمفتاحك الخاص قبل النشر على Google Play**)
- Store/Key password: `***REMOVED***` — Alias: `onyx`
- شهادة: CN=Onyx Keyboard, O=Onyx, C=SA

**أخطاء أُصلحت أثناء التحقق (للمرجعية):**
1. `SettingsEntity` كان مُشاراً في `@Database` و`SettingsDao` دون تعريف — أُضيف الكيان.
2. `Room.UniqueIndex` غير موجود في API — استُبدل بـ `Index(value=[...], unique=true)`.
3. استيراد `AppGraph` كان من مسار خاطئ في 4 ملفات ViewModel — صُحّح إلى `com.onyx.keyboard.AppGraph`.
4. `android.view.inputmethod.InputType` غير موجود — الصحيح `android.text.InputType`.
5. `setViewTreeViewModelStoreOwner(this, this)` — الوسيط الثاني زائد.
6. `AudioManager.FX_KEYPRESS` غير موجود — الصحيح `FX_KEYPRESS_STANDARD`.
7. `PanelHeader` كانت `private` في ملف واحد وتُستخدم في أربعة — أصبحت `internal`.
8. `produceState` بوسائط مسماة لم تُحلّ — استُبدلت بوسائط موضعية.
9. استيرادات ناقصة: `Box`/`size` في SettingsPanel و`width` في ThemesScreen.
10. `ime_name`/`ime_settings_activity` في values-en فقط (خطأ Lint قاتل للإصدار Release) — أُضيفت للملف الافتراضي.

---

## سجل الإصدار

### V 1.0.1 (versionCode 2) — إصلاح "اللوحة لا تظهر"

- **إصلاح Android 15 Edge-to-Edge للوحات IME**: مع targetSdk 35 تُفرض النافذة على امتداد الشاشة خلف شريط التنقل؛ أُضيف `OnApplyWindowInsetsListener` على `OnyxInputView` يضيف حشوة navigation-bar + display-cutout — التوثيق الرسمي: developer.android.com/about/versions/15 (IME edge-to-edge).
- **حماية من فشل العرض**: `onCreateInputView` محاط بـ try/catch مع واجهة بديلة وتسجيل الخطأ في Logcat (`OnyxIME`) بدلاً من لوحة فارغة.
- **تفعيل أسهل**: عند عودة المستخدم للتطبيق وحالة "مُفعّلة لكن غير مختارة" تُفتح نافذة اختيار لوحة النظام تلقائياً.
- ترقية versionCode إلى 2 (تثبيت فوقي مباشر فوق 1.0.0 بنفس التوقيع).
