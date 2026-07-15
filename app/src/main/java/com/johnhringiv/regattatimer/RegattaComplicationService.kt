package com.johnhringiv.regattatimer

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import android.graphics.drawable.Icon
import java.time.Instant

/**
 * Watch-face complication: weather-style ring where the dot is time remaining,
 * ticking countdown in the center, sailboat at the bottom (face-rendered).
 *
 * Zero-push design: the countdown text ticks via TimeDifference text, the ring dot
 * sweeps via a platform-evaluated dynamic value, and a data timeline flips
 * countdown -> count-up at the gun — all without this process running.
 */
class RegattaComplicationService : SuspendingTimelineComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        val type = request.complicationType
        if (type != ComplicationType.RANGED_VALUE && type != ComplicationType.SHORT_TEXT) return null

        val store = RaceStore(this)
        val race = store.activeRace()
        // Armed complications follow the last mode used anywhere (app, tile, complication).
        val armed = armedData(type, store.lastMode(), request.complicationInstanceId)

        return when {
            race == null -> ComplicationDataTimeline(armed, emptyList())

            race.phase == "COUNTDOWN" -> {
                val mode = race.mode
                // Whole-second instant so the ticking text and the dot's dynamic value
                // flip together on epoch-second boundaries (offset flips look jarring).
                val deadline = wholeSecond(race.wallMs)
                val expiry = Instant.ofEpochMilli(race.savedAtMs + RaceStore.MAX_AGE_MS)
                ComplicationDataTimeline(
                    armed,
                    listOf(
                        TimelineEntry(
                            TimeInterval(Instant.ofEpochMilli(race.savedAtMs), deadline),
                            countdownData(type, mode, deadline),
                        ),
                        TimelineEntry(
                            TimeInterval(deadline, expiry),
                            countUpData(type, mode, deadline),
                        ),
                    ),
                )
            }

            else -> { // COUNTUP
                val gun = wholeSecond(race.wallMs)
                val expiry = Instant.ofEpochMilli(race.savedAtMs + RaceStore.MAX_AGE_MS)
                ComplicationDataTimeline(
                    armed,
                    listOf(
                        TimelineEntry(
                            TimeInterval(Instant.ofEpochMilli(race.savedAtMs), expiry),
                            countUpData(type, race.mode, gun),
                        ),
                    ),
                )
            }
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.RANGED_VALUE ->
            RangedValueComplicationData.Builder(
                value = 150f, min = 0f, max = 300f,
                contentDescription = plain("Regatta Timer, 2:30 remaining"),
            )
                .setValueType(RangedValueComplicationData.TYPE_RATING) // weather-style dot, not a drain arc
                .setText(plain("2:30"))
                .setMonochromaticImage(boat())
                .build()

        ComplicationType.SHORT_TEXT ->
            ShortTextComplicationData.Builder(plain("2:30"), plain("Regatta Timer"))
                .setMonochromaticImage(boat())
                .build()

        else -> null
    }

    // ---- State builders ------------------------------------------------------

    private fun armedData(type: ComplicationType, mode: Mode, instanceId: Int): ComplicationData {
        val text = plain("${mode.durationSeconds / 60}m") // which sequence a tap starts
        val desc = plain("Regatta Timer armed, tap to start ${mode.durationSeconds / 60} minute sequence")
        val tap = startTap(mode, instanceId)
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = mode.durationSeconds.toFloat(),
                    min = 0f,
                    max = mode.durationSeconds.toFloat(),
                    contentDescription = desc,
                )
                    .setValueType(RangedValueComplicationData.TYPE_RATING) // weather-style dot, not a drain arc
                    .setText(text)
                    .setMonochromaticImage(boat())
                    .setTapAction(tap)
                    .build()

            else ->
                ShortTextComplicationData.Builder(text, desc)
                    .setMonochromaticImage(boat())
                    .setTapAction(tap)
                    .build()
        }
    }

    private fun countdownData(type: ComplicationType, mode: Mode, deadline: Instant): ComplicationData {
        val ticking = TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.STOPWATCH,
            CountDownTimeReference(deadline),
        ).build()
        val desc = plain("Regatta Timer counting down")
        return when (type) {
            ComplicationType.RANGED_VALUE -> {
                // Seconds until the gun, evaluated ~1 Hz by the platform (no pushes),
                // clamped at 0 so the dot parks rather than going negative.
                val raw = DynamicInstant.platformTimeWithSecondsPrecision()
                    .durationUntil(DynamicInstant.withSecondsPrecision(deadline))
                    .toIntSeconds()
                    .asFloat()
                val remaining = DynamicFloat.onCondition(raw.lt(0f))
                    .use(DynamicFloat.constant(0f))
                    .elseUse(raw)
                RangedValueComplicationData.Builder(
                    dynamicValue = remaining,
                    fallbackValue = mode.durationSeconds.toFloat(),
                    min = 0f,
                    max = mode.durationSeconds.toFloat(),
                    contentDescription = desc,
                )
                    .setValueType(RangedValueComplicationData.TYPE_RATING) // weather-style dot, not a drain arc
                    .setText(ticking)
                    .setMonochromaticImage(boat())
                    .setTapAction(openTap())
                    .build()
            }

            else ->
                ShortTextComplicationData.Builder(ticking, desc)
                    .setMonochromaticImage(boat())
                    .setTapAction(openTap())
                    .build()
        }
    }

    private fun countUpData(type: ComplicationType, mode: Mode, gun: Instant): ComplicationData {
        val ticking = TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.STOPWATCH,
            CountUpTimeReference(gun),
        ).build()
        val desc = plain("Regatta Timer race in progress")
        return when (type) {
            ComplicationType.RANGED_VALUE -> {
                // Dot sweeps progress through a nominal race hour, parking at max for
                // marathon races — the ticking text stays the exact source of truth.
                val maxSeconds = EXPECTED_RACE_SECONDS.toFloat()
                val elapsed = DynamicInstant.withSecondsPrecision(gun)
                    .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
                    .toIntSeconds()
                    .asFloat()
                val progress = DynamicFloat.onCondition(elapsed.gt(maxSeconds))
                    .use(DynamicFloat.constant(maxSeconds))
                    .elseUse(elapsed)
                RangedValueComplicationData.Builder(
                    dynamicValue = progress,
                    fallbackValue = 0f,
                    min = 0f,
                    max = maxSeconds,
                    contentDescription = desc,
                )
                    .setValueType(RangedValueComplicationData.TYPE_RATING) // weather-style dot, not a drain arc
                    .setText(ticking)
                    .setMonochromaticImage(boat())
                    .setTapAction(openTap())
                    .build()
            }

            else ->
                ShortTextComplicationData.Builder(ticking, desc)
                    .setMonochromaticImage(boat())
                    .setTapAction(openTap())
                    .build()
        }
    }

    // ---- Helpers -------------------------------------------------------------

    private fun plain(s: String) = PlainComplicationText.Builder(s).build()

    /** Round to the nearest whole second (epoch) — keeps text and dot flips in phase. */
    private fun wholeSecond(wallMs: Long): Instant =
        Instant.ofEpochMilli(((wallMs + 500) / 1000) * 1000)

    private fun boat() = MonochromaticImage.Builder(
        Icon.createWithResource(this, R.drawable.ic_complication_sailboat)
    ).build()

    /**
     * Mode rides in the data URI: PendingIntent dedup ignores extras, so two
     * complications configured FIVE vs THREE would otherwise collide.
     */
    private fun startTap(mode: Mode, instanceId: Int): PendingIntent =
        PendingIntent.getActivity(
            this,
            instanceId,
            Intent(this, MainActivity::class.java).apply {
                data = Uri.parse("regattatimer://autostart/${mode.name}")
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_AUTO_START, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openTap(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        /** Nominal race length the count-up dot sweeps against (clamps at max beyond). */
        const val EXPECTED_RACE_SECONDS = 60 * 60L

        fun component(context: Context) =
            ComponentName(context, RegattaComplicationService::class.java)
    }
}
