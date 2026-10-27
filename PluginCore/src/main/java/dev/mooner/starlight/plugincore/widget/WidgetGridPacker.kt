package dev.mooner.starlight.plugincore.widget

data class GridCell(
    val column: Int,
    val row: Int,
)

data class GridPlacement(
    val cell: GridCell,
    val span: WidgetSpan,
)

object WidgetGridPacker {

    fun pack(spans: List<WidgetSpan>, columns: Int): List<GridPlacement> {
        require(columns > 0) { "columns must be positive" }

        val occupied: MutableList<BooleanArray> = mutableListOf()
        val placements: MutableList<GridPlacement> = ArrayList(spans.size)
        var cursorRow = 0
        var cursorColumn = 0

        fun isFree(column: Int, row: Int, span: WidgetSpan): Boolean {
            for (r in row until row + span.y) {
                val cells = occupied.getOrNull(r) ?: continue
                for (c in column until column + span.x)
                    if (cells[c]) return false
            }
            return true
        }

        fun occupy(column: Int, row: Int, span: WidgetSpan) {
            while (occupied.size < row + span.y)
                occupied += BooleanArray(columns)
            for (r in row until row + span.y)
                for (c in column until column + span.x)
                    occupied[r][c] = true
        }

        for (rawSpan in spans) {
            val span = WidgetSpan(rawSpan.x.coerceIn(1, columns), rawSpan.y.coerceAtLeast(1))
            var row = cursorRow
            var column = cursorColumn
            while (true) {
                if (column + span.x <= columns && isFree(column, row, span))
                    break
                if (++column + span.x > columns) {
                    column = 0
                    row++
                }
            }
            occupy(column, row, span)
            placements += GridPlacement(GridCell(column, row), span)
            cursorRow = row
            cursorColumn = column
        }
        return placements
    }

    fun rowCount(placements: List<GridPlacement>): Int =
        placements.maxOfOrNull { it.cell.row + it.span.y } ?: 0
}
