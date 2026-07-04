// Is the DEVICE actually online right now? Used to tell a genuine "no network"
// apart from "the server couldn't be reached" — so we never tell a member on
// full WiFi/4G that they're offline.
package org.nuruplace.member.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkStatus {
    /** True when there's an active network with validated internet capability. */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // no manager (shouldn't happen) — don't cry wolf
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
