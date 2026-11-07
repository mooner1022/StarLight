package dev.mooner.starlight.agent.docs

import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.jvm.kotlinProperty

/**
 * What scripts can use, joined with its docs: the registered APIs and events, and the types they lead to inside the
 * documented packages. See docs/agent-docs.md §5. Plain data in, plain data out; [AgentDocs] fills it from the
 * running app.
 */
class ScriptCatalog(
    val docs: DocFiles,
    val apis: List<ApiInfo>,
    val events: List<EventInfo>,
) {

    class ApiInfo(
        val name: String,
        val cls: Class<*>,
        /** Exposed as the class itself (constructors and static members) rather than an instance. */
        val isClass: Boolean,
        val levels: List<String>,
        /** `starlight`, or the ID of the plugin that registered it. */
        val source: String,
    )

    class EventInfo(
        val id: String,
        val eventClass: Class<*>,
        /** How a script receives it, as lists show it: `onMessage(event)`, `bot.on(Event.MESSAGE)`. */
        val usage: String,
        val functionName: String,
        val levels: List<String>,
        /** What the script's function or listener receives. */
        val arguments: List<Pair<String, Class<*>>>,
        val source: String,
        /** The name a listener is added under, for an event received through one (`ProjectEvent.listenerName`). */
        val listenerName: String? = null,
    )

    class Param(val name: String, val type: TypeRef, val optional: Boolean, val vararg: Boolean)

    class Signature(
        val name: String,
        val params: List<Param>,
        val returns: TypeRef,
        val doc: DocText?,
    )

    class PropertyInfo(
        val name: String,
        val type: TypeRef,
        val isReadOnly: Boolean,
        val doc: DocText?,
        /** A string constant's value, which the docs show as its type: `readonly MESSAGE: "message"`. */
        val value: String? = null,
    )

    class TypeInfo(
        val cls: Class<*>,
        val name: String,
        val doc: DocText?,
        /** The Java `Map` or `List` it is, whose members scripts reach through `JavaMap`/`JavaList`. */
        val collection: TypeRef?,
        val constructors: List<Signature>,
        val properties: List<PropertyInfo>,
        val methods: List<Signature>,
        /** Documented classes its members take or return. */
        val references: Set<Class<*>>,
        /** Classes its members take or return that have no docs: the platform's, or another module's. */
        val external: Set<Class<*>>,
        /** Public members with no docs, for the coverage test and the log. */
        val undocumented: List<String>,
    )

    private val described = HashMap<Pair<Class<*>, Boolean>, TypeInfo>()

    fun api(name: String): ApiInfo? =
        apis.firstOrNull { it.name == name } ?: apis.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun event(id: String): EventInfo? =
        events.firstOrNull { it.id == id }

    /** The name a documented class goes by in rendered docs: its simple name. */
    fun nameOf(cls: Class<*>): String =
        cls.name.substringAfterLast('.').substringAfterLast('$')

    /**
     * A class reachable from an API or event, by the name [nameOf] gives it or its full name: a documented one, or else
     * one of [undocumentedTypes].
     */
    fun typeNamed(name: String): Class<*>? {
        fun matches(cls: Class<*>) =
            nameOf(cls) == name || cls.name == name || cls.name.replace('$', '.') == name || KOTLIN_NAMES[name] == cls.name
        return closure.firstOrNull(::matches) ?: undocumentedTypes.firstOrNull(::matches)
    }

    /**
     * The classes scripts reach that have no docs. Those of StarLight's and the plugins' own code ([isOwn]), such as
     * PluginCore's `Project` or a plugin's types, are followed through their members, [UNDOCUMENTED_DEPTH] steps from
     * the documented ones; others (the platform's, and libraries a plugin bundles) only appear.
     */
    val undocumentedTypes: Set<Class<*>> by lazy {
        val seen = LinkedHashSet<Class<*>>()
        var reached: List<Class<*>> = closure.flatMap { cls ->
            externalOf(cls, isClass = false) + if (apis.any { it.cls == cls && it.isClass }) externalOf(cls, isClass = true) else emptySet()
        } + events.flatMap { event -> event.arguments.map { it.second } }
        repeat(UNDOCUMENTED_DEPTH) {
            val followed = reached.filter { it !in closure && seen.add(it) && isOwn(it) }
            reached = followed.flatMap { externalOf(it, isClass = false) }
        }
        reached.filterTo(seen) { it !in closure }
        seen
    }

    /**
     * [cls]'s undocumented references; none when its members can't be read, as when they name classes Android
     * doesn't have (a bundled library's `javax.sound`).
     */
    private fun externalOf(cls: Class<*>, isClass: Boolean): Set<Class<*>> =
        runCatching { describe(cls, isClass).external }.getOrDefault(emptySet())

    /** Package prefixes of the code APIs come from: StarLight's, and each plugin's own beside the libraries it bundles. */
    private val ownPackages: List<String> by lazy {
        (listOf("dev.mooner.") + apis.map { api -> api.cls.name.split('.').take(3).joinToString(".", postfix = ".") }).distinct()
    }

    /** Whether [cls] is StarLight's or a plugin's own, rather than the platform's or a library's. */
    fun isOwn(cls: Class<*>): Boolean =
        !isPlatform(cls) && ownPackages.any(cls.name::startsWith)

    /** A class of the closure by its Kotlin name, as doc files write types: nested classes joined with `.`. */
    fun classForKotlinName(name: String): Class<*>? =
        byKotlinName[name]

    private val byKotlinName: Map<String, Class<*>> by lazy {
        closure.associateBy { it.name.replace('$', '.') }
    }

    /** [closure], computed once. */
    val closure: Set<Class<*>> by lazy { closure() }

    fun docOf(cls: Class<*>): DocText? =
        docs.docOf(cls)?.doc

    /**
     * [cls] as a script sees it. As a class ([isClass]), its constructors and static members, as an API exposed
     * with `InstanceType.CLASS` is; otherwise an instance's members.
     */
    fun describe(cls: Class<*>, isClass: Boolean = false): TypeInfo =
        synchronized(described) { described.getOrPut(cls to isClass) { build(cls, isClass) } }

    /** Every documented class an API or event leads to, the APIs' and events' own classes included. */
    private fun closure(): Set<Class<*>> {
        val seen = LinkedHashSet<Class<*>>()
        val queue = ArrayDeque<Class<*>>()
        fun visit(cls: Class<*>) {
            if (seen.add(cls))
                queue += cls
        }
        apis.forEach { visit(it.cls) }
        events.forEach { event -> event.arguments.forEach { (_, cls) -> if (docs.isDocumented(cls)) visit(cls) } }
        while (queue.isNotEmpty()) {
            val cls = queue.removeFirst()
            // A plugin's class may name classes Android doesn't have; it is still listed, and fails only when described.
            runCatching { describe(cls).references }.getOrDefault(emptySet()).forEach(::visit)
            if (apis.any { it.cls == cls && it.isClass })
                runCatching { describe(cls, isClass = true).references }.getOrDefault(emptySet()).forEach(::visit)
        }
        return seen
    }

    /**
     * [field]'s value when it is a string constant: a final static field, or a final field of a class that only holds
     * such fields (API2's `Event`), which is safe to construct to read them. Null otherwise.
     */
    private fun constantOf(field: Field, isStatic: Boolean): String? {
        if (field.type != String::class.java || !Modifier.isFinal(field.modifiers))
            return null
        val owner = field.declaringClass
        val instance = when {
            isStatic -> null
            !holdsOnlyConstants(owner) -> return null
            else -> synchronized(constantHolders) {
                constantHolders.getOrPut(owner) { runCatching { owner.getConstructor().newInstance() }.getOrNull() }
            } ?: return null
        }
        return runCatching { field.get(instance) as String? }.getOrNull()
    }

    private val constantHolders = HashMap<Class<*>, Any?>()

    private fun holdsOnlyConstants(cls: Class<*>): Boolean =
        cls.declaredMethods.none { Modifier.isPublic(it.modifiers) && !it.isSynthetic } &&
            cls.declaredFields.all { Modifier.isStatic(it.modifiers) || Modifier.isFinal(it.modifiers) } &&
            runCatching { cls.getConstructor() }.isSuccess

    private fun build(cls: Class<*>, isClass: Boolean): TypeInfo {
        val view = ScriptView(cls)
        val members = (if (isClass) view.static else view.instance).let { all ->
            if (!docs.isDocumented(cls))
                return@let all
            // A documented class shows what it declares itself, or inherits from documented classes: not what an outside
            // supertype brings, such as `CoroutineScope.coroutineContext` on Project. Enum's are described apart.
            fun shown(owner: Class<*>) = owner == java.lang.Enum::class.java || docs.isDocumented(owner)
            ScriptView.Members(
                methods = all.methods.mapValues { (_, list) -> list.filter { shown(it.declaringClass) } }.filterValues { it.isNotEmpty() },
                properties = all.properties.filter { shown((it.getter ?: it.javaField ?: it.setter)!!.declaringClass) },
            )
        }
        val references = LinkedHashSet<Class<*>>()
        val external = LinkedHashSet<Class<*>>()
        val undocumented = mutableListOf<String>()

        fun reach(type: Type) {
            for (reached in classesIn(type))
                if (reached != cls)
                    (if (docs.isDocumented(reached)) references else external) += reached
        }

        // A class of static members only, such as Utils, isn't meant to be constructed.
        val constructible = isClass && view.instance.let { it.properties.isNotEmpty() || it.methods.isNotEmpty() }
        val constructors = if (constructible) signatures("constructor", view.constructors, undocumented) else emptyList()
        if (constructible)
            view.constructors.filterNot(::isSuppressed).forEach { constructor -> constructor.genericParameterTypes.forEach(::reach) }

        // A getter written as a function (`fun getCurrentBot()`) is meant to be called, as MessengerBot's docs call
        // it; NeonJS also reads it as a property, but the docs show the form its author chose.
        val calledGetters = LinkedHashMap<String, MutableList<Method>>()
        val properties = members.properties.mapNotNull { property ->
            val getter = property.getter
            val field = property.javaField
            if (getter != null) {
                val members = docs.membersOf(getter.declaringClass)
                val declared = members[key(getter)] ?: prefixOwner(getter, members)
                if (declared?.kind == "function") {
                    calledGetters.getOrPut(getter.name) { mutableListOf() } += getter
                    property.setter?.let { calledGetters.getOrPut(it.name) { mutableListOf() } += it }
                    return@mapNotNull null
                }
            }
            val entry = when {
                getter != null -> docs.membersOf(getter.declaringClass).let { members ->
                    members[key(getter)] ?: prefixOwner(getter, members)
                }
                else -> field?.let { docs.membersOf(it.declaringClass)["field:" + it.name] }
            }
            if (entry?.doc?.suppress == true)
                return@mapNotNull null
            // A property has a getter or is a field: ScriptView leaves a setter without a getter as a method.
            val reflected: Type = getter?.genericReturnType ?: field!!.genericType
            reach(reflected)
            if (entry?.doc == null)
                undocumented += property.name
            PropertyInfo(
                name = property.name,
                type = entry?.returns?.let(TypeRef::parse)
                    ?: TypeRef.of(reflected).let { if (isKotlinNullable(getter, field)) it.copy(nullable = true) else it },
                isReadOnly = !property.isWritable,
                doc = entry?.doc,
                value = field?.takeIf { getter == null }?.let { constantOf(it, isClass) },
            )
        }

        val allMethods = (members.methods.keys + calledGetters.keys).toSortedSet().associateWith { name ->
            (members.methods[name].orEmpty() + calledGetters[name].orEmpty()).distinct()
        }
        val methods = allMethods.flatMap { (name, list) ->
            // Only what is shown leads on: a member marked @suppress names no types for the "Without docs" list.
            list.filterNot(::isSuppressed).forEach { method ->
                reach(method.genericReturnType)
                method.genericParameterTypes.forEach(::reach)
            }
            signatures(name, list, undocumented)
        }

        return TypeInfo(
            cls = cls,
            name = nameOf(cls),
            doc = docs.docOf(cls)?.doc?.takeUnless { it.suppress },
            collection = if (isClass) null else collectionOf(cls),
            constructors = constructors,
            properties = properties,
            methods = methods,
            references = references,
            external = external,
            undocumented = undocumented.apply { if (docOf(cls) == null && docs.isDocumented(cls)) add(0, "(class)") },
        )
    }

    /** Whether [executable]'s doc entry, or that of the declaration it is an overload of, is marked @suppress. */
    private fun isSuppressed(executable: Executable): Boolean {
        val members = docs.membersOf(executable.declaringClass)
        return (members[key(executable)] ?: prefixOwner(executable, members))?.doc?.suppress == true
    }

    /**
     * Groups [executables] into signatures: the overloads `@JvmOverloads` made of one documented declaration become
     * one signature, whose parameters past the shortest overload are optional. Undocumented overloads stay apart.
     */
    private fun signatures(name: String, executables: List<Executable>, undocumented: MutableList<String>): List<Signature> {
        val groups = LinkedHashMap<DocMember?, MutableList<Executable>>()
        val loose = mutableListOf<Executable>()
        for (executable in executables) {
            val members = if (executable.declaringClass == java.lang.Enum::class.java)
                ENUM_DOCS
            else
                docs.membersOf(executable.declaringClass)
            val entry = members[key(executable)] ?: prefixOwner(executable, members)
            if (entry == null) loose += executable else groups.getOrPut(entry) { mutableListOf() } += executable
        }
        val result = mutableListOf<Signature>()
        for ((entry, overloads) in groups) {
            entry ?: continue
            if (entry.doc?.suppress == true)
                continue
            val shortest = overloads.minOf { it.parameterCount }
            if (entry.doc == null)
                undocumented += name
            result += Signature(
                name = name,
                params = entry.params.mapIndexed { i, param ->
                    Param(param.name, TypeRef.parse(param.type), optional = i >= shortest, vararg = param.vararg)
                },
                returns = entry.returns?.takeIf { executables.first() is Method }?.let(TypeRef::parse) ?: TypeRef("kotlin.Unit"),
                doc = entry.doc,
            )
        }
        for (executable in loose) {
            undocumented += name
            // An overload @JvmOverloads made has no metadata of its own: the longer one it shortens has the names.
            val kotlin = runCatching { kotlinFunctionOf(executable) }.getOrNull()
            val names = kotlinParameterNames(executable) ?: executables
                .filter { it.parameterCount > executable.parameterCount && it.parameterTypes.take(executable.parameterCount) == executable.parameterTypes.toList() }
                .firstNotNullOfOrNull { longer -> kotlinParameterNames(longer)?.take(executable.parameterCount) }
            result += Signature(
                name = name,
                params = executable.parameters.mapIndexed { i, parameter ->
                    val vararg = executable.isVarArgs && i == executable.parameterCount - 1
                    val javaType = executable.genericParameterTypes.getOrElse(i) { parameter.type }
                    Param(
                        name = names?.getOrNull(i) ?: parameter.name,
                        // A vararg reflects as its array, and the declaration adds the `[]` itself: the element is its type,
                        // as the docs give it.
                        type = TypeRef.of(if (vararg) elementOf(javaType) else javaType).let { type ->
                            val nullable = runCatching { kotlin?.valueParameters?.getOrNull(i)?.type?.isMarkedNullable }.getOrNull()
                            if (nullable == true) type.copy(nullable = true) else type
                        },
                        optional = false,
                        vararg = vararg,
                    )
                },
                returns = (executable as? Method)?.genericReturnType?.let(TypeRef::of)
                    ?.let { if (runCatching { kotlin?.returnType?.isMarkedNullable }.getOrNull() == true) it.copy(nullable = true) else it }
                    ?: TypeRef("kotlin.Unit"),
                doc = null,
            )
        }
        return result
    }

    private fun kotlinFunctionOf(executable: Executable): KFunction<*>? = when (executable) {
        is Method -> executable.kotlinFunction
        is Constructor<*> -> executable.kotlinFunction
        else -> null
    }

    /** The element type of the array type [type], or [type] when it isn't one. */
    private fun elementOf(type: Type): Type = when (type) {
        is GenericArrayType -> type.genericComponentType
        is Class<*> -> type.componentType ?: type
        else -> type
    }

    /**
     * Whether Kotlin's metadata marks the property of [getter] or [field] nullable, which Java reflection doesn't tell:
     * an undocumented `val build: String?` reads as `string | null`.
     */
    private fun isKotlinNullable(getter: Method?, field: Field?): Boolean = runCatching {
        field?.kotlinProperty?.returnType?.isMarkedNullable
            ?: getter?.let { method ->
                method.kotlinFunction?.returnType?.isMarkedNullable
                    ?: method.declaringClass.kotlin.memberProperties.firstOrNull { it.javaGetter == method }?.returnType?.isMarkedNullable
            }
    }.getOrNull() == true

    /**
     * The parameter names Kotlin's metadata keeps for [executable], which Java reflection reads as `arg0` unless the
     * code was compiled with `-java-parameters`; `null` for Java code, or when the names don't line up.
     */
    private fun kotlinParameterNames(executable: Executable): List<String>? = runCatching {
        val function = kotlinFunctionOf(executable) ?: return null
        function.parameters.filter { it.kind == KParameter.Kind.VALUE }.map { it.name ?: return null }
            .takeIf { it.size == executable.parameterCount }
    }.getOrNull()

    /** The `Map<K, V>` or `List<T>` [cls] implements, with its type arguments where they are known. */
    private fun collectionOf(cls: Class<*>): TypeRef? {
        val seen = HashSet<Class<*>>()
        val queue = ArrayDeque<Type>(listOfNotNull(*cls.genericInterfaces, cls.genericSuperclass))
        while (queue.isNotEmpty()) {
            val type = queue.removeFirst()
            val raw = (type as? ParameterizedType)?.rawType as? Class<*> ?: type as? Class<*> ?: continue
            if (raw == java.util.Map::class.java || raw == java.util.List::class.java)
                return TypeRef.of(type)
            if (seen.add(raw))
                queue += listOfNotNull(*raw.genericInterfaces, raw.genericSuperclass)
        }
        return null
    }

    /** The documented declaration [executable] is a shorter `@JvmOverloads` overload of. */
    private fun prefixOwner(executable: Executable, members: Map<String, DocMember>): DocMember? {
        val name = if (executable is Constructor<*>) "<init>" else executable.name
        val params = executable.parameterTypes.map(::descriptor)
        val returns = (executable as? Method)?.returnType?.let(::descriptor) ?: "V"
        // The closest one: prepare() belongs to prepare(throwOnError), not to prepare(botName, throwOnError).
        return members.entries.filter { (memberKey, member) ->
            if (member.kind != "function" && member.kind != "constructor")
                return@filter false
            val (memberName, memberParams, memberReturns) = parseKey(memberKey) ?: return@filter false
            memberName == name && memberReturns == returns &&
                memberParams.size > params.size && memberParams.subList(0, params.size) == params
        }.minByOrNull { it.value.params.size }?.value
    }

    companion object {

        /** Docs for what scripts see of `java.lang.Enum`, which no doc file holds. */
        private val ENUM_DOCS = mapOf(
            "name()Ljava/lang/String;" to DocMember(
                kind = "function", name = "name", returns = "kotlin.String",
                doc = DocText(summary = "The constant's name, as written in code."),
            ),
            "ordinal()I" to DocMember(
                kind = "function", name = "ordinal", returns = "kotlin.Int",
                doc = DocText(summary = "The constant's position among its type's constants, from 0."),
            ),
        )

        /** The JVM name and descriptor a doc file keys a method or constructor by. */
        fun key(executable: Executable): String {
            val name = if (executable is Constructor<*>) "<init>" else executable.name
            val returns = (executable as? Method)?.returnType?.let(::descriptor) ?: "V"
            return name + "(" + executable.parameterTypes.joinToString("") { descriptor(it) } + ")" + returns
        }

        fun descriptor(cls: Class<*>): String = when {
            cls == Void.TYPE -> "V"
            cls == java.lang.Boolean.TYPE -> "Z"
            cls == java.lang.Byte.TYPE -> "B"
            cls == java.lang.Character.TYPE -> "C"
            cls == java.lang.Short.TYPE -> "S"
            cls == java.lang.Integer.TYPE -> "I"
            cls == java.lang.Long.TYPE -> "J"
            cls == java.lang.Float.TYPE -> "F"
            cls == java.lang.Double.TYPE -> "D"
            cls.isArray -> "[" + descriptor(cls.componentType!!)
            else -> "L" + cls.name.replace('.', '/') + ";"
        }

        /** Splits a key into its name, parameter descriptors and return descriptor. */
        fun parseKey(key: String): Triple<String, List<String>, String>? {
            val open = key.indexOf('(')
            val close = key.indexOf(')', open)
            if (open < 0 || close < 0)
                return null
            val params = mutableListOf<String>()
            var i = open + 1
            while (i < close) {
                val start = i
                while (key[i] == '[')
                    i++
                i = if (key[i] == 'L') key.indexOf(';', i) + 1 else i + 1
                params += key.substring(start, i)
            }
            return Triple(key.substring(0, open), params, key.substring(close + 1))
        }

        /** How far [undocumentedTypes] follows undocumented types: `Projects` gives `Project`, then `ProjectInfo`. */
        const val UNDOCUMENTED_DEPTH = 2

        /** Java's, Kotlin's, Android's and the libraries' classes: described by their own references, not here. */
        fun isPlatform(cls: Class<*>): Boolean =
            PLATFORM_PACKAGES.any(cls.name::startsWith)

        private val PLATFORM_PACKAGES = listOf(
            "java.", "javax.", "kotlin.", "kotlinx.", "android.", "androidx.", "dalvik.", "org.", "com.google.", "okhttp3.",
            "okio.", "dev.mooner.neonjs.",
        )

        /** Kotlin's names for Java classes, as doc files write types. */
        val KOTLIN_NAMES = mapOf(
            "kotlin.Throwable" to "java.lang.Throwable", "kotlin.Exception" to "java.lang.Exception",
            "kotlin.CharSequence" to "java.lang.CharSequence", "kotlin.Comparable" to "java.lang.Comparable",
        )

        /** The classes [type] names, its type arguments and array components included. */
        fun classesIn(type: Type): List<Class<*>> = when (type) {
            is Class<*> -> if (type.isArray) classesIn(type.componentType!!) else if (type.isPrimitive) emptyList() else listOf(type)
            is ParameterizedType -> classesIn(type.rawType) + type.actualTypeArguments.flatMap(::classesIn)
            is GenericArrayType -> classesIn(type.genericComponentType)
            // `(Throwable?) -> Unit` takes `? super Throwable`: the lower bound is the type that matters.
            is WildcardType -> (type.upperBounds + type.lowerBounds).flatMap(::classesIn)
            else -> emptyList()
        }
    }
}
