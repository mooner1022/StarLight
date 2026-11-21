/*
 * AccountTagParserSpec.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.specs

import android.service.notification.StatusBarNotification

/**
 * Reads the room ID out of the tag KakaoTalk 26.10.0 changed in its chat notifications.
 *
 * Since that version the tag is the receiving account's ID and the room's ID joined by `_`
 * (`423005562_18452224509561830`), where it was the room's ID alone. Rooms keep the ID they had: see
 * [ParserSpecPolicy.roomIdOf]. The room name is read as [ConversationShortcutParserSpec] reads it.
 */
class AccountTagParserSpec: ConversationShortcutParserSpec() {

    override val id: String = ParserSpecPolicy.ACCOUNT_IN_TAG

    override val name: String = "안드로이드 11 이상, 카카오톡 26.10.0 이상 메세지 분석 스펙"

    override fun roomIdOf(sbn: StatusBarNotification): String =
        ParserSpecPolicy.roomIdOf(sbn.tag)
}
