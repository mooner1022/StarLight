package dev.mooner.starlight.ui.plugins.store

import android.animation.ArgbEvaluator
import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import dev.mooner.starlight.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private fun View.dp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

internal fun Path.addSparkle(cx: Float, cy: Float, radius: Float) {
    moveTo(cx, cy - radius)
    quadTo(cx, cy, cx + radius, cy)
    quadTo(cx, cy, cx, cy + radius)
    quadTo(cx, cy, cx - radius, cy)
    quadTo(cx, cy, cx, cy - radius)
    close()
}

class StarFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private class Star(
        val x: Float,
        val y: Float,
        val radius: Float,
        val phase: Float,
        val speed: Float,
        val depth: Float,
        val isSparkle: Boolean,
        val isGold: Boolean,
    )

    private class ShootingStar(
        val startX: Float,
        val startY: Float,
        val startedAt: Float,
    )

    private val stars = mutableListOf<Star>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val starColor = context.getColor(R.color.store_star)
    private val goldColor = context.getColor(R.color.store_gold)

    private var time = 0f
    private var shooting: ShootingStar? = null
    private var nextShootingAt = 2.5f

    var parallax: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var parallaxX: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var density: Float = 0.5f
        set(value) {
            if (field == value)
                return
            field = value
            generate(width, height)
        }

    var brightness: Float = 1f
        set(value) {
            field = value
            invalidate()
        }

    var seed: Int = 7
        set(value) {
            if (field == value)
                return
            field = value
            generate(width, height)
        }

    private val animator = TimeAnimator().apply {
        setTimeListener { _, total, _ ->
            time = total / 1000f
            if (shooting == null && time >= nextShootingAt && width > 0)
                shooting = ShootingStar(
                    startX = width * Random.nextFloat() * 0.6f + width * 0.35f,
                    startY = height * Random.nextFloat() * 0.35f,
                    startedAt = time,
                )
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        generate(w, h)
    }

    private fun generate(w: Int, h: Int) {
        stars.clear()
        if (w == 0 || h == 0)
            return
        val random = Random(seed)
        val cell = dp(26f)
        val count = (((w * h) / (cell * cell)).toInt().coerceIn(12, 180) * density).toInt()
        repeat(count) {
            val sparkle = random.nextFloat() < 0.12f
            stars += Star(
                x = random.nextFloat() * w,
                y = random.nextFloat() * h * 1.2f,
                radius = if (sparkle) dp(3f + random.nextFloat() * 2.5f) else dp(0.6f + random.nextFloat() * 1.1f),
                phase = random.nextFloat() * 2f * PI.toFloat(),
                speed = 0.8f + random.nextFloat() * 2.2f,
                depth = 0.15f + random.nextFloat() * 0.5f,
                isSparkle = sparkle,
                isGold = sparkle || random.nextFloat() < 0.25f,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (star in stars) {
            val twinkle = 0.25f + 0.75f * (0.5f + 0.5f * sin(time * star.speed + star.phase))
            val y = star.y - parallax * star.depth
            if (y < -star.radius * 2 || y > height + star.radius * 2)
                continue
            val x = if (parallaxX == 0f) star.x else (star.x - parallaxX * star.depth).mod(width.toFloat())
            paint.color = if (star.isGold) goldColor else starColor
            paint.alpha = (twinkle * brightness * 255).toInt()
            if (star.isSparkle) {
                val radius = star.radius * (0.75f + 0.35f * twinkle)
                path.reset()
                path.addSparkle(x, y, radius)
                canvas.drawPath(path, paint)
                paint.alpha = (twinkle * brightness * 60).toInt()
                canvas.drawCircle(x, y, radius * 0.9f, paint)
            } else {
                canvas.drawCircle(x, y, star.radius, paint)
            }
        }
        drawShootingStar(canvas)
    }

    private fun drawShootingStar(canvas: Canvas) {
        val star = shooting ?: return
        val life = 0.9f
        val t = (time - star.startedAt) / life
        if (t !in 0f..<1f) {
            shooting = null
            nextShootingAt = time + 4f + Random.nextFloat() * 5f
            return
        }
        val distance = dp(160f) * t
        val angle = (PI * 0.8).toFloat()
        val headX = star.startX + cos(angle) * distance
        val headY = star.startY + sin(angle) * distance - parallax * 0.3f
        val length = dp(70f) * (1f - t * 0.5f)
        val tailX = headX - cos(angle) * length
        val tailY = headY - sin(angle) * length
        val alpha = sin(t * PI).toFloat().coerceIn(0f, 1f)
        trailPaint.strokeWidth = dp(1.6f)
        trailPaint.shader = LinearGradient(
            headX, headY, tailX, tailY,
            ColorUtils.setAlphaComponent(starColor, (alpha * 255).toInt()),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawLine(headX, headY, tailX, tailY, trailPaint)
        paint.color = starColor
        paint.alpha = (alpha * 255).toInt()
        canvas.drawCircle(headX, headY, dp(1.6f), paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        time = 0f
        shooting = null
        nextShootingAt = 2f + Random.nextFloat() * 3f
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}

class SparkleBurstView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private class Particle(
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
        val radius: Float,
        val color: Int,
        val startedAt: Long,
        val life: Long,
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val colors = intArrayOf(
        context.getColor(R.color.store_gold),
        context.getColor(R.color.store_gold_bright),
        context.getColor(R.color.store_gold_deep),
        context.getColor(R.color.store_star),
    )
    private var now = 0L

    private val animator = TimeAnimator().apply {
        setTimeListener { _, _, _ ->
            now = System.currentTimeMillis()
            particles.removeAll { now - it.startedAt > it.life }
            if (particles.isEmpty())
                cancel()
            invalidate()
        }
    }

    fun burst(target: View) {
        val self = IntArray(2).also(::getLocationInWindow)
        val other = IntArray(2).also(target::getLocationInWindow)
        burst(
            x = other[0] - self[0] + target.width / 2f,
            y = other[1] - self[1] + target.height / 2f,
            spread = target.width / 2f,
        )
    }

    fun burst(x: Float, y: Float, spread: Float) {
        val startedAt = System.currentTimeMillis()
        repeat(18) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = dp(60f + Random.nextFloat() * 120f)
            particles += Particle(
                x = x + (Random.nextFloat() - 0.5f) * spread,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - dp(60f),
                radius = dp(3f + Random.nextFloat() * 4f),
                color = colors.random(),
                startedAt = startedAt + Random.nextLong(0, 90),
                life = 650L + Random.nextLong(0, 350),
            )
        }
        if (!animator.isStarted)
            animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        for (p in particles) {
            val elapsed = (now - p.startedAt).coerceAtLeast(0) / 1000f
            val t = elapsed * 1000f / p.life
            if (t <= 0f)
                continue
            val gravity = dp(220f)
            val x = p.x + p.vx * elapsed
            val y = p.y + p.vy * elapsed + 0.5f * gravity * elapsed * elapsed
            val radius = p.radius * (1f - t * 0.6f)
            paint.color = p.color
            paint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
            path.reset()
            path.addSparkle(x, y, radius)
            canvas.drawPath(path, paint)
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        particles.clear()
        super.onDetachedFromWindow()
    }
}

class StoreShimmerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val highlight = context.getColor(R.color.store_shimmer)
    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setWillNotDraw(false)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        val band = width * 0.5f
        val x = -band + (width + band * 2) * progress
        paint.shader = LinearGradient(
            x - band, 0f, x, height * 0.25f,
            intArrayOf(Color.TRANSPARENT, highlight, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.restoreToCount(save)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) animator.start() else animator.cancel()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}

class StoreInstallButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val shape = GradientDrawable()
    private val fill = View(context)
    private val label = TextView(context)

    private val goldColor = context.getColor(R.color.store_gold)
    private val softColor = context.getColor(R.color.store_gold_soft)
    private val restartColor = context.getColor(R.color.store_restart_button)

    private var state: StoreInstallState? = null
    private var colorAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    var onCompleted: ((View) -> Unit)? = null

    var compact: Boolean = false
        set(value) {
            field = value
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (value) 12f else 15f)
        }

    init {
        background = shape
        foreground = TypedValue()
            .also { context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }
            .let { AppCompatResources.getDrawable(context, it.resourceId) }
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
        fill.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(context.getColor(R.color.store_gold_deep), goldColor)
        )
        fill.alpha = 0.55f
        fill.pivotX = 0f
        fill.scaleX = 0f
        addView(fill, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.typeface = ResourcesCompat.getFont(context, R.font.nanumsquare_neo_bold)
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        compact = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shape.cornerRadius = h / 2f
    }

    fun setState(state: StoreInstallState, animate: Boolean) {
        val previous = this.state
        this.state = state
        if (previous == state)
            return

        val text = when (state) {
            StoreInstallState.NotInstalled       -> context.getString(R.string.plugin_store_install)
            is StoreInstallState.UpdateAvailable -> context.getString(R.string.plugin_store_update)
            StoreInstallState.Installed          -> context.getString(R.string.plugin_store_installed)
            is StoreInstallState.Downloading     -> context.getString(R.string.plugin_store_progress, state.progress)
            StoreInstallState.Verifying          -> context.getString(R.string.plugin_store_verifying)
            StoreInstallState.PendingRestart     -> context.getString(R.string.plugin_store_pending_restart)
            StoreInstallState.Incompatible       -> context.getString(R.string.plugin_store_incompatible)
        }
        val (backgroundColor, textColor) = when (state) {
            StoreInstallState.NotInstalled,
            is StoreInstallState.UpdateAvailable -> goldColor to context.getColor(R.color.store_on_gold)
            StoreInstallState.PendingRestart     -> restartColor to goldColor
            is StoreInstallState.Downloading,
            StoreInstallState.Verifying          -> softColor to context.getColor(R.color.text)
            StoreInstallState.Installed,
            StoreInstallState.Incompatible       -> softColor to context.getColor(R.color.text_sub)
        }
        isEnabled = state == StoreInstallState.NotInstalled ||
                state is StoreInstallState.UpdateAvailable ||
                state == StoreInstallState.PendingRestart
        alpha = if (state == StoreInstallState.Incompatible) 0.6f else 1f

        val sameKind = previous is StoreInstallState.Downloading && state is StoreInstallState.Downloading
        if (animate && previous != null && !sameKind)
            swapLabel(text, textColor)
        else {
            label.text = text
            label.setTextColor(textColor)
        }
        setShapeColor(backgroundColor, animate && previous != null)
        updateFill(state, animate)

        if (animate && previous?.isBusy == true && state == StoreInstallState.PendingRestart) {
            animate().cancel()
            scaleX = 0.92f
            scaleY = 0.92f
            animate().scaleX(1f).scaleY(1f)
                .setInterpolator(OvershootInterpolator(3f))
                .setDuration(420)
                .start()
            onCompleted?.invoke(this)
        }
    }

    private fun swapLabel(text: String, color: Int) {
        val offset = dp(8f)
        label.animate().cancel()
        label.animate()
            .alpha(0f)
            .translationY(-offset)
            .setDuration(110)
            .withEndAction {
                label.text = text
                label.setTextColor(color)
                label.translationY = offset
                label.animate().alpha(1f).translationY(0f).setDuration(160).start()
            }
            .start()
    }

    private fun setShapeColor(color: Int, animate: Boolean) {
        colorAnimator?.cancel()
        val from = shape.color?.defaultColor
        if (!animate || from == null) {
            shape.setColor(color)
            return
        }
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, color).apply {
            duration = 260
            addUpdateListener { shape.setColor(it.animatedValue as Int) }
            start()
        }
    }

    private fun updateFill(state: StoreInstallState, animate: Boolean) {
        val target = when (state) {
            is StoreInstallState.Downloading -> state.progress / 100f
            StoreInstallState.Verifying      -> 1f
            else                             -> 0f
        }
        fill.animate().cancel()
        if (state == StoreInstallState.Verifying) {
            if (pulseAnimator == null)
                pulseAnimator = ValueAnimator.ofFloat(0.55f, 0.25f).apply {
                    duration = 500
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { fill.alpha = it.animatedValue as Float }
                    start()
                }
        } else {
            pulseAnimator?.cancel()
            pulseAnimator = null
            fill.alpha = 0.55f
        }
        if (!animate) {
            fill.scaleX = target
            return
        }
        if (target == 0f && fill.scaleX > 0f)
            fill.animate().alpha(0f).setDuration(250).withEndAction {
                fill.scaleX = 0f
                fill.alpha = 0.55f
            }.start()
        else
            fill.animate().scaleX(target).setDuration(140).setInterpolator(LinearInterpolator()).start()
    }

    override fun onDetachedFromWindow() {
        colorAnimator?.cancel()
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDetachedFromWindow()
    }
}
