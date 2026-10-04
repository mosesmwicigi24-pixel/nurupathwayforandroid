// One state language (pathway docs/EXPERIENCE.md §4, Cycle 1) — what a member
// reads when a load or an action fails, said the same way on every screen:
// a title, a line, and the one thing to do. Never raw server or exception
// text ("Invalid or expired access token", "HTTP 500", a JSON parser's
// complaint), and never "check your connection" when it wasn't the connection.
// A refusal written for members by our own API (400/409/422, "Finish every
// module in this level before the exam") keeps its own words.
//
// Pure, so StateLanguageTest pins every row. The same table is built on iOS;
// both apps say exactly these words.
package org.nuruplace.member.data.net

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/** What happened (§4's Cause column) — it picks the state card's glyph. */
enum class StateCause { OFFLINE, SESSION_ENDED, SERVER, NOT_FOUND, REFUSAL }

/** What a member can do about a state (§4's Action column). */
enum class StateAction(val label: String) {
    RETRY("Try again"),
    SIGN_IN("Sign in"),
    BACK("Go back"),
    NONE(""),
}

/** One state, in the shared language. */
data class StateMessage(val cause: StateCause, val title: String, val line: String?, val action: StateAction) {
    /** The one-line form, for a note under a button or a toast: "Title. Line"
     *  — or the title alone, when it is a whole sentence of the server's. */
    val sentence: String get() = if (line.isNullOrBlank()) title else "$title. $line"
}

object StateLanguage {
    // §4, verbatim.
    const val OFFLINE_TITLE = "You're offline"
    const val OFFLINE_LINE_KEPT = "Showing what you last saw — we'll refresh when you're back."
    const val OFFLINE_LINE_EMPTY = "Connect to the internet, then try again."

    /** No network, or no answer (§4 row 1). [hasSavedCopy]: the screen still
     *  shows what the member last saw. */
    fun offline(hasSavedCopy: Boolean = false) = StateMessage(
        StateCause.OFFLINE,
        OFFLINE_TITLE,
        if (hasSavedCopy) OFFLINE_LINE_KEPT else OFFLINE_LINE_EMPTY,
        StateAction.RETRY,
    )

    /** The session is over — a 401 the token refresh could not mend (§4 row 2). */
    val sessionEnded = StateMessage(
        StateCause.SESSION_ENDED,
        "Your session has ended",
        "Sign in again to pick up where you left off.",
        StateAction.SIGN_IN,
    )

    /** Our side failed — a 5xx, an answer we could not read (§4 row 3). */
    val serverError = StateMessage(
        StateCause.SERVER,
        "Something went wrong on our side",
        "It isn't you — please try again in a moment.",
        StateAction.RETRY,
    )

    /** A 404 (§4 row 4). */
    val notFound = StateMessage(
        StateCause.NOT_FOUND,
        "This isn't here any more",
        "It may have been moved or removed.",
        StateAction.BACK,
    )

    /** A refusal in our API's own words (§4 row 5) — the screen decides what
     *  to offer; a full-screen load offers Try again (and Go back). */
    fun refusal(words: String) = StateMessage(StateCause.REFUSAL, words.trim(), null, StateAction.RETRY)

    /**
     * An HTTP answer by its status. [serverWords] — the message of OUR error
     * envelope (one with a code; a proxy's page or a bare status is not
     * something we said) — is shown only for a member-facing refusal (any
     * other 4xx); a 401's "Invalid or expired access token" or a 5xx's
     * "Internal server error" never reaches the member.
     */
    fun forStatus(status: Int, serverWords: String?): StateMessage = when {
        status == 401 -> sessionEnded
        status == 404 -> notFound
        status in 400..499 && !serverWords.isNullOrBlank() -> refusal(serverWords)
        else -> serverError
    }

    /**
     * A call that never got an answer. [deviceOnline] is the phone's own
     * network state, when known: a phone without a network is offline; a
     * phone WITH one that still got no answer was failed by our side, not by
     * its connection. Unknown: a timeout is the host's silence (a phone with
     * no network fails long before a connect or read timeout), anything
     * else reads as offline.
     */
    fun forTransport(e: IOException, deviceOnline: Boolean?, hasSavedCopy: Boolean = false): StateMessage = when {
        deviceOnline == false -> offline(hasSavedCopy)
        deviceOnline == true -> serverError
        e is SocketTimeoutException -> serverError
        else -> offline(hasSavedCopy)
    }

    /** Any failure: HTTP by status, transport by the network, and anything
     *  else (an answer we could not read, a client fault) is ours. */
    fun forError(
        e: Throwable,
        deviceOnline: Boolean? = null,
        serverWords: String? = null,
        hasSavedCopy: Boolean = false,
    ): StateMessage = when (e) {
        is HttpException -> forStatus(e.code(), serverWords)
        is IOException -> forTransport(e, deviceOnline, hasSavedCopy)
        else -> serverError
    }
}
