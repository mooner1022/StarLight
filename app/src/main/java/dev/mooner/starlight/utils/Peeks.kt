package dev.mooner.starlight.utils

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.peekalert.PeekAlert
import dev.mooner.peekalert.PeekAlertBuilder
import dev.mooner.peekalert.createPeekAlert
import dev.mooner.starlight.R

/*
 * The app's alerts. Every alert is the same quiet card, set apart from the page by its outline and its shadow:
 * the card's own color never says what kind of alert it is. That is said by the icon, and by how long the alert
 * stays. An alert looks the way it does because of look(), and nowhere else: a screen says which level its alert
 * has and what it says, not which colors it has.
 */

/**
 * How much of the user's attention an alert asks for.
 *
 * [INFO] and [SUCCESS] answer something the user just did, and are gone soon. [WARNING] and [ERROR] are what the
 * user shouldn't miss: they stay longer. The icon is all that has the level's color.
 */
enum class PeekLevel(
    @DrawableRes val icon: Int,
    @ColorRes val tint: Int,
    val showMillis: Long,
) {
    INFO(dev.mooner.configdsl.R.drawable.ic_round_info_24, R.color.peek_icon, 3000L),
    SUCCESS(dev.mooner.configdsl.R.drawable.ic_round_check_24, R.color.peek_success, 2500L),
    WARNING(R.drawable.ic_round_warning_24, R.color.peek_warning, 4500L),
    ERROR(dev.mooner.configdsl.R.drawable.ic_round_error_24, R.color.peek_error, 5000L),
}

val PeekType.level: PeekLevel
    get() = when (this) {
        PeekType.INFO    -> PeekLevel.INFO
        PeekType.SUCCESS -> PeekLevel.SUCCESS
        PeekType.ERROR   -> PeekLevel.ERROR
    }

private const val RADIUS_DP = 14
private const val PADDING_DP = 12
private const val ICON_DP = 20
private const val ELEVATION_DP = 6f
/** A line alone is the whole alert; under a title it is the smaller of two. */
private const val TEXT_SP = 14f
private const val TITLE_SP = 13f
private const val BODY_SP = 12f
private const val BODY_MAX_LINES = 3
private const val TEXT_MAX_LINES = 4

/**
 * Gives an alert being built the app's look for [level], with [text] and, over it, [title] if there is one. What is
 * set after this in the same builder wins, which is how an alert gets another icon, place or time to stay.
 */
fun PeekAlertBuilder.look(context: Context, level: PeekLevel, text: String, title: String? = null) {
    width = ViewGroup.LayoutParams.WRAP_CONTENT
    cornerRadius = dp(RADIUS_DP).toFloat()
    paddingDp = PADDING_DP
    iconSizeDp = ICON_DP
    elevationDp = ELEVATION_DP
    draggable = true
    autoHideMillis = level.showMillis
    backgroundColor(res = R.color.peek_surface)
    stroke(res = R.color.peek_stroke)
    iconRes = level.icon
    iconTint(res = level.tint)

    if (title == null) {
        text(text) {
            textColor(R.color.peek_text)
            textSize = TEXT_SP
            typeface = getTypeface(context, R.font.nanumsquare_round_bold)
        }
    } else {
        title(title) {
            textColor(R.color.peek_text)
            textSize = TITLE_SP
            typeface = getTypeface(context, R.font.nanumsquare_neo_extrabold)
        }
        text(text) {
            textColor(R.color.peek_text_sub)
            textSize = BODY_SP
            typeface = getTypeface(context, R.font.nanumsquare_neo_regular)
            maxLines = BODY_MAX_LINES
        }
    }
}

/**
 * Turns an alert that was made with the app's look into one of another [level], as when "compiling" becomes its
 * result. Its action, if it had one, is gone: set the new one after this.
 */
fun PeekAlert.look(
    context: Context,
    level: PeekLevel,
    text: String,
    title: String? = null,
    @DrawableRes icon: Int = level.icon,
): PeekAlert = apply {
    setAutoHide(level.showMillis)
    setIcon(icon)
    setIconTint(res = level.tint)
    setTitle(title)
    setText(text)
    setAction(text = null) {}
    if (title == null) {
        setTextColor(res = R.color.peek_text)
        setTextSize(TEXT_SP)
        getTypeface(context, R.font.nanumsquare_round_bold)?.let(::setTextTypeface)
        setTextMaxLines(TEXT_MAX_LINES)
    } else {
        setTitleColor(res = R.color.peek_text)
        setTitleSize(TITLE_SP)
        getTypeface(context, R.font.nanumsquare_neo_extrabold)?.let(::setTitleTypeface)
        setTextColor(res = R.color.peek_text_sub)
        setTextSize(BODY_SP)
        getTypeface(context, R.font.nanumsquare_neo_regular)?.let(::setTextTypeface)
        setTextMaxLines(BODY_MAX_LINES)
    }
}

/**
 * An alert with the app's look, at the top unless [builder] says otherwise. [builder] is for what this one alert
 * needs beyond its level: an icon of its own, an action, staying until it is hidden.
 */
fun Activity.createSimplePeek(
    title: String? = null,
    text: String,
    level: PeekLevel = PeekLevel.INFO,
    builder: PeekAlertBuilder.() -> Unit = {},
): PeekAlert =
    createPeekAlert(this) {
        position = PeekAlert.Position.Top
        look(this@createSimplePeek, level, text, title)
        builder()
    }

fun Fragment.createSimplePeek(
    title: String? = null,
    text: String,
    level: PeekLevel = PeekLevel.INFO,
    builder: PeekAlertBuilder.() -> Unit = {},
): PeekAlert =
    createPeekAlert(this) {
        position = PeekAlert.Position.Top
        look(requireContext(), level, text, title)
        builder()
    }

fun ViewGroup.createSimplePeek(
    title: String? = null,
    text: String,
    level: PeekLevel = PeekLevel.INFO,
    builder: PeekAlertBuilder.() -> Unit = {},
): PeekAlert =
    createPeekAlert(this) {
        position = PeekAlert.Position.Top
        look(context, level, text, title)
        builder()
    }

fun Activity.createSuccessPeek(title: String, position: PeekAlert.Position): PeekAlert =
    createSimplePeek(text = title, level = PeekLevel.SUCCESS) { this.position = position }

fun Fragment.createSuccessPeek(title: String, position: PeekAlert.Position): PeekAlert =
    createSimplePeek(text = title, level = PeekLevel.SUCCESS) { this.position = position }

fun ViewGroup.createSuccessPeek(title: String, position: PeekAlert.Position): PeekAlert =
    createSimplePeek(text = title, level = PeekLevel.SUCCESS) { this.position = position }

fun Activity.createFailurePeek(title: String, position: PeekAlert.Position): PeekAlert =
    createSimplePeek(text = title, level = PeekLevel.ERROR) { this.position = position }

fun Fragment.createFailurePeek(title: String, position: PeekAlert.Position): PeekAlert =
    createSimplePeek(text = title, level = PeekLevel.ERROR) { this.position = position }

fun ViewGroup.createFailurePeek(title: String, position: PeekAlert.Position): PeekAlert =
    createSimplePeek(text = title, level = PeekLevel.ERROR) { this.position = position }

/**
 * Shows [text] as a short alert at the top or bottom of the screen. One shown while others are up joins them: the
 * newest is in front and the earlier ones are tucked behind it, see PeekAlert.
 */
fun Context.peek(text: String, type: PeekType = PeekType.INFO, position: PeekAlert.Position = PeekAlert.Position.Bottom) =
    peek(text, type.level, position)

fun Context.peek(text: String, level: PeekLevel, position: PeekAlert.Position = PeekAlert.Position.Bottom) {
    val activity = findActivity()
        ?: return Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    if (activity.isFinishing || activity.isDestroyed)
        return
    activity.createSimplePeek(text = text, level = level) { this.position = position }.peek()
}
