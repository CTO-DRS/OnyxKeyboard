package com.onyx.keyboard.intelligence

import com.onyx.keyboard.data.db.WordEntity
import com.onyx.keyboard.data.repository.OnyxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Suggestion(
    val text: String,
    val isCorrection: Boolean = false,
)

/**
 * On-device next-word / prefix suggestion engine.
 *
 * Ranking sources (highest first):
 *  1. The active profile's learned + custom words (Room, frequency-ordered)
 *  2. Built-in offline lexicon for the current language
 *  3. Arabic auto-correct candidates within 2 edits (when enabled)
 *
 * Everything runs locally; no network, no telemetry.
 */
class SuggestionEngine(private val repo: OnyxRepository) {

    suspend fun suggest(
        rawPrefix: String,
        lang: String,
        profileId: Long,
        autocorrect: Boolean,
        maxSuggestions: Int = 3,
    ): List<Suggestion> = withContext(Dispatchers.Default) {
        val prefix = rawPrefix.trim().lowercase()
        if (prefix.isEmpty()) return@withContext emptyList()

        val results = ArrayList<Suggestion>(maxSuggestions)
        val seen = HashSet<String>()

        // 1) User vocabulary first — it is personal and most relevant.
        repo.prefixMatch(profileId, prefix, 10).forEach { w: WordEntity ->
            if (results.size < maxSuggestions && seen.add(w.word)) {
                results += Suggestion(w.word)
            }
        }

        // 2) Built-in lexicon.
        val lexicon = if (lang == "ar") ArabicLexicon.words else EnglishLexicon.words
        for (w in lexicon) {
            if (results.size >= maxSuggestions) break
            if (w.startsWith(prefix) && seen.add(w)) results += Suggestion(w)
        }

        // 3) Arabic auto-correct within edit distance 2.
        if (results.isEmpty() && autocorrect && prefix.length >= 3) {
            val pool = lexicon + repo.topWords(profileId).map { it.word }
            ArabicCorrector.correct(prefix, pool, maxDistance = 2)?.let {
                if (seen.add(it)) results += Suggestion(it, isCorrection = true)
            }
        }

        results.take(maxSuggestions)
    }
}
