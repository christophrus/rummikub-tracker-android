package org.lorus.rummiq.counter.ml

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NmsProcessorTest {

    @Before
    fun mockLog() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun unmockLog() {
        unmockkStatic(Log::class)
    }

    private val identity = LetterboxInfo(scale = 1f, padX = 0f, padY = 0f)

    private fun pred(x1: Float, y1: Float, x2: Float, y2: Float, conf: Float, cls: Float) =
        floatArrayOf(x1, y1, x2, y2, conf, cls)

    @Test
    fun `keeps confident detections and maps class ids to tile numbers`() {
        val tiles = NmsProcessor.postProcess(
            predictions = arrayOf(
                pred(0f, 0f, 10f, 10f, 0.9f, 0f),   // class 0 -> tile 1
                pred(20f, 0f, 30f, 10f, 0.9f, 12f), // class 12 -> tile 13
                pred(40f, 0f, 50f, 10f, 0.9f, 13f)  // class 13 -> Joker
            ),
            letterboxInfo = identity,
            origWidth = 100,
            origHeight = 100
        )
        assertEquals(3, tiles.size)
        assertEquals(1, tiles[0].number)
        assertEquals(13, tiles[1].number)
        assertNull(tiles[2].number)
        assertTrue(tiles[2].isJoker)
    }

    @Test
    fun `filters low confidence and out-of-range classes`() {
        val tiles = NmsProcessor.postProcess(
            predictions = arrayOf(
                pred(0f, 0f, 10f, 10f, 0.10f, 0f),  // below threshold
                pred(0f, 0f, 10f, 10f, 0.90f, -1f), // padding row
                pred(0f, 0f, 10f, 10f, 0.90f, 14f), // invalid class
                pred(0f, 0f, 10f, 10f, 0.90f, 5f)   // valid
            ),
            letterboxInfo = identity,
            origWidth = 100,
            origHeight = 100,
            confThreshold = 0.25f
        )
        assertEquals(1, tiles.size)
        assertEquals(6, tiles[0].number)
    }

    @Test
    fun `maps letterbox coordinates back to original image space`() {
        // Original 1000x1000 scaled by 0.5 and padded by (100, 50) in letterbox space.
        val info = LetterboxInfo(scale = 0.5f, padX = 100f, padY = 50f)
        val tiles = NmsProcessor.postProcess(
            predictions = arrayOf(pred(150f, 100f, 250f, 200f, 0.9f, 0f)),
            letterboxInfo = info,
            origWidth = 1000,
            origHeight = 1000
        )
        assertEquals(1, tiles.size)
        val t = tiles[0]
        assertEquals(100, t.x)       // (150-100)/0.5
        assertEquals(100, t.y)       // (100-50)/0.5
        assertEquals(200, t.width)   // ((250-100)/0.5) - 100
        assertEquals(200, t.height)  // ((200-50)/0.5) - 100
    }

    @Test
    fun `clamps coordinates to the original image bounds`() {
        val tiles = NmsProcessor.postProcess(
            predictions = arrayOf(pred(-20f, -20f, 500f, 500f, 0.9f, 0f)),
            letterboxInfo = identity,
            origWidth = 100,
            origHeight = 100
        )
        assertEquals(1, tiles.size)
        val t = tiles[0]
        assertEquals(0, t.x)
        assertEquals(0, t.y)
        assertEquals(100, t.width)
        assertEquals(100, t.height)
    }

    @Test
    fun `drops degenerate boxes`() {
        val tiles = NmsProcessor.postProcess(
            predictions = arrayOf(pred(10f, 10f, 10f, 30f, 0.9f, 0f)), // zero width
            letterboxInfo = identity,
            origWidth = 100,
            origHeight = 100
        )
        assertTrue(tiles.isEmpty())
    }

    @Test
    fun `sorts results left to right`() {
        val tiles = NmsProcessor.postProcess(
            predictions = arrayOf(
                pred(80f, 0f, 90f, 10f, 0.9f, 2f),
                pred(10f, 0f, 20f, 10f, 0.9f, 0f),
                pred(40f, 0f, 50f, 10f, 0.9f, 1f)
            ),
            letterboxInfo = identity,
            origWidth = 100,
            origHeight = 100
        )
        assertEquals(listOf(10, 40, 80), tiles.map { it.x })
        assertEquals(listOf(1, 2, 3), tiles.map { it.number })
    }
}
