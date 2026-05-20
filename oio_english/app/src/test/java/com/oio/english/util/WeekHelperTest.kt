package com.oio.english.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekHelperTest {

    @Test
    fun `weekOfDay day 1 is week 1`() {
        assertEquals(1, WeekHelper.weekOfDay(1))
    }

    @Test
    fun `weekOfDay day 7 is week 1`() {
        assertEquals(1, WeekHelper.weekOfDay(7))
    }

    @Test
    fun `weekOfDay day 8 is week 2`() {
        assertEquals(2, WeekHelper.weekOfDay(8))
    }

    @Test
    fun `weekOfDay day 14 is week 2`() {
        assertEquals(2, WeekHelper.weekOfDay(14))
    }

    @Test
    fun `dayRangeOfWeek week 1`() {
        assertEquals(1..7, WeekHelper.dayRangeOfWeek(1))
    }

    @Test
    fun `dayRangeOfWeek week 2`() {
        assertEquals(8..14, WeekHelper.dayRangeOfWeek(2))
    }

    @Test
    fun `computeWeeks`() {
        val days = listOf(1, 2, 3, 5, 8, 10, 15, 20)
        assertEquals(listOf(1, 2, 3), WeekHelper.computeWeeks(days))
    }
}
