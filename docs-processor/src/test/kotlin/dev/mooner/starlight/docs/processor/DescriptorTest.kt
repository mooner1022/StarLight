package dev.mooner.starlight.docs.processor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DescriptorTest {

    @Test
    fun `splits a method's parameters`() {
        assertEquals(listOf("I", "[Ljava/lang/String;", "[[J", "Ljava/util/List;"),
            parameterDescriptors("(I[Ljava/lang/String;[[JLjava/util/List;)V"))
        assertEquals(emptyList<String>(), parameterDescriptors("()V"))
    }

    @Test
    fun `gives up on a malformed descriptor instead of looping`() {
        assertNull(parameterDescriptors("(Ljava/lang/String)V"))
        assertNull(parameterDescriptors("(Ljava/lang/String)Ljava/lang/Object;"))
        assertNull(parameterDescriptors("([)V"))
        assertNull(parameterDescriptors("I)V"))
    }
}
