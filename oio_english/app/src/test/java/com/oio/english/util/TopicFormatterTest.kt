package com.oio.english.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TopicFormatterTest {

    @Test
    fun `clean strips number prefix`() {
        assertEquals("Food", TopicFormatter.clean("1. Food"))
        assertEquals("Cooking", TopicFormatter.clean("2. Cooking"))
        assertEquals("Restaurant", TopicFormatter.clean("3. Restaurant"))
    }

    @Test
    fun `clean handles plain name`() {
        assertEquals("Review Day", TopicFormatter.clean("Review Day"))
    }

    @Test
    fun `clean handles day prefix`() {
        assertEquals("Work", TopicFormatter.clean("Day 1 - Work"))
        assertEquals("Study", TopicFormatter.clean("1. Study"))
    }

    @Test
    fun `parse extracts number and name`() {
        val parts = TopicFormatter.parse("1. Food")
        assertEquals(1, parts.number)
        assertEquals("Food", parts.name)
    }

    @Test
    fun `parse returns null number for plain text`() {
        val parts = TopicFormatter.parse("Review")
        assertEquals(null, parts.number)
        assertEquals("Review", parts.name)
    }
}
