/*
 * NightEmblemView.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.common

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import dev.mooner.starlight.R
import org.xmlpull.v1.XmlPullParser
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The app's emblem as part of a night sky: its petals drawn as faint facets of light with thin edges,
 * and a star twinkling slowly at the tip of each. It sits large and turned at the end of a night header,
 * running off the edge, in place of the solid emblem that used to look pasted onto the sky.
 *
 * The petals are the paths of the emblem's own drawable, so the shape is the emblem's exactly.
 */
class NightEmblemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private class Petal(val path: Path, val tipX: Float, val tipY: Float)

    private val gold = context.getColor(R.color.store_gold)
    private val star = context.getColor(R.color.store_star)
    private val density = resources.displayMetrics.density

    private var viewport = 500f
    private val petals: List<Petal> = loadPetals()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = ColorUtils.setAlphaComponent(gold, EDGE_ALPHA)
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Brighter toward the heart of the emblem. Alternate petals are fuller, which reads as facets catching light.
    private var fullFacet: Shader? = null
    private var faintFacet: Shader? = null

    private var time = 0f
    private var lastDrawnAt = 0f

    private val animator = TimeAnimator().apply {
        setTimeListener { _, total, _ ->
            time = total / 1000f
            // The twinkle is slow, so a few frames a second are enough to carry it.
            if (time - lastDrawnAt >= FRAME_INTERVAL) {
                lastDrawnAt = time
                invalidate()
            }
        }
    }

    private fun dp(value: Float) = value * density

    private fun loadPetals(): List<Petal> {
        val namespace = "http://schemas.android.com/apk/res/android"
        val paths = ArrayList<Path>()
        val parser = resources.getXml(R.drawable.ic_logo_transparent)
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "vector" -> viewport = parser.getAttributeFloatValue(namespace, "viewportWidth", viewport)
                        "path"   -> parser.getAttributeValue(namespace, "pathData")
                            ?.let(PathParser::createPathFromPathData)
                            ?.let(paths::add)
                    }
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }

        // A petal's tip is the point of its outline farthest from the emblem's center.
        val center = viewport / 2f
        val position = FloatArray(2)
        return paths.map { path ->
            val measure = PathMeasure(path, true)
            var tipX = center
            var tipY = center
            var farthest = 0f
            for (step in 0 until TIP_SAMPLES) {
                measure.getPosTan(measure.length * step / TIP_SAMPLES, position, null)
                val distance = hypot(position[0] - center, position[1] - center)
                if (distance > farthest) {
                    farthest = distance
                    tipX = position[0]
                    tipY = position[1]
                }
            }
            Petal(path, tipX, tipY)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val center = viewport / 2f
        fun facet(heart: Int, edge: Int) = RadialGradient(
            center, center, viewport * 0.52f,
            intArrayOf(ColorUtils.setAlphaComponent(gold, heart), ColorUtils.setAlphaComponent(gold, edge)),
            floatArrayOf(0.1f, 1f),
            Shader.TileMode.CLAMP,
        )
        fullFacet = facet(FULL_FACET_HEART, FULL_FACET_EDGE)
        faintFacet = facet(FAINT_FACET_HEART, FAINT_FACET_EDGE)

        val radius = dp(SIZE) * 0.8f
        glowPaint.shader = RadialGradient(
            w - dp(CENTER_FROM_END) - dp(10f), h * CENTER_Y, radius,
            intArrayOf(ColorUtils.setAlphaComponent(gold, GLOW_ALPHA), ColorUtils.setAlphaComponent(gold, GLOW_ALPHA / 4), 0),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val size = dp(SIZE)
        val scale = size / viewport
        val centerX = width - dp(CENTER_FROM_END)
        val centerY = height * CENTER_Y

        canvas.drawCircle(centerX - dp(10f), centerY, size * 0.8f, glowPaint)

        canvas.save()
        canvas.rotate(TILT, centerX, centerY)
        canvas.translate(centerX - size / 2f, centerY - size / 2f)
        canvas.scale(scale, scale)

        edgePaint.strokeWidth = dp(EDGE_WIDTH) / scale
        petals.forEachIndexed { index, petal ->
            fillPaint.shader = if (index in FULL_FACETS) fullFacet else faintFacet
            canvas.drawPath(petal.path, fillPaint)
            canvas.drawPath(petal.path, edgePaint)
        }
        petals.forEachIndexed { index, petal ->
            // Each tip twinkles at its own slow pace, never going dark.
            val twinkle = 0.62f + 0.38f * (0.5f + 0.5f * sin(time * (0.45f + 0.11f * index) + index * 1.7f))
            val radius = dp(TIP_SIZES[index % TIP_SIZES.size]) / scale
            starPaint.color = ColorUtils.setAlphaComponent(gold, (twinkle * 46).toInt())
            canvas.drawCircle(petal.tipX, petal.tipY, radius * 2.5f, starPaint)
            starPaint.color = ColorUtils.setAlphaComponent(star, (twinkle * 238).toInt())
            canvas.drawCircle(petal.tipX, petal.tipY, radius, starPaint)
        }
        canvas.restore()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        // Still when nobody sees it, and when the system's animations are off.
        if (isVisible && ValueAnimator.areAnimatorsEnabled())
            animator.start()
        else
            animator.cancel()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        /** In dp: the emblem's size, and how far its center is from the end of the header. */
        const val SIZE = 166f
        const val CENTER_FROM_END = 40f
        /** The emblem's center, as a share of the header's height. */
        const val CENTER_Y = 0.45f
        const val TILT = 12f

        const val EDGE_WIDTH = 0.8f
        const val EDGE_ALPHA = 120
        const val GLOW_ALPHA = 50
        const val FULL_FACET_HEART = 120
        const val FULL_FACET_EDGE = 34
        const val FAINT_FACET_HEART = 62
        const val FAINT_FACET_EDGE = 12

        /** The petals, in the drawable's order, that are drawn fuller: one of each neighbouring pair. */
        val FULL_FACETS = setOf(0, 3, 5, 6)
        /** In dp: the star at each petal's tip, a little different from the next. */
        val TIP_SIZES = floatArrayOf(1.95f, 1.35f, 1.6f, 1.3f, 1.7f, 1.2f, 1.55f, 1.35f)

        const val TIP_SAMPLES = 96
        const val FRAME_INTERVAL = 0.066f
    }
}
