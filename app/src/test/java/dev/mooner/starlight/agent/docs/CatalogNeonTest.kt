package dev.mooner.starlight.agent.docs

import dev.mooner.neonjs.interop.HostClassInfo
import dev.mooner.starlight.agent.docs.ScriptCatalog.Signature
import dev.mooner.starlight.agent.docs.ScriptCatalog.TypeInfo
import dev.mooner.starlight.api.api2.BotManagerApi
import dev.mooner.starlight.languages.neonjs.buildHostAccess
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Every member the docs show, checked against what NeonJS itself exposes for every class scripts reach: a method
 * with each arity its optional parameters allow, a property's getter (and setter if writable), a constructor. Rules
 * that only fixtures check in [ScriptViewTest] are checked here on the real classes.
 */
class CatalogNeonTest {

    private val catalog = TestDocs.catalog

    private val access = buildHostAccess(restricted = false)

    @Test
    fun `NeonJS exposes every member the docs show`() {
        val problems = mutableListOf<String>()
        for (cls in catalog.closure)
            check(catalog.describe(cls), HostClassInfo.of(cls, access), isStatic = false, problems)
        for (api in catalog.apis.filter { it.isClass })
            check(catalog.describe(api.cls, isClass = true), HostClassInfo.of(api.cls, access), isStatic = true, problems)
        assertTrue("Shown but not exposed (${problems.size}):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `assignments the docs mention work in NeonJS`() {
        // Bot's KDoc: `bot.commandPrefix = "/"` and `bot.power = false`.
        val bot = HostClassInfo.of(BotManagerApi.Bot::class.java, access)
        assertTrue(bot.instanceSetters.keys.toString(), "commandPrefix" in bot.instanceSetters)
        assertTrue(bot.instanceSetters.keys.toString(), "power" in bot.instanceSetters)
    }

    private fun check(type: TypeInfo, info: HostClassInfo, isStatic: Boolean, problems: MutableList<String>) {
        val where = type.cls.name + if (isStatic) " (static)" else ""
        val methods = if (isStatic) info.staticMethods else info.instanceMethods
        val getters = if (isStatic) info.staticGetters else info.instanceGetters
        val setters = if (isStatic) info.staticSetters else info.instanceSetters
        val fields = if (isStatic) info.staticFields else info.instanceFields

        for (signature in type.methods) {
            val overloads = methods[signature.name]
            if (overloads == null) {
                problems += "$where: method ${signature.name}"
                continue
            }
            for (arity in arities(signature))
                if (overloads.none { it.parameterCount == arity || (it.isVarArgs && arity >= it.parameterCount - 1) })
                    problems += "$where: ${signature.name} with $arity arguments"
        }
        for (property in type.properties) {
            val field = fields[property.name]
            if (getters[property.name] == null && field == null)
                problems += "$where: property ${property.name}"
            if (!property.isReadOnly && setters[property.name] == null && (field == null || Modifier.isFinal(field.modifiers)))
                problems += "$where: writable ${property.name}"
        }
        if (isStatic)
            for (constructor in type.constructors)
                for (arity in arities(constructor))
                    if (info.constructors.none { it.parameterCount == arity })
                        problems += "$where: constructor with $arity arguments"
    }

    /** The argument counts a signature claims: every count from its required parameters to all of them. */
    private fun arities(signature: Signature): IntRange =
        signature.params.count { !it.optional }..signature.params.size
}
