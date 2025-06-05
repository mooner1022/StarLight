/*
 * CheckUpdateActivity.kt created by Minki Moon(mooner1022) on 8/10/24, 7:48 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.database.getLongOrNull
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.button
import dev.mooner.configdsl.options.spinner
import dev.mooner.configdsl.options.toggle
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.TimeUtils
import dev.mooner.starlight.plugincore.utils.onSaveConfigAdapter
import dev.mooner.starlight.ui.config.ConfigActivity
import dev.mooner.starlight.utils.*
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonVisitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.commonmark.node.SoftLineBreak
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun Context.startCheckUpdateActivity() {
    startConfigActivity(
        title = "설정",
        subTitle = "업데이트 확인",
        saved = GlobalConfig.getMutableData(),
        onValueUpdated = GlobalConfig::onSaveConfigAdapter,
        structBlock = {
            config {
                category {
                    id = "updates"
                    items {
                        val lastUpdateDate = getSharedPreferences("general", 0)
                            .getLong("lastUpdateCheck", 0)
                            .let {
                                if (it == 0L)
                                    "없음"
                                else
                                    TimeUtils.formatMillis(it, "yyyy년 MM월 dd일 HH:mm")
                            }
                        button {
                            id = "check_update"
                            title = "업데이트 확인"
                            description = "마지막 확인: $lastUpdateDate"
                            icon = Icon.CLOUD_DOWNLOAD
                            iconTintColor = color("#748DA6")
                            setOnClickListener { _ ->
                                checkUpdate()
                            }
                        }
                    }
                }
                category {
                    id = "update_config"
                    items {
                        toggle {
                            id = "auto_check"
                            title = "업데이트 자동 확인"
                            icon = Icon.REFRESH
                            iconTintColor = color("#9CB4CC")
                            defaultValue = true
                        }
                        spinner {
                            id = "channel"
                            title = "업데이트 확인 채널"
                            description = """
                            최신 버전을 확인할 채널을 설정합니다.
                            STABLE - 안정 채널
                            BETA - 베타 채널
                            SNAPSHOT - 개발 채널 (불안정)
                        """.trimIndent()
                            icon = Icon.BRANCH
                            iconTintColor = color("#F2D7D9")
                            items = VersionChecker.Channel.entries.map(VersionChecker.Channel::name)
                            defaultIndex = VersionChecker.Channel.STABLE.ordinal
                        }
                        button {
                            val versionName = getPackageInfo().versionName
                            id = "show_changelog"
                            title = "현재 버전 변경 사항"
                            description = "$versionName 변경 사항"
                            icon = Icon.LIST_BULLETED
                            iconTintColor = color("#C1AEFC")
                            setOnClickListener { _ ->
                                showChangelogDialog("✦ $versionName 변경 사항")
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun ConfigActivity.checkUpdate() {
    createSimplePeek(
        text = translate {
            Locale.ENGLISH { "Plz wait for a sec..." }
            Locale.KOREAN  { "서버를 열심히 뒤져보는 중..." }
        }
    ) {
        position = PeekAlert.Position.Bottom
        iconRes = dev.mooner.configdsl.R.drawable.ic_round_cloud_24
        iconTint(res = R.color.main_bright)
        backgroundColor(res = R.color.background_popup)
    }.peek()

    val checker = VersionChecker()
    lifecycleScope.launch {
        val channel = GlobalConfig
            .category("update_config")
            .getInt("channel", VersionChecker.Channel.STABLE.ordinal)
            .let(VersionChecker.Channel.entries::get)

        val version = withContext(Dispatchers.IO) {
            checker.fetchVersion(channel)
        }
        LoggerFactory.logger("UpdateChecker").debug { "checkUpdate channel: $channel, version: $version" }

        if (version == null) {
            createSimplePeek(
                text = translate {
                    Locale.ENGLISH { "Failed to parse or fetch version info from server. Please try again later." }
                    Locale.KOREAN  { "서버로부터 버전 정보를 불러오는 데 실패했어요. 나중에 다시 시도해주세요." }
                }
            ) {
                position = PeekAlert.Position.Bottom
                iconRes = dev.mooner.configdsl.R.drawable.ic_round_close_24
                iconTint(res = R.color.code_error)
                backgroundColor(res = R.color.background_popup)
            }.peek()
            return@launch
        }
        getSharedPreferences("general", 0).edit {
            putLong("lastUpdateCheck", System.currentTimeMillis())
        }

        val versionCode = getAppVersionCode()
        if (version.versionCode <= versionCode) {
            createSimplePeek(
                text = translate {
                    Locale.ENGLISH { "App is already on the newest version." }
                    Locale.KOREAN  { "앱이 이미 최신 버전이에요." }
                }
            ) {
                position = PeekAlert.Position.Bottom
                iconRes = dev.mooner.configdsl.R.drawable.ic_round_check_24
                iconTint(res = R.color.noctis_green)
                backgroundColor(res = R.color.background_popup)
            }.peek()
            return@launch
        }

        val pInfo = getPackageInfo()
        val changeLog = checker.fetchChangeLog(version)

        MaterialDialog(this@checkUpdate, BottomSheet(LayoutMode.WRAP_CONTENT)).noAutoDismiss().show {
            setCommonAttrs()
            cancelOnTouchOutside(false)
            title(text = "새로운 버전 확인 (*˙˘˙*)!")
            val markdown = Markwon
                .builder(context)
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
                .toMarkdown(
                    """
                    |현재 설치된 버전보다 새로운 버전이 있어요:
                    |${pInfo.versionName} >> ${version.version}
                    |새 버전을 다운로드 할까요?
                    |
                    |
                    |${changeLog ?: "(변경사항을 불러오지 못했어요.)"}
                    """.trimMargin()
                )
            message(text = markdown)
            positiveButton(res = R.string.ok) {
                val dest = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
                    .resolve("sl_update")
                    .also(File::mkdirs)
                    .resolve("sl_${version.version}_${version.versionCode}.apk")
                if (dest.exists())
                    dest.delete()

                val alert = createSimplePeek(
                    text = "다운로드중..."
                ) {
                    position = PeekAlert.Position.Bottom
                    iconRes = dev.mooner.configdsl.R.drawable.ic_round_download_24
                    iconTint(res = R.color.main_bright)
                    backgroundColor(res = R.color.background_popup)
                    autoHideMillis = null
                }.also(PeekAlert::peek)

                downloadFileFromURL(this@checkUpdate, version.downloadUrl, dest)
                    .onEach { (status, progress) ->
                        println("$status : ${progress}%")
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                alert.apply {
                                    setIcon(dev.mooner.configdsl.R.drawable.ic_round_check_24)
                                    setIconTint(R.color.noctis_green)
                                    setText("파일을 성공적으로 다운로드 했어요! (뿌듯)")
                                    setAutoHide(3000L)
                                }.peek()
                                val destUri = FileProvider.getUriForFile(this@checkUpdate, "dev.mooner.starlight.provider", dest)

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    if (!packageManager.canRequestPackageInstalls()) {
                                        Toast.makeText(this@checkUpdate, "먼저 앱 설치 권한을 허용해 주세요.", Toast.LENGTH_LONG).show()
                                        requestAppInstallPermission()
                                    }
                                }
                                requestInstall(this@checkUpdate, destUri)
                            }
                            DownloadManager.STATUS_FAILED -> {
                                alert.apply {
                                    setIcon(dev.mooner.configdsl.R.drawable.ic_round_check_24)
                                    setIconTint(R.color.noctis_green)
                                    setText("파일 다운로드에 실패했어요.")
                                    setAutoHide(3000L)
                                }.peek()
                            }
                        }
                    }
                    .launchIn(lifecycleScope)
                dismiss()
            }
            negativeButton(res = R.string.cancel) {
                dismiss()
            }
        }
    }
}

context(activity: AppCompatActivity)
@RequiresApi(Build.VERSION_CODES.O)
internal suspend fun requestAppInstallPermission() {
    suspendCoroutine { cont ->
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData("package:${activity.packageName}".toUri())
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            cont.resume(result.resultCode == Activity.RESULT_OK)
        }.launch(intent)
    }
}

private fun downloadFileFromURL(context: Activity, url: String, dest: File): StateFlow<Pair<Int, Int>> {
    val scope = CoroutineScope(Dispatchers.Default)
    val flow = MutableStateFlow(0 to -1)

    val downloadManager = context.getSystemService(Activity.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(url.toUri())
        .setDestinationUri(Uri.fromFile(dest))
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        .setTitle("새 버전 다운로드")
        .setDescription("열심히 내려받는 중...")

    val downloadId = downloadManager.enqueue(request)
    scope.launch {
        var broken = false
        while (!broken) {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (columnIdx < 0)
                        return@use
                    when(val status = cursor.getInt(columnIdx)) {
                        DownloadManager.STATUS_SUCCESSFUL,
                        DownloadManager.STATUS_FAILED -> {
                            flow.emit(status to -1)
                            broken = true
                            return@use
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            val totalBytes = cursor.getLongOrNull(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                                ?: return@use
                            println("totalBytes: ${totalBytes / 1000}kb")
                            if (totalBytes < 0)
                                return@use
                            val downloaded = cursor.getLongOrNull(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                ?: return@use
                            println("downloaded: ${downloaded / 1000}kb")
                            flow.emit(status to (downloaded * 100 / totalBytes).toInt())
                        }
                    }
                }
            }
            delay(100L)
        }
    }

    return flow
}

private fun requestInstall(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.setDataAndType(
        uri, "application/vnd.android.package-archive"
    )
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(intent)
    //startActivity(context, intent, null)
}