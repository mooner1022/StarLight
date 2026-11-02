package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.tools.WebPages
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** What web_fetch makes of a page. */
class WebPagesTest {

    private val html = """
        <html><head><title>Weather API</title><script>track()</script><style>p{}</style></head>
        <body>
          <nav><a href="/home">Home</a> <a href="/pricing">Pricing</a></nav>
          <main>
            <h1>Weather API</h1>
            <p>Get the forecast for a city.</p>
            <h2>Endpoints</h2>
            <ul><li>GET /forecast</li><li>GET /now</li></ul>
            <pre>curl https://api.example.com/forecast?city=Seoul
          -H "Key: …"</pre>
            <p>See <a href="/docs/auth">authentication</a>.</p>
          </main>
          <footer>© Example</footer>
        </body></html>
    """.trimIndent()

    @Test
    fun keepsTheMainContentAsBlocks() {
        val page = WebPages.read(Jsoup.parse(html, "https://api.example.com/docs"), "https://api.example.com/docs")
        assertEquals("Weather API", page.title)
        val lines = page.text.lines()
        assertEquals("# Weather API", lines.first())
        assertTrue("## Endpoints" in lines)
        assertTrue("- GET /forecast" in lines && "- GET /now" in lines)
        // Code keeps its line breaks and indentation.
        assertTrue(page.text.contains("forecast?city=Seoul\n  -H"))
        // Menus, footers and scripts are left out.
        assertFalse(page.text.contains("Pricing") || page.text.contains("© Example") || page.text.contains("track()"))
        assertEquals(listOf("authentication" to "https://api.example.com/docs/auth"), page.links)
    }

    @Test
    fun refusesAddressesThatArentWebPages() {
        assertThrows(IllegalArgumentException::class.java) { WebPages.fetch("file:///sdcard/StarLight/projects/a/.env") }
        assertThrows(IllegalArgumentException::class.java) { WebPages.fetch("javascript:alert(1)") }
        assertThrows(IllegalArgumentException::class.java) { WebPages.fetch("not a url") }
    }
}
