package dev.mooner.starlight.ui.editor.tab

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class TabItemMoveCallbackListener(
    val adapter: TabViewAdapter
): ItemTouchHelper.Callback() {

    override fun isItemViewSwipeEnabled(): Boolean =
        false

    override fun isLongPressDragEnabled(): Boolean =
        true

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPos: Int = viewHolder.bindingAdapterPosition
        val toPos: Int = target.bindingAdapterPosition
        adapter.swapData(fromPos, toPos)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
}