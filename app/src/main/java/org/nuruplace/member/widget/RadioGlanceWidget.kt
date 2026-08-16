// Nuru Radio home-screen widget — a pulsing-red "ON AIR" dot + program title +
// listener count while live; the next program (or "Tap to tune in") off air.
// The medium size adds the host name and a stylized artwork tile (a static
// gold note glyph, NOT the show's real artwork — fetching that would mean
// networking from inside the widget, which this feature deliberately never
// does; see WidgetSnapshotStore's header). When the church is live right now,
// the medium size also surfaces a tappable "● LIVE now" line straight to the
// live player. Reads ONLY the on-disk snapshot. Tap → the radio tab (the
// LIVE line taps through to "live-now", MainShell's live-player forwarder).
package org.nuruplace.member.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

private val SIZE_SMALL = DpSize(110.dp, 110.dp)
private val SIZE_MEDIUM = DpSize(250.dp, 120.dp)

class RadioGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SIZE_SMALL, SIZE_MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore.read(context)
        provideContent {
            GlanceTheme(WidgetBrand.colors) {
                RadioWidgetContent(context, snapshot)
            }
        }
    }
}

/** Manifest-registered entry point (res/xml/radio_widget_info.xml → this). */
class RadioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RadioGlanceWidget()
}

@Composable
private fun RadioWidgetContent(context: Context, snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    val medium = size.width >= 200.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBrand.navy)
            .cornerRadius(20.dp)
            .clickable(actionStartActivity(widgetDestIntent(context, "radio")))
            .padding(14.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (medium) {
                ArtworkTile(onAir = snapshot.radioOnAir)
                Spacer(modifier = GlanceModifier.width(12.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = GlanceModifier.size(7.dp).cornerRadius(999.dp)
                            .background(if (snapshot.radioOnAir) WidgetBrand.liveRed else WidgetBrand.gold),
                    ) {}
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        if (snapshot.radioOnAir) "ON AIR" else "NURU RADIO",
                        style = TextStyle(color = WidgetBrand.goldProvider, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                Text(
                    radioHeadline(snapshot),
                    maxLines = if (medium) 2 else 1,
                    style = TextStyle(
                        color = WidgetBrand.onNavyProvider, fontSize = if (medium) 15.sp else 13.sp,
                        fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif,
                    ),
                )

                val listeners = snapshot.radioListeners
                val nextTitle = snapshot.radioNextProgramTitle?.takeIf { it.isNotBlank() }
                val host = snapshot.radioHost?.takeIf { it.isNotBlank() }

                if (snapshot.radioOnAir) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        if (listeners != null && listeners > 0) "$listeners listening now" else "Live now",
                        style = TextStyle(color = WidgetBrand.onNavyDimProvider, fontSize = 10.sp),
                    )
                } else if (medium && nextTitle != null) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        "Next: $nextTitle",
                        maxLines = 1,
                        style = TextStyle(color = WidgetBrand.onNavyDimProvider, fontSize = 10.sp),
                    )
                }

                if (medium && snapshot.radioOnAir && host != null) {
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(host, maxLines = 1, style = TextStyle(color = WidgetBrand.onNavyFaintProvider, fontSize = 9.sp))
                }

                if (medium && snapshot.churchLive) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        "● LIVE now",
                        modifier = GlanceModifier.clickable(actionStartActivity(widgetDestIntent(context, "live-now"))),
                        style = TextStyle(color = WidgetBrand.liveRedProvider, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkTile(onAir: Boolean) {
    Box(
        modifier = GlanceModifier.size(52.dp).cornerRadius(12.dp)
            .background(if (onAir) WidgetBrand.gold else WidgetBrand.navyDeep),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "♪",
            style = TextStyle(
                color = if (onAir) WidgetBrand.navyDeepProvider else WidgetBrand.goldProvider,
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** Pure and unit-tested — see WidgetFormattingTest. */
internal fun radioHeadline(snapshot: WidgetSnapshot): String {
    val programTitle = snapshot.radioProgramTitle?.takeIf { it.isNotBlank() }
    val nextTitle = snapshot.radioNextProgramTitle?.takeIf { it.isNotBlank() }
    return when {
        snapshot.radioOnAir && programTitle != null -> programTitle
        snapshot.radioOnAir -> "Worship & the Word"
        nextTitle != null -> nextTitle
        else -> "Tap to tune in"
    }
}
