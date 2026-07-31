// Application entry — initialises the process-wide HTTP stack (and its secure
// token vault) once, before any screen asks for the API.
package org.nuruplace.member

import android.app.Application
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.CelebrationCenter
import org.nuruplace.member.widget.WidgetRefresher

class NuruApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Net.init(this)
        AppPrefs.init(this)
        CelebrationCenter.init(this) // once-only celebration memory (§human moments)
        // Home-screen widgets (Pathway + Radio, Glance) — belt-and-suspenders
        // 15-min timeline refresh; cheap no-op when no widget is pinned.
        WidgetRefresher.schedulePeriodic(this)
    }
}
