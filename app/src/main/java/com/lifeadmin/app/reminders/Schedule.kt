package com.lifeadmin.app.reminders

import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.History
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.data.alertOffsetList
import com.lifeadmin.app.data.dueDate
import com.lifeadmin.app.data.isRepeating
import com.lifeadmin.app.data.repeatDayList
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * All reminder timing rules, free of Android code so they can be unit-tested.
 * "defaultMinute" is the time used for all-day items (Settings > default reminder time).
 */
object Schedule {

    /** Alerts missed by more than this (phone off, etc.) are skipped instead of firing late. */
    const val CATCH_UP_WINDOW_MS = 6 * 60 * 60 * 1000L

    fun occurrence(item: Item, defaultMinute: Int): LocalDateTime =
        item.dueDate().atStartOfDay().plusMinutes((item.dueMinute ?: defaultMinute).toLong())

    fun toMillis(t: LocalDateTime, zone: ZoneId): Long = t.atZone(zone).toInstant().toEpochMilli()

    /** Every alert for the current occurrence: the "before" alerts plus any repeat alerts after. */
    fun alertTimes(item: Item, defaultMinute: Int): List<LocalDateTime> {
        val occ = occurrence(item, defaultMinute)
        val before = item.alertOffsetList().map { occ.minusMinutes(it.toLong()) }
        val after = if (item.nagEveryMin > 0) {
            (1..item.nagCount).map { occ.plusMinutes(item.nagEveryMin.toLong() * it) }
        } else {
            emptyList()
        }
        return (before + after).distinct().sorted()
    }

    /** When the current occurrence is fully over (due time plus repeat alerts). */
    fun occurrenceEnd(item: Item, defaultMinute: Int): LocalDateTime {
        val extra = if (item.nagEveryMin > 0) item.nagEveryMin.toLong() * item.nagCount else 0L
        return occurrence(item, defaultMinute).plusMinutes(extra)
    }

    /**
     * The next moment an alert should go off (epoch millis), or null if none is left.
     * Can be in the past when an alert was missed; the scheduler decides what to do then.
     */
    fun nextAlertMillis(item: Item, defaultMinute: Int, zone: ZoneId): Long? {
        if (item.done) return null
        val candidates = alertTimes(item, defaultMinute)
            .map { toMillis(it, zone) }
            .filter { it > item.lastFiredAt }
            .toMutableList()
        item.snoozedUntil?.let { if (it > item.lastFiredAt) candidates += it }
        return candidates.minOrNull()
    }

    /**
     * The first occurrence of the repeat series strictly after both [after] and the
     * current occurrence, or null if the item doesn't repeat or the series has ended.
     */
    fun nextOccurrence(item: Item, after: LocalDateTime, defaultMinute: Int): LocalDateTime? {
        if (!item.isRepeating) return null
        if (item.repeatLimit != null && item.repeatLimit <= 0) return null
        val start = occurrence(item, defaultMinute)
        val base = if (after.isAfter(start)) after else start
        val n = item.repeatEvery.coerceAtLeast(1).toLong()

        val next: LocalDateTime? = when (item.cycle) {
            Cycle.NONE -> null
            Cycle.MINUTES, Cycle.HOURS -> {
                val step = if (item.cycle == Cycle.MINUTES) n else n * 60
                val k = ChronoUnit.MINUTES.between(start, base) / step + 1
                start.plusMinutes(k * step)
            }
            Cycle.DAILY -> {
                val k = ChronoUnit.DAYS.between(start.toLocalDate(), base.toLocalDate()) / n
                var t = start.plusDays(k * n)
                while (!t.isAfter(base)) t = t.plusDays(n)
                t
            }
            Cycle.WEEKLY -> {
                val days = item.repeatDayList()
                if (days.isEmpty()) {
                    val k = ChronoUnit.WEEKS.between(start.toLocalDate(), base.toLocalDate()) / n
                    var t = start.plusWeeks(k * n)
                    while (!t.isAfter(base)) t = t.plusWeeks(n)
                    t
                } else {
                    nextOnWeekdays(start, base, days, n)
                }
            }
            Cycle.MONTHLY, Cycle.QUARTERLY, Cycle.YEARLY -> {
                var date = start.toLocalDate()
                var guard = 0
                while (!date.atTime(start.toLocalTime()).isAfter(base) && guard < 10_000) {
                    date = item.cycle.next(date, item.repeatEvery)
                    guard++
                }
                date.atTime(start.toLocalTime())
            }
        }
        val until = item.repeatUntilEpochDay
        if (next != null && until != null && next.toLocalDate().toEpochDay() > until) return null
        return next
    }

    private fun nextOnWeekdays(
        start: LocalDateTime,
        base: LocalDateTime,
        days: Set<Int>,
        everyWeeks: Long,
    ): LocalDateTime? {
        val anchorWeek = start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var d = base.toLocalDate()
        repeat((7 * everyWeeks * 2 + 7).toInt()) {
            val week = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeksApart = ChronoUnit.WEEKS.between(anchorWeek, week)
            if (d.dayOfWeek.value in days && weeksApart % everyWeeks == 0L) {
                val t = d.atTime(start.toLocalTime())
                if (t.isAfter(base)) return t
            }
            d = d.plusDays(1)
        }
        return null
    }

    /** The item moved to occurrence [to]. */
    fun movedTo(item: Item, to: LocalDateTime): Item = item.copy(
        dueEpochDay = to.toLocalDate().toEpochDay(),
        dueMinute = if (item.dueMinute == null && item.cycle != Cycle.MINUTES && item.cycle != Cycle.HOURS) {
            null
        } else {
            to.hour * 60 + to.minute
        },
        repeatLimit = item.repeatLimit?.minus(1),
        snoozedUntil = null,
        lastNotifiedStage = Item.NOT_NOTIFIED,
    )

    /** Auto-repeating items: once the current occurrence is over, move on to the next one. */
    fun rollForward(item: Item, now: LocalDateTime, defaultMinute: Int, zone: ZoneId): Item {
        if (item.done || !item.isRepeating || !item.autoRenew) return item
        if (occurrenceEnd(item, defaultMinute).isAfter(now)) return item
        val snooze = item.snoozedUntil
        if (snooze != null && snooze > toMillis(now, zone)) return item
        val next = nextOccurrence(item, now, defaultMinute) ?: return item
        return movedTo(item, next)
    }

    /** Whether [minuteOfDay] falls inside quiet hours [start, end), which may wrap past midnight. */
    fun inQuietHours(minuteOfDay: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minuteOfDay in start until end
        else -> minuteOfDay >= start || minuteOfDay < end
    }

    /** The moment quiet hours end, after [now]. */
    fun quietEnd(now: LocalDateTime, end: Int): LocalDateTime {
        val todayEnd = now.toLocalDate().atStartOfDay().plusMinutes(end.toLong())
        return if (todayEnd.isAfter(now)) todayEnd else todayEnd.plusDays(1)
    }

    data class Completion(val updated: Item, val history: History, val next: LocalDateTime?)

    /**
     * "Done" / "Paid" / "Renewed": records it, then moves a repeating item to its next
     * occurrence, or marks a one-off item as done.
     */
    fun complete(item: Item, nowMillis: Long, defaultMinute: Int, actualAmount: Double? = null): Completion {
        val history = History(
            itemId = item.id,
            itemTitle = item.title,
            category = item.category,
            dueEpochDay = item.dueEpochDay,
            paidAt = nowMillis,
            amount = if (item.kind == Kind.REMINDER) null else (actualAmount ?: item.amount),
            currency = item.currency,
            kind = item.kind.name,
        )
        val next = nextOccurrence(item, occurrence(item, defaultMinute), defaultMinute)
        val updated = if (next != null) {
            movedTo(item, next).copy(lastFiredAt = nowMillis)
        } else {
            item.copy(done = true, doneAt = nowMillis, snoozedUntil = null)
        }
        return Completion(updated, history, next)
    }

    /**
     * Consecutive days, ending today or yesterday, on which this was completed at least once.
     * Used for habit-like reminders ("12-day streak").
     */
    fun streak(completionDays: Collection<java.time.LocalDate>, today: java.time.LocalDate): Int {
        val days = completionDays.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var count = 0
        while (d in days) {
            count++
            d = d.minusDays(1)
        }
        return count
    }
}
