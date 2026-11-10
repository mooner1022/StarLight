/*
 * SkyEmblemView.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.common

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import dev.mooner.starlight.R
import dev.mooner.starlight.utils.isNightMode
import org.xmlpull.v1.XmlPullParser
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The app's emblem as part of the sky a header shows, large and turned at the end of the header and running
 * off its edge.
 *
 * In dark mode it is a night sky: the petals are faint facets of gold light with thin edges. In light mode
 * it is a sunset: the old header art's colours wash around the emblem, whose petals are white facets, and
 * nothing else: no points or sparkles around it. In both a star twinkles slowly at the tip of each petal.
 * The wash is drawn here and not by a view of its own because it is placed around the emblem, wherever a
 * layout puts that.
 *
 * The petals are the paths of the emblem's own drawable, so the shape is the emblem's exactly.
 */
class SkyEmblemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private class Petal(val path: Path, val tipX: Float, val tipY: Float)

    /** How the emblem is painted in a theme. Alphas are out of 255. */
    private class Look(
        val facet: Int,
        val fullHeart: Int, val fullEdge: Int,
        val faintHeart: Int, val faintEdge: Int,
        val edge: Int, val edgeWidth: Float,
        val tip: Int, val tipAlpha: Int,
        val halo: Int, val haloAlpha: Int,
    )

    private val isNight = context.isNightMode()

    private val look: Look = run {
        val gold = context.getColor(R.color.store_gold)
        if (isNight)
            Look(
                facet = gold, fullHeart = 120, fullEdge = 34, faintHeart = 62, faintEdge = 12,
                edge = ColorUtils.setAlphaComponent(gold, 120), edgeWidth = 0.8f,
                tip = context.getColor(R.color.store_star), tipAlpha = 238,
                halo = gold, haloAlpha = 46,
            )
        else
            Look(
                facet = Color.WHITE, fullHeart = 238, fullEdge = 138, faintHeart = 156, faintEdge = 76,
                edge = ColorUtils.setAlphaComponent(Color.WHITE, 240), edgeWidth = 0.9f,
                tip = Color.WHITE, tipAlpha = 255,
                halo = Color.WHITE, haloAlpha = 80,
            )
    }

    /**
     * In px: the emblem's size, how far its center is from the end, and how far below the status bar
     * (unset: [CENTER_Y] of the height under it).
     */
    private var emblemSize = dp(SIZE)
    private var centerFromEnd = dp(CENTER_FROM_END)
    private var centerFromTop = -1f
    /** Whether the sky around the emblem is drawn too: the sunset's wash, or the night's glow. */
    private var hasBackdrop = true
    /** How far the sunset's wash spreads, against its spread in a page header. More where it has a panel to fill. */
    private var washSpread = 1f

    private var viewport = 500f
    private val petals: List<Petal> = loadPetals()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = look.edge
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val washPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    // Brighter toward the heart of the emblem. Alternate petals are fuller, which reads as facets catching light.
    private var fullFacet: Shader? = null
    private var faintFacet: Shader? = null

    private var wash: Bitmap? = null
    private val washBounds = RectF()

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

    init {
        context.withStyledAttributes(attrs, R.styleable.SkyEmblemView) {
            emblemSize = getDimension(R.styleable.SkyEmblemView_emblemSize, emblemSize)
            centerFromEnd = getDimension(R.styleable.SkyEmblemView_emblemCenterFromEnd, centerFromEnd)
            centerFromTop = getDimension(R.styleable.SkyEmblemView_emblemCenterFromTop, centerFromTop)
            hasBackdrop = getBoolean(R.styleable.SkyEmblemView_emblemBackdrop, hasBackdrop)
            washSpread = getFloat(R.styleable.SkyEmblemView_emblemWashSpread, washSpread)
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    /** A length drawn for the emblem at its default size, at the size it has here. */
    private fun unit(value: Float) = value * emblemSize / dp(SIZE)

    /** A length of the wash, which is as large as the emblem and then spread by [washSpread]. */
    private fun spread(value: Float) = unit(value) * washSpread

    /**
     * In px: how much of the top of this view is behind the status bar. The emblem keeps its place below
     * that line, so a header reads the same on any device, and only the sky runs up through it.
     */
    var topInset: Int = 0
        set(value) {
            if (field == value)
                return
            field = value
            rebuild()
            invalidate()
        }

    /** Whether the emblem stands upright in the middle of the width, and not turned at the end of a header. */
    private var isCentered = false

    /**
     * Shows the emblem as a page's own mark: upright in the middle of the width, [size] across, its center
     * [centerFromTop] below the status bar, with the sunset's wash spread [washSpread] times as far as in a
     * header. All in px, but the spread.
     */
    fun showCentered(size: Float, centerFromTop: Float, washSpread: Float) {
        isCentered = true
        emblemSize = size
        this.centerFromTop = centerFromTop
        this.washSpread = washSpread
        rebuild()
        invalidate()
    }

    private val centerX get() = if (isCentered) width / 2f else width - centerFromEnd
    private val centerY get() = topInset + if (centerFromTop >= 0f) centerFromTop else (height - topInset) * CENTER_Y

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
        rebuild()
    }

    /** Makes what is drawn from the view's size and the emblem's place in it. */
    private fun rebuild() {
        val w = width
        val h = height
        val center = viewport / 2f
        fun facet(heart: Int, edge: Int) = RadialGradient(
            center, center, viewport * 0.52f,
            intArrayOf(ColorUtils.setAlphaComponent(look.facet, heart), ColorUtils.setAlphaComponent(look.facet, edge)),
            floatArrayOf(0.1f, 1f),
            Shader.TileMode.CLAMP,
        )
        fullFacet = facet(look.fullHeart, look.fullEdge)
        faintFacet = facet(look.faintHeart, look.faintEdge)

        wash?.recycle()
        wash = null
        if (!hasBackdrop || w == 0 || h == 0)
            return
        if (isNight) {
            val gold = look.facet
            glowPaint.shader = RadialGradient(
                centerX - unit(dp(10f)), centerY, emblemSize * 0.8f,
                intArrayOf(ColorUtils.setAlphaComponent(gold, GLOW_ALPHA), ColorUtils.setAlphaComponent(gold, GLOW_ALPHA / 4), 0),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            wash = renderWash(w, h)
            washBounds.set(0f, 0f, w.toFloat(), h.toFloat())
        }
    }

    /**
     * The sunset as a soft ellipse of colour around the emblem. Where the view starts under the status bar
     * the wash is gone by the top edge, so that it doesn't end in a line there; where the view runs up behind
     * the status bar it just thins out toward the top of the screen. It is a blur, so it is drawn once and
     * small, and stretched when shown.
     */
    private fun renderWash(w: Int, h: Int): Bitmap {
        val scale = 1f / WASH_DOWNSCALE
        val bitmap = Bitmap.createBitmap(ceil(w * scale).toInt(), ceil(h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        // The colours run from dusk above the emblem to the warm glow below it, as they did down the old art.
        paint.shader = LinearGradient(
            0f, centerY - spread(dp(104f)), 0f, centerY + spread(dp(112f)),
            SUNSET, SUNSET_STOPS, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        // A circle of the ellipse's width, flattened to its height and moved to where the emblem is.
        val radiusX = spread(dp(WASH_RADIUS_X))
        paint.shader = RadialGradient(
            0f, 0f, radiusX,
            IntArray(WASH_ALPHAS.size) { ColorUtils.setAlphaComponent(Color.BLACK, WASH_ALPHAS[it]) }, WASH_STOPS,
            Shader.TileMode.CLAMP,
        ).apply {
            setLocalMatrix(Matrix().apply {
                setScale(1f, WASH_RADIUS_Y / WASH_RADIUS_X)
                postTranslate(centerX - spread(dp(6f)), centerY + spread(dp(4f)))
            })
        }
        canvas.drawPaint(paint)

        if (topInset == 0) {
            paint.shader = LinearGradient(
                0f, 0f, 0f, dp(WASH_TOP_FADE),
                intArrayOf(0, ColorUtils.setAlphaComponent(Color.BLACK, 90), Color.BLACK), floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPaint(paint)
        }
        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        val size = emblemSize
        val scale = size / viewport
        val centerX = centerX
        val centerY = centerY

        if (hasBackdrop) {
            if (isNight) {
                canvas.drawCircle(centerX - unit(dp(10f)), centerY, size * 0.8f, glowPaint)
            } else {
                wash?.let { canvas.drawBitmap(it, null, washBounds, washPaint) }
            }
        }

        canvas.save()
        if (!isCentered)
            canvas.rotate(TILT, centerX, centerY)
        canvas.translate(centerX - size / 2f, centerY - size / 2f)
        canvas.scale(scale, scale)

        edgePaint.strokeWidth = dp(look.edgeWidth) / scale
        petals.forEachIndexed { index, petal ->
            fillPaint.shader = if (index in FULL_FACETS) fullFacet else faintFacet
            canvas.drawPath(petal.path, fillPaint)
            canvas.drawPath(petal.path, edgePaint)
        }
        petals.forEachIndexed { index, petal ->
            // Each tip twinkles at its own slow pace, never going dark.
            val twinkle = 0.62f + 0.38f * (0.5f + 0.5f * sin(time * (0.45f + 0.11f * index) + index * 1.7f))
            val radius = dp(TIP_SIZES[index % TIP_SIZES.size]) / scale
            starPaint.color = ColorUtils.setAlphaComponent(look.halo, (twinkle * look.haloAlpha).toInt())
            canvas.drawCircle(petal.tipX, petal.tipY, radius * 2.5f, starPaint)
            starPaint.color = ColorUtils.setAlphaComponent(look.tip, (twinkle * look.tipAlpha).toInt())
            canvas.drawCircle(petal.tipX, petal.tipY, radius, starPaint)
        }
        canvas.restore()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        // Still when nobody sees it, and when the system's animations are off.
        if (isVisible && ValueAnimator.areAnimatorsEnabled()) {
            if (!animator.isStarted) {
                // The animator counts from zero again each time it starts.
                lastDrawnAt = 0f
                animator.start()
            }
        } else {
            animator.cancel()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        /** In dp: the emblem's size, and how far its center is from the end of the header, unless a layout says. */
        const val SIZE = 166f
        const val CENTER_FROM_END = 40f
        /** The emblem's center, as a share of the header's height, unless a layout says. */
        const val CENTER_Y = 0.45f
        const val TILT = 12f

        const val GLOW_ALPHA = 50

        /** The petals, in the drawable's order, that are drawn fuller: one of each neighbouring pair. */
        val FULL_FACETS = setOf(0, 3, 5, 6)
        /** In dp: the star at each petal's tip, a little different from the next. */
        val TIP_SIZES = floatArrayOf(1.95f, 1.35f, 1.6f, 1.3f, 1.7f, 1.2f, 1.55f, 1.35f)

        /** The old header art's sunset, top to bottom. */
        val SUNSET = intArrayOf(
            0xFF6455A2.toInt(), 0xFFC073A0.toInt(), 0xFFD47E97.toInt(),
            0xFFF59193.toInt(), 0xFFF9AE91.toInt(), 0xFFFBD8BF.toInt(),
        )
        val SUNSET_STOPS = floatArrayOf(0f, 0.38f, 0.52f, 0.69f, 0.85f, 1f)

        /** The wash: its strength from the middle of the ellipse to its rim, and the ellipse's radii in dp. */
        val WASH_ALPHAS = intArrayOf(150, 118, 60, 18, 0)
        val WASH_STOPS = floatArrayOf(0f, 0.30f, 0.60f, 0.84f, 1f)
        const val WASH_RADIUS_X = 250f
        const val WASH_RADIUS_Y = 140f
        /** In dp: how far down from the top edge the wash takes to reach its strength. */
        const val WASH_TOP_FADE = 52f
        const val WASH_DOWNSCALE = 4

        const val TIP_SAMPLES = 96
        const val FRAME_INTERVAL = 0.066f
    }
}
