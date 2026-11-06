package dev.mooner.starlight.plugincore.plugin

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun info(extra: String = "") = json.decodeFromString<PluginInfo>(
        """{"id":"a","name":"A","main_class":"a.Main","version":"1.0.0","api_version":"0.3.0","authors":[],"description":""$extra}"""
    )

    @Test
    fun categoryDefaultsWhenMissing() {
        assertEquals(PluginCategory.DEFAULT, info().category)
    }

    @Test
    fun categoryIsRead() {
        assertEquals(PluginCategory.LIBRARY, info(""","category":"library"""").category)
        assertEquals(PluginCategory.DEVTOOLS, info(""","category":" DevTools """").category)
    }

    @Test
    fun unknownCategoryFallsBackToDefault() {
        assertEquals(PluginCategory.DEFAULT, info(""","category":"games"""").category)
        assertEquals(PluginCategory.DEFAULT, info(""","category":" """").category)
    }

    @Test
    fun categoryIsEncodedById() {
        val encoded = json.encodeToString(PluginInfo.serializer(), info(""","category":"engine""""))
        assertTrue(""""category":"engine"""" in encoded)
        assertEquals(PluginCategory.ENGINE, json.decodeFromString<PluginInfo>(encoded).category)
    }
}
