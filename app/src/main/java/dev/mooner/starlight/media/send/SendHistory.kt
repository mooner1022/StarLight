/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.send

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/** Recent sends, for the settings screen. */
class SendHistory(private val capacity: Int = 30) {

    data class Record(
        val time: Long,
        val project: String,
        val room: String,
        val files: Int,
        val failed: Int,
        val durationMillis: Long,
        /** Why nothing was sent; null if the files went to KakaoTalk. */
        val error: String?,
        /** How KakaoTalk was reached if not directly: another user's, or through Android's chooser. */
        val via: String? = null,
    )

    private val records = ArrayDeque<Record>()
    private val mVersion = MutableStateFlow(0L)

    val version: StateFlow<Long> = mVersion.asStateFlow()

    @Synchronized
    fun record(record: Record) {
        records.addFirst(record)
        while (records.size > capacity)
            records.removeLast()
        mVersion.value++
    }

    @Synchronized
    fun snapshot(): List<Record> =
        records.toList()
}
