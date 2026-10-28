package dev.mooner.starlight.ui.settings.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.database.getLongOrNull
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.utils.VersionChecker
import dev.mooner.starlight.utils.createSimplePeek
import dev.mooner.starlight.utils.getAppVersionCode
import dev.mooner.starlight.utils.getPackageInfo
import dev.mooner.starlight.utils.setCommonAttrsWithLifecycle
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonVisitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.commonmark.node.SoftLineBreak
import java.io.File
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

private val LOG = LoggerFactory.logger("UpdateChecker")

private const val CA_UPDATE_CONFIG = "update_config"

sealed interface UpdateCheckResult {
    data class Available(val version: VersionChecker.VersionInfo, val changelog: String?): UpdateCheckResult
    data object UpToDate: UpdateCheckResult
    data object Failed: UpdateCheckResult
}

val isAutoUpdateCheckEnabled: Boolean
    get() = GlobalConfig.category(CA_UPDATE_CONFIG).getBoolean("auto_check", true)

private val updateChannel: VersionChecker.Channel
    get() = GlobalConfig.category(CA_UPDATE_CONFIG)
        .getInt("channel", VersionChecker.Channel.STABLE.ordinal)
        .let { VersionChecker.Channel.entries.getOrElse(it) { VersionChecker.Channel.STABLE } }

fun Context.lastUpdateCheckMillis(): Long =
    getSharedPreferences("general", 0).getLong("lastUpdateCheck", 0)

suspend fun Context.checkForUpdate(): UpdateCheckResult =
    withContext(Dispatchers.IO) {
        val checker = VersionChecker()
        val channel = updateChannel
        val version = checker.fetchVersion(channel)
        LOG.debug { "checkUpdate channel: $channel, version: $version" }
        if (version == null)
            return@withContext UpdateCheckResult.Failed

        getSharedPreferences("general", 0).edit {
            putLong("lastUpdateCheck", System.currentTimeMillis())
        }
        if (version.versionCode <= getAppVersionCode())
            UpdateCheckResult.UpToDate
        else
            UpdateCheckResult.Available(version, checker.fetchChangeLog(version))
    }

class AppUpdater(private val activity: ComponentActivity) {

    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onPermissionResult?.invoke(activity.packageManager.canRequestPackageInstalls())
        onPermissionResult = null
    }

    fun showUpdateDialog(update: UpdateCheckResult.Available) {
        val version = update.version
        val markdown = Markwon
            .builder(activity)
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
                |${activity.getPackageInfo().versionName} >> ${version.version}
                |새 버전을 다운로드 할까요?
                |
                |
                |${update.changelog ?: "(변경사항을 불러오지 못했어요.)"}
                """.trimMargin()
            )

        MaterialDialog(activity, BottomSheet(LayoutMode.WRAP_CONTENT)).noAutoDismiss().show {
            with(activity) { setCommonAttrsWithLifecycle() }
            cancelOnTouchOutside(false)
            title(text = "새로운 버전 확인 (*˙˘˙*)!")
            message(text = markdown)
            positiveButton(res = R.string.ok) {
                download(version)
                dismiss()
            }
            negativeButton(res = R.string.cancel) {
                dismiss()
            }
        }
    }

    private fun download(version: VersionChecker.VersionInfo) {
        val dest = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
            .resolve("sl_update")
            .also(File::mkdirs)
            .resolve("sl_${version.version}_${version.versionCode}.apk")
            .also { if (it.exists()) it.delete() }

        val alert = activity.createSimplePeek(text = "다운로드중...") {
            position = PeekAlert.Position.Bottom
            iconRes = dev.mooner.configdsl.R.drawable.ic_round_download_24
            iconTint(res = R.color.main_bright)
            backgroundColor(res = R.color.background_popup)
            autoHideMillis = null
        }.also { it.peekAboveBars() }

        downloadFile(version.downloadUrl, dest)
            .onEach { status ->
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        alert.apply {
                            setIcon(dev.mooner.configdsl.R.drawable.ic_round_check_24)
                            setIconTint(R.color.noctis_green)
                            setText("파일을 성공적으로 다운로드 했어요! (뿌듯)")
                            setAutoHide(3000L)
                        }.peekAboveBars()
                        if (!ensureInstallPermission()) {
                            Toast.makeText(activity, "앱 설치 권한이 없어 업데이트를 설치할 수 없어요.", Toast.LENGTH_LONG).show()
                            return@onEach
                        }
                        requestInstall(FileProvider.getUriForFile(activity, "dev.mooner.starlight.provider", dest))
                    }
                    DownloadManager.STATUS_FAILED -> {
                        alert.apply {
                            setIcon(dev.mooner.configdsl.R.drawable.ic_round_close_24)
                            setIconTint(R.color.code_error)
                            setText("파일 다운로드에 실패했어요.")
                            setAutoHide(3000L)
                        }.peekAboveBars()
                    }
                }
            }
            .launchIn(activity.lifecycleScope)
    }

    private fun PeekAlert.peekAboveBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            activity.window.decorView.rootWindowInsets?.let { insets ->
                setMargin(vertical = insets.getInsets(WindowInsets.Type.systemBars()).bottom)
            }
        peek()
    }

    private suspend fun ensureInstallPermission(): Boolean {
        if (activity.packageManager.canRequestPackageInstalls())
            return true
        Toast.makeText(activity, "먼저 앱 설치 권한을 허용해 주세요.", Toast.LENGTH_LONG).show()
        return suspendCancellableCoroutine { cont ->
            onPermissionResult = { granted -> cont.resume(granted) }
            permissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData("package:${activity.packageName}".toUri())
            )
        }
    }

    private fun downloadFile(url: String, dest: File): Flow<Int> {
        val downloadManager = activity.getSystemService(DownloadManager::class.java)
        val request = DownloadManager.Request(url.toUri())
            .setDestinationUri(Uri.fromFile(dest))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setTitle("새 버전 다운로드")
            .setDescription("열심히 내려받는 중...")
        val downloadId = downloadManager.enqueue(request)

        return flow {
            while (true) {
                val status = downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                    if (!cursor.moveToFirst())
                        return@use null
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_RUNNING) {
                        val total = cursor.getLongOrNull(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val downloaded = cursor.getLongOrNull(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        LOG.verbose { "update download: $downloaded / $total" }
                    }
                    status
                }
                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                    emit(status)
                    break
                }
                delay(100.milliseconds)
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun requestInstall(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(intent)
    }
}
