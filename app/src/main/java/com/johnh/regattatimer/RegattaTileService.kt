package com.johnh.regattatimer

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

        fun modeChip(label: String, mode: Mode) =
            Chip.Builder(this, launchClickable(mode), device)
                .setPrimaryLabelContent(label)
                .setWidth(dp(88f))
                .setChipColors(ChipColors(argb(0xFF1B3A5C.toInt()), argb(0xFFF5F5F5.toInt())))
                .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, "REGATTA")
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(0xFF7FA6C9.toInt()))
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).build())
            .addContent(modeChip("5 min", Mode.FIVE))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(modeChip("3 min", Mode.THREE))
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column)
            .build()
    }

    private fun launchClickable(mode: Mode): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId("arm_${mode.name}")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .addKeyToExtraMapping(EXTRA_MODE, ActionBuilders.stringExtra(mode.name))
                            .build()
                    )
                    .build()
            )
            .build()
}
