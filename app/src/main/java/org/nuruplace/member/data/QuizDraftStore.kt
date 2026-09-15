package org.nuruplace.member.data

// The member's answers, saved as they go.
//
// Owner, 2026-09-15: "For every question you submit, make sure it's saved and
// in case you refresh in the middle, you go back to the question you had
// submitted and not from the beginning."
//
// Before this, answers lived in a `remember { mutableStateMapOf() }` and were
// sent all at once at the end. A relaunch, a low-memory kill (common on the
// phones our members carry), or a refresh lost every answer and put them back
// on question 1. Now every answer is written here the moment it is given.
//
// WHY THE DRAFT CARRIES ITS OWN QUESTIONS. The server assembles each quiz
// with `ORDER BY random()` and shuffled choices, so a fresh fetch never matches
// the set the member was answering. The draft therefore stores the exact
// questions it was taken against and the screen renders from those. Grading is
// against the active question bank, not the fetched order, so stored questions
// submit fine.
//
// WHAT THIS IS NOT. It is not a verdict. Scoring stays server-authoritative
// (§1.1): the draft holds answers only, and is cleared the moment a submit
// succeeds. Device-local by design — "refresh" is a device event; a cross-phone
// draft was neither asked for nor worth a new server contract.

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nuruplace.member.data.net.QuizQuestion
import org.nuruplace.member.data.net.GiftQuestionSet

@Serializable
data class QuizDraft(
    /** "module:<id>" | "level:<n>" — one draft per test. */
    val key: String,
    /** The exact set answered against; rendered from, never refetched. */
    val questions: List<QuizQuestion>,
    /** Where they were. */
    val index: Int,
    /** single-choice id / scale number / free text, by questionId. */
    val values: Map<String, String>,
    /** checkbox selections, by questionId. */
    val checks: Map<String, Set<String>>,
    val savedAtMs: Long,
) {
    val answeredCount: Int get() = values.size + checks.count { it.value.isNotEmpty() }
}

/**
 * The gifts assessment: a different question shape, and a set that can be
 * AI-personalised per fetch — so the draft carries the exact set the member
 * was answering (it is @Serializable) and never refetches.
 */
@Serializable
data class GiftsDraft(
    val questionSet: GiftQuestionSet,
    val chosen: Map<String, Int>,
    val savedAtMs: Long,
)

object QuizDraftStore {
    private const val FILE = "nuru_quiz_drafts"
    private const val GIFTS_KEY = "gifts"

    /** Drafts older than this are stale — the member has moved on. */
    private const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    private val ready: Boolean get() = ::prefs.isInitialized

    // ── Quizzes and exams ────────────────────────────────────────────────────

    fun load(key: String): QuizDraft? {
        if (!ready) return null
        val raw = prefs.getString(key, null) ?: return null
        val d = runCatching { json.decodeFromString<QuizDraft>(raw) }.getOrNull()
            ?: run { clear(key); return null }            // unreadable = discard, never crash
        if (System.currentTimeMillis() - d.savedAtMs > MAX_AGE_MS) { clear(key); return null }
        if (d.questions.isEmpty()) { clear(key); return null }
        return d
    }

    /** Called on EVERY answer and every question change. Cheap: one small string. */
    fun save(
        key: String,
        questions: List<QuizQuestion>,
        index: Int,
        values: Map<String, String>,
        checks: Map<String, Set<String>>,
    ) {
        if (!ready) return
        val d = QuizDraft(key, questions, index, values, checks, System.currentTimeMillis())
        prefs.edit().putString(key, json.encodeToString(d)).apply()
    }

    /** On a successful submit, or an explicit "start over". */
    fun clear(key: String) {
        if (ready) prefs.edit().remove(key).apply()
    }

    // ── Gifts assessment ─────────────────────────────────────────────────────

    fun loadGifts(): GiftsDraft? {
        if (!ready) return null
        val raw = prefs.getString(GIFTS_KEY, null) ?: return null
        val d = runCatching { json.decodeFromString<GiftsDraft>(raw) }.getOrNull()
            ?: run { clearGifts(); return null }
        if (System.currentTimeMillis() - d.savedAtMs > MAX_AGE_MS) { clearGifts(); return null }
        if (d.questionSet.data.isEmpty()) { clearGifts(); return null }
        return d
    }

    fun saveGifts(set: GiftQuestionSet, chosen: Map<String, Int>) {
        if (!ready) return
        prefs.edit().putString(GIFTS_KEY, json.encodeToString(GiftsDraft(set, chosen, System.currentTimeMillis()))).apply()
    }

    fun clearGifts() {
        if (ready) prefs.edit().remove(GIFTS_KEY).apply()
    }
}
