/*
 * settings.struct.kt created by Minki Moon(mooner1022) on 8/3/23, 10:11 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.database.getLongOrNull
import androidx.lifecycle.lifecycleScope
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.config.CategoryConfigObject
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.settings.dev.startDevModeActivity
import dev.mooner.starlight.ui.settings.info.AppInfoActivity
import dev.mooner.starlight.ui.settings.notifications.NotificationRulesActivity
import dev.mooner.starlight.ui.settings.solver.getProblemSolverStruct
import dev.mooner.starlight.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File

context(SettingsFragment)
internal fun getSettingsStruct() = config {
    category {
        id = "general"
        title = "일반"
        icon = Icon.SETTINGS
        iconTintColor = color { "#5584AC" }
        //textColor = color { "#706EB9" }
        flags = CategoryConfigObject.FLAG_NESTED
        items {
            toggle {
                id = "global_power"
                title = "전역 전원"
                description = "모든 프로젝트의 답장/처리 여부를 결정합니다."
                icon = Icon.POWER
                iconTintColor = color { "#7ACA8A" }
                defaultValue = false
            }
            toggle {
                id = "newbie_mode"
                title = "쉬운 사용 모드"
                description = "설정을 단순화하여 초심자도 사용이 쉽도록 변경합니다."
                icon = Icon.ECO
                defaultValue = true
                setOnValueChangedListener { _, _ ->
                    showConfirmDialog(
                        requireActivity(),
                        title = "설정을 적용하려면 앱을 재시작해야 합니다.",
                        message = "지금 앱을 재시작할까요?"
                    ) { confirm ->
                        if (confirm)
                            requireActivity().restartApplication()
                    }
                }
            }
            /*
            string {
                id = "log_buffer_max_size"
                title = "로그 최대 저장 크기"
                description = "로그 기록 배열의 최대 크기를 설정합니다. 이 값이 클수록 메모리 사용량이 증가합니다.\n(0 = 비활성화)"
                icon = Icon.LIST_BULLETED
                inputType = InputType.TYPE_CLASS_NUMBER
                defaultValue = "100"
            }
             */
            button {
                id = "restart_application"
                title = "앱 재시작"
                description = "모든 프로세스를 안전하게 종료하고 재시작합니다."
                icon = Icon.REFRESH
                iconTintColor = requireContext().getColor(R.color.code_orange)
                setOnClickListener(requireActivity()::restartApplication)
            }
        }
    }
    category {
        id = "project"
        title = "프로젝트"
        icon = Icon.PROJECTS
        iconTintColor = color { "#B4CFB0" }
        //textColor = color { "#706EB9" }
        flags = CategoryConfigObject.FLAG_NESTED
        items {
            toggle {
                id = "compile_animation"
                title = "컴파일 애니메이션"
                description = "컴파일 시 프로그레스 바의 애니메이션을 부드럽게 조정합니다."
                icon = Icon.COMPRESS
                iconTintColor = color { "#FEAC5E" }
                defaultValue = true
            }
            toggle {
                id = "load_global_libraries"
                title = "전역 모듈 로드"
                description = "StarLight/modules 폴더 내의 모듈을 컴파일 시 적용합니다. 신뢰할 수 없는 코드가 실행될 수 있습니다."
                icon = Icon.FOLDER
                iconTintColor = color { "#4BC0C8" }
                defaultValue = false
                warnOnEnable {
                    """
                    |이 기능은 신뢰할 수 없는 코드를 기기에서 실행할 수 있으며, 이로 인해 발생하는 어떠한 상해나 손실도 본 앱의 개발자는 보장하지 않습니다.
                    |기능을 활성화 할까요?
                    """.trimMargin()
                }
            }
        }
    }
    category {
        id = "plugin"
        title = "플러그인"
        icon = Icon.ARCHIVE
        iconTintColor = color { "#95D1CC" }
        //textColor = color { "#706EB9" }
        flags = CategoryConfigObject.FLAG_NESTED
        items {
            toggle {
                id = "safe_mode"
                title = "안전 모드 (재시작 필요)"
                description = "플러그인 안전 모드를 활성화 합니다. 모든 플러그인을 로드 하지 않습니다."
                icon = Icon.LAYERS_CLEAR
                iconTintColor = color { "#95D1CC" }
                defaultValue = false
            }
            button {
                id = "restart_with_safe_mode"
                title = "안전 모드로 재시작"
                description = "안전 모드 활성화 후 앱을 재시작 합니다."
                icon = Icon.REFRESH
                iconTintColor = color { "#FF6F3C" }
                setOnClickListener(requireContext()::restartApplication)
                dependency = "safe_mode"
            }
        }
    }
    category {
        id = "notifications"
        title = "알림, 이벤트"
        icon = Icon.NOTIFICATIONS
        iconTintColor = color { "#98BAE7" }
        //textColor = color { "#706EB9" }
        flags = CategoryConfigObject.FLAG_NESTED
        items {
            button {
                id = "read_noti_perm"
                title = "알림 읽기 권한 설정"
                icon = Icon.NOTIFICATIONS_ACTIVE
                iconTintColor = color { "#C8E4B2" }
                setOnClickListener { _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            toggle {
                id = "use_legacy_event"
                title = "레거시 이벤트 사용"
                description = "메신저봇이나 채자봇과 호환되는 이벤트를 사용합니다. (response)"
                icon = Icon.BOOKMARK
                iconTintColor = color { "#9ED2BE" }
                defaultValue = false
            }
            toggle {
                id = "log_received_message"
                title = "수신 메세지 로그 표시"
                description = "수신된 메세지를 로그에 표시합니다. ('내부 로그 표시' 활성화 필요)"
                icon = Icon.MARK_CHAT_READ
                iconTintColor = color { "#7EAA92" }
                defaultValue = false
            }
            button {
                id = "set_package_rules"
                title = "패키지 규칙 설정"
                description = "패키지 별 알림을 수신할 규칙을 설정합니다."
                icon = Icon.DEVELOPER_BOARD
                iconTintColor = color { "#7EAA92" }
                setOnClickListener { _ ->
                    requireActivity().startActivity<NotificationRulesActivity>()
                }
            }
            toggle {
                id = "use_on_notification_posted"
                title = "onNotificationPosted 이벤트 사용"
                description = "메신저봇의 onNotificationPosted 이벤트를 사용합니다. 부하가 증가할 수 있습니다."
                icon = Icon.COMPRESS
                iconTintColor = color { "#87AAAA" }
                defaultValue = false
            }
            button {
                id = "magic"
                title = "문제 해결 도우미"
                description = "알림 수신 관련 문제를 해결할 수 있도록 돕습니다."
                icon = Icon.ECO
                iconTintColor = color { "#FFD9B7" }
                setOnClickListener { _ ->
                    requireActivity().startConfigActivity(
                        title = "문제 해결 도우미",
                        subTitle = "알림 수신 관련 문제 해결을 돕습니다.",
                        struct = getProblemSolverStruct(),
                    )
                }
            }
        }
    }
    category {
        id = "info"
        title = "정보"
        textColor = requireContext().getColor(R.color.main_bright)
        items {
            button {
                id = "check_update"
                title = "업데이트 확인"
                icon = Icon.CLOUD_DOWNLOAD
                iconTintColor = color { "#A7D0CD" }
                setOnClickListener { _ ->
                    checkUpdate()
                }
            }
            button {
                id = "app_info"
                title = "앱 정보"
                icon = Icon.INFO
                iconTintColor = color { "#F1CA89" }
                setOnClickListener { _ ->
                    startActivity(Intent(context, AppInfoActivity::class.java))
                }
            }
            button {
                id = "help_dev"
                title = "개발자 돕기"
                description = "이 개발자는 자원봉사 중이에요.."
                icon = Icon.FAVORITE
                iconTintColor = color { "#FF90BC" }
                setOnClickListener { _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://toss.me/mooner"))
                    startActivity(intent)
                }
            }
            if (GlobalConfig.category("dev").getBoolean("dev_mode") == true) {
                button {
                    id = "developer_mode"
                    title = "개발자 모드"
                    icon = Icon.DEVELOPER_MODE
                    iconTintColor = color { "#93B5C6" }
                    setOnClickListener(requireContext()::startDevModeActivity)
                }
            }
        }
    }
}

context(SettingsFragment)
internal fun getNoobSettingStruct() = config {
    val mainColor = requireContext().getColor(R.color.main_bright)
    category {
        id = "general"
        title = "일반"
        textColor = mainColor
        icon = Icon.SETTINGS
        iconTintColor = color { "#5584AC" }
        items {
            toggle {
                id = "global_power"
                title = "전역 전원"
                description = "모든 프로젝트의 답장/처리 여부를 결정합니다."
                icon = Icon.POWER
                iconTintColor = color { "#7ACA8A" }
                defaultValue = false
            }
            toggle {
                id = "newbie_mode"
                title = "쉬운 사용 모드"
                description = "설정을 단순화하여 초심자도 쉽게 사용이 가능하도록 변경합니다."
                icon = Icon.ECO
                defaultValue = true
                setOnValueChangedListener { _, _ ->
                    showConfirmDialog(
                        requireActivity(),
                        title = "설정을 적용하려면 앱을 재시작해야 합니다.",
                        message = "지금 앱을 재시작할까요?"
                    ) { confirm ->
                        if (confirm)
                            requireActivity().restartApplication()
                    }
                }
            }
            button {
                id = "restart_application"
                title = "앱 재시작"
                description = "모든 프로세스를 안전하게 종료하고 재시작합니다."
                icon = Icon.REFRESH
                iconTintColor = requireContext().getColor(R.color.code_orange)
                setOnClickListener(requireActivity()::restartApplication)
            }
        }
    }
    category {
        id = "project"
        title = "프로젝트"
        textColor = mainColor
        icon = Icon.PROJECTS
        iconTintColor = color { "#B4CFB0" }
        items {
            toggle {
                id = "compile_animation"
                title = "컴파일 애니메이션"
                description = "컴파일 시 프로그레스 바의 애니메이션을 부드럽게 조정합니다."
                icon = Icon.COMPRESS
                iconTintColor = color { "#FEAC5E" }
                defaultValue = true
            }
        }
    }
    category {
        id = "notifications"
        title = "알림, 이벤트"
        textColor = mainColor
        icon = Icon.NOTIFICATIONS
        iconTintColor = color { "#98BAE7" }
        items {
            button {
                id = "read_noti_perm"
                title = "알림 읽기 권한 설정"
                icon = Icon.NOTIFICATIONS_ACTIVE
                iconTintColor = color { "#C8E4B2" }
                setOnClickListener { _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            toggle {
                id = "use_legacy_event"
                title = "레거시 이벤트 사용"
                description = "메신저봇이나 채자봇과 호환되는 이벤트를 사용합니다. (response)"
                icon = Icon.BOOKMARK
                iconTintColor = color { "#9ED2BE" }
                defaultValue = false
            }
            toggle {
                id = "use_on_notification_posted"
                title = "onNotificationPosted 이벤트 사용"
                description = "메신저봇의 onNotificationPosted 이벤트를 사용합니다. 부하가 증가할 수 있습니다."
                icon = Icon.COMPRESS
                iconTintColor = color { "#87AAAA" }
                defaultValue = false
            }
        }
    }
    category {
        id = "info"
        title = "정보"
        textColor = mainColor
        items {
            button {
                id = "check_update"
                title = "업데이트 확인"
                icon = Icon.CLOUD_DOWNLOAD
                iconTintColor = color { "#A7D0CD" }
                setOnClickListener { _ ->
                    checkUpdate()
                }
            }
            button {
                id = "app_info"
                title = "앱 정보"
                icon = Icon.INFO
                iconTintColor = color { "#F1CA89" }
                setOnClickListener { _ ->
                    startActivity(Intent(context, AppInfoActivity::class.java))
                }
            }
            if (GlobalConfig.category("dev").getBoolean("dev_mode") == true) {
                button {
                    id = "developer_mode"
                    title = "개발자 모드"
                    icon = Icon.DEVELOPER_MODE
                    iconTintColor = color { "#93B5C6" }
                    setOnClickListener(requireContext()::startDevModeActivity)
                }
            }
        }
    }
}

context(SettingsFragment)
private fun checkUpdate() {
    createSimplePeek(
        text = translate {
            Locale.ENGLISH { "Plz wait for a sec..." }
            Locale.KOREAN  { "서버를 열심히 뒤져보는 중..." }
        }
    ) {
        position = PeekAlert.Position.Bottom
        iconRes = R.drawable.ic_round_cloud_24
        iconTint(res = R.color.main_bright)
        backgroundColor(res = R.color.background_popup)
    }.peek()

    lifecycleScope.launch {
        val version = withContext(Dispatchers.IO) {
            VersionChecker().fetchVersion(VersionChecker.Channel.SNAPSHOT)
        }
        if (version == null) {
            createSimplePeek(
                text = translate {
                    Locale.ENGLISH { "Failed to parse or fetch version info from server. Please try again later." }
                    Locale.KOREAN  { "서버로부터 버전 정보를 불러오는 데 실패했어요. 나중에 다시 시도해주세요." }
                }
            ) {
                position = PeekAlert.Position.Bottom
                iconRes = R.drawable.ic_round_close_24
                iconTint(res = R.color.code_error)
                backgroundColor(res = R.color.background_popup)
            }.peek()
            return@launch
        }
        val versionCode = requireContext().getAppVersionCode()
        if (version.versionCode <= versionCode) {
            createSimplePeek(
                text = translate {
                    Locale.ENGLISH { "App is already on the newest version." }
                    Locale.KOREAN  { "앱이 이미 최신 버전이에요." }
                }
            ) {
                position = PeekAlert.Position.Bottom
                iconRes = R.drawable.ic_round_check_24
                iconTint(res = R.color.noctis_green)
                backgroundColor(res = R.color.background_popup)
            }.peek()
            return@launch
        }

        val pInfo = requireContext().getPackageInfo()
        showConfirmDialog(
            context = requireActivity(),
            title = translate {
                Locale.ENGLISH { "New version found" }
                Locale.KOREAN  { "새로운 버전 확인 (*˙˘˙*)!" }
            },
            message = translate {
                Locale.ENGLISH {
                    """
                    |A version newer than installed was found:
                    |${pInfo.versionName} >> ${version.version}
                    |Would you download the newer version of this app?
                    """.trimMargin()
                }
                Locale.KOREAN  {
                    """
                    |현재 설치된 버전보다 새로운 버전이 있어요:
                    |${pInfo.versionName} >> ${version.version}
                    |새 버전을 다운로드 할까요?
                    """.trimMargin()
                }
            }
        ) { confirm ->
            if (!confirm)
                return@showConfirmDialog
            val dest = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
                .resolve("sl_update")
                .also(File::mkdirs)
                .resolve("sl_${version.version}_${version.versionCode}.apk")
            if (dest.exists())
                dest.delete()

            val alert = createSimplePeek(
                text = "다운로드중..."
            ) {
                position = PeekAlert.Position.Bottom
                iconRes = R.drawable.ic_round_download_24
                iconTint(res = R.color.main_bright)
                backgroundColor(res = R.color.background_popup)
                autoHideMillis = null
            }.also(PeekAlert::peek)

            downloadFileFromURL(requireContext(), version.downloadUrl, dest)
                .onEach { (status, progress) ->
                    println("$status : ${progress}%")
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        alert.apply {
                            setIcon(R.drawable.ic_round_check_24)
                            setIconTint(R.color.noctis_green)
                            setText("파일을 성공적으로 다운로드 했어요! (뿌듯)")
                            setAutoHide(3000L)
                        }.peek()
                        val destUri = FileProvider.getUriForFile(requireContext(), "dev.mooner.starlight.provider", dest)

                        if (!requireContext().packageManager.canRequestPackageInstalls()) {
                            Toast.makeText(requireContext(), "먼저 앱 설치 권한을 허용해 주세요.", Toast.LENGTH_LONG).show()
                            requestAppInstallPermission()
                        }
                        requestInstall(requireContext(), destUri)
                    }
                }
                .launchIn(lifecycleScope)
        }
    }
}

private fun downloadFileFromURL(context: Context, url: String, dest: File): StateFlow<Pair<Int, Int>> {
    val scope = CoroutineScope(Dispatchers.Default)
    val flow = MutableStateFlow(0 to -1)

    val downloadManager = context.getSystemService(Activity.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(Uri.parse(url))
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
                            downloadManager.openDownloadedFile(downloadId).use {

                            }
                            println(downloadManager.getUriForDownloadedFile(downloadId))
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