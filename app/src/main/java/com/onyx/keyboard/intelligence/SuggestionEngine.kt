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

    /**
     * NEXT-WORD prediction — the second half of the full suggestion system.
     *
     * Called whenever the composing buffer is empty and a context word is
     * known (the word the user just committed). Ranking:
     *
     *  1. Learned bigrams for THIS profile — pairs the user has actually
     *     typed before, frequency-ordered (personal, most relevant).
     *  2. Sentence-starter words for the current language — used when the
     *     sentence is fresh (after a terminator / newline) or no bigram
     *     exists yet for the context word.
     *
     * Pure on-device computation; no network anywhere on this path.
     */
    suspend fun predictNext(
        prevWord: String?,
        lang: String,
        profileId: Long,
        maxSuggestions: Int = 3,
    ): List<Suggestion> = withContext(Dispatchers.Default) {
        val prev = prevWord?.trim()?.lowercase().orEmpty()

        // 1) Personal bigram memory first.
        if (prev.isNotEmpty()) {
            val learned = repo.nextWords(profileId, prev, maxSuggestions + 3)
                .filter { it.isNotBlank() && it != prev }
            if (learned.isNotEmpty()) {
                return@withContext learned.take(maxSuggestions).map { Suggestion(it) }
            }
        }

        // 2) Fresh sentence (or unknown context) — language starters.
        val starters = if (lang == "ar") ArabicLexicon.starters else EnglishLexicon.starters
        starters.filter { it != prev }
            .take(maxSuggestions)
            .map { Suggestion(it) }
    }
}
