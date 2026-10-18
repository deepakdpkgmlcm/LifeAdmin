package com.lifeadmin.app.util

import java.util.Locale

/** Converts other currencies into the main one, using rates the person enters (works offline). */
object Money {
    /** "USD=83.2,EUR=90.1" -> {USD: 83.2, EUR: 90.1}; each rate means 1 unit = rate main-currency units. */
    fun parseRates(raw: String): Map<String, Double> =
        raw.split(",").mapNotNull { pair ->
            val bits = pair.split("=")
            val code = bits.getOrNull(0)?.trim()?.uppercase(Locale.ROOT).orEmpty()
            val rate = bits.getOrNull(1)?.trim()?.toDoubleOrNull()
            if (code.length == 3 && rate != null && rate > 0) code to rate else null
        }.toMap()

    fun ratesToString(rates: Map<String, Double>): String =
        rates.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }

    /** [amount] in [from] expressed in [to], or null when no rate is known. Blank [from] counts as [to]. */
    fun convert(amount: Double, from: String, to: String, rates: Map<String, Double>): Double? {
        if (from.isBlank() || from.equals(to, ignoreCase = true)) return amount
        return rates[from.uppercase(Locale.ROOT)]?.let { amount * it }
    }
}
