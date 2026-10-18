package com.lifeadmin.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/**
 * Turns a typed or spoken sentence into a reminder, offline:
 * "Pay rent 5th every month 10am", "Call mom Sunday 7pm", "Drink water every 2 hours",
 * "Passport expires 12 Mar 2027", "Netflix ₹649 monthly on 3rd", "Standup weekdays 10:30 alarm".
 */
object QuickParse {

    data class Result(
        val title: String,
        val kind: Kind,
        val category: Category,
        val date: LocalDate,
        /** Minutes after midnight, or null for all day. */
        val minute: Int?,
        val cycle: Cycle,
        val every: Int,
        /** ISO weekdays for weekly repeats, comma separated. */
        val days: String,
        val amount: Double?,
        val currency: String?,
        val alarm: Boolean,
    )

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private const val MONTH_RX = "(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?"
    private const val DAY_RX = "(monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun)"

    private fun weekdayOf(word: String): Int = when (word.take(3)) {
        "mon" -> 1
        "tue" -> 2
        "wed" -> 3
        "thu" -> 4
        "fri" -> 5
        "sat" -> 6
        else -> 7
    }

    private fun unitCycle(unit: String): Cycle = when {
        unit.startsWith("min") -> Cycle.MINUTES
        unit.startsWith("h") -> Cycle.HOURS
        unit.startsWith("d") -> Cycle.DAILY
        unit.startsWith("w") -> Cycle.WEEKLY
        unit.startsWith("mo") -> Cycle.MONTHLY
        else -> Cycle.YEARLY
    }

    private class Text(input: String) {
        val original = StringBuilder(" $input ")
        val lower = StringBuilder(" ${input.lowercase()} ")

        /** Finds [rx] and blanks the match out of both copies (keeps positions aligned). */
        fun take(rx: Regex): MatchResult? {
            val m = rx.find(lower.toString()) ?: return null
            for (i in m.range) {
                lower.setCharAt(i, ' ')
                original.setCharAt(i, ' ')
            }
            return m
        }
    }

    fun parse(input: String, now: LocalDateTime): Result {
        val full = input.lowercase()
        val t = Text(input)
        val today = now.toLocalDate()

        val alarm = t.take(Regex("\\b(as an |with an? )?alarm\\b")) != null

        // ---- amount ----
        var amount: Double? = null
        var currency: String? = null
        t.take(Regex("(₹|\\$|€|\\brs\\.?|\\binr|\\busd|\\beur)\\s?(\\d[\\d,]*(?:\\.\\d+)?)"))?.let { m ->
            amount = m.groupValues[2].replace(",", "").toDoubleOrNull()
            currency = currencyOf(m.groupValues[1])
        } ?: t.take(Regex("\\b(\\d[\\d,]*(?:\\.\\d+)?)\\s?(rs|rupees|inr|usd|dollars|eur|euros)\\b"))?.let { m ->
            amount = m.groupValues[1].replace(",", "").toDoubleOrNull()
            currency = currencyOf(m.groupValues[2])
        }

        // ---- repeat ----
        var cycle = Cycle.NONE
        var every = 1
        var days = ""
        var dayOfMonth: Int? = null
        t.take(Regex("\\bevery\\s+(\\d+)\\s+(minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)\\b"))?.let { m ->
            every = m.groupValues[1].toInt().coerceAtLeast(1)
            cycle = unitCycle(m.groupValues[2])
        }
        if (cycle == Cycle.NONE) {
            t.take(Regex("\\b(every\\s+)?weekdays?\\b"))?.let { cycle = Cycle.WEEKLY; days = "1,2,3,4,5" }
        }
        if (cycle == Cycle.NONE) {
            t.take(Regex("\\b(every\\s+)?weekends?\\b"))?.let { cycle = Cycle.WEEKLY; days = "6,7" }
        }
        if (cycle == Cycle.NONE) {
            t.take(Regex("\\bevery\\s+($DAY_RX(?:\\s*(?:,|and|&)\\s*$DAY_RX)*)\\b"))?.let { m ->
                cycle = Cycle.WEEKLY
                days = Regex(DAY_RX).findAll(m.groupValues[1]).map { weekdayOf(it.value) }
                    .toSortedSet().joinToString(",")
            }
        }
        if (cycle == Cycle.NONE) {
            t.take(Regex("\\bevery\\s+(\\d{1,2})(st|nd|rd|th)\\b"))?.let { m ->
                cycle = Cycle.MONTHLY
                dayOfMonth = m.groupValues[1].toInt()
            }
        }
        if (cycle == Cycle.NONE) {
            t.take(Regex("\\b(every\\s+(minute|hour|day|week|month|year)|hourly|daily|weekly|monthly|yearly|annually)\\b"))?.let { m ->
                val w = m.value.removePrefix("every").trim()
                cycle = when {
                    w.startsWith("minute") -> Cycle.MINUTES
                    w.startsWith("hour") -> Cycle.HOURS
                    w.startsWith("da") -> Cycle.DAILY
                    w.startsWith("week") -> Cycle.WEEKLY
                    w.startsWith("month") -> Cycle.MONTHLY
                    else -> Cycle.YEARLY
                }
            }
        }

        // ---- relative times: "in 30 minutes", "in 3 days" ----
        var date: LocalDate? = null
        var minute: Int? = null
        t.take(Regex("\\bin\\s+(\\d+)\\s+(minutes?|mins?|hours?|hrs?|days?|weeks?|months?)\\b"))?.let { m ->
            val n = m.groupValues[1].toLong()
            when (unitCycle(m.groupValues[2])) {
                Cycle.MINUTES -> now.plusMinutes(n).let { date = it.toLocalDate(); minute = it.hour * 60 + it.minute }
                Cycle.HOURS -> now.plusHours(n).let { date = it.toLocalDate(); minute = it.hour * 60 + it.minute }
                Cycle.DAILY -> date = today.plusDays(n)
                Cycle.WEEKLY -> date = today.plusWeeks(n)
                else -> date = today.plusMonths(n)
            }
        }

        // ---- explicit dates ----
        if (date == null) {
            if (t.take(Regex("\\bday after tomorrow\\b")) != null) date = today.plusDays(2)
            else if (t.take(Regex("\\btomorrow\\b")) != null) date = today.plusDays(1)
            else if (t.take(Regex("\\btoday\\b")) != null) date = today
        }
        if (date == null) {
            t.take(Regex("\\b(\\d{1,2})[/\\-.](\\d{1,2})(?:[/\\-.](\\d{2,4}))?\\b"))?.let { m ->
                date = safeDate(m.groupValues[3], m.groupValues[2].toInt(), m.groupValues[1].toInt(), today)
            }
        }
        if (date == null) {
            t.take(Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?$MONTH_RX(?:,?\\s+(\\d{4}))?\\b"))?.let { m ->
                date = safeDate(m.groupValues[3], monthOf(m.groupValues[2]), m.groupValues[1].toInt(), today)
            }
        }
        if (date == null) {
            t.take(Regex("\\b$MONTH_RX\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b"))?.let { m ->
                date = safeDate(m.groupValues[3], monthOf(m.groupValues[1]), m.groupValues[2].toInt(), today)
            }
        }
        if (date == null && dayOfMonth == null) {
            t.take(Regex("\\b(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(st|nd|rd|th)\\b"))?.let { m ->
                dayOfMonth = m.groupValues[1].toInt()
            }
        }
        var weekday: Int? = null
        if (date == null && days.isEmpty()) {
            t.take(Regex("\\b(?:next\\s+|on\\s+|this\\s+)?$DAY_RX\\b"))?.let { m ->
                weekday = weekdayOf(m.groupValues[1])
            }
        }
        if (date == null) {
            t.take(Regex("\\bnext\\s+(week|month|year)\\b"))?.let { m ->
                date = when (m.groupValues[1]) {
                    "week" -> today.plusWeeks(1)
                    "month" -> today.plusMonths(1)
                    else -> today.plusYears(1)
                }
            }
        }

        // ---- time of day ----
        if (minute == null) {
            t.take(Regex("\\b(?:at\\s+)?(\\d{1,2})[:.](\\d{2})\\s*(am|pm|a\\.m\\.|p\\.m\\.)?(?=\\s|$)"))?.let { m ->
                minute = toMinute(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3])
            }
        }
        if (minute == null) {
            t.take(Regex("\\b(?:at\\s+)?(\\d{1,2})\\s*(am|pm|a\\.m\\.|p\\.m\\.)(?=\\s|$)"))?.let { m ->
                minute = toMinute(m.groupValues[1].toInt(), 0, m.groupValues[2])
            }
        }
        if (minute == null) {
            t.take(Regex("\\bat\\s+(\\d{1,2})\\b"))?.let { m -> minute = toMinute(m.groupValues[1].toInt(), 0, "") }
        }
        if (minute == null) {
            t.take(Regex("\\b(in the |this )?(noon|midnight|morning|afternoon|evening|tonight|night)\\b"))?.let { m ->
                minute = when (m.groupValues[2]) {
                    "noon" -> 12 * 60
                    "midnight" -> 0
                    "morning" -> 9 * 60
                    "afternoon" -> 14 * 60
                    "evening" -> 18 * 60
                    else -> 21 * 60
                }
            }
        }

        // ---- resolve the first date ----
        val subDaily = cycle == Cycle.MINUTES || cycle == Cycle.HOURS
        if (subDaily && minute == null) {
            val nextHour = now.plusHours(1).withMinute(0)
            minute = nextHour.hour * 60
            if (date == null) date = nextHour.toLocalDate()
        }
        fun passed(d: LocalDate): Boolean =
            d == today && minute != null && minute!! <= now.hour * 60 + now.minute

        if (date == null && dayOfMonth != null) {
            val dom = dayOfMonth!!
            var d = today.withDayOfMonth(dom.coerceAtMost(today.lengthOfMonth()))
            if (d.isBefore(today) || passed(d)) {
                val nm = today.plusMonths(1)
                d = nm.withDayOfMonth(dom.coerceAtMost(nm.lengthOfMonth()))
            }
            date = d
            if (cycle == Cycle.NONE && full.contains("month")) cycle = Cycle.MONTHLY
        }
        if (date == null && weekday != null) {
            var d = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday!!)))
            if (passed(d)) d = d.plusWeeks(1)
            date = d
        }
        if (date == null && days.isNotEmpty()) {
            val set = days.split(",").map { it.toInt() }.toSet()
            var d = today
            repeat(8) { if (d.dayOfWeek.value !in set || passed(d)) d = d.plusDays(1) }
            date = d
        }
        if (date == null) {
            date = if (passed(today)) today.plusDays(1) else today
        }

        // ---- kind, category, title ----
        val kind = when {
            Regex("\\b(expire[sd]?|expiry|expiring|renew(al|s)?|passport|licen[cs]e|visa|warranty|puc|registration)\\b").containsMatchIn(full) -> Kind.RENEWAL
            amount != null || Regex("\\b(pay|paid|bill|rent|emi|subscription|recharge|premium|fee|fees|invoice|due)\\b").containsMatchIn(full) -> Kind.PAYMENT
            else -> Kind.REMINDER
        }
        val category = categoryOf(full, kind)
        val title = cleanTitle(t.original.toString())

        return Result(
            title = title.ifBlank { if (kind == Kind.PAYMENT) "Payment" else "Reminder" },
            kind = kind,
            category = category,
            date = date!!,
            minute = minute,
            cycle = cycle,
            every = every,
            days = days,
            amount = amount,
            currency = currency,
            alarm = alarm,
        )
    }

    private fun currencyOf(token: String): String = when (token.trim().lowercase().trimEnd('.')) {
        "₹", "rs", "inr", "rupees" -> "INR"
        "$", "usd", "dollars" -> "USD"
        "€", "eur", "euros" -> "EUR"
        else -> "INR"
    }

    private fun monthOf(word: String): Int = MONTHS.indexOf(word.take(3)) + 1

    /** A date this year (or the given year); if it has passed, next year. Invalid dates fall back to null. */
    private fun safeDate(yearText: String, month: Int, day: Int, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val year = when {
            yearText.isEmpty() -> today.year
            yearText.length == 2 -> 2000 + yearText.toInt()
            else -> yearText.toInt()
        }
        return try {
            val d = LocalDate.of(year, month, day)
            if (yearText.isEmpty() && d.isBefore(today)) d.plusYears(1) else d
        } catch (e: Exception) {
            null
        }
    }

    private fun toMinute(hour: Int, min: Int, ampm: String): Int? {
        if (hour > 23 || min > 59) return null
        val a = ampm.replace(".", "")
        val h = when {
            a == "pm" && hour < 12 -> hour + 12
            a == "am" && hour == 12 -> 0
            a.isEmpty() && hour in 1..6 -> hour + 12 // "at 5" usually means 5 pm
            else -> hour
        }
        return h * 60 + min
    }

    private fun categoryOf(text: String, kind: Kind): Category {
        fun has(vararg words: String) = words.any { Regex("\\b$it").containsMatchIn(text) }
        return when {
            has("netflix", "spotify", "prime", "youtube", "subscription", "chatgpt", "icloud", "google one", "gym", "membership") -> Category.SUBSCRIPTION
            has("insurance", "premium", "policy") -> Category.INSURANCE
            has("passport", "licen", "visa", "id card", "aadhaar", "pan card", "permit") -> Category.DOCUMENT
            has("car", "bike", "vehicle", "puc", "pollution", "registration", "service") && kind != Kind.REMINDER -> Category.VEHICLE
            has("warranty") -> Category.WARRANTY
            has("rent", "electricity", "bill", "emi", "loan", "recharge", "credit card", "water bill", "gas", "internet", "fee") -> Category.BILL
            kind == Kind.PAYMENT -> Category.BILL
            kind == Kind.RENEWAL -> Category.DOCUMENT
            has("meeting", "standup", "stand-up", "office", "client", "report", "timesheet", "work", "email") -> Category.WORK
            has("medicine", "pill", "tablet", "doctor", "water", "workout", "exercise", "gym", "walk", "stretch", "vitamin") -> Category.HEALTH
            has("plants", "trash", "garbage", "clean", "laundry", "filter", "groceries") -> Category.HOME
            has("mom", "dad", "birthday", "anniversary", "call", "friend") -> Category.PERSONAL
            else -> Category.TASK
        }
    }

    private fun cleanTitle(raw: String): String {
        var s = raw.replace(Regex("\\s+"), " ").trim()
        s = s.replace(Regex("^(please\\s+)?(remind me (to|about)?|reminder( to)?|don'?t forget( to)?|remember to)\\s*", RegexOption.IGNORE_CASE), "")
        val filler = Regex("^(on|at|by|the|to|in|of|every|and|for)\\b\\s*|\\s*\\b(on|at|by|the|to|in|of|every|and|for|due|expires?|expiry|renews?|is|starting|from)$", RegexOption.IGNORE_CASE)
        var prev: String
        do {
            prev = s
            s = s.replace(filler, "").trim().trim(',', '-', '.', ':')
        } while (s != prev)
        return s.replaceFirstChar { it.uppercaseChar() }
    }
}
