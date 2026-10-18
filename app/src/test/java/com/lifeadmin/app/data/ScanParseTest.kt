package com.lifeadmin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScanParseTest {
    private val today = LocalDate.of(2026, 10, 18)

    @Test
    fun electricityBill() {
        val r = ScanParse.parse(
            """
            City Power Ltd
            Consumer No. 1234567890
            Bill Date: 05/10/2026
            Due Date: 25/10/2026
            Total Amount Due ₹ 2,345.50
            """.trimIndent(), today
        )
        assertEquals("City Power Ltd", r.title)
        assertEquals(LocalDate.of(2026, 10, 25), r.date)
        assertEquals(2345.50, r.amount!!, 0.001)
        assertEquals("INR", r.currency)
        assertEquals(Kind.PAYMENT, r.kind)
    }

    @Test
    fun insurancePolicyExpiry() {
        val r = ScanParse.parse(
            """
            ACME General Insurance
            Policy Start: 12 Mar 2026
            Policy Expiry: 11 Mar 2027
            Premium: $ 640.00
            """.trimIndent(), today
        )
        assertEquals(LocalDate.of(2027, 3, 11), r.date)
        assertEquals(Kind.RENEWAL, r.kind)
        assertEquals(640.0, r.amount!!, 0.001)
        assertEquals("USD", r.currency)
    }

    @Test
    fun keywordOnPreviousLineAndMonthNameFirst() {
        val r = ScanParse.parse("Passport\nDate of expiry\nJune 4, 2031", today)
        assertEquals(LocalDate.of(2031, 6, 4), r.date)
        assertEquals("Passport", r.title)
        assertEquals(Kind.RENEWAL, r.kind)
    }

    @Test
    fun cardValidThru() {
        val r = ScanParse.parse("BANK CARD\nVALID THRU 08/28", today)
        assertEquals(LocalDate.of(2028, 8, 31), r.date)
    }

    @Test
    fun usDatesAndNoMoney() {
        val r = ScanParse.parse("Gym renewal notice\nRenew by 11/02/2026\nMember ID 445566", today, dayFirst = false)
        assertEquals(LocalDate.of(2026, 11, 2), r.date)
        assertNull(r.amount)
    }

    @Test
    fun isoDateAndRupeeSuffix() {
        val r = ScanParse.parse("Phone bill\nPay by 2026-11-03\nAmount payable 799/-", today)
        assertEquals(LocalDate.of(2026, 11, 3), r.date)
        assertEquals(799.0, r.amount!!, 0.001)
        assertEquals("INR", r.currency)
    }
}
