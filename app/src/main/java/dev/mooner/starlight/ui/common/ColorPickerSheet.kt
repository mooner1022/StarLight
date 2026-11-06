package dev.mooner.starlight.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.flexbox.FlexboxLayout
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.SheetColorPickerBinding

/**
 * Picks a color in the app's sheet: one of [colors], then one of its shades if it has any, or with [allowCustom] any
 * color, from a hue and saturation wheel with brightness, or typed in as RRGGBB, with its opacity when [allowAlpha].
 * [onResult] gets `null` when the sheet closes without a color.
 */
fun Context.showColorPickerSheet(
    title       : String,
    colors      : Map<Int, IntArray>,
    selected    : Int?,
    allowCustom : Boolean,
    allowAlpha  : Boolean,
    onResult    : (Int?) -> Unit,
) {
    val picker = ColorPicker(this, colors, selected, allowCustom, allowAlpha)
    var result: Int? = null
    val sheet = showSheet {
        this.title = title
        icon = R.drawable.ic_round_palette_24
        content(picker.view)
        negative(R.string.cancel)
        positive(R.string.ok) { result = picker.current }
        onDismiss { onResult(result) }
    }
    picker.onChange = { sheet.setPositiveEnabled(it != null) }
    sheet.setPositiveEnabled(picker.current != null)
}

private class ColorPicker(
    private val context: Context,
    private val colors: Map<Int, IntArray>,
    selected: Int?,
    private val allowCustom: Boolean,
    private val allowAlpha: Boolean,
) {

    /** Where a change came from, which keeps its own state and isn't written back. */
    private enum class Source { OPEN, PALETTE, FIELD, WHEEL, BRIGHTNESS, OPACITY }

    private val binding = SheetColorPickerBinding.inflate(LayoutInflater.from(context))
    val view: View get() = binding.root

    var onChange: (Int?) -> Unit = {}

    var current: Int? = selected
        private set

    /** Hue, saturation and brightness, kept apart from [current] so black and grays keep their hue. */
    private val hsv = FloatArray(3).also { Color.colorToHSV(selected ?: Color.WHITE, it) }
    private var alpha = selected?.let(Color::alpha) ?: 0xFF

    private val density = context.resources.displayMetrics.density
    private val behind = ContextCompat.getColor(context, R.color.cardview)
    private val ring = ContextCompat.getColor(context, R.color.main_bright)
    private val border = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.text), 0x40)

    /** Set while this writes into the field, so the field doesn't write back. */
    private var isUpdatingField = false

    init {
        binding.modes.isVisible = allowCustom
        binding.opacityRow.isVisible = allowCustom && allowAlpha
        binding.hexLayout.prefixTextView.typeface = binding.hexInput.typeface

        binding.modePalette.setOnClickListener { showCustom(false) }
        binding.modeCustom.setOnClickListener { showCustom(true) }

        binding.wheel.onPick = { hue, saturation ->
            hsv[0] = hue
            hsv[1] = saturation
            // On a black pick, the wheel would show nothing changing.
            if (hsv[2] < 0.1f)
                hsv[2] = 1f
            pick(Color.HSVToColor(alpha, hsv), Source.WHEEL)
        }
        binding.brightness.addOnChangeListener { _, value, fromUser ->
            binding.brightnessValue.text = "${value.toInt()}%"
            if (!fromUser)
                return@addOnChangeListener
            hsv[2] = value / 100
            pick(Color.HSVToColor(alpha, hsv), Source.BRIGHTNESS)
        }
        binding.opacity.addOnChangeListener { _, value, fromUser ->
            binding.opacityValue.text = "${value.toInt()}%"
            if (!fromUser)
                return@addOnChangeListener
            alpha = (value / 100 * 255).toInt()
            pick(Color.HSVToColor(alpha, hsv), Source.OPACITY)
        }
        binding.hexInput.doAfterTextChanged { text ->
            if (isUpdatingField)
                return@doAfterTextChanged
            val typed = parse(text?.toString().orEmpty()) ?: return@doAfterTextChanged
            pick(typed, Source.FIELD)
        }

        // A color that isn't in the palette was made on the wheel; open where it can be changed.
        val inPalette = selected == null || colors.any { (main, shades) -> main == selected || selected in shades }
        showCustom(allowCustom && !inPalette)
        render(Source.OPEN)
    }

    private fun showCustom(custom: Boolean) {
        binding.palettePage.isVisible = !custom
        binding.custom.isVisible = custom
        binding.modePalette.setPicked(!custom)
        binding.modeCustom.setPicked(custom)
        if (!custom)
            renderPalette()
    }

    private fun TextView.setPicked(picked: Boolean) {
        background = if (picked) ContextCompat.getDrawable(context, R.drawable.bg_segment_selected) else null
        setTextColor(ContextCompat.getColor(context, if (picked) R.color.agent_on_gold else R.color.agent_text))
        isSelected = picked
    }

    private fun pick(color: Int, source: Source) {
        current = color
        if (source == Source.PALETTE || source == Source.FIELD) {
            Color.colorToHSV(color, hsv)
            alpha = Color.alpha(color)
        }
        render(source)
        onChange(color)
    }

    /** RRGGBB keeps the opacity set; AARRGGBB, where opacity can be picked, sets it. */
    private fun parse(text: String): Int? =
        when (text.length) {
            6    -> text.toLongOrNull(16)?.let { ColorUtils.setAlphaComponent((0xFF000000 or it).toInt(), alpha) }
            8    -> text.toLongOrNull(16)?.toInt()?.takeIf { allowAlpha }
            else -> null
        }

    private fun render(source: Source) {
        val color = current
        binding.preview.background = circle(color ?: Color.TRANSPARENT, stroke = true)
        binding.previewText.text = color?.let(::hex) ?: context.getString(R.string.color_picker_none)

        binding.wheel.setColor(hsv[0], hsv[1], hsv[2])
        if (source != Source.BRIGHTNESS)
            binding.brightness.value = (hsv[2] * 100).toInt().toFloat()
        binding.brightnessValue.text = "${binding.brightness.value.toInt()}%"
        if (source != Source.OPACITY)
            binding.opacity.value = (alpha * 100f / 255).toInt().toFloat()
        binding.opacityValue.text = "${binding.opacity.value.toInt()}%"

        if (source != Source.FIELD) {
            isUpdatingField = true
            binding.hexInput.setText(color?.let { hex(it).removePrefix("#") }.orEmpty())
            binding.hexInput.setSelection(binding.hexInput.text?.length ?: 0)
            isUpdatingField = false
        }
        if (binding.palettePage.isVisible)
            renderPalette()
    }

    private fun renderPalette() {
        val color = current
        // The palette color whose shades hold the pick, or that is the pick.
        val main = colors.entries.firstOrNull { (main, shades) -> color != null && (main == color || color in shades) }

        binding.swatches.removeAllViews()
        for (option in colors.keys)
            binding.swatches.addView(swatch(option, isPicked = option == main?.key))

        val shades = main?.value ?: IntArray(0)
        binding.shadesLabel.isVisible = shades.isNotEmpty()
        binding.shades.isVisible = shades.isNotEmpty()
        binding.shades.removeAllViews()
        for (shade in shades)
            binding.shades.addView(swatch(shade, isPicked = shade == color))
    }

    private fun swatch(color: Int, isPicked: Boolean): View {
        val size = (44 * density).toInt()
        val margin = (4 * density).toInt()
        val shown = ColorUtils.compositeColors(color, behind)
        return FrameLayout(context).apply {
            layoutParams = FlexboxLayout.LayoutParams(size + margin * 2, size + margin * 2)
            contentDescription = context.getString(R.string.color_picker_swatch, hex(color))
            isSelected = isPicked
            // A ring around the picked one, a hairline around the rest so light colors keep an edge.
            background = if (isPicked) GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((2 * density).toInt(), ring)
            } else null
            addView(View(context).apply { background = circle(color, stroke = !isPicked) },
                FrameLayout.LayoutParams(size - margin, size - margin, Gravity.CENTER))
            if (isPicked)
                addView(ImageView(context).apply {
                    setImageResource(dev.mooner.configdsl.R.drawable.ic_round_check_24)
                    imageTintList = ColorStateList.valueOf(checkColorOn(shown))
                }, FrameLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt(), Gravity.CENTER))
            setOnClickListener { pick(color, Source.PALETTE) }
        }
    }

    private fun circle(color: Int, stroke: Boolean) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (stroke)
                setStroke((1 * density).toInt().coerceAtLeast(1), border)
        }

    private fun checkColorOn(color: Int): Int {
        val dark = ContextCompat.getColor(context, R.color.store_night_light)
        return if (ColorUtils.calculateContrast(dark, color) >= ColorUtils.calculateContrast(Color.WHITE, color)) dark else Color.WHITE
    }

    private fun hex(color: Int): String =
        if (allowAlpha && Color.alpha(color) != 0xFF)
            "#%08X".format(color)
        else
            "#%06X".format(color and 0xFFFFFF)
}
