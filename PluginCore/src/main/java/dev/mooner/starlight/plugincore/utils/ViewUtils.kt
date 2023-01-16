package dev.mooner.starlight.plugincore.utils

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

fun draw(view: View, build: ViewBuilder.() -> Unit) = ViewBuilder(view as FrameLayout).apply(build)

class ViewBuilder(
    private val layout: FrameLayout
) {

    companion object {
        private const val UNDEFINED = -1
    }

    private val context: Context = layout.context

    private val Number.toPx get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()

    val wrapContent get() = ViewGroup.LayoutParams.WRAP_CONTENT

    val matchParent get() = ViewGroup.LayoutParams.MATCH_PARENT

    fun dp(value: () -> Int): Int = dp(value())

    fun dp(value: Int): Int = value.toPx

    fun View.params(block: FrameLayout.LayoutParams.() -> Unit) {
        val params = if (layoutParams == null) {
            FrameLayout.LayoutParams(wrapContent, wrapContent).apply(block)
        } else {
            (layoutParams as FrameLayout.LayoutParams).apply(block)
        }
        layoutParams = params
    }

    fun <T: View> T.place(
        width: Int = UNDEFINED,
        height: Int = UNDEFINED,
        block: T.() -> Unit = {}
    ) {
        val view = this.apply(block)
        if (width != UNDEFINED || height != UNDEFINED)
            view.params {
                if (width != UNDEFINED)
                    this.width = width
                if (height != UNDEFINED)
                    this.height = height
            }
        layout.addView(view)
    }

    fun button(
        width: Int = UNDEFINED,
        height: Int = UNDEFINED,
        text: String? = null,
        onClick: ((View) -> Unit)? = null,
        block: Button.() -> Unit = {}
    ) {
        val button = Button(context).apply(block)
        if (width != UNDEFINED || height != UNDEFINED)
            button.params {
                if (width != UNDEFINED)
                    this.width = width
                if (height != UNDEFINED)
                    this.height = height
            }
        if (text != null)
            button.text = text
        if (onClick != null)
            button.setOnClickListener(onClick)
        layout.addView(button)
    }

    fun textView(
        width: Int = UNDEFINED,
        height: Int = UNDEFINED,
        text: String? = null,
        textSize: Float? = null,
        block: TextView.() -> Unit = {}
    ) {
        val textView = TextView(context).apply(block)
        if (width != UNDEFINED || height != UNDEFINED)
            textView.params {
                if (width != UNDEFINED)
                    this.width = width
                if (height != UNDEFINED)
                    this.height = height
            }
        if (text != null)
            textView.text = text
        if (textSize != null)
            textView.textSize = textSize

        layout.addView(textView)
    }
}