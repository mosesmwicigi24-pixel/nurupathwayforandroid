// Pathway home-screen widget — progress ring, "Level N", modules done/total,
// next-module title, streak flame; the medium size additionally shows the
// level's title and a gold "Continue" pill (iOS NuruDoor parity in spirit —
// the iOS widget itself currently ships only a static door, see
// docs/PARITY_AUDIT.md's widgets entry for why Android goes further). Reads
// ONLY the on-disk WidgetSnapshotStore snapshot — never the network. Tapping
// anywhere opens the app straight into the Pathway tab.
package org.nuruplace.member.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle

private val SIZE_SMALL = DpSize(110.dp, 110.dp)
private val SIZE_MEDIUM = DpSize(250.dp, 120.dp)

class PathwayGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SIZE_SMALL, SIZE_MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore.read(context)
        val ring = WidgetRing.render(
            context = context,
            pct = snapshot.modulePct,
            sizeDp = 46,
            strokeDp = 4f,
            trackColor = android.graphics.Color.argb(46, 255, 255, 255),
            ringColor = android.graphics.Color.parseColor("#C9A227"),
        )
        provideContent {
            GlanceTheme(WidgetBrand.colors) {
                PathwayWidgetContent(context, snapshot, ring)
            }
        }
    }
}

/** Manifest-registered entry point (res/xml/pathway_widget_info.xml → this). */
class PathwayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PathwayGlanceWidget()
}

@Composable
private fun PathwayWidgetContent(context: Context, snapshot: WidgetSnapshot, ring: android.graphics.Bitmap) {
    val size = LocalSize.current
    val medium = size.width >= 200.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBrand.navy)
            .cornerRadius(20.dp)
            .clickable(actionStartActivity(widgetDestIntent(context, "pathway")))
            .padding(14.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Image(provider = ImageProvider(ring), contentDescription = "${snapshot.modulePct} percent complete")
                    Text(
                        "${snapshot.modulePct}%",
                        style = TextStyle(color = WidgetBrand.onNavyProvider, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(modifier = GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        "LEVEL ${snapshot.currentLevel}",
                        style = TextStyle(color = WidgetBrand.goldProvider, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    )
                    if (medium && snapshot.levelTitle.isNotBlank()) {
                        Text(
                            snapshot.levelTitle,
                            maxLines = 1,
                            style = TextStyle(
                                color = WidgetBrand.onNavyProvider, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif,
                            ),
                        )
                    }
                    Text(
                        "${snapshot.completedModules} of ${snapshot.totalModules} modules",
                        style = TextStyle(color = WidgetBrand.onNavyDimProvider, fontSize = 10.sp),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            Text(
                snapshot.nextModuleTitle?.takeIf { it.isNotBlank() } ?: "Level complete — pick your next one",
                maxLines = 2,
                style = TextStyle(
                    color = WidgetBrand.onNavyProvider, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif,
                ),
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (snapshot.streak > 0) {
                    Text(
                        "🔥 ${snapshot.streak}-day streak",
                        style = TextStyle(color = WidgetBrand.goldHiProvider, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    )
                } else {
                    Text("Open Pathway", style = TextStyle(color = WidgetBrand.onNavyFaintProvider, fontSize = 10.sp))
                }
                if (medium) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier
                            .background(WidgetBrand.gold)
                            .cornerRadius(999.dp)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "Continue",
                            style = TextStyle(
                                color = WidgetBrand.navyDeepProvider, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
        }
    }
}
