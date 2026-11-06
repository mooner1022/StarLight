/*
 * DebugRoomChatAdapter.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.debugroom

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import coil.transform.RoundedCornersTransformation
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.model.Card
import dev.mooner.starlight.agent.model.CardKind
import dev.mooner.starlight.ui.agent.FilePreview
import dev.mooner.starlight.ui.common.showSheet
import dev.mooner.starlight.ui.debugroom.models.DebugRoomMessage
import dev.mooner.starlight.utils.peek
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/**
 * The debug room's chat, laid out like KakaoTalk's: the user's bubbles on the end side, the bot's under its profile
 * and name, a run of bubbles one sender sent in a minute sharing one time, and the date over each day's first message.
 */
class DebugRoomChatAdapter(
    parent: Activity,
    private val dir: File,
    private val chatList: MutableList<DebugRoomMessage>,
    style: Style,
) : RecyclerView.Adapter<DebugRoomChatAdapter.ViewHolder>() {

    companion object {
        // Saved with each message as its `v`, so these keep their numbers.
        const val CHAT_SELF = 0
        const val CHAT_BOT = 1
        const val CHAT_SELF_LONG = 2
        const val CHAT_BOT_LONG = 3
        /** A file the bot sent; [DebugRoomMessage.fileName] is its copy under `chats/`. */
        const val CHAT_BOT_MEDIA = 4

        private const val LAYOUT_SELF = 0
        private const val LAYOUT_BOT = 1

        private val SELF_TYPES = arrayOf(CHAT_SELF, CHAT_SELF_LONG)
        private val LONG_TYPES = arrayOf(CHAT_SELF_LONG, CHAT_BOT_LONG)

        private const val MINUTE_MILLIS = 60_000L

        val DebugRoomMessage.isSelf: Boolean
            get() = viewType in SELF_TYPES
    }

    /**
     * Bubble colors and the bot's profile. A color the user picked is used as it is, with dark or light text,
     * whichever reads better on it; otherwise the theme's.
     */
    class Style(
        val selfBubble   : Int,
        val onSelfBubble : Int,
        val botBubble    : Int,
        val onBotBubble  : Int,
        val botProfile   : File?,
    ) {
        companion object {

            fun of(context: Context, selfColor: Int?, botColor: Int?, botProfilePath: String?): Style {
                val behind = ContextCompat.getColor(context, R.color.debugroom_chat_bg)
                val self = selfColor ?: ContextCompat.getColor(context, R.color.debugroom_bubble_self)
                val bot = botColor ?: ContextCompat.getColor(context, R.color.debugroom_bubble_bot)
                return Style(
                    selfBubble   = self,
                    onSelfBubble = textColorOn(context, self, behind),
                    botBubble    = bot,
                    onBotBubble  = textColorOn(context, bot, behind),
                    botProfile   = botProfilePath?.let(::File)?.takeIf(File::isFile),
                )
            }

            private fun textColorOn(context: Context, color: Int, behind: Int): Int {
                // A picked color may be translucent; what shows is it over the chat.
                val shown = ColorUtils.compositeColors(color, behind)
                val dark = ContextCompat.getColor(context, R.color.debugroom_text_dark)
                val light = ContextCompat.getColor(context, R.color.debugroom_text_light)
                return if (ColorUtils.calculateContrast(dark, shown) >= ColorUtils.calculateContrast(light, shown))
                    dark
                else
                    light
            }
        }
    }

    var style: Style = style
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    private val context = parent as Context

    private val clipboard: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val density = context.resources.displayMetrics.density
    private val bubbleRadius = 16 * density
    private val tailRadius = 4 * density
    private val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Positions of messages that just arrived, which enter once when first bound. */
    private val entering: MutableSet<Int> = hashSetOf()

    /**
     * Gives the message at [position] a short entrance when it's bound, instead of the list's item animations,
     * which hold a new message back until the others have moved and a reply right after would lag behind.
     */
    fun markEntering(position: Int) {
        entering += position
    }

    override fun getItemCount(): Int = chatList.size

    override fun getItemViewType(position: Int): Int =
        if (chatList[position].isSelf) LAYOUT_SELF else LAYOUT_BOT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == LAYOUT_SELF) R.layout.item_debug_room_self else R.layout.item_debug_room_bot
        return ViewHolder(LayoutInflater.from(context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = chatList[position]
        enter(holder.itemView, entering.remove(position))
        val previous = chatList.getOrNull(position - 1)
        val next = chatList.getOrNull(position + 1)
        val startsRun = !inSameRun(previous, message)
        val endsRun = !inSameRun(message, next)

        val startsDay = previous == null || !onSameDay(previous.timestamp, message.timestamp)
        holder.date.isVisible = startsDay
        if (startsDay)
            holder.date.text = DateUtils.formatDateTime(context, message.timestamp,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_YEAR)
        holder.row.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = (if (startsRun && !startsDay) 12 * density else 4 * density).toInt()
        }

        holder.time.isVisible = endsRun
        holder.time.text = timeFormat.format(Date(message.timestamp))

        if (!message.isSelf)
            bindHeader(holder, message, startsRun)

        val isSelf = message.isSelf
        val bubbleColor = if (isSelf) style.selfBubble else style.botBubble
        val textColor = if (isSelf) style.onSelfBubble else style.onBotBubble
        holder.bubble.background = bubbleBackground(bubbleColor, isSelf, tail = startsRun)
        holder.message.setTextColor(textColor)
        holder.message.setOnClickListener(null)
        holder.message.isClickable = false

        if (message.viewType == CHAT_BOT_MEDIA) {
            bindMedia(holder, message, textColor)
            return
        }

        holder.image.dispose()
        holder.image.isVisible = false
        holder.message.isVisible = true
        holder.message.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)

        val isLong = message.viewType in LONG_TYPES
        holder.message.text = if (isLong) message.message + "…" else message.message
        holder.footer.isVisible = isLong
        if (isLong) {
            holder.divider.setBackgroundColor(ColorUtils.setAlphaComponent(textColor, 0x33))
            holder.showAll.setTextColor(textColor)
            TextViewCompat.setCompoundDrawableTintList(holder.showAll, ColorStateList.valueOf(textColor))
            holder.showAll.setOnClickListener { showFullMessageDialog(fullText(message)) }
        }

        holder.message.setOnLongClickListener { view ->
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.project_debugroom), fullText(message)))
            view.context.peek(context.getString(R.string.debugroom_copied), PeekType.SUCCESS, PeekAlert.Position.Top)
            true
        }
    }

    private fun enter(view: View, isNew: Boolean) {
        view.animate().cancel()
        if (!isNew) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = 6 * density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(140)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** The profile and name over the first of a run; later ones keep the profile's space empty. */
    private fun bindHeader(holder: ViewHolder, message: DebugRoomMessage, startsRun: Boolean) {
        val profile = holder.profile!!
        val sender = holder.sender!!
        sender.isVisible = startsRun
        profile.isVisible = startsRun
        if (!startsRun)
            return
        sender.text = message.sender

        val corners = RoundedCornersTransformation(context.resources.getDimension(R.dimen.debugroom_profile_corner_radius))
        val custom = style.botProfile
        if (custom != null) {
            profile.colorFilter = null
            profile.load(custom) { transformations(corners) }
        } else {
            profile.setColorFilter(ContextCompat.getColor(context, R.color.main_bright))
            profile.load(R.drawable.default_profile)
        }
    }

    /**
     * An image is shown as it is, without a bubble; any other file as its name and size. Tapped, images, Markdown
     * and text open in a preview sheet as in the AI tab; other files in another app.
     */
    private fun bindMedia(holder: ViewHolder, message: DebugRoomMessage, textColor: Int) {
        holder.footer.isVisible = false
        holder.message.setOnLongClickListener(null)

        val file = message.fileName?.let { dir.resolve("chats").resolve(it) }?.takeIf(File::isFile)
        val isImage = file != null && message.mimeType?.startsWith("image/") == true
        holder.image.isVisible = isImage
        holder.message.isVisible = !isImage
        if (!isImage)
            holder.image.dispose()
        if (isImage) {
            holder.bubble.background = null
            holder.image.load(file) {
                transformations(RoundedCornersTransformation(bubbleRadius))
            }
            holder.image.contentDescription = message.message
        } else {
            holder.message.text = if (file == null)
                context.getString(R.string.debugroom_file_missing, message.message)
            else
                "${message.message} · ${Formatter.formatShortFileSize(context, file.length())}"
            holder.message.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_round_article_24, 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(holder.message, ColorStateList.valueOf(textColor))
        }
        val open = View.OnClickListener { if (file != null) preview(file, message) }
        holder.image.setOnClickListener(open)
        holder.message.setOnClickListener(open)
    }

    private fun bubbleBackground(color: Int, isSelf: Boolean, tail: Boolean): GradientDrawable {
        val r = bubbleRadius
        val t = if (tail) tailRadius else bubbleRadius
        // Top start, top end, bottom end, bottom start: the tail is the corner toward the sender.
        val radii = if (isSelf)
            floatArrayOf(r, r, t, t, r, r, r, r)
        else
            floatArrayOf(t, t, r, r, r, r, r, r)
        return GradientDrawable().apply {
            cornerRadii = radii
            setColor(color)
        }
    }

    /** One sender's messages in the same minute share a run, as KakaoTalk groups them. */
    private fun inSameRun(first: DebugRoomMessage?, second: DebugRoomMessage?): Boolean =
        first != null && second != null &&
            first.isSelf == second.isSelf &&
            first.sender == second.sender &&
            first.timestamp / MINUTE_MILLIS == second.timestamp / MINUTE_MILLIS &&
            onSameDay(first.timestamp, second.timestamp)

    private fun onSameDay(first: Long, second: Long): Boolean =
        Instant.ofEpochMilli(first).atZone(zone).toLocalDate() == Instant.ofEpochMilli(second).atZone(zone).toLocalDate()

    /** A long message's whole text is kept in its own file; the list holds its first 500 characters. */
    private fun fullText(message: DebugRoomMessage): String =
        message.fileName
            ?.takeIf { message.viewType in LONG_TYPES }
            ?.let { runCatching { dir.resolve("chats").resolve(it).readText() }.getOrNull() }
            ?: message.message

    private fun showFullMessageDialog(fullMessage: String) {
        context.showSheet {
            title = context.getString(R.string.title_show_all)
            message = fullMessage
            isMessageSelectable = true
            positive(R.string.close)
        }
    }

    private fun preview(file: File, messageData: DebugRoomMessage) {
        val card = Card(CardKind.FILE, name = messageData.message, size = file.length(), mimeType = messageData.mimeType)
        val activity = context as? AppCompatActivity
        if (activity != null)
            FilePreview.open(activity, card, file)
        else
            FilePreview.openWith(context, card, file)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val date     : TextView     = itemView.findViewById(R.id.date)
        val row      : View         = itemView.findViewById(R.id.row)
        val bubble   : LinearLayout = itemView.findViewById(R.id.bubble)
        val image    : ImageView    = itemView.findViewById(R.id.image)
        val message  : TextView     = itemView.findViewById(R.id.message)
        val footer   : View         = itemView.findViewById(R.id.footer)
        val divider  : View         = itemView.findViewById(R.id.divider)
        val showAll  : TextView     = itemView.findViewById(R.id.showAll)
        val time     : TextView     = itemView.findViewById(R.id.time)
        // Only in the bot's layout.
        val profile  : ImageView?   = itemView.findViewById(R.id.profile)
        val sender   : TextView?    = itemView.findViewById(R.id.sender)
    }
}
