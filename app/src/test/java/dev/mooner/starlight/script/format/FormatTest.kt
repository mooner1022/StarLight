package dev.mooner.starlight.script.format

import dev.mooner.starlight.api.original.FormatApi
import dev.mooner.starlight.script.Engines
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class FormatTest {

    @Before
    fun setUp() {
        Formats.locale = { Locale.KOREAN }
        Formats.zone = { ZoneId.of("Asia/Seoul") }
    }

    @After
    fun tearDown() {
        Formats.locale = Locale::getDefault
        Formats.zone = ZoneId::systemDefault
    }

    private fun failsWithIllegalArgument(block: () -> Any?) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("$error", error is IllegalArgumentException)
    }

    @Test
    fun `number groups the whole part and keeps the fraction a double shows`() {
        assertEquals("1,234,567.5", Formats.number(1234567.5))
        assertEquals("1,234,567", Formats.number(1234567))
        assertEquals("1,234,567", Formats.number(1234567.0))
        // As Rhino hands a script's 5 over.
        assertEquals("5", Formats.number(5.0))
        assertEquals("-1,234.5", Formats.number(-1234.5))
        assertEquals("0.30000000000000004", Formats.number(0.1 + 0.2))
        assertEquals("0", Formats.number(-0.0))
        assertEquals("NaN", Formats.number(Double.NaN))
        assertEquals("Infinity", Formats.number(Double.POSITIVE_INFINITY))
        assertEquals("-Infinity", Formats.number(Double.NEGATIVE_INFINITY))
        assertEquals("1,000,000,000,000,000,000,000", Formats.number(1e21))
        // Longs and BigIntegers exactly.
        assertEquals("9,223,372,036,854,775,807", Formats.number(Long.MAX_VALUE))
        assertEquals("-12,345,678,901,234,567,890", Formats.number(BigInteger("-12345678901234567890")))
    }

    @Test
    fun `number rounds half up to the fraction digits asked for`() {
        assertEquals("1,234.50", Formats.number(1234.5, 2))
        assertEquals("1,234.57", Formats.number(1234.565, 2))
        assertEquals("1", Formats.number(0.5, 0))
        assertEquals("1,000.00", Formats.number(999.999, 2))
        failsWithIllegalArgument { Formats.number(1.0, -1) }
        failsWithIllegalArgument { Formats.number(1.0, 21) }
    }

    private val epoch = Instant.ofEpochMilli(0)

    @Test
    fun `date writes the pattern in the zone, with names in the device's language`() {
        assertEquals("1970-01-01 00:00:00", Formats.date(epoch, "yyyy-MM-dd HH:mm:ss", "UTC"))
        assertEquals("1월 1일 (목)", Formats.date(epoch, "M월 d일 (E)", "UTC"))
        assertEquals("09:00", Formats.date(epoch, "HH:mm", "Asia/Seoul"))
        // The device's zone when none is given.
        assertEquals("09:00", Formats.date(epoch, "HH:mm"))
        Formats.locale = { Locale.ENGLISH }
        assertEquals("1월 1일 (Thu)", Formats.date(epoch, "M월 d일 (E)", "UTC"))
    }

    @Test
    fun `date refuses a pattern or zone that isn't one`() {
        for (block in listOf<() -> Any?>(
            { Formats.date(epoch, "{", "UTC") },
            { Formats.date(epoch, "HH:mm", "Mars/Olympus") },
        )) {
            val error = runCatching(block).exceptionOrNull()
            assertTrue("$error", error is IllegalArgumentException && error.message!!.startsWith("Format.date: "))
        }
    }

    @Test
    fun `duration leaves out the units that are 0, in Korean or not`() {
        val cases = listOf(
            3_725_000.0 to ("1시간 2분 5초" to "1h 2m 5s"),
            0.0 to ("0초" to "0s"),
            999.0 to ("0초" to "0s"),
            1000.0 to ("1초" to "1s"),
            3_600_000.0 to ("1시간" to "1h"),
            90_061_000.0 to ("1일 1시간 1분 1초" to "1d 1h 1m 1s"),
            -65_000.0 to ("-1분 5초" to "-1m 5s"),
        )
        for ((millis, expected) in cases)
            assertEquals("$millis", expected.first, Formats.duration(millis))
        Formats.locale = { Locale.ENGLISH }
        for ((millis, expected) in cases)
            assertEquals("$millis", expected.second, Formats.duration(millis))
        failsWithIllegalArgument { Formats.duration(Double.NaN) }
        failsWithIllegalArgument { Formats.duration(Double.POSITIVE_INFINITY) }
        failsWithIllegalArgument { Formats.duration(Double.NEGATIVE_INFINITY) }
    }

    @Test
    fun `josa picks the form that fits the word's last sound`() {
        for ((word, pair, expected) in listOf(
            Triple("사과", "을/를", "사과를"),
            Triple("책", "을/를", "책을"),
            Triple("책", "를/을", "책을"),
            Triple("서울", "으로/로", "서울로"),
            Triple("부산", "으로/로", "부산으로"),
            Triple("제주", "으로/로", "제주로"),
            Triple("3", "이/가", "3이"),
            Triple("2", "이/가", "2가"),
            Triple("7", "으로/로", "7로"),
            Triple("10", "은/는", "10은"),
            Triple("bot", "은/는", "bot은"),
            Triple("user", "이/가", "user가"),
            Triple("mail", "으로/로", "mail로"),
            Triple("\"사과\"", "을/를", "\"사과\"를"),
            Triple("“사과”", "을/를", "“사과”를"),
            Triple("‘책’", "을/를", "‘책’을"),
            Triple("（서울）", "으로/로", "（서울）로"),
            Triple("", "은/는", "는"),
            Triple("민수", "아/야", "민수야"),
            Triple("영철", "아/야", "영철아"),
            Triple("철수", "이랑/랑", "철수랑"),
            Triple("지민", "랑/이랑", "지민이랑"),
        ))
            assertEquals("$word $pair", expected, Josa.attach(word, pair))
        failsWithIllegalArgument { Josa.attach("사과", "을") }
    }

    @Test
    fun `Format gives the same text in both engines`() {
        Engines { mapOf("Format" to FormatApi.Format()) }.use { engines ->
            engines.both("Format.number(1234567.5)", "1,234,567.5")
            engines.both("Format.number(5)", "5")
            engines.both("Format.date(new Date(0), \"yyyy-MM-dd\", \"UTC\")", "1970-01-01")
            engines.both("Format.duration(3725000)", "1시간 2분 5초")
            engines.both("Format.josa(\"사과\", \"을/를\")", "사과를")
            // A time that is none, which would otherwise read as 1970.
            for (time in listOf("NaN", "new Date('x')", "Infinity")) {
                val (neon, rhino) = engines.errors("Format.date($time, \"yyyy\")")
                assertTrue("$time: $neon", "Format.date" in neon)
                assertTrue("$time: $rhino", "Format.date" in rhino)
            }
        }
    }
}
