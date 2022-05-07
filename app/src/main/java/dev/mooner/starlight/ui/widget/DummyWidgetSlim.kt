package dev.mooner.starlight.ui.widget

import android.animation.LayoutTransition
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dev.mooner.starlight.plugincore.utils.draw
import dev.mooner.starlight.plugincore.widget.Widget
import dev.mooner.starlight.plugincore.widget.WidgetSize


class DummyWidgetSlim: Widget() {

    override val id: String = "dummy_slim"
    override val name: String = "더미 위젯(슬림)"
    override val size: WidgetSize = WidgetSize.Large

    override fun onCreateWidget(view: View) {
        /*
        val context = view.context
        val params: FrameLayout.LayoutParams =
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.marginStart = 100
        val textView = TextView(context).apply {
            text = "Hello, World!"
            textSize = 18f
            layoutParams = params
        }
        (view as FrameLayout).addView(textView)
         */
        val context = view.context
        draw(view as FrameLayout) {
            val chipGroup = ChipGroup(context)
            chipGroup.add(
                width = matchParent,
                height = wrapContent,
            ) {
                isSingleLine = false
                isNestedScrollingEnabled = true
                layoutTransition = LayoutTransition()
                params {
                    marginStart = dp { 10 }
                    marginEnd = dp { 70 }
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                }
            }
            button(
                width = dp { 70 },
                height = wrapContent,
                text = "어 킹아",
                onClick = {
                    val array: Array<String> = arrayOf(
                        "아이고난!", "아이고난1", "아이고난2", "주겨불랑", "얘!", "흐헤헤"
                    )
                    val chip = Chip(context).apply {
                        text = array.random()
                    }
                    chipGroup.addView(chip)
                }
            ) {
                params {
                    topMargin = dp { 30 }
                    gravity = Gravity.TOP or Gravity.END
                }
            }
            button(
                width = dp { 70 },
                height = wrapContent,
                text = "어 나가",
                onClick = {
                    chipGroup.removeAllViewsInLayout()
                }
            ) {
                params {
                    bottomMargin = dp { 30 }
                    gravity = Gravity.BOTTOM or Gravity.END
                }
            }
        }
    }

    override fun onCreateThumbnail(view: View) {

    }
}