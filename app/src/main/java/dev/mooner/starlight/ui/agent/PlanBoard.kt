package dev.mooner.starlight.ui.agent

import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.model.Plan
import dev.mooner.starlight.databinding.ItemAgentPlanStepBinding
import dev.mooner.starlight.databinding.ViewAgentPlanBinding

/**
 * The plan the agent is working through, above the composer: what is done, being done and left, changing as the agent
 * calls update_plan. It shows while steps are left or the run goes on, and folds to one line on a tap.
 */
internal class PlanBoard(private val binding: ViewAgentPlanBinding, onGo: () -> Unit) {

    private val context: Context
        get() = binding.root.context

    private var shown: Plan? = null

    /** Folded by the user, for the conversation on screen. */
    var collapsed = false
        set(value) {
            field = value
            update(animate = true)
        }

    /** The keyboard is up: the list folds away to leave the chat room, unless the user opens it. */
    var imeShown = false
        set(value) {
            if (field == value)
                return
            field = value
            peeking = false
            update(animate = false)
        }

    /** The user opened the list while typing. */
    private var peeking = false

    private val expanded: Boolean
        get() = if (imeShown) peeking else !collapsed

    init {
        binding.planHeader.setOnClickListener {
            if (imeShown) {
                peeking = !peeking
                update(animate = true)
            } else {
                collapsed = !collapsed
            }
        }
        binding.planScroll.maxHeight = AgentUi.dp(context, MAX_LIST_DP)
        binding.planGo.setOnClickListener { onGo() }
    }

    /**
     * Shows [plan], the conversation's latest, while it has steps left or [running]. In plan mode, [canGo] offers to
     * carry it out.
     */
    fun show(plan: Plan?, running: Boolean, canGo: Boolean = false) {
        val visible = plan != null && (running || !plan.isFinished)
        binding.root.isVisible = visible
        binding.planGo.isVisible = visible && canGo && !running
        if (plan == null || plan == shown)
            return
        val animate = shown != null && visible
        shown = plan
        bind(plan, animate)
    }

    /** Forgets what was shown, for another conversation. */
    fun reset() {
        shown = null
        peeking = false
        collapsed = false
        binding.root.isVisible = false
    }

    private fun bind(plan: Plan, animate: Boolean) {
        if (animate)
            TransitionManager.beginDelayedTransition(binding.root, AutoTransition().setDuration(TRANSITION_MILLIS))
        val total = plan.steps.size
        binding.planCount.text = context.getString(R.string.agent_plan_count, plan.done, total)
        binding.planCount.contentDescription = context.getString(R.string.agent_plan_progress, plan.done, total)
        binding.planProgress.max = total
        binding.planProgress.setProgressCompat(plan.done, animate)

        val rows = binding.planSteps
        while (rows.childCount > total)
            rows.removeViewAt(rows.childCount - 1)
        val inflater = LayoutInflater.from(context)
        while (rows.childCount < total)
            ItemAgentPlanStepBinding.inflate(inflater, rows, true)
        plan.steps.forEachIndexed { index, step ->
            bindStep(ItemAgentPlanStepBinding.bind(rows.getChildAt(index)), step)
        }
        update(animate = false)
        // A long plan scrolls; keep the step being worked on in view.
        val current = plan.steps.indexOfFirst { it.status == Plan.Status.IN_PROGRESS }
        if (current >= 0)
            binding.planScroll.post {
                val row = rows.getChildAt(current) ?: return@post
                binding.planScroll.smoothScrollTo(0, (row.top - row.height).coerceAtLeast(0))
            }
    }

    private fun bindStep(row: ItemAgentPlanStepBinding, step: Plan.Step) {
        val (icon, tint, text, font, description) = when (step.status) {
            Plan.Status.COMPLETED   -> Style(R.drawable.ic_agent_check, R.color.agent_ok, R.color.agent_text_faint,
                R.font.wantedsans_medium, R.string.agent_plan_step_done)
            Plan.Status.IN_PROGRESS -> Style(R.drawable.ic_agent_step_doing, R.color.agent_gold, R.color.agent_text,
                R.font.wantedsans_bold, R.string.agent_plan_step_doing)
            Plan.Status.PENDING     -> Style(R.drawable.ic_agent_step_todo, R.color.agent_text_faint, R.color.agent_text_muted,
                R.font.wantedsans_medium, R.string.agent_plan_step_todo)
        }
        row.mark.setImageResource(icon)
        row.mark.setColorFilter(context.getColor(tint))
        row.text.text = step.text
        row.text.setTextColor(context.getColor(text))
        row.text.typeface = ResourcesCompat.getFont(context, font)
        row.text.paintFlags = if (step.status == Plan.Status.COMPLETED)
            row.text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else
            row.text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        row.root.contentDescription = context.getString(description, step.text)
    }

    private fun update(animate: Boolean) {
        val plan = shown ?: return
        if (animate && binding.root.isVisible)
            TransitionManager.beginDelayedTransition(binding.root.parent as? ViewGroup ?: binding.root,
                AutoTransition().setDuration(TRANSITION_MILLIS))
        val open = expanded
        binding.planScroll.isVisible = open
        // Folded, the header says what is going on now.
        val now = plan.current ?: plan.steps.firstOrNull { it.status == Plan.Status.PENDING }
        binding.planCurrent.text = if (open) "" else now?.text.orEmpty()
        // The board opens upward from the composer: folded points up to open it, open points down to fold it.
        binding.planChevron.rotation = if (open) 0f else 180f
    }

    private data class Style(val icon: Int, val tint: Int, val text: Int, val font: Int, val description: Int)

    private companion object {
        const val MAX_LIST_DP = 168
        const val TRANSITION_MILLIS = 180L
    }
}

/** The board's step list, which scrolls once it is taller than [maxHeight] so a long plan leaves room for the chat. */
class PlanStepsScroll @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : NestedScrollView(context, attrs) {

    var maxHeight = Int.MAX_VALUE
        set(value) {
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(heightMeasureSpec)
        val limit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) maxHeight else minOf(size, maxHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
    }
}
