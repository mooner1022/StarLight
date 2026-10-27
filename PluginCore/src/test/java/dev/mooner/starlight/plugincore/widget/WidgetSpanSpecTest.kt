package dev.mooner.starlight.plugincore.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSpanSpecTest {

    private val spec = WidgetSpanSpec(default = 4 x 2, min = 2 x 1, max = 8 x 4)

    @Test
    fun resolvesNullToDefault() {
        assertEquals(4 x 2, spec.resolve(null, columns = 4))
    }

    @Test
    fun resolveClampsToRangeAndColumns() {
        assertEquals(2 x 1, spec.resolve(1 x 0, columns = 4))
        assertEquals(4 x 4, spec.resolve(8 x 9, columns = 4))
        assertEquals(8 x 4, spec.resolve(8 x 9, columns = 8))
    }

    @Test
    fun resolveRespectsGridNarrowerThanMinimum() {
        val wide = WidgetSpanSpec(default = 6 x 1, min = 6 x 1, max = 8 x 1)
        assertEquals(4 x 1, wide.resolve(null, columns = 4))
    }

    @Test
    fun resizableOnlyWhenRangeIsNotFixed() {
        assertTrue(spec.isResizable)
        assertFalse(WidgetSpanSpec(4 x 1).isResizable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDefaultOutsideRange() {
        WidgetSpanSpec(default = 4 x 5, min = 2 x 1, max = 4 x 4)
    }
}
