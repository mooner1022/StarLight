package dev.mooner.starlight.agent.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemberKeyTest {

    @Test
    fun `splits a member key into its name, parameters and return type`() {
        assertEquals(Triple("send", listOf("Ljava/lang/String;", "[I", "Z"), "V"),
            ScriptCatalog.parseKey("send(Ljava/lang/String;[IZ)V"))
        assertEquals(Triple("get", emptyList<String>(), "Ljava/lang/Object;"), ScriptCatalog.parseKey("get()Ljava/lang/Object;"))
    }

    @Test
    fun `a malformed key is rejected instead of parsed forever`() {
        assertNull(ScriptCatalog.parseKey("foo(Ljava/lang/String)V"))
        assertNull(ScriptCatalog.parseKey("foo(Ljava/lang/String)Ljava/lang/Object;"))
        assertNull(ScriptCatalog.parseKey("foo([)V"))
        assertNull(ScriptCatalog.parseKey("foo"))
    }
}
