package com.inkcast.android

import com.inkcast.android.ui.formatTime
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.max

class EInkPaginationTest {

    @Test
    fun testPaginationMath() {
        val pageSize = 6

        // Case 1: 0 items
        val count0 = 0
        val pages0 = max(1, ceil(count0.toDouble() / pageSize).toInt())
        assertEquals(1, pages0)

        // Case 2: 6 items (exactly 1 page)
        val count6 = 6
        val pages6 = max(1, ceil(count6.toDouble() / pageSize).toInt())
        assertEquals(1, pages6)

        // Case 3: 7 items (2 pages)
        val count7 = 7
        val pages7 = max(1, ceil(count7.toDouble() / pageSize).toInt())
        assertEquals(2, pages7)

        // Case 4: 15 items (3 pages: 6 + 6 + 3)
        val count15 = 15
        val pages15 = max(1, ceil(count15.toDouble() / pageSize).toInt())
        assertEquals(3, pages15)
    }

    @Test
    fun testPageSlicing() {
        val items = (1..15).map { "Item $it" }
        val pageSize = 6

        // Page 1: Items 1..6
        val page1 = items.subList(0, 6)
        assertEquals(6, page1.size)
        assertEquals("Item 1", page1.first())
        assertEquals("Item 6", page1.last())

        // Page 2: Items 7..12
        val page2 = items.subList(6, 12)
        assertEquals(6, page2.size)
        assertEquals("Item 7", page2.first())
        assertEquals("Item 12", page2.last())

        // Page 3: Items 13..15
        val page3 = items.subList(12, 15)
        assertEquals(3, page3.size)
        assertEquals("Item 13", page3.first())
        assertEquals("Item 15", page3.last())
    }

    @Test
    fun testSteppedTimeIntervals() {
        // Test that position values within a 10s window map to the exact same stepped position
        val pos1 = 12345L // 12.3s -> should step to 10000ms
        val stepped1 = (pos1 / 10000L) * 10000L
        assertEquals(10000L, stepped1)

        val pos2 = 19999L // 19.9s -> should step to 10000ms
        val stepped2 = (pos2 / 10000L) * 10000L
        assertEquals(10000L, stepped2)

        val pos3 = 20000L // 20.0s -> steps to 20000ms
        val stepped3 = (pos3 / 10000L) * 10000L
        assertEquals(20000L, stepped3)
    }

    @Test
    fun testFormatTime() {
        assertEquals("00:00", formatTime(0L))
        assertEquals("00:09", formatTime(9000L))
        assertEquals("01:05", formatTime(65000L))
        assertEquals("18:42", formatTime(1122000L))
        assertEquals("01:01:05", formatTime(3665000L))
    }
}
