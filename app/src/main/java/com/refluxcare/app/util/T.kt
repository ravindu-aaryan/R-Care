package com.refluxcare.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Small date/time helpers. Dates are stored as "yyyy-MM-dd" strings, times as epoch millis. */
object T {
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val shortFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M")

    val zone: ZoneId get() = ZoneId.systemDefault()

    fun today(): LocalDate = LocalDate.now()
    fun key(d: LocalDate): String = d.toString()
    fun parse(k: String): LocalDate = LocalDate.parse(k)
    fun todayKey(): String = key(today())

    fun millis(d: LocalDate, h: Int, m: Int): Long =
        d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    fun toLdt(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)

    fun time(ms: Long): String = toLdt(ms).format(timeFmt)
    fun day(d: LocalDate): String = d.format(dayFmt)
    fun short(d: LocalDate): String = d.format(shortFmt)

    fun minutesOfDay(ms: Long): Int {
        val t = toLdt(ms)
        return t.hour * 60 + t.minute
    }

    fun duration(mins: Long): String = "${mins / 60}h ${mins % 60}m"

    fun fmtMinutesOfDay(m: Int): String {
        val mm = ((m % 1440) + 1440) % 1440
        return LocalTime.of(mm / 60, mm % 60).format(timeFmt)
    }

    const val HOUR = 60L * 60L * 1000L
    const val MINUTE = 60L * 1000L
}
