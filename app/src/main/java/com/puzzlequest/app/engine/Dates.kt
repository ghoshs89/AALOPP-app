package com.puzzlequest.app.engine

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

typealias LocalDateString = String

object Dates {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val shortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun today(): LocalDateString = LocalDate.now().format(isoFormatter)

    fun addDays(date: LocalDateString, delta: Int): LocalDateString =
        LocalDate.parse(date, isoFormatter).plusDays(delta.toLong()).format(isoFormatter)

    fun daysBetween(from: LocalDateString, to: LocalDateString): Int =
        ChronoUnit.DAYS.between(
            LocalDate.parse(from, isoFormatter),
            LocalDate.parse(to, isoFormatter),
        ).toInt()

    fun shortLabel(date: LocalDateString): String =
        LocalDate.parse(date, isoFormatter).format(shortFormatter)
}
