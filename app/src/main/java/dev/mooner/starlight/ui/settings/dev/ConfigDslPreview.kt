package dev.mooner.starlight.ui.settings.dev

import android.content.Context
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.*
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.utils.TimeUtils
import dev.mooner.starlight.utils.getPackageInfo
import dev.mooner.starlight.utils.peek
import dev.mooner.starlight.utils.startConfigActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val previewData: MutableDataMap = hashMapOf()

fun Context.startConfigDslPreview() {
    startConfigActivity(
        title = "개발자 모드",
        subTitle = "ConfigDSL 미리보기",
        struct = previewStructure(),
        saved = previewData,
        onValueUpdated = { parentId, id, _, jsonValue ->
            previewData.getOrPut(parentId) { hashMapOf() }[id] = jsonValue
        },
    )
}

private fun Context.previewStructure(): ConfigStructure = config {
    val accent = getColor(R.color.main_bright)
    category {
        id = "info"
        title = "정보 행 · info"
        textColor = accent
        items {
            info {
                id = "version"
                title = "앱 버전"
                description = "길게 눌러 복사해요"
                icon = Icon.STAR
                iconTintColor = accent
                value = getPackageInfo().versionName
                copyable = true
            }
            info {
                id = "status_ok"
                title = "상태 OK"
                icon = Icon.CHECK_CIRCLE
                value = "정상"
                status = OptionStatus.OK
            }
            info {
                id = "status_warning"
                title = "상태 WARNING"
                icon = Icon.BATTERY_ALERT
                value = "확인 필요"
                status = OptionStatus.WARNING
            }
            info {
                id = "status_error"
                title = "상태 ERROR"
                icon = Icon.POWER
                value = "꺼짐"
                status = OptionStatus.ERROR
            }
            info {
                id = "clickable"
                title = "누를 수 있는 정보 행"
                description = "onClick이 있을 때만 리플이 생겨요"
                icon = Icon.INFO
                value = "열기"
                setOnClickListener { view ->
                    view.context.peek("info 행을 눌렀어요")
                }
            }
        }
    }
    category {
        id = "live"
        title = "실시간 갱신 · bind"
        textColor = accent
        items {
            info {
                id = "clock"
                title = "현재 시각"
                icon = Icon.ACCESS_TIME
                bindValue(every = 1.seconds) { TimeUtils.formatMillis(System.currentTimeMillis(), "HH:mm:ss") }
            }
            info {
                id = "connection"
                title = "가짜 연결 상태"
                description = "3초마다 연결 중 → 연결됨 → 끊김"
                icon = Icon.LINK
                bind(fakeConnection())
            }
            button {
                id = "uptime"
                title = "화면을 연 지"
                icon = Icon.TIMER
                val openedAt = System.currentTimeMillis()
                bindDescription(every = 1.seconds) { "${(System.currentTimeMillis() - openedAt) / 1000}초 지났어요" }
            }
        }
    }
    category {
        id = "actions"
        title = "버튼 · action / confirm / style"
        textColor = accent
        items {
            button {
                id = "action_basic"
                title = "2초 걸리는 작업"
                description = "실행하는 동안 아이콘 자리에 로딩이 돌아요"
                icon = Icon.CLOUD_DOWNLOAD
                action {
                    delay(2.seconds)
                    peek("작업을 마쳤어요", PeekType.SUCCESS)
                }
            }
            button {
                id = "action_input"
                title = "입력 받기"
                description = "input()"
                icon = Icon.EDIT
                action {
                    val name = input("이름 입력", hint = "이름") ?: return@action
                    peek("입력한 값: $name")
                }
            }
            button {
                id = "action_select"
                title = "목록에서 고르기"
                description = "select()"
                icon = Icon.CHECK
                action {
                    val channels = listOf("일반", "공지", "로그")
                    val index = select("채널 선택", channels) ?: return@action
                    peek("${channels[index]} 채널을 골랐어요")
                }
            }
            button {
                id = "action_confirm"
                title = "작업 중간에 확인 받기"
                description = "confirm()"
                icon = Icon.INFO
                action {
                    peek(if (confirm("확인", "계속 진행할까요?")) "진행했어요" else "취소했어요")
                }
            }
            button {
                id = "action_error"
                title = "실패하는 작업"
                description = "예외가 나도 앱이 꺼지지 않고 알림으로 알려줘요"
                icon = Icon.ERROR
                action {
                    delay(500.milliseconds)
                    error("테스트 예외: 서버에 연결할 수 없어요")
                }
            }
            button {
                id = "style_primary"
                title = "PRIMARY 스타일"
                icon = Icon.STAR
                style = ButtonStyle.PRIMARY
                action { peek("PRIMARY 버튼을 눌렀어요") }
            }
            button {
                id = "style_danger"
                title = "DANGER 스타일 + confirm"
                description = "누르면 먼저 확인을 받아요. 실제로 지우는 건 없어요"
                icon = Icon.DELETE_SWEEP
                style = ButtonStyle.DANGER
                confirm("정말 실행할까요?")
                action { peek("확인 후 실행했어요", PeekType.SUCCESS) }
            }
        }
    }
    category {
        id = "progress"
        title = "진행 표시 · progress"
        textColor = accent
        items {
            val task = MutableStateFlow(ProgressState(0, 3, "대기 중"))
            progress {
                id = "task_progress"
                title = "단계별 작업"
                description = "아래 버튼으로 시작해요"
                icon = Icon.LIST_BULLETED
                bind(task)
            }
            button {
                id = "run_task"
                title = "작업 실행"
                description = "준비(진행률 모름) → 3단계 진행"
                icon = Icon.REFRESH
                action {
                    task.value = ProgressState.indeterminate("준비 중")
                    delay(1500.milliseconds)
                    for (step in 1..3) {
                        task.value = ProgressState(step - 1, 3, "$step / 3 단계")
                        delay(1.seconds)
                    }
                    task.value = ProgressState(3, 3, "완료")
                    peek("모든 단계를 마쳤어요", PeekType.SUCCESS)
                }
            }
            progress {
                id = "storage"
                title = "저장 공간"
                description = "값을 정해서 보여주는 고정 진행률"
                icon = Icon.ARCHIVE
                progress = 72
                label = "7.2 / 10 GB"
            }
        }
    }
    category {
        id = "lists"
        title = "텍스트 목록 · textList"
        textColor = accent
        items {
            textList {
                id = "allowed_ips"
                title = "허용된 IP"
                description = "길게 눌러 한 줄씩 복사해요"
                icon = Icon.LINK
                lines = listOf("192.168.0.10", "192.168.0.24", "10.0.2.2")
                monospace = true
                copyable = true
            }
            textList {
                id = "recent_access"
                title = "최근 접속"
                description = "2초마다 새 접속이 추가돼요 (최대 8개)"
                icon = Icon.ACCESS_TIME
                maxVisible = 4
                monospace = true
                bind(fakeAccessLog())
            }
            textList {
                id = "blocked_ips"
                title = "차단된 IP"
                icon = Icon.LOCK
                emptyText = "차단된 주소가 없어요"
            }
        }
    }
    category {
        id = "select"
        title = "선택 · choice / multiChoice / visibleWhen"
        textColor = accent
        items {
            choice {
                id = "mode"
                title = "모드"
                description = "고른 항목의 key가 저장돼요"
                icon = Icon.SETTINGS
                option("basic", "기본")
                option("advanced", "고급")
                option("custom", "직접 지정")
            }
            info {
                id = "advanced_hint"
                title = "고급 모드 안내"
                description = "모드가 '고급'일 때만 보여요"
                icon = Icon.INFO
                value = "고급"
                status = OptionStatus.WARNING
                visibleWhen("mode", "advanced")
            }
            multiChoice {
                id = "channels"
                title = "채널"
                description = "모드가 '직접 지정'일 때만 보여요. 누르면 시트에서 여러 개를 골라요"
                icon = Icon.LIST_BULLETED
                option("general", "일반")
                option("notice", "공지")
                option("log", "로그")
                defaultKeys = setOf("general")
                visibleWhen("mode", "custom")
            }
            toggle {
                id = "show_extra"
                title = "추가 항목 보이기"
                icon = Icon.LAYERS
            }
            info {
                id = "extra"
                title = "추가 항목"
                description = "토글이 켜져 있을 때만 보여요"
                icon = Icon.LAYERS
                visibleWhen("show_extra", true)
            }
        }
    }
    category {
        id = "dev_only"
        title = "개발자 모드 전용"
        textColor = accent
        flags = CategoryConfigOption.FLAG_DEV_MODE_ONLY
        items {
            info {
                id = "dev_only_info"
                title = "FLAG_DEV_MODE_ONLY"
                description = "개발자 모드가 꺼져 있으면 이 카테고리가 통째로 숨겨져요"
                icon = Icon.DEVELOPER_MODE
                value = "보임"
                status = OptionStatus.OK
            }
        }
    }
}

private fun fakeAccessLog(): Flow<List<String>> = flow {
    val entries = ArrayDeque<String>()
    var index = 1
    while (true) {
        val time = TimeUtils.formatMillis(System.currentTimeMillis(), "HH:mm:ss")
        entries.addFirst("$time  172.16.0.${index++}")
        if (entries.size > 8)
            entries.removeLast()
        emit(entries.toList())
        delay(2.seconds)
    }
}

private fun fakeConnection(): Flow<InfoState> = flow {
    val states = listOf(
        InfoState(null, OptionStatus.LOADING),
        InfoState("연결됨", OptionStatus.OK),
        InfoState("끊김", OptionStatus.ERROR),
    )
    var index = 0
    while (true) {
        emit(states[index++ % states.size])
        delay(3.seconds)
    }
}
