package com.pushpendratech.toolbox.core

import java.time.LocalDate
import java.time.Period
import kotlin.math.abs
import kotlin.math.pow

/** Pure, Android-free calculator logic — fully unit-tested on the JVM. All functions validate inputs and throw IllegalArgumentException with a user-readable message on bad input. */
object Calculators {

    private fun requirePositive(v: Double, name: String) { require(v.isFinite() && v >= 0) { "$name must be a valid number ≥ 0" } }

    // ---- Percentage ----
    /** X% of Y */
    fun percentOf(percent: Double, of: Double): Double { requirePositive(of, "Value"); return of * percent / 100.0 }
    /** What % is part of whole? */
    fun whatPercent(part: Double, whole: Double): Double { require(whole != 0.0) { "Whole cannot be zero" }; return part * 100.0 / whole }
    /** % change from old to new (signed). */
    fun percentChange(oldV: Double, newV: Double): Double { require(oldV != 0.0) { "Original value cannot be zero" }; return (newV - oldV) * 100.0 / oldV }
    /** % difference relative to the average (always ≥ 0). */
    fun percentDifference(a: Double, b: Double): Double { val avg = (abs(a) + abs(b)) / 2.0; require(avg != 0.0) { "Both values are zero" }; return abs(a - b) * 100.0 / avg }

    // ---- GST (India) ----
    data class GstResult(val taxable: Double, val gstAmount: Double, val total: Double, val cgst: Double, val sgst: Double)
    /** price is EXCLUSIVE of GST -> adds GST. CGST/SGST = half each (intra-state split). */
    fun gstAdd(priceExcl: Double, ratePercent: Double): GstResult {
        requirePositive(priceExcl, "Price"); require(ratePercent in 0.0..100.0) { "GST rate must be 0–100%" }
        val gst = priceExcl * ratePercent / 100.0
        return GstResult(priceExcl, gst, priceExcl + gst, gst / 2, gst / 2)
    }
    /** price is INCLUSIVE of GST -> extracts GST. */
    fun gstRemove(priceIncl: Double, ratePercent: Double): GstResult {
        requirePositive(priceIncl, "Price"); require(ratePercent in 0.0..100.0) { "GST rate must be 0–100%" }
        val taxable = priceIncl * 100.0 / (100.0 + ratePercent); val gst = priceIncl - taxable
        return GstResult(taxable, gst, priceIncl, gst / 2, gst / 2)
    }
    val gstRates = listOf(0.0, 0.1, 0.25, 3.0, 5.0, 12.0, 18.0, 28.0)

    // ---- EMI ----
    data class EmiResult(val emi: Double, val totalPayment: Double, val totalInterest: Double)
    /** Standard amortizing-loan formula: EMI = P·r·(1+r)^n / ((1+r)^n − 1), r = monthly rate. Assumes fixed rate, monthly payments, no fees. */
    fun emi(principal: Double, annualRatePercent: Double, months: Int): EmiResult {
        require(principal > 0) { "Loan amount must be > 0" }; require(annualRatePercent >= 0) { "Interest rate cannot be negative" }; require(months > 0) { "Tenure must be at least 1 month" }
        if (annualRatePercent == 0.0) { val e = principal / months; return EmiResult(e, principal, 0.0) }
        val r = annualRatePercent / 1200.0; val f = (1 + r).pow(months)
        val e = principal * r * f / (f - 1); val total = e * months
        return EmiResult(e, total, total - principal)
    }

    // ---- Discount ----
    data class DiscountResult(val discountAmount: Double, val finalPrice: Double, val effectivePercent: Double)
    /** Applies successive percentage discounts in order, e.g. 20% then 10% off. */
    fun discount(price: Double, percents: List<Double>): DiscountResult {
        requirePositive(price, "Price"); require(percents.isNotEmpty()) { "Add at least one discount" }
        var p = price
        for (d in percents) { require(d in 0.0..100.0) { "Discount must be 0–100%" }; p -= p * d / 100.0 }
        val saved = price - p
        return DiscountResult(saved, p, if (price == 0.0) 0.0 else saved * 100.0 / price)
    }

    // ---- Age ----
    data class AgeResult(val years: Int, val months: Int, val days: Int, val totalDays: Long, val totalWeeks: Long)
    /** Uses java.time.Period — correct for leap years and month boundaries. */
    fun age(dob: LocalDate, today: LocalDate = LocalDate.now()): AgeResult {
        require(!dob.isAfter(today)) { "Date of birth cannot be in the future" }
        val p = Period.between(dob, today); val totalDays = today.toEpochDay() - dob.toEpochDay()
        return AgeResult(p.years, p.months, p.days, totalDays, totalDays / 7)
    }

    // ---- Units ----
    enum class UnitFamily { LENGTH, WEIGHT, TEMPERATURE, AREA, VOLUME, DATA }
    /** Base units: metre, kilogram, (special-cased °C), square metre, litre, byte. factor = how many base units one unit equals. */
    data class UnitDef(val family: UnitFamily, val symbol: String, val label: String, val factor: Double)
    val units: List<UnitDef> = listOf(
        UnitDef(UnitFamily.LENGTH, "mm", "Millimetre", 0.001), UnitDef(UnitFamily.LENGTH, "cm", "Centimetre", 0.01),
        UnitDef(UnitFamily.LENGTH, "m", "Metre", 1.0), UnitDef(UnitFamily.LENGTH, "km", "Kilometre", 1000.0),
        UnitDef(UnitFamily.LENGTH, "in", "Inch", 0.0254), UnitDef(UnitFamily.LENGTH, "ft", "Foot", 0.3048),
        UnitDef(UnitFamily.LENGTH, "yd", "Yard", 0.9144), UnitDef(UnitFamily.LENGTH, "mi", "Mile", 1609.344),
        UnitDef(UnitFamily.WEIGHT, "mg", "Milligram", 1e-6), UnitDef(UnitFamily.WEIGHT, "g", "Gram", 0.001),
        UnitDef(UnitFamily.WEIGHT, "kg", "Kilogram", 1.0), UnitDef(UnitFamily.WEIGHT, "t", "Tonne", 1000.0),
        UnitDef(UnitFamily.WEIGHT, "oz", "Ounce", 0.028349523125), UnitDef(UnitFamily.WEIGHT, "lb", "Pound", 0.45359237),
        UnitDef(UnitFamily.TEMPERATURE, "°C", "Celsius", 1.0), UnitDef(UnitFamily.TEMPERATURE, "°F", "Fahrenheit", 1.0),
        UnitDef(UnitFamily.TEMPERATURE, "K", "Kelvin", 1.0),
        UnitDef(UnitFamily.AREA, "cm²", "Square centimetre", 0.0001), UnitDef(UnitFamily.AREA, "m²", "Square metre", 1.0),
        UnitDef(UnitFamily.AREA, "km²", "Square kilometre", 1e6), UnitDef(UnitFamily.AREA, "ha", "Hectare", 10000.0),
        UnitDef(UnitFamily.AREA, "ft²", "Square foot", 0.09290304), UnitDef(UnitFamily.AREA, "ac", "Acre", 4046.8564224),
        UnitDef(UnitFamily.VOLUME, "ml", "Millilitre", 0.001), UnitDef(UnitFamily.VOLUME, "L", "Litre", 1.0),
        UnitDef(UnitFamily.VOLUME, "m³", "Cubic metre", 1000.0), UnitDef(UnitFamily.VOLUME, "gal(US)", "US gallon", 3.785411784),
        UnitDef(UnitFamily.VOLUME, "gal(UK)", "UK gallon", 4.54609),
        UnitDef(UnitFamily.DATA, "B", "Byte", 1.0), UnitDef(UnitFamily.DATA, "KB", "Kilobyte (1000 B)", 1000.0),
        UnitDef(UnitFamily.DATA, "KiB", "Kibibyte (1024 B)", 1024.0), UnitDef(UnitFamily.DATA, "MB", "Megabyte", 1e6),
        UnitDef(UnitFamily.DATA, "MiB", "Mebibyte", 1048576.0), UnitDef(UnitFamily.DATA, "GB", "Gigabyte", 1e9),
        UnitDef(UnitFamily.DATA, "GiB", "Gibibyte", 1073741824.0), UnitDef(UnitFamily.DATA, "TB", "Terabyte", 1e12)
    )
    fun convert(value: Double, from: UnitDef, to: UnitDef): Double {
        require(from.family == to.family) { "Cannot convert between ${from.family} and ${to.family}" }
        if (from.family == UnitFamily.TEMPERATURE) {
            val celsius = when (from.symbol) { "°C" -> value; "°F" -> (value - 32) * 5.0 / 9.0; "K" -> value - 273.15; else -> error("Unknown temp unit") }
            return when (to.symbol) { "°C" -> celsius; "°F" -> celsius * 9.0 / 5.0 + 32; "K" -> celsius + 273.15; else -> error("Unknown temp unit") }
        }
        return value * from.factor / to.factor
    }

    // ---- Video size estimator (also used by the Video Size Estimator tool) ----
    /** Estimated bytes = (videoBitrate + audioBitrate) bits/s × seconds / 8. Clearly an estimate — actual muxing/codec overhead varies (~1–3%). */
    fun estimateVideoBytes(durationSeconds: Double, videoBitrateKbps: Double, audioBitrateKbps: Double): Long {
        require(durationSeconds >= 0 && videoBitrateKbps >= 0 && audioBitrateKbps >= 0) { "Values cannot be negative" }
        return ((videoBitrateKbps + audioBitrateKbps) * 1000.0 * durationSeconds / 8.0).toLong()
    }
}
