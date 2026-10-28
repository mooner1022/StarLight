package dev.mooner.starlight.ui.plugins.store

import androidx.annotation.DrawableRes
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.plugin.PluginDependency
import dev.mooner.starlight.plugincore.version.Version
import kotlinx.coroutines.delay
import java.io.IOException
import java.security.MessageDigest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class StoreRepository(
    val name: String,
    val url: String,
    val branch: String,
    val commit: String,
    val syncedAt: Long,
)

data class StoreCategory(
    val id: String,
    val name: String,
)

data class StoreAuthor(
    val name: String,
    val url: String?,
    val isVerified: Boolean,
)

data class StoreScreenshot(
    val caption: String,
    val lines: Int,
)

data class StoreRelease(
    val version: Version,
    val apiVersion: Version,
    val releasedAt: Long,
    val changelog: String,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val downloadUrl: String,
    val dependency: List<PluginDependency>,
    val usesNativeLibrary: Boolean,
)

data class StorePlugin(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val authors: List<StoreAuthor>,
    val category: String,
    val tags: List<String>,
    @DrawableRes val icon: Int,
    val screenshots: List<StoreScreenshot>,
    val license: String,
    val sourceUrl: String?,
    val publishedAt: Long,
    val downloads: Int,
    val releases: List<StoreRelease>,
) {
    val latest: StoreRelease
        get() = releases.first()

    val isVerified: Boolean
        get() = authors.any(StoreAuthor::isVerified)
}

data class StoreFeature(
    val pluginId: String,
    val headline: String,
)

data class StoreIndex(
    val repository: StoreRepository,
    val categories: List<StoreCategory>,
    val featured: List<StoreFeature>,
    val plugins: List<StorePlugin>,
) {
    fun find(id: String): StorePlugin? =
        plugins.find { it.id == id }
}

object SampleStoreSource {

    private const val REPO_URL = "https://github.com/mooner1022/starlight-plugins"

    suspend fun fetchIndex(simulateFailure: Boolean): StoreIndex {
        delay(1400)
        if (simulateFailure)
            throw IOException("Unable to resolve host \"raw.githubusercontent.com\"")
        return sampleIndex()
    }

    val sampleInstalled: Map<String, Version> = mapOf(
        "dev.mooner.discord" to Version(1, 1, 0),
        "dev.mooner.netkit"  to Version(1, 4, 2),
    )

    private fun ago(duration: Duration): Long =
        System.currentTimeMillis() - duration.inWholeMilliseconds

    private fun sha256Of(seed: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private val mooner = StoreAuthor("mooner1022", "https://github.com/mooner1022", isVerified = true)

    private fun release(
        id: String,
        version: String,
        released: Duration,
        changelog: String,
        size: Long,
        api: String = "0.3.0",
        dependency: List<PluginDependency> = emptyList(),
        native: Boolean = false,
    ): StoreRelease {
        val fileName = "${id.substringAfterLast('.')}-$version.slp"
        return StoreRelease(
            version           = Version.fromString(version),
            apiVersion        = Version.fromString(api),
            releasedAt        = ago(released),
            changelog         = changelog,
            fileName          = fileName,
            fileSize          = size,
            sha256            = sha256Of("$id@$version"),
            downloadUrl       = "$REPO_URL/raw/main/plugins/$id/$version/$fileName",
            dependency        = dependency,
            usesNativeLibrary = native,
        )
    }

    private fun sampleIndex(): StoreIndex {
        val netkitDep = PluginDependency("dev.mooner.netkit", "1.2.0", isOptional = false)
        val plugins = listOf(
            StorePlugin(
                id          = "dev.mooner.discord",
                name        = "Discord 브릿지",
                summary     = "카카오톡 방과 디스코드 채널을 서로 연결해요",
                description = """
                    |카카오톡 방과 디스코드 채널의 메시지를 양방향으로 주고받을 수 있게 해 주는 플러그인이에요.
                    |
                    |**주요 기능**
                    |- 방마다 연결할 채널을 따로 지정
                    |- 이미지, 이모티콘을 링크로 변환해 전달
                    |- 프로젝트에서 `discord.send()`로 직접 메시지 전송
                    |
                    |봇 토큰은 플러그인 설정에서 입력하며, 기기 밖으로 전송되지 않아요.
                """.trimMargin(),
                authors     = listOf(mooner),
                category    = "messenger",
                tags        = listOf("디스코드", "브릿지", "메신저"),
                icon        = dev.mooner.configdsl.R.drawable.ic_round_mark_chat_read_24,
                screenshots = listOf(
                    StoreScreenshot("채널 연결 설정", 4),
                    StoreScreenshot("메시지 전달", 5),
                    StoreScreenshot("프로젝트 API", 3),
                ),
                license     = "GPL-3.0",
                sourceUrl   = "https://github.com/mooner1022/DiscordPlugin",
                publishedAt = ago(210.days),
                downloads   = 4821,
                releases    = listOf(
                    release("dev.mooner.discord", "1.2.0", 2.days, "- 스레드 메시지 전달 지원\n- 재연결이 끊기던 문제 수정", 3_812_000),
                    release("dev.mooner.discord", "1.1.0", 34.days, "- 방마다 채널 지정\n- 이미지 링크 변환", 3_640_000),
                    release("dev.mooner.discord", "1.0.0", 210.days, "- 첫 공개 버전", 3_120_000, api = "0.2.0"),
                ),
            ),
            StorePlugin(
                id          = "dev.mooner.netkit",
                name        = "NetKit",
                summary     = "스크립트에서 HTTP, 웹소켓을 쉽게 쓰게 해 주는 라이브러리",
                description = """
                    |프로젝트와 다른 플러그인이 함께 쓰는 네트워크 라이브러리예요.
                    |
                    |- `http.get`, `http.post` 등 간단한 요청 API
                    |- 자동 재연결되는 웹소켓
                    |- 응답 캐시와 요청 제한
                """.trimMargin(),
                authors     = listOf(mooner),
                category    = "library",
                tags        = listOf("HTTP", "웹소켓", "라이브러리"),
                icon        = dev.mooner.configdsl.R.drawable.ic_round_cloud_24,
                screenshots = listOf(StoreScreenshot("요청 로그", 5), StoreScreenshot("캐시 설정", 3)),
                license     = "MIT",
                sourceUrl   = "https://github.com/mooner1022/NetKit",
                publishedAt = ago(320.days),
                downloads   = 9310,
                releases    = listOf(
                    release("dev.mooner.netkit", "1.4.2", 12.days, "- 응답 캐시 만료 시간 설정\n- 타임아웃 기본값 10초로 변경", 820_000),
                    release("dev.mooner.netkit", "1.4.0", 60.days, "- 웹소켓 자동 재연결", 790_000),
                ),
            ),
            StorePlugin(
                id          = "dev.hana.telegram",
                name        = "Telegram 브릿지",
                summary     = "텔레그램 봇으로 메시지를 보내고 받아요",
                description = """
                    |텔레그램 봇 API로 카카오톡 방의 메시지를 전달해요.
                    |
                    |- 여러 채팅 연결
                    |- 명령어 메시지 필터
                """.trimMargin(),
                authors     = listOf(StoreAuthor("hana", "https://github.com/hana-dev", isVerified = false)),
                category    = "messenger",
                tags        = listOf("텔레그램", "브릿지"),
                icon        = dev.mooner.configdsl.R.drawable.ic_round_send_24,
                screenshots = listOf(StoreScreenshot("봇 연결", 4), StoreScreenshot("필터 규칙", 4)),
                license     = "Apache-2.0",
                sourceUrl   = "https://github.com/hana-dev/sl-telegram",
                publishedAt = ago(48.days),
                downloads   = 1204,
                releases    = listOf(
                    release("dev.hana.telegram", "0.3.1", 5.hours, "- 긴 메시지가 잘리던 문제 수정", 1_450_000, dependency = listOf(netkitDep)),
                    release("dev.hana.telegram", "0.3.0", 20.days, "- 명령어 필터 추가", 1_420_000, dependency = listOf(netkitDep)),
                    release("dev.hana.telegram", "0.2.0", 48.days, "- 첫 공개 버전", 1_300_000, dependency = listOf(netkitDep)),
                ),
            ),
            StorePlugin(
                id          = "dev.kyu.scheduler",
                name        = "스케줄러",
                summary     = "정해진 시간에 프로젝트 이벤트를 실행해요",
                description = """
                    |크론 표현식이나 간단한 반복 규칙으로 프로젝트에 `onSchedule` 이벤트를 보내요.
                    |
                    |앱이 잠들어 있어도 정확한 시각에 깨우도록 알람을 사용해요.
                """.trimMargin(),
                authors     = listOf(StoreAuthor("kyu", null, isVerified = false)),
                category    = "utility",
                tags        = listOf("자동화", "크론", "알람"),
                icon        = dev.mooner.configdsl.R.drawable.ic_round_timer_24,
                screenshots = listOf(StoreScreenshot("일정 목록", 5), StoreScreenshot("규칙 편집", 3)),
                license     = "MIT",
                sourceUrl   = null,
                publishedAt = ago(90.days),
                downloads   = 2671,
                releases    = listOf(
                    release("dev.kyu.scheduler", "2.0.0", 40.minutes, "- 크론 표현식 지원\n- 설정 구조가 바뀌어 이전 일정은 다시 등록해야 해요", 540_000),
                    release("dev.kyu.scheduler", "1.3.0", 90.days, "- 요일 반복", 512_000),
                ),
            ),
            StorePlugin(
                id          = "dev.mooner.sqlite",
                name        = "SQLite 저장소",
                summary     = "프로젝트마다 SQLite 데이터베이스를 제공해요",
                description = """
                    |프로젝트별로 분리된 SQLite 데이터베이스와 간단한 쿼리 API를 제공해요.
                    |
                    |빠른 처리를 위해 네이티브 라이브러리를 포함하고 있어요.
                """.trimMargin(),
                authors     = listOf(mooner, StoreAuthor("lumi", null, isVerified = false)),
                category    = "library",
                tags        = listOf("데이터베이스", "저장소", "네이티브"),
                icon        = R.drawable.ic_round_sd_storage_24,
                screenshots = listOf(StoreScreenshot("쿼리 콘솔", 5)),
                license     = "GPL-3.0",
                sourceUrl   = "https://github.com/mooner1022/sl-sqlite",
                publishedAt = ago(150.days),
                downloads   = 3388,
                releases    = listOf(
                    release("dev.mooner.sqlite", "1.0.3", 9.days, "- 트랜잭션 API 추가", 6_920_000, native = true),
                ),
            ),
            StorePlugin(
                id          = "dev.snake.pyengine",
                name        = "Python 엔진",
                summary     = "파이썬으로 프로젝트를 작성할 수 있게 해요",
                description = "Chaquopy 기반의 파이썬 스크립트 엔진이에요. 아직 새 PluginCore를 지원하지 않아요.",
                authors     = listOf(StoreAuthor("snake", "https://github.com/snake", isVerified = false)),
                category    = "engine",
                tags        = listOf("파이썬", "스크립트 엔진"),
                icon        = R.drawable.ic_round_code_24,
                screenshots = listOf(StoreScreenshot("에디터", 5), StoreScreenshot("패키지 관리", 4)),
                license     = "MIT",
                sourceUrl   = "https://github.com/snake/sl-python",
                publishedAt = ago(400.days),
                downloads   = 5120,
                releases    = listOf(
                    release("dev.snake.pyengine", "0.9.0", 180.days, "- Python 3.11", 18_400_000, api = "0.2.1", native = true),
                ),
            ),
            StorePlugin(
                id          = "io.lingo.translate",
                name        = "자동 번역",
                summary     = "메시지를 원하는 언어로 번역해 보여 줘요",
                description = "번역 API를 프로젝트에 제공해요. NetKit이 있으면 응답을 캐시해서 더 빨라요.",
                authors     = listOf(StoreAuthor("lingo", null, isVerified = false)),
                category    = "utility",
                tags        = listOf("번역", "언어"),
                icon        = dev.mooner.configdsl.R.drawable.ic_round_text_fields_24,
                screenshots = listOf(StoreScreenshot("번역 결과", 4)),
                license     = "MIT",
                sourceUrl   = null,
                publishedAt = ago(16.days),
                downloads   = 642,
                releases    = listOf(
                    release("io.lingo.translate", "1.0.0", 16.days, "- 첫 공개 버전", 430_000,
                        dependency = listOf(PluginDependency("dev.mooner.netkit", PluginDependency.VERSION_ANY, isOptional = true))),
                ),
            ),
            StorePlugin(
                id          = "dev.stat.dashboard",
                name        = "통계 대시보드",
                summary     = "방별 메시지 통계를 그래프로 보여 줘요",
                description = "SQLite 저장소에 쌓인 메시지 기록으로 방별, 시간대별 통계를 만들어요.",
                authors     = listOf(StoreAuthor("stat", null, isVerified = false)),
                category    = "devtools",
                tags        = listOf("통계", "그래프"),
                icon        = dev.mooner.configdsl.R.drawable.ic_round_developer_board_24,
                screenshots = listOf(StoreScreenshot("시간대별", 5), StoreScreenshot("방별 순위", 4)),
                license     = "GPL-3.0",
                sourceUrl   = "https://github.com/stat/sl-dashboard",
                publishedAt = ago(70.days),
                downloads   = 988,
                releases    = listOf(
                    release("dev.stat.dashboard", "0.5.0", 3.days, "- 주간 리포트", 2_210_000, dependency = listOf(
                        PluginDependency("dev.mooner.sqlite", "1.0.0", isOptional = false),
                        PluginDependency("dev.chart.core", PluginDependency.VERSION_ANY, isOptional = false),
                    )),
                ),
            ),
        )
        return StoreIndex(
            repository = StoreRepository(
                name     = "StarLight Plugins",
                url      = REPO_URL,
                branch   = "main",
                commit   = sha256Of("index").take(7),
                syncedAt = ago(3.minutes),
            ),
            categories = listOf(
                StoreCategory("messenger", "메신저 연동"),
                StoreCategory("engine", "스크립트 엔진"),
                StoreCategory("library", "라이브러리"),
                StoreCategory("utility", "유틸리티"),
                StoreCategory("devtools", "개발 도구"),
            ),
            featured = listOf(
                StoreFeature("dev.mooner.discord", "카카오톡과 디스코드를 하나로"),
                StoreFeature("dev.kyu.scheduler", "정해진 시간에, 알아서"),
                StoreFeature("dev.mooner.netkit", "스크립트에 네트워크 날개를"),
            ),
            plugins = plugins,
        )
    }
}
