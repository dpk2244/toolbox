package com.pushpendratech.toolbox

import com.pushpendratech.toolbox.core.Calculators
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalculatorsTest {
    private fun eq(expected: Double, actual: Double) = assertEquals(expected, actual, 0.011)

    @Test fun percentage() {
        eq(50.0, Calculators.percentOf(25.0, 200.0))
        eq(25.0, Calculators.whatPercent(50.0, 200.0))
        eq(25.0, Calculators.percentChange(80.0, 100.0))
        eq(-20.0, Calculators.percentChange(100.0, 80.0))
        eq(22.222, Calculators.percentDifference(80.0, 100.0))
    }
    @Test fun gstAddAndRemoveRoundTrip() {
        val add = Calculators.gstAdd(1000.0, 18.0); eq(180.0, add.gstAmount); eq(1180.0, add.total); eq(90.0, add.cgst)
        val rem = Calculators.gstRemove(1180.0, 18.0); eq(1000.0, rem.taxable); eq(180.0, rem.gstAmount)
        val five = Calculators.gstAdd(500.0, 5.0); eq(25.0, five.gstAmount)
    }
    @Test fun emiKnownCase() {
        // ₹1,00,000 at 12% for 12 months -> EMI ≈ ₹8,884.88 (standard amortization tables)
        val r = Calculators.emi(100000.0, 12.0, 12); eq(8884.88, r.emi); eq(6618.55, r.totalInterest)
        val zero = Calculators.emi(12000.0, 0.0, 12); eq(1000.0, zero.emi); eq(0.0, zero.totalInterest)
    }
    @Test fun discountSuccessive() {
        val r = Calculators.discount(1000.0, listOf(20.0, 10.0)) // 1000 -> 800 -> 720
        eq(720.0, r.finalPrice); eq(280.0, r.discountAmount); eq(28.0, r.effectivePercent)
    }
    @Test fun ageLeapYear() {
        val r = Calculators.age(LocalDate.of(2000, 2, 29), LocalDate.of(2024, 2, 29))
        assertEquals(24, r.years); assertEquals(0, r.months); assertEquals(0, r.days); assertEquals(8766L, r.totalDays)
        val r2 = Calculators.age(LocalDate.of(1990, 6, 15), LocalDate.of(2026, 10, 22))
        assertEquals(36, r2.years); assertEquals(4, r2.months); assertEquals(7, r2.days)
    }
    @Test fun units() {
        fun conv(v: Double, f: String, t: String): Double {
            val from = Calculators.units.first { it.symbol == f }; val to = Calculators.units.first { it.symbol == t }
            return Calculators.convert(v, from, to)
        }
        eq(1000.0, conv(1.0, "km", "m")); eq(2.54, conv(1.0, "in", "cm")); eq(0.45359237, conv(1.0, "lb", "kg"))
        eq(32.0, conv(0.0, "°C", "°F")); eq(273.15, conv(0.0, "°C", "K")); eq(100.0, conv(212.0, "°F", "°C"))
        eq(1024.0, conv(1.0, "KiB", "B")); eq(1000.0, conv(1.0, "KB", "B")); eq(4046.8564224, conv(1.0, "ac", "m²"))
    }
    @Test(expected = IllegalArgumentException::class) fun unitsRejectCrossFamily() {
        Calculators.convert(1.0, Calculators.units.first { it.symbol == "m" }, Calculators.units.first { it.symbol == "kg" })
    }
    @Test fun videoEstimator() {
        // 60s at 2000+128 kbps = 2128*1000*60/8 = 15,960,000 bytes
        assertEquals(15_960_000L, Calculators.estimateVideoBytes(60.0, 2000.0, 128.0))
    }
}
