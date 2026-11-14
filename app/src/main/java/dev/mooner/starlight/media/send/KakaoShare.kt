/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.send

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.mooner.starlight.listener.chat.OwnReads
import java.io.File
import java.lang.reflect.InvocationTargetException

/**
 * Intents that hand files to KakaoTalk's share screen and bring the user back afterwards.
 *
 * KakaoTalk may be installed twice: for the device owner and in a clone profile (Samsung's Dual Messenger, user 95).
 * Each room belongs to one of them, known from the user its notification came to. Two things stand in the way of
 * reaching the right one without a prompt:
 * - Samsung's DualAppManagerService turns any ACTION_SEND/SEND_MULTIPLE whose package or component is a dual app
 *   into a chooser of both KakaoTalks, whatever the target user. It only looks at the intent's own package and
 *   component, so KakaoTalk is named by a selector instead, which Android resolves to exactly that activity.
 * - Starting an activity in another user needs INTERACT_ACROSS_USERS, which StarLight must declare and be granted
 *   (`adb shell pm grant dev.mooner.starlight android.permission.INTERACT_ACROSS_USERS`). Without it, a room of the
 *   other KakaoTalk is sent through the chooser.
 */
object KakaoShare {

    const val KAKAO_PACKAGE = "com.kakao.talk"

    /** KakaoTalk's share receiver, used when no matching activity is found. */
    const val SHARE_ACTIVITY = "com.kakao.talk.activity.RecentExcludeIntentFilterActivity"

    /** KakaoTalk's receiver that posts a shared text to the memo chat (나와의 채팅) at once, showing nothing. */
    const val MEMO_ACTIVITY = "com.kakao.talk.activity.MemoChatConnectActivity"

    const val INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS"

    // Extras of KakaoTalk's direct share: the chat room to post into.
    private const val EXTRA_ROOM_ID = "key_id"
    private const val EXTRA_ROOM_TYPE = "key_type"
    private const val EXTRA_DIRECT_SHARE = "key_from_direct_share"

    // Extras of a chat message KakaoTalk's share screen reads beside the text; the attachment carries Markdown,
    // spoilers and mini emoticons (KakaoTextAttachment).
    private const val EXTRA_CHAT_MESSAGE = "EXTRA_CHAT_MESSAGE"
    private const val EXTRA_CHAT_MESSAGE_TYPE = "EXTRA_CHAT_MESSAGE_TYPE_VALUE"
    private const val EXTRA_CHAT_ATTACHMENT = "EXTRA_CHAT_ATTACHMENT"
    private const val MESSAGE_TYPE_TEXT = 1

    private const val TEXT_TYPE = "text/plain"
    private const val MULTIPLE_TYPE = "*/*"

    /** UIDs of user N are N * 100000 + app ID. */
    private const val PER_USER_RANGE = 100_000

    @Volatile
    private var authority: String? = null

    /** How a share reached KakaoTalk. */
    sealed interface Route {
        /** KakaoTalk of StarLight's own user, started directly. */
        data object Direct : Route

        /** KakaoTalk of another user, such as the Dual Messenger clone. */
        data class OtherUser(val userId: Int) : Route

        /** Android asked which KakaoTalk to use. */
        data class Chooser(val reason: String) : Route
    }

    /** An activity of KakaoTalk matching a share, and the user whose KakaoTalk it is. */
    data class ShareActivity(val className: String, val userId: Int)

    /** A content URI for [file] from StarLight's FileProvider; throws IllegalArgumentException if it can't serve it. */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, authority(context), file)

    /**
     * Posts [files] (URI and MIME type) to the room [channelId] through the KakaoTalk of [roomUserId] (null if not
     * known: then the only KakaoTalk, or Android asks).
     */
    fun share(context: Context, channelId: Long, files: List<Pair<Uri, String>>, roomUserId: Int?): Route {
        // KakaoTalk opens the room and reads it, taking its notification away: that isn't a deleted message.
        OwnReads.shared.note(channelId.toString(), OwnReads.SHARE_MILLIS)
        val matches = findShareActivities(context, shareIntent(channelId, files).setPackage(KAKAO_PACKAGE))
        val className = chooseShareActivity(matches.map { it.className }) ?: SHARE_ACTIVITY
        val myUserId = myUserId()
        val kakaoUsers = matches.filter { it.className == className }.map { it.userId }

        val failure = when (val userId = targetUser(roomUserId, kakaoUsers, myUserId)) {
            null     -> "the room's KakaoTalk is not known yet (no notification from it was seen)"
            myUserId -> {
                context.startActivity(directed(shareIntent(channelId, files), className))
                return Route.Direct
            }
            else     -> {
                // URIs carry StarLight's user, so the other user's KakaoTalk reads them from StarLight's provider.
                val intent = directed(shareIntent(channelId, files.map { (uri, mime) -> withUser(uri, myUserId) to mime }), className)
                startAsUser(context, intent, userId)
                    ?: return Route.OtherUser(userId)
            }
        }
        context.startActivity(shareIntent(channelId, files).setClassName(KAKAO_PACKAGE, className))
        return Route.Chooser(failure)
    }

    /**
     * Posts [text] with [attachment] (from [KakaoTextAttachment.toJson], or null) to the room [channelId] through the
     * KakaoTalk of [roomUserId], as [share] posts files: KakaoTalk opens the room and sends it.
     *
     * The share is named by KakaoTalk's share receiver itself: more than one of KakaoTalk's activities takes a shared
     * text, so naming only the package would make Android ask which.
     */
    fun shareText(context: Context, channelId: Long, text: String, attachment: String?, roomUserId: Int?): Route {
        OwnReads.shared.note(channelId.toString(), OwnReads.SHARE_MILLIS)
        val intent = { withRoom(textIntent(text, attachment), channelId) }
        val matches = findShareActivities(context, intent().setPackage(KAKAO_PACKAGE))
        val className = chooseShareActivity(matches.map { it.className }) ?: SHARE_ACTIVITY
        val myUserId = myUserId()
        val kakaoUsers = matches.filter { it.className == className }.map { it.userId }

        val failure = when (val userId = targetUser(roomUserId, kakaoUsers, myUserId)) {
            null     -> "the room's KakaoTalk is not known yet (no notification from it was seen)"
            myUserId -> {
                context.startActivity(directed(intent(), className))
                return Route.Direct
            }
            else     -> startAsUser(context, directed(intent(), className), userId)
                ?: return Route.OtherUser(userId)
        }
        context.startActivity(intent().setClassName(KAKAO_PACKAGE, className))
        return Route.Chooser(failure)
    }

    /**
     * Posts [text] with [attachment] to the memo chat (나와의 채팅) of StarLight's own user's KakaoTalk. KakaoTalk sends
     * it at once without showing a screen, and shows a toast.
     */
    fun shareTextToMemo(context: Context, text: String, attachment: String?) {
        context.startActivity(directed(textIntent(text, attachment), MEMO_ACTIVITY))
    }

    /** The intent sharing [text] as a chat message with [attachment], naming no app or room yet. */
    fun textIntent(text: String, attachment: String?): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = TEXT_TYPE
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(EXTRA_CHAT_MESSAGE, text)
            putExtra(EXTRA_CHAT_MESSAGE_TYPE, MESSAGE_TYPE_TEXT)
            if (attachment != null)
                putExtra(EXTRA_CHAT_ATTACHMENT, attachment)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    /** [intent] with KakaoTalk's direct share extras naming the chat room [channelId]. */
    private fun withRoom(intent: Intent, channelId: Long): Intent =
        intent.putExtras(Bundle().apply {
            putLong(EXTRA_ROOM_ID, channelId)
            putInt(EXTRA_ROOM_TYPE, 1)
            putBoolean(EXTRA_DIRECT_SHARE, true)
        })

    /**
     * The user to send through: the room's, else the only one with KakaoTalk (StarLight's own if none was found),
     * or null if KakaoTalk is in several users and the room's is unknown.
     */
    fun targetUser(roomUserId: Int?, kakaoUsers: Collection<Int>, myUserId: Int): Int? {
        if (roomUserId != null)
            return roomUserId
        val users = kakaoUsers.distinct()
        return when (users.size) {
            0    -> myUserId
            1    -> users.single()
            else -> null
        }
    }

    /**
     * The share receiver among KakaoTalk's activities matching a share: [SHARE_ACTIVITY] if it is one, else one that
     * isn't the memo chat (which also takes text), else the first.
     */
    fun chooseShareActivity(classNames: List<String>): String? {
        val distinct = classNames.distinct()
        return distinct.firstOrNull { it == SHARE_ACTIVITY }
            ?: distinct.firstOrNull { !it.contains("Memo", ignoreCase = true) }
            ?: distinct.firstOrNull()
    }

    /**
     * The intent posting [files] to the room [channelId], naming no app yet.
     *
     * A single file goes as ACTION_SEND with its type; several, or a text file (which KakaoTalk ignores otherwise),
     * as ACTION_SEND_MULTIPLE with the wildcard type.
     */
    fun shareIntent(channelId: Long, files: List<Pair<Uri, String>>): Intent {
        require(files.isNotEmpty()) { "No files to share" }
        val uris = files.map { it.first }
        val (firstUri, firstMime) = files.first()
        val intent = if (files.size == 1 && !firstMime.startsWith("text/")) {
            Intent(Intent.ACTION_SEND).apply {
                type = firstMime
                putExtra(Intent.EXTRA_STREAM, firstUri)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = MULTIPLE_TYPE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        // The read grant covers the clip's URIs.
        intent.clipData = ClipData.newRawUri(null, firstUri).apply {
            for (uri in uris.drop(1))
                addItem(ClipData.Item(uri))
        }
        intent.putExtras(Bundle().apply {
            putLong(EXTRA_ROOM_ID, channelId)
            putInt(EXTRA_ROOM_TYPE, 1)
            putBoolean(EXTRA_DIRECT_SHARE, true)
        })
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        return intent
    }

    /** Whether StarLight may start activities in other users, as for the Dual Messenger KakaoTalk. */
    fun canReachOtherUsers(context: Context): Boolean =
        context.checkSelfPermission(INTERACT_ACROSS_USERS) == PackageManager.PERMISSION_GRANTED

    fun myUserId(): Int =
        Process.myUid() / PER_USER_RANGE

    /** Opens the launcher of [packageName], or the home screen if it is null or can't be opened. */
    fun leaveKakao(context: Context, packageName: String?) {
        if (!packageName.isNullOrBlank()) {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            try {
                context.startActivity(launch)
                return
            } catch (_: Exception) {
                // Not installed or no launcher: go home instead.
            }
        }
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    fun isKakaoInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(KAKAO_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    /**
     * Whether StarLight may draw over other apps. Without it, Android blocks starting KakaoTalk while StarLight is
     * in the background.
     */
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun openOverlaySettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** KakaoTalk's activities matching [intent], in every user whose KakaoTalk Android reports. */
    private fun findShareActivities(context: Context, intent: Intent): List<ShareActivity> {
        val matches = try {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Exception) {
            emptyList()
        }
        return matches.mapNotNull { it.activityInfo }
            .filter { it.packageName == KAKAO_PACKAGE }
            .map { ShareActivity(it.name, it.applicationInfo.uid / PER_USER_RANGE) }
    }

    /** [intent] resolving to [className] of KakaoTalk only, through a selector Samsung's dual app check ignores. */
    private fun directed(intent: Intent, className: String): Intent =
        intent.apply { selector = Intent().setClassName(KAKAO_PACKAGE, className) }

    /** Starts [intent] in [userId]; returns why it couldn't, or null once started. */
    private fun startAsUser(context: Context, intent: Intent, userId: Int): String? {
        if (!canReachOtherUsers(context))
            return "StarLight is not granted $INTERACT_ACROSS_USERS, needed to open the KakaoTalk of user $userId"
        val method = try {
            Context::class.java.getMethod("startActivityAsUser", Intent::class.java, UserHandle::class.java)
        } catch (e: ReflectiveOperationException) {
            return "Android doesn't let StarLight call startActivityAsUser: $e"
        }
        return try {
            method.invoke(context, intent, UserHandle.getUserHandleForUid(userId * PER_USER_RANGE))
            null
        } catch (e: InvocationTargetException) {
            val cause = e.targetException
            if (cause is SecurityException) "Opening the KakaoTalk of user $userId was refused: ${cause.message}" else throw cause
        } catch (e: ReflectiveOperationException) {
            "Android doesn't let StarLight call startActivityAsUser: $e"
        }
    }

    /** [uri] naming the user whose provider serves it, as `content://0@authority/...`. */
    private fun withUser(uri: Uri, userId: Int): Uri {
        val authority = uri.encodedAuthority ?: return uri
        if ('@' in authority)
            return uri
        return uri.buildUpon().encodedAuthority("$userId@$authority").build()
    }

    /** The authority of the FileProvider StarLight declares, read from its manifest. */
    private fun authority(context: Context): String =
        authority ?: findAuthority(context).also { authority = it }

    private fun findAuthority(context: Context): String {
        val providers = try {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers
        } catch (_: Exception) {
            null
        }
        return providers
            ?.firstOrNull { it.name == FileProvider::class.java.name }
            ?.authority
            ?.split(';')
            ?.firstOrNull()
            ?: "${context.packageName}.provider"
    }
}
