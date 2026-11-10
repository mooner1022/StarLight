/*
 * ConversationShortcutParserSpec.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.specs

import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/**
 * Reads the room name KakaoTalk 26.7.0 took out of its chat notifications.
 *
 * Since that version a chat notification is a bare messaging style: no sub text, no conversation title,
 * only the sender. Nothing in the notification names the room. The name is left on the conversation shortcut
 * KakaoTalk publishes for the room, which the system keeps in the notification's ranking: see
 * [ConversationShortcuts] for how it is read.
 */
class ConversationShortcutParserSpec: AndroidRParserSpec() {

    override val id: String = ParserSpecPolicy.CONVERSATION_SHORTCUT

    override val name: String = "안드로이드 11 이상, 카카오톡 26.7.0 이상 메세지 분석 스펙"

    override fun resolveRoom(
        sbn       : StatusBarNotification,
        extras    : Bundle,
        senderName: String,
        rankingMap: NotificationListenerService.RankingMap?
    ): Pair<String, Boolean> {
        val shortcut = rankingMap?.let { ConversationShortcuts.of(it, sbn.key) }

        // The fields older versions fill stay in the list, for when the version could not be told.
        val room = ParserSpecPolicy.pickRoomName(
            senderName,
            extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT),
            shortcut?.longLabel,
            shortcut?.shortLabel,
            extras.getCharSequence(NotificationCompat.EXTRA_CONVERSATION_TITLE),
            extras.getCharSequence(NotificationCompat.EXTRA_SUMMARY_TEXT),
        )
        // A group chat named after the one who sent the message is told apart by KakaoTalk's own mark.
        val isGroupChat = room != senderName
                || extras.getBoolean(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION)

        return room to isGroupChat
    }
}
