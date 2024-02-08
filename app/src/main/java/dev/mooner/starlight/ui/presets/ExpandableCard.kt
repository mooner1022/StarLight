/*
 * ExpandableCard.kt created by Minki Moon(mooner1022) on 22. 2. 4. 오후 3:23
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.presets

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.view.animation.Transformation
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import coil.load
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ExpandableCardBinding
import dev.mooner.starlight.ui.presets.interpolator.CubicBezierInterpolator
import kotlin.properties.Delegates

class ExpandableCard @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : LinearLayout(context, attrs, defStyle) {

    companion object {
        private const val STATE_EXPANDING  = 2
        private const val STATE_EXPANDED   = 1
        private const val STATE_IDLE       = 0
        private const val STATE_COLLAPSED  = -1
        private const val STATE_COLLAPSING = -2

        private const val ANIM_DURATION = 200L
    }

    private var binding: ExpandableCardBinding by Delegates.notNull()

    private var _title: String? = null
    private var _switchValue: Boolean = false
    private var _switchEnabled: Boolean = true
    private var innerView: View? = null
    private var expandState: Int = STATE_COLLAPSED
    private var animateState: Int = STATE_IDLE

    private var collapsedHeight: Int = 0
    private var totalHeight: Int? = null

    private val isAnimating: Boolean
        get() = animateState == STATE_EXPANDING || animateState == STATE_COLLAPSING

    @LayoutRes
    private var innerViewResId: Int = NO_ID
    private var iconDrawable: Drawable? = null
    private var startExpanded: Boolean = false
    private var showSwitch: Boolean = true

    private val cubicBezierInterpolator
        get() = CubicBezierInterpolator(0.1, 0.7, 0.1, 1.0)

    private var switchListener: OnSwitchChangeListener? = null
    private var viewInflateListener: (() -> Unit)? = null

    private val onClickListener = OnClickListener {
        if (expandState == STATE_EXPANDED)
            collapse()
        else
            expand()
    }

    private val switchCheckChangedListener = CompoundButton.OnCheckedChangeListener { view, isChecked -> switchListener?.onSwitchChanged(view, isChecked) }

    private val arrowAnimExpand: Animation by lazy {
        RotateAnimation(0f, 180f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            fillAfter = true
            duration = ANIM_DURATION
            interpolator = cubicBezierInterpolator
        }
    }
    private val arrowAnimCollapse: Animation by lazy {
        RotateAnimation(180f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            fillAfter = true
            duration = ANIM_DURATION
            interpolator = cubicBezierInterpolator
        }
    }

    init {
        binding = ExpandableCardBinding.inflate(LayoutInflater.from(context), this, true)
        init(context, attrs, defStyle)
    }

    private fun init(context: Context, attrs: AttributeSet?, defStyle: Int) {
        val attr = context.obtainStyledAttributes(
            attrs, R.styleable.ExpandableCard, defStyle, 0
        )

        _title         = attr.getString(R.styleable.ExpandableCard_title)
        innerViewResId = attr.getResourceId(R.styleable.ExpandableCard_innerView, View.NO_ID)
        iconDrawable   = attr.getDrawable(R.styleable.ExpandableCard_icon)
        startExpanded  = attr.getBoolean(R.styleable.ExpandableCard_startExpanded, false)
        showSwitch     = attr.getBoolean(R.styleable.ExpandableCard_showSwitch, true)

        attr.recycle()
        //invalidateTextPaintAndMeasurements()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        with(binding) {
            if (!_title.isNullOrEmpty())
                cardTitle.text = _title

            cardHeader.visibility = View.VISIBLE
            iconDrawable?.let { drawable ->
                cardIcon.visibility   = View.VISIBLE
                cardIcon.load(drawable)
            }

            if (startExpanded) {
                expand()
            }

            cardLayout.apply {
                rippleColor = ContextCompat.getColorStateList(context, android.R.color.transparent)
                setOnClickListener(onClickListener)
            }
            cardArrow.setOnClickListener(onClickListener)

            cardSwitch.apply {
                visibility = View.GONE
                if (showSwitch) {
                    setOnCheckedChangeListener(switchCheckChangedListener)
                }
            }
        }
    }

    private fun inflateInnerView(resId: Int) {
        if (innerView == null) {
            //Logger.v("Lazy-inflating inner view...")
            binding.cardStub.layoutResource = resId
            innerView = binding.cardStub.inflate()
            if (viewInflateListener != null)
                viewInflateListener!!()
        }
    }

    fun expand() {
        if (expandState == STATE_EXPANDED) return

        val initialHeight = binding.cardLayout.height
        if (!isAnimating) {
            collapsedHeight = initialHeight
        }

        inflateInnerView(innerViewResId)
        totalHeight ?: let { totalHeight = getFullHeight(binding.cardLayout) }

        if (totalHeight!! - collapsedHeight != 0) {
            animateLayout(initialHeight, totalHeight!! - collapsedHeight, AnimationType.Expand)
        }
    }

    fun collapse() {
        if (expandState == STATE_COLLAPSED) return

        val initialHeight = binding.cardLayout.measuredHeight
        if (initialHeight - collapsedHeight != 0) {
            animateLayout(initialHeight, initialHeight - collapsedHeight, AnimationType.Collapse)
        }
    }

    private fun animateLayout(initialHeight: Int, distance: Int, animType: AnimationType) {
        val expandAnim = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                if (interpolatedTime == 1f) {
                    animateState = STATE_IDLE
                    if (animType == AnimationType.Collapse)
                        innerView?.visibility = View.GONE
                } else if (interpolatedTime == 0f) {
                    if (animType == AnimationType.Expand)
                        innerView?.visibility = View.VISIBLE
                }

                val height = when(animType) {
                    AnimationType.Expand -> (initialHeight + distance * interpolatedTime)
                    AnimationType.Collapse -> (initialHeight - distance * interpolatedTime)
                }.toInt()

                with(binding) {
                    cardLayout.layoutParams.height = height

                    cardContainer.requestLayout()
                    cardContainer.layoutParams.height = height
                }
            }

            override fun willChangeBounds() = true
        }.apply {
            duration = ANIM_DURATION
            interpolator = cubicBezierInterpolator
        }

        startAnimation(expandAnim)
        binding.cardArrow.startAnimation(
            when(animType) {
                AnimationType.Expand -> {
                    animateState = STATE_EXPANDING
                    expandState = STATE_EXPANDED
                    arrowAnimExpand
                }
                AnimationType.Collapse -> {
                    animateState = STATE_COLLAPSING
                    expandState = STATE_COLLAPSED
                    arrowAnimCollapse
                }
            }
        )

        if (showSwitch) {
            val switchAnim = when(animType) {
                AnimationType.Expand ->
                    AlphaAnimation(0f, 1f)
                AnimationType.Collapse ->
                    AlphaAnimation(1f, 0f)
            }.apply {
                interpolator = cubicBezierInterpolator
                duration = ANIM_DURATION
                fillAfter = false
            }

            binding.cardSwitch.startAnimation(switchAnim)

            switchAnim.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(p0: Animation?) {}
                override fun onAnimationStart(p0: Animation?) {
                    binding.cardSwitch.visibility = when(animType) {
                        AnimationType.Expand -> View.INVISIBLE
                        AnimationType.Collapse -> View.VISIBLE
                    }
                }

                override fun onAnimationEnd(p0: Animation?) {
                    binding.cardSwitch.visibility = when(animType) {
                        AnimationType.Expand -> View.VISIBLE
                        AnimationType.Collapse -> View.GONE
                    }
                }
            })
        }
    }

    private fun getFullHeight(layout: ViewGroup): Int {
        val specWidth = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val specHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        layout.measure(specWidth, specHeight)
        var totalHeight = 0
        val initialVisibility = layout.visibility
        layout.visibility = View.VISIBLE
        val numberOfChildren = layout.childCount
        for (i in 0 until numberOfChildren) {
            val child = layout.getChildAt(i)

            val desiredWidth = MeasureSpec.makeMeasureSpec(layout.width,
                MeasureSpec.AT_MOST)
            child.measure(desiredWidth, MeasureSpec.UNSPECIFIED)
            child.measuredHeight
            totalHeight += child.measuredHeight
        }
        layout.visibility = initialVisibility
        return totalHeight
    }

    fun setIcon(@DrawableRes drawableRes: Int = -1, drawable: Drawable? = null, loader: ((ImageView) -> Unit)?) {
        if (drawableRes != -1) {
            iconDrawable = ContextCompat.getDrawable(context, drawableRes)
            binding.cardIcon.load(iconDrawable)
        } else if (drawable != null) {
            binding.cardIcon.load(drawable)
            iconDrawable = drawable
        } else if (loader != null) {
            loader(binding.cardIcon)
            iconDrawable = binding.cardIcon.drawable
        }
    }

    fun getIconVisibility(): Int =
        binding.cardIcon.visibility

    fun setIconVisibility(visibility: Int) {
        binding.cardIcon.visibility = visibility
    }

    fun setOnSwitchChangeListener(listener: OnSwitchChangeListener) {
        this.switchListener = listener
    }

    fun setOnSwitchChangeListener(method: (v: View?, isChecked: Boolean) -> Unit) {
        this.switchListener = object : OnSwitchChangeListener {
            override fun onSwitchChanged(v: View?, isChecked: Boolean) {
                method(v, isChecked)
            }
        }
    }

    fun setOnInnerViewInflateListener(listener: () -> Unit) {
        if (innerView != null) {
            listener()
            return
        }
        this.viewInflateListener = listener
    }

    val expanded: Boolean
        get() = expandState == STATE_EXPANDED || expandState == STATE_EXPANDING

    var title: String?
        get() = _title
        set(value) {
            binding.cardTitle.text = value
            _title = value
        }

    var switchValue: Boolean
        get() = _switchValue
        set(value) {
            binding.cardSwitch.isChecked = value
            _switchValue = value
        }

    var isSwitchEnabled: Boolean
        get() = _switchEnabled
        set(value) {
            binding.cardSwitch.isEnabled = value
            _switchValue = value
        }

    sealed class AnimationType {
        object Expand: AnimationType()
        object Collapse: AnimationType()
    }

    interface OnSwitchChangeListener {
        fun onSwitchChanged(v: View?, isChecked: Boolean)
    }
}