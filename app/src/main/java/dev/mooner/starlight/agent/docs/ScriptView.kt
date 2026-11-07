package dev.mooner.starlight.agent.docs

import dev.mooner.neonjs.HostName
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * What a script sees of a class through NeonJS's host interop, mirroring its `HostClassInfo` (docs/agent-docs.md
 * §6.2): public methods and fields under their JS names, bean getters and setters as properties, and the
 * constructors of a class exposed as one.
 */
class ScriptView(val cls: Class<*>) {

    class Property(
        val name: String,
        val getter: Method?,
        val setter: Method?,
        val javaField: Field?,
    ) {
        val isWritable: Boolean
            get() = setter != null || javaField != null && !Modifier.isFinal(javaField.modifiers)
    }

    class Members(
        /** Methods by JS name, without the ones read as properties. */
        val methods: Map<String, List<Method>>,
        val properties: List<Property>,
    )

    /**
     * The members of the JDK collection interfaces [cls] implements, which scripts use through `JavaList`/`JavaMap`;
     * by name, since Kotlin implements them with its own names and types (`getSize()`, a `get(String)`).
     */
    private val collectionMethods: Set<String> =
        COLLECTIONS.filter { it.isAssignableFrom(cls) }
            .flatMap { it.methods.map(Method::getName) + KOTLIN_COLLECTION_NAMES }
            .toSet()

    val constructors: List<Constructor<*>> =
        if (Modifier.isAbstract(cls.modifiers) || cls.isInterface) emptyList()
        else cls.constructors.filter { !it.isSynthetic && it.parameterTypes.none(::isKotlinMarker) }

    val instance: Members = members(isStatic = false)

    val static: Members = members(isStatic = true)

    private fun members(isStatic: Boolean): Members {
        val fields = cls.fields
            .filter { Modifier.isStatic(it.modifiers) == isStatic && isVisible(it) && it.name !in HIDDEN_FIELDS }
            .associateBy { jsName(it, it.name) }
        val methods = LinkedHashMap<String, MutableList<Method>>()
        for (method in cls.methods.sortedWith(compareBy({ it.name }, { it.parameterCount }))) {
            if (Modifier.isStatic(method.modifiers) != isStatic || !isVisible(method) || method.isBridge)
                continue
            val list = methods.getOrPut(jsName(method, method.name)) { mutableListOf() }
            if (list.none { it.parameterTypes.contentEquals(method.parameterTypes) })
                list += method
        }

        val getters = LinkedHashMap<String, Method>()
        val setters = LinkedHashMap<String, Method>()
        for ((name, list) in methods) {
            for (method in list) {
                val property = when {
                    name.length > 3 && name.startsWith("get") && method.parameterCount == 0 && method.returnType != Void.TYPE ->
                        decapitalize(name.substring(3))
                    name.length > 2 && name.startsWith("is") && method.parameterCount == 0 &&
                        (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == Boolean::class.javaObjectType) ->
                        decapitalize(name.substring(2))
                    else -> null
                } ?: continue
                if (property in methods || property in fields)
                    continue
                getters.putIfAbsent(property, method)
            }
            if (name.length > 3 && name.startsWith("set")) {
                val property = decapitalize(name.substring(3))
                if (property in methods || property in fields)
                    continue
                // A fluent builder's setter returns the builder: it reads as the method it is meant to be.
                list.firstOrNull { it.parameterCount == 1 && it.returnType == Void.TYPE }?.let { setters.putIfAbsent(property, it) }
            }
        }

        // A setter with no getter (Bot.setCommandPrefix) stays the method it was written as: shown as a property, it
        // would read as one a script can read back.
        setters.keys.retainAll(getters.keys)

        // A getter that is one of several overloads (getRooms() of an @JvmOverloads getRooms(packageName)) stays
        // with them, where it makes the parameters optional; scripts can call it both ways.
        val accessors = (getters.values + setters.values).toSet()
        val remaining = methods
            .mapValues { (_, list) -> if (list.size > 1) list else list.filter { it !in accessors } }
            .filterValues { it.isNotEmpty() }
        val properties = buildList {
            for (name in (getters.keys + setters.keys).distinct().sorted())
                add(Property(name, getters[name], setters[name], null))
            for ((name, field) in fields.toSortedMap())
                add(Property(name, null, null, field))
        }
        return Members(remaining.toSortedMap(), properties)
    }

    private fun isVisible(member: Member): Boolean {
        if (member.isSynthetic || member.declaringClass == Any::class.java)
            return false
        if (member is Method && member.name in collectionMethods)
            return false
        // An enum's own constants and members stay; of java.lang.Enum's, only name() and ordinal() are worth a line.
        if (member.declaringClass == java.lang.Enum::class.java && member.name !in ENUM_MEMBERS)
            return false
        val name = member.name
        return '$' !in name && name !in HIDDEN_METHODS && !COMPONENT.matches(name)
    }

    companion object {

        private val HIDDEN_METHODS = setOf(
            "equals", "hashCode", "toString", "getClass", "wait", "notify", "notifyAll",
            "copy",
        )

        /** Kotlin's instance and companion of an `object` or a class with a companion. */
        private val HIDDEN_FIELDS = setOf("INSTANCE", "Companion")

        private val COMPONENT = Regex("component\\d+")

        private val ENUM_MEMBERS = setOf("name", "ordinal")

        /** Collection interfaces whose members a class's description leaves out, saying `extends JavaMap<…>` instead. */
        private val KOTLIN_COLLECTION_NAMES = listOf("getSize", "getEntries", "getKeys", "getValues")

        val COLLECTIONS: List<Class<*>> = listOf(
            java.util.Map::class.java, java.util.List::class.java, java.util.Collection::class.java,
            java.lang.Iterable::class.java,
        )

        fun jsName(member: AccessibleObject, default: String): String =
            member.getAnnotation(HostName::class.java)?.value ?: default

        private fun decapitalize(name: String): String =
            name.replaceFirstChar(Char::lowercaseChar)

        /** The marker parameter of Kotlin's synthetic default-argument constructors. */
        private fun isKotlinMarker(type: Class<*>): Boolean =
            type.name == "kotlin.jvm.internal.DefaultConstructorMarker"
    }
}
