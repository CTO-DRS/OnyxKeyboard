// Onyx Keyboard V 1.0.0 — Native performance core (C++17 / JNI)
// High-frequency keyboard operations live here so the Java heap never churns:
//  - Wagner–Fischer edit distance (bounded) for the Arabic/Latin corrector
//  - Arabic orthography normalization (harakat strip, alef/ya/ha unification)
//  - Fast avalanche hash used by the learning engine for quick word bucketing

#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>
#include <cstddef>
#include <cstdlib>

namespace {

inline bool is_harakat(uint16_t c) {
    // Tanween, harakat, shadda, sukun, superscript alef, tatweel
    return (c >= 0x064B && c <= 0x065F) || c == 0x0670 || c == 0x0640;
}

inline uint16_t normalize_char(uint16_t c) {
    switch (c) {
        case 0x0622: // آ
        case 0x0623: // أ
        case 0x0625: // إ
        case 0x0671: // ٱ
            return 0x0627; // ا
        case 0x0649: // ى
            return 0x064A; // ي
        case 0x0629: // ة
            return 0x0647; // ه
        case 0x0624: // ؤ
            return 0x0648; // و
        case 0x0626: // ئ
            return 0x064A; // ي
        default:
            return c;
    }
}

int bounded_edit_distance(const std::u16string &a, const std::u16string &b, int max_dist) {
    const size_t n = a.size();
    const size_t m = b.size();
    if (std::abs(static_cast<int>(n) - static_cast<int>(m)) > max_dist) return max_dist + 1;
    if (n == 0) return static_cast<int>(m);
    if (m == 0) return static_cast<int>(n);

    std::vector<int> prev(m + 1), curr(m + 1);
    for (size_t j = 0; j <= m; ++j) prev[j] = static_cast<int>(j);

    for (size_t i = 1; i <= n; ++i) {
        curr[0] = static_cast<int>(i);
        int row_min = curr[0];
        for (size_t j = 1; j <= m; ++j) {
            const int cost = (a[i - 1] == b[j - 1]) ? 0 : 1;
            int d = prev[j] + 1;                 // deletion
            const int ins = curr[j - 1] + 1;     // insertion
            const int sub = prev[j - 1] + cost;  // substitution
            if (ins < d) d = ins;
            if (sub < d) d = sub;
            curr[j] = d;
            if (d < row_min) row_min = d;
        }
        if (row_min > max_dist) return max_dist + 1; // early bail
        std::swap(prev, curr);
    }
    return prev[m];
}

uint64_t mix64(uint64_t x) {
    x ^= x >> 33;
    x *= 0xff51afd7ed558ccdULL;
    x ^= x >> 33;
    x *= 0xc4ceb9fe1a85ec53ULL;
    x ^= x >> 33;
    return x;
}

} // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_com_onyx_keyboard_nativebridge_OnyxNative_editDistance(
        JNIEnv *env, jclass /*clazz*/, jstring a, jstring b, jint max_dist) {
    if (a == nullptr || b == nullptr || max_dist < 0) return max_dist + 1;
    const jchar *pa = env->GetStringChars(a, nullptr);
    const jchar *pb = env->GetStringChars(b, nullptr);
    const jsize la = env->GetStringLength(a);
    const jsize lb = env->GetStringLength(b);
    std::u16string sa(reinterpret_cast<const char16_t *>(pa), static_cast<size_t>(la));
    std::u16string sb(reinterpret_cast<const char16_t *>(pb), static_cast<size_t>(lb));
    env->ReleaseStringChars(a, pa);
    env->ReleaseStringChars(b, pb);
    return bounded_edit_distance(sa, sb, max_dist);
}

JNIEXPORT jstring JNICALL
Java_com_onyx_keyboard_nativebridge_OnyxNative_normalizeArabic(
        JNIEnv *env, jclass /*clazz*/, jstring input) {
    if (input == nullptr) return env->NewStringUTF("");
    const jchar *p = env->GetStringChars(input, nullptr);
    const jsize len = env->GetStringLength(input);

    std::vector<uint16_t> out;
    out.reserve(static_cast<size_t>(len));
    for (jsize i = 0; i < len; ++i) {
        const uint16_t c = p[i];
        if (is_harakat(c)) continue; // strip diacritics entirely
        out.push_back(normalize_char(c));
    }
    env->ReleaseStringChars(input, p);

    jstring result = env->NewString(reinterpret_cast<const jchar *>(out.data()),
                                    static_cast<jsize>(out.size()));
    return result == nullptr ? env->NewStringUTF("") : result;
}

JNIEXPORT jlong JNICALL
Java_com_onyx_keyboard_nativebridge_OnyxNative_mixHash(
        JNIEnv *env, jclass /*clazz*/, jstring input, jlong seed) {
    if (input == nullptr) return static_cast<jlong>(mix64(static_cast<uint64_t>(seed)));
    const jchar *p = env->GetStringChars(input, nullptr);
    const jsize len = env->GetStringLength(input);
    uint64_t h = static_cast<uint64_t>(seed) ^ (static_cast<uint64_t>(len) << 1);
    for (jsize i = 0; i < len; ++i) {
        h = mix64(h ^ static_cast<uint64_t>(p[i]));
    }
    env->ReleaseStringChars(input, p);
    return static_cast<jlong>(h);
}

} // extern "C"
