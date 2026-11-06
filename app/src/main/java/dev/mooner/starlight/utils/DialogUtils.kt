package dev.mooner.starlight.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.configdsl.ConfigBuilder
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.configdsl.adapters.OnValueUpdatedListener
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.peekalert.PeekAlert
import dev.mooner.peekalert.PeekAlertBuilder
import dev.mooner.peekalert.createPeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.logging.LogCollector
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.ui.common.SheetBuilder
import dev.mooner.starlight.ui.common.showSheet
import dev.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonVisitor
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.commonmark.node.SoftLineBreak
import java.io.Reader
import java.lang.ref.WeakReference
import kotlin.properties.Delegates.notNull

typealias ConfirmCallback = (confirm: Boolean) -> Unit

fun Context.showLogsDialog() {
    val logUpdateScope = CoroutineScope(Dispatchers.Default)
    val context = this

    showSheet {
        title = getString(R.string.title_logs)
        icon = R.drawable.ic_round_article_24
        content(scrollable = false) { _, parent ->
            val showInternalLog = GlobalConfig
                .category("dev_mode_config")
                .getBoolean("show_internal_log", false)
            val logs = LogCollector.logs
            val mAdapter = LogsRecyclerViewAdapter().withData(logs)
            val recycler = RecyclerView(context).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
                itemAnimator = FadeInUpAnimator()
                layoutManager = LinearLayoutManager(context).apply {
                    reverseLayout = true
                    stackFromEnd = true
                }
                adapter = mAdapter
            }
            parent.addView(recycler, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.5f).toInt(),
            ))
            mAdapter.notifyItemRangeInserted(0, logs.size)

            EventHandler.on<Events.Log.Create>(logUpdateScope) {
                if (log.type == LogType.VERBOSE && !showInternalLog) return@on
                recycler.post {
                    mAdapter.push(log)
                    recycler.smoothScrollToPosition(mAdapter.getItems().size - 1)
                }
            }
        }
        onDismiss { logUpdateScope.cancel() }
        negative(R.string.close)
    }
}

fun showConfirmDialog(context: Context, title: String, message: String, onDismiss: ConfirmCallback? = null) {
    var isConfirmed = false
    context.showSheet {
        this.title = title
        this.message = message
        cancelOnTouchOutside = false
        positive(R.string.ok) {
            isConfirmed = true
            onDismiss?.invoke(true)
        }
        negative(R.string.cancel)
        onDismiss {
            if (!isConfirmed)
                onDismiss?.invoke(false)
        }
    }
}

fun Context.showErrorLogDialog(title: String, e: Throwable) {
    showSheet {
        this.title = title
        icon = R.drawable.ic_round_error_outline_24
        message = e.toString() + "\n" + e.stackTraceToString()
        isMessageSelectable = true
        cancelOnTouchOutside = false
        positive(R.string.close)
    }
}

fun Context.showChangelogDialog(title: String) {
    val changelog = this.assets
        .open("changes.md")
        .bufferedReader()
        .use(Reader::readText)
    val markdown = Markwon
        .builder(this)
        .usePlugin(
            object : AbstractMarkwonPlugin() {
                override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                    builder.on(SoftLineBreak::class.java) { visitor, _ ->
                        visitor.forceNewLine()
                    }
                }
            }
        )
        .build()
        .toMarkdown(changelog)

    showSheet {
        this.title = title
        message = markdown
        cancelOnTouchOutside = false
        positive(R.string.close)
    }
}

context(lifecycleOwner: LifecycleOwner)
fun SheetBuilder.configStruct(context: Context, block: DialogConfigStructBuilder.() -> Unit) {
    var adapter: ConfigAdapter? = null
    content { _, parent ->
        val recycler = RecyclerView(context).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = false
        }
        parent.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        adapter = ConfigAdapter.Builder(context) {
            bind(recycler)
            lifecycleOwner(lifecycleOwner)

            DialogConfigStructBuilder().apply(block).build(this)
        }.build()
    }
    onDismiss {
        adapter?.destroy()
        adapter = null
    }
}

class DialogConfigStructBuilder {

    private var structure: ConfigStructure by notNull()
    private var dataMap  : MutableDataMap = hashMapOf()
    private var listener : OnValueUpdatedListener = { _, _, _, _ -> }

    fun struct(structure: ConfigStructure) {
        this.structure = structure
    }

    fun struct(block: ConfigBuilder.() -> Unit) {
        structure = ConfigBuilder()
            .apply(block)
            .build(flush = true)
    }

    fun data(block: () -> MutableDataMap) {
        dataMap = block()
    }

    fun onUpdate(listener: OnValueUpdatedListener) {
        this.listener = listener
    }

    internal fun build(builder: ConfigAdapter.Builder) {
        builder.apply {
            structure { structure }
            configData(dataMap)
            onValueUpdated(listener)
        }
    }
}

fun Fragment.createSimplePeek(title: String? = null, text: String, builder: PeekAlertBuilder.() -> Unit): PeekAlert {
    return createPeekAlert(this) {
        setup(requireContext(), title, text)
        this.apply(builder)
    }
}

fun ViewGroup.createSimplePeek(title: String? = null, text: String, builder: PeekAlertBuilder.() -> Unit): PeekAlert {
    return createPeekAlert(this) {
        setup(context, title, text)
        this.apply(builder)
    }
}

fun Activity.createSimplePeek(title: String? = null, text: String, builder: PeekAlertBuilder.() -> Unit): PeekAlert {
    return createPeekAlert(this) {
        setup(this@createSimplePeek, title, text)
        this.apply(builder)
    }
}

fun Fragment.createSuccessPeek(title: String, position: PeekAlert.Position): PeekAlert {
    return createSimplePeek(text = title) {
        this.position = position
        iconRes = dev.mooner.configdsl.R.drawable.ic_round_check_24
        iconTint(res = R.color.noctis_green)
        backgroundColor(res = R.color.background_popup)
    }
}

fun ViewGroup.createSuccessPeek(title: String, position: PeekAlert.Position): PeekAlert {
    return createSimplePeek(text = title) {
        this.position = position
        iconRes = dev.mooner.configdsl.R.drawable.ic_round_check_24
        iconTint(res = R.color.noctis_green)
        backgroundColor(res = R.color.background_popup)
    }
}

fun Activity.createSuccessPeek(title: String, position: PeekAlert.Position): PeekAlert {
    return createSimplePeek(text = title) {
        this.position = position
        iconRes = dev.mooner.configdsl.R.drawable.ic_round_check_24
        iconTint(res = R.color.noctis_green)
        backgroundColor(res = R.color.background_popup)
    }
}

fun Fragment.createFailurePeek(title: String, position: PeekAlert.Position): PeekAlert {
    return createSimplePeek(text = title) {
        this.position = position
        iconRes = dev.mooner.configdsl.R.drawable.ic_round_close_24
        iconTint(res = R.color.code_error)
        backgroundColor(res = R.color.background_popup)
    }
}

fun ViewGroup.createFailurePeek(title: String, position: PeekAlert.Position): PeekAlert {
    return createSimplePeek(text = title) {
        this.position = position
        iconRes = dev.mooner.configdsl.R.drawable.ic_round_close_24
        iconTint(res = R.color.code_error)
        backgroundColor(res = R.color.background_popup)
    }
}

fun Activity.createFailurePeek(title: String, position: PeekAlert.Position): PeekAlert {
    return createSimplePeek(text = title) {
        this.position = position
        iconRes = dev.mooner.configdsl.R.drawable.ic_round_close_24
        iconTint(res = R.color.code_error)
        backgroundColor(res = R.color.background_popup)
    }
}

fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()

private var lastPeek: WeakReference<PeekAlert>? = null

fun Context.peek(text: String, type: PeekType = PeekType.INFO, position: PeekAlert.Position = PeekAlert.Position.Bottom) {
    val activity = findActivity()
        ?: return Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    if (activity.isFinishing || activity.isDestroyed)
        return
    lastPeek?.get()?.hide()
    val peek = when (type) {
        PeekType.SUCCESS -> activity.createSuccessPeek(text, position)
        PeekType.ERROR   -> activity.createFailurePeek(text, position)
        PeekType.INFO    -> activity.createSimplePeek(text = text) {
            this.position = position
            iconRes = dev.mooner.configdsl.R.drawable.ic_round_info_24
            iconTint(res = R.color.main_bright)
            backgroundColor(res = R.color.background_popup)
        }
    }
    lastPeek = WeakReference(peek)
    peek.peek()
}

private fun PeekAlertBuilder.setup(context: Context, title: String?, text: String) {
    position = PeekAlert.Position.Top
    width = ViewGroup.LayoutParams.WRAP_CONTENT
    cornerRadius = dp(14).toFloat()
    autoHideMillis = 3000L
    draggable = true
    iconTint(res = R.color.white)

    if (title == null) {
        text(text) {
            textColor(R.color.text)
            textSize = 14f
            typeface = getTypeface(context, R.font.nanumsquare_round_bold)
        }
    } else {
        //paddingDp = 17
        title(title) {
            textColor(R.color.text)
            textSize = 10f
            typeface = getTypeface(context, R.font.nanumsquare_neo_regular)
        }
        text(text) {
            textColor(R.color.text)
            textSize = 12f
            typeface = getTypeface(context, R.font.nanumsquare_neo_bold)
        }
    }
}