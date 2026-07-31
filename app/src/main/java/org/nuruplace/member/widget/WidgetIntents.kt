// Tap targets for both widgets — reuses the exact "nuru.dest" extra + route
// vocabulary MainShell already consumes from notifications/launcher shortcuts
// (see MainActivity.kt's PendingDest + NuruMessagingService's destFor), so a
// widget tap needs no new nav wiring on the app side.
package org.nuruplace.member.widget

import android.content.Context
import android.content.Intent
import org.nuruplace.member.MainActivity

/** [dest] is a MainShell route ("pathway", "radio", "live-now", ...). */
internal fun widgetDestIntent(context: Context, dest: String): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra("nuru.dest", dest)
        // Distinct data Uris per destination keep Android from treating taps on
        // different widgets (or a widget vs. a stale pending notification) as
        // "the same Intent" and coalescing their PendingIntents.
        .setData(android.net.Uri.parse("nuru-widget://dest/$dest"))
