package dev.mooner.starlight.plugincore.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGridPackerTest {

    private fun cells(columns: Int, vararg spans: WidgetSpan): List<GridCell> =
        WidgetGridPacker.pack(spans.toList(), columns).map(GridPlacement::cell)

    @Test
    fun fullWidthItemsStackVertically() {
        assertEquals(
            listOf(GridCell(0, 0), GridCell(0, 1), GridCell(0, 3)),
            cells(4, 4 x 1, 4 x 2, 4 x 1)
        )
    }

    @Test
    fun smallItemsFillRowsAndTallItemHoles() {
        assertEquals(
            listOf(GridCell(0, 0), GridCell(3, 0), GridCell(0, 1), GridCell(1, 1), GridCell(1, 2)),
            cells(4, 3 x 1, 1 x 1, 1 x 2, 3 x 1, 3 x 1)
        )
    }

    @Test
    fun itemNeverPlacedBeforePreviousItem() {
        assertEquals(
            listOf(GridCell(0, 0), GridCell(0, 1), GridCell(0, 2)),
            cells(4, 2 x 1, 4 x 1, 2 x 1)
        )
    }

    @Test
    fun visualOrderMatchesListOrder() {
        val placements = WidgetGridPacker.pack(
            listOf(2 x 2, 2 x 1, 1 x 1, 4 x 1, 1 x 3, 3 x 1, 3 x 2),
            columns = 4
        )
        placements.zipWithNext { a, b ->
            val ordered = a.cell.row < b.cell.row ||
                    (a.cell.row == b.cell.row && a.cell.column < b.cell.column)
            assertTrue("$a should come before $b", ordered)
        }
    }

    @Test
    fun placementsNeverOverlap() {
        val placements = WidgetGridPacker.pack(
            listOf(2 x 2, 1 x 1, 1 x 3, 4 x 1, 2 x 1, 3 x 2, 1 x 1, 2 x 2),
            columns = 4
        )
        val occupied: MutableSet<GridCell> = hashSetOf()
        for ((cell, span) in placements) {
            assertTrue(cell.column + span.x <= 4)
            for (r in cell.row until cell.row + span.y)
                for (c in cell.column until cell.column + span.x)
                    assertTrue("Overlap at $c,$r", occupied.add(GridCell(c, r)))
        }
    }

    @Test
    fun spanWiderThanGridIsClamped() {
        val placements = WidgetGridPacker.pack(listOf(8 x 1, 2 x 1), columns = 4)
        assertEquals(4 x 1, placements[0].span)
        assertEquals(GridCell(0, 1), placements[1].cell)
    }

    @Test
    fun tabletGridFillsBesideTallItem() {
        assertEquals(
            listOf(GridCell(0, 0), GridCell(4, 0), GridCell(4, 1)),
            cells(8, 4 x 2, 4 x 1, 4 x 1)
        )
    }

    @Test
    fun rowCountCoversTallestItem() {
        val placements = WidgetGridPacker.pack(listOf(2 x 1, 2 x 3), columns = 4)
        assertEquals(3, WidgetGridPacker.rowCount(placements))
        assertEquals(0, WidgetGridPacker.rowCount(emptyList()))
    }
}
