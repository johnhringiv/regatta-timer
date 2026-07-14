package com.johnhringiv.regattatimer

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"

/** Watch-face tile: one tap opens the app armed in the chosen mode. */
class RegattaTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val root = tileLayout(requestParams)
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder().setRoot(root).build()
                    )
                    .build()
            )
            .build()
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .build()
        )
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        )

    private fun tileLayout(
        requestParams: RequestBuilders.TileRequest,
    ): LayoutElementBuilders.LayoutElement {
        val device = requestParams.deviceConfiguration

        fun title() = Text.Builder(this, "REGATTA")
            .setTypography(Typography.TYPOGRAPHY_TITLE3)
            .setColor(argb(GOLD))
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(title())

        val race = RaceStore(this).activeRace()
        if (race != null) {
            // A timer is in flight: say so instead of offering to arm a new one.
            val status =
                if (race.phase == "COUNTDOWN" && race.wallMs > System.currentTimeMillis()) {
                    "COUNTDOWN"
                } else {
                    "RACING"
                }
            column
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
                .addContent(
                    Text.Builder(this, status)
                        .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                        .setColor(argb(0xFF4CAF50.toInt()))
                        .build()
                )
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(10f)).build())
                .addContent(chip("Open", launchClickable("open", null), device))
        } else {
            column
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).build())
                .addContent(chip("5 min", launchClickable("arm_FIVE", Mode.FIVE), device))
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
                .addContent(chip("3 min", launchClickable("arm_THREE", Mode.THREE), device))
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column.build())
            .build()
    }

    private fun chip(
        label: String,
        clickable: ModifiersBuilders.Clickable,
        device: androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters,
    ) = Chip.Builder(this, clickable, device)
        .setPrimaryLabelContent(label)
        .setWidth(dp(88f))
        .setChipColors(ChipColors(argb(0xFF1B3A5C.toInt()), argb(0xFFF5F5F5.toInt())))
        .build()

    /** Launch MainActivity, optionally pre-arming [mode] (null = just open). */
    private fun launchClickable(id: String, mode: Mode?): ModifiersBuilders.Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(MainActivity::class.java.name)
        if (mode != null) {
            activity.addKeyToExtraMapping(EXTRA_MODE, ActionBuilders.stringExtra(mode.name))
        }
        return ModifiersBuilders.Clickable.Builder()
            .setId(id)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(activity.build())
                    .build()
            )
            .build()
    }

    private companion object {
        const val GOLD = 0xFFF5C518.toInt()
    }
}
