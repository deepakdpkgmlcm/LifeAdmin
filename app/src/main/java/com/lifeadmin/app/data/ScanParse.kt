package com.lifeadmin.app.data

import java.time.LocalDate
import java.time.Month
import java.util.Locale

/**
 * Reads the text recognised from a photo of a bill, policy or card and picks out
 * the date that matters (due / expiry), the amount and a likely title. Works offline.
 */
object ScanParse {
    data class Result(
        val title: String,
        val date: LocalDate?,
        val amount: Double?,
        val currency: String?,
        val kind: Kind,
    )

    private val EXPIRY_WORDS = listOf("expiry", "expires", "expiration", "exp.", "exp ", "valid until", "valid till",
        "valid upto", "valid up to", "valid thru", "renewal", "renew by", "date of expiry", "policy end", "end date")
    private val DUE_WORDS = listOf("due date", "due", "pay by", "payment date", "last date", "pay before")
    private val TOTAL_WORDS = listOf("total due", "amount due", "amount payable", "total amount", "balance due",
        "net payable", "premium", "total", "amount", "payable", "balance")

    private val MONTHS: Map<String, Month> = buildMap {
        Month.entries.forEach { m ->
            val n = m.name.lowercase(Locale.ROOT)
            put(n, m)
            put(n.take(3), m)
        }
        put("sept", Month.SEPTEMBER)
    }

    private val NUMERIC = Regex("""\b(\d{1,4})[/.\-](\d{1,2})[/.\-](\d{2,4})\b""")
    private val DAY_MONTH_NAME = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[\s\-./]*([a-z]{3,9})[\s\-.,/]*(\d{4}|\d{2})\b""")
    private val MONTH_NAME_DAY = Regex("""\b([a-z]{3,9})[\s.]*(\d{1,2})(?:st|nd|rd|th)?,?\s*(\d{4})\b""")
    private val MONTH_YEAR = Regex("""\b(\d{1,2})\s*/\s*(\d{2}|\d{4})\b""")

    private val CURRENCY_SYMBOLS = linkedMapOf("₹" to "INR", "rs." to "INR", "rs" to "INR", "inr" to "INR",
        "$" to "USD", "usd" to "USD", "€" to "EUR", "eur" to "EUR", "£" to "GBP", "gbp" to "GBP",
        "aed" to "AED", "sgd" to "SGD", "aud" to "AUD", "cad" to "CAD", "¥" to "JPY")
    private val AMOUNT = Regex("""(₹|rs\.?|inr|\$|usd|€|eur|£|gbp|aed|sgd|aud|cad|¥)?\s*(\d{1,3}(?:[,\s]\d{2,3})+|\d+)(\.\d{1,2})?\s*(₹|inr|usd|eur|gbp|aed|sgd|aud|cad|/-)?""")

    private fun year(raw: String): Int = raw.toInt().let { if (raw.length == 2) 2000 + it else it }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        if (y in 1990..2100 && m in 1..12 && d in 1..31) runCatching { LocalDate.of(y, m, d) }.getOrNull() else null

    /** Every date written on one line. */
    fun datesIn(line: String, dayFirst: Boolean): List<LocalDate> {
        val l = line.lowercase(Locale.ROOT)
        val out = mutableListOf<LocalDate>()
        NUMERIC.findAll(l).forEach { m ->
            val (a, b, c) = m.destructured
            val date = if (a.length == 4) {
                safeDate(a.toInt(), b.toInt(), c.toInt())
            } else {
                val x = a.toInt()
                val y = b.toInt()
                val yr = year(c)
                when {
                    x > 12 -> safeDate(yr, y, x)
                    y > 12 -> safeDate(yr, x, y)
                    dayFirst -> safeDate(yr, y, x)
                    else -> safeDate(yr, x, y)
                }
            }
            date?.let { out += it }
        }
        DAY_MONTH_NAME.findAll(l).forEach { m ->
            val (d, mon, y) = m.destructured
            val month = MONTHS[mon] ?: MONTHS[mon.take(3)].takeIf { mon.length <= 4 }
            month?.let { safeDate(year(y), it.value, d.toInt())?.let { date -> out += date } }
        }
        MONTH_NAME_DAY.findAll(l).forEach { m ->
            val (mon, d, y) = m.destructured
            MONTHS[mon]?.let { safeDate(year(y), it.value, d.toInt())?.let { date -> out += date } }
        }
        // Cards often say only "VALID THRU 08/28": the end of that month.
        if (out.isEmpty() && EXPIRY_WORDS.any { it.trim() in l }) {
            MONTH_YEAR.findAll(l).forEach { m ->
                val (mo, y) = m.destructured
                if (mo.toInt() in 1..12) safeDate(year(y), mo.toInt(), 1)?.let { out += it.withDayOfMonth(it.lengthOfMonth()) }
            }
        }
        return out.distinct()
    }

    private data class Money(val amount: Double, val currency: String?)

    private fun amountsIn(line: String): List<Money> {
        val l = line.lowercase(Locale.ROOT)
        if (NUMERIC.containsMatchIn(l) && !l.any { it in "₹$€£¥" }) {
            // A line that is mostly a date: ignore its numbers as amounts.
            if (NUMERIC.replace(l, "").none { it.isDigit() }) return emptyList()
        }
        return AMOUNT.findAll(l).mapNotNull { m ->
            val pre = m.groupValues[1]
            val post = m.groupValues[4]
            val whole = m.groupValues[2].replace(",", "").replace(" ", "")
            val value = (whole + m.groupValues[3]).toDoubleOrNull() ?: return@mapNotNull null
            val code = CURRENCY_SYMBOLS[pre.trim()] ?: CURRENCY_SYMBOLS[post.trim()]
                ?: if (post == "/-") "INR" else null
            val hasDecimals = m.groupValues[3].isNotEmpty()
            // Plain whole numbers without a currency are usually account or phone numbers.
            if (code == null && !hasDecimals) null
            else if (value <= 0 || value > 10_000_000) null
            else Money(value, code)
        }.toList()
    }

    fun parse(text: String, today: LocalDate, dayFirst: Boolean = true): Result {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val lower = lines.map { it.lowercase(Locale.ROOT) }

        fun keywordLine(words: List<String>, i: Int): Boolean =
            words.any { w -> lower[i].contains(w) } || (i > 0 && words.any { w -> lower[i - 1].endsWith(w.trim()) || lower[i - 1].endsWith(w.trim() + ":") })

        var expiry: LocalDate? = null
        var due: LocalDate? = null
        val allDates = mutableListOf<LocalDate>()
        lines.forEachIndexed { i, line ->
            val dates = datesIn(line, dayFirst)
            allDates += dates
            if (dates.isEmpty()) return@forEachIndexed
            val latest = dates.max()
            if (expiry == null && keywordLine(EXPIRY_WORDS, i)) expiry = latest
            if (due == null && keywordLine(DUE_WORDS, i)) due = latest
        }

        var keyed: Money? = null
        val allMoney = mutableListOf<Money>()
        lines.forEachIndexed { i, line ->
            val found = amountsIn(line)
            allMoney += found
            if (keyed == null && found.isNotEmpty() && keywordLine(TOTAL_WORDS, i)) keyed = found.maxBy { it.amount }
        }
        val money = keyed ?: allMoney.filter { it.currency != null }.maxByOrNull { it.amount }

        val kind = when {
            expiry != null -> Kind.RENEWAL
            due != null || money != null -> Kind.PAYMENT
            else -> Kind.RENEWAL
        }
        val date = expiry ?: due ?: allDates.filter { !it.isBefore(today) }.maxOrNull()

        val title = lines.firstOrNull { line ->
            line.count { it.isLetter() } >= 3 &&
                line.count { it.isDigit() } <= line.length / 3 &&
                EXPIRY_WORDS.none { line.lowercase(Locale.ROOT).contains(it) }
        }?.take(40)?.trim().orEmpty()

        return Result(title, date, money?.amount, money?.currency, kind)
    }
}
