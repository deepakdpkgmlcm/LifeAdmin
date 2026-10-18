package com.lifeadmin.app.util

import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.repeatDayList
import com.lifeadmin.app.reminders.Schedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Builds the calendar repeat rule (RFC 5545 RRULE) so an item can be added to Google Calendar. */
object CalendarExport {
    private val DAY_CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    private val UNTIL_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun rrule(item: Item): String? {
        if (item.cycle == Cycle.NONE) return null
        val n = item.repeatEvery.coerceAtLeast(1)
        val parts = mutableListOf<String>()
        var interval = n
        when (item.cycle) {
            Cycle.NONE -> return null
            Cycle.MINUTES -> parts += "FREQ=MINUTELY"
            Cycle.HOURS -> parts += "FREQ=HOURLY"
            Cycle.DAILY -> if (item.repeatRule == Schedule.RULE_SKIP_WEEKENDS) {
                parts += "FREQ=WEEKLY"
                parts += "BYDAY=MO,TU,WE,TH,FR"
                interval = 1
            } else {
                parts += "FREQ=DAILY"
            }
            Cycle.WEEKLY -> {
                parts += "FREQ=WEEKLY"
                val days = item.repeatDayList().sorted()
                if (days.isNotEmpty()) parts += "BYDAY=" + days.joinToString(",") { DAY_CODES[it - 1] }
            }
            Cycle.MONTHLY, Cycle.QUARTERLY -> {
                parts += "FREQ=MONTHLY"
                if (item.cycle == Cycle.QUARTERLY) interval = n * 3
                val rule = item.repeatRule
                when {
                    rule == Schedule.RULE_LAST_DAY -> parts += "BYMONTHDAY=-1"
                    rule == Schedule.RULE_LAST_WORKDAY -> {
                        parts += "BYDAY=MO,TU,WE,TH,FR"
                        parts += "BYSETPOS=-1"
                    }
                    rule.startsWith("NTH:") -> {
                        val p = rule.split(":")
                        val week = p.getOrNull(1)?.toIntOrNull() ?: 1
                        val dow = (p.getOrNull(2)?.toIntOrNull() ?: 1).coerceIn(1, 7)
                        parts += "BYDAY=" + (if (week >= 5) "-1" else "$week") + DAY_CODES[dow - 1]
                    }
                }
            }
            Cycle.YEARLY -> parts += "FREQ=YEARLY"
        }
        if (interval > 1) parts += "INTERVAL=$interval"
        val limit = item.repeatLimit
        val until = item.repeatUntilEpochDay
        if (limit != null) parts += "COUNT=${limit + 1}"
        else if (until != null) parts += "UNTIL=" + LocalDate.ofEpochDay(until).format(UNTIL_FMT)
        return parts.joinToString(";")
    }
}
