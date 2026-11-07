package dev.mooner.starlight.agent.docs

import dev.mooner.starlight.agent.docs.ScriptCatalog.ApiInfo
import dev.mooner.starlight.agent.docs.ScriptCatalog.EventInfo
import dev.mooner.starlight.agent.docs.ScriptCatalog.Param
import dev.mooner.starlight.agent.docs.ScriptCatalog.Signature
import dev.mooner.starlight.agent.docs.ScriptCatalog.TypeInfo
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

/**
 * Renders the catalog as TypeScript declarations with JSDoc, the way the agent reads it (docs/agent-docs.md §6).
 * The type table of §6.3 is [ts], and nowhere else.
 */
class DtsRenderer(
    val catalog: ScriptCatalog,
    private val guides: GuideLibrary,
) {

    /** The events, by ID. */
    fun events(): List<EventInfo> =
        catalog.events.sortedBy(EventInfo::id)

    /** One line per API: name, kind, levels, source and summary. */
    fun apiLine(api: ApiInfo): String = buildString {
        append(api.name)
        append(" (").append(if (api.isClass) "class" else "object")
        if (api.levels.isNotEmpty())
            append("; levels: ").append(api.levels.joinToString())
        if (api.source != STARLIGHT)
            append("; from ").append(api.source)
        append(")")
        catalog.docOf(api.cls)?.summary?.takeIf(String::isNotEmpty)?.let { append(" — ").append(it) }
    }

    /** One line per event: ID, how it is received, levels, source and summary. */
    fun eventLine(event: EventInfo): String = buildString {
        append(event.id).append(": ").append(event.usage)
        if (event.levels.isNotEmpty())
            append(" (levels: ").append(event.levels.joinToString()).append(")")
        if (event.source != STARLIGHT)
            append(" (from ").append(event.source).append(")")
        catalog.docOf(event.eventClass)?.summary?.takeIf(String::isNotEmpty)?.let { append(" — ").append(it) }
    }

    /**
     * The full description of [api], or of a type of the closure, with the declarations of the types it reaches
     * first. Further types are named, to be described separately.
     */
    fun describeApi(api: ApiInfo): String {
        val root = catalog.describe(api.cls, api.isClass)
        val instance = if (api.isClass) catalog.describe(api.cls) else null
        val header = buildString {
            append("// ").append(api.name).append(": ").append(if (api.isClass) "a class" else "a global object")
            if (api.levels.isNotEmpty())
                append(" in the API levels ").append(api.levels.joinToString())
            append("; from ").append(api.source)
        }
        return document(header, root, instance) { out ->
            if (instance != null) declareClass(out, api.name, root, instance) else declareConst(out, api.name, root)
        }
    }

    /** A type an API or event leads to: documented, or else reflected without docs, or a note for the platform's. */
    fun describeType(cls: Class<*>): String {
        if (cls !in catalog.closure && !catalog.isOwn(cls))
            return "// ${cls.name}: ${platformOf(cls)}'s class, not StarLight's.\n" +
                "// Scripts use its public members as its own reference describes; a getter also reads as a property " +
                "(`x.getName()` as `x.name`).\n"
        val root = catalog.describe(cls)
        val header = if (cls in catalog.closure) "// ${root.name}: ${cls.name}"
            else "// ${root.name}: ${cls.name}\n// Not documented: its members as scripts see them, without descriptions."
        return document(header, root, null) { out -> declareInterface(out, root, full = true) }
    }

    private fun platformOf(cls: Class<*>): String = when {
        cls.name.startsWith("android.") || cls.name.startsWith("androidx.") -> "Android"
        cls.name.startsWith("java.") || cls.name.startsWith("javax.") -> "Java"
        cls.name.startsWith("kotlin.") -> "Kotlin"
        else -> "A library"
    }

    fun describeEvent(event: EventInfo): String {
        val doc = catalog.docOf(event.eventClass)
        // The arguments' types, and the types they reach first: what a handler uses (Message, then ChatRoom).
        val arguments = event.arguments.map { it.second }.filter(catalog.docs::isDocumented).toSet()
        val references = arguments + arguments.flatMap { catalog.describe(it).references }
        val types = references.map { catalog.describe(it) }
        val body = StringBuilder()
        body.append("// ").append(event.id)
        if (event.levels.isNotEmpty())
            body.append(" in the API levels ").append(event.levels.joinToString())
        body.append("; from ").append(event.source).append('\n')
        // An API2 event reaches a listener the script adds (`bot.on(Event.MESSAGE)`), not a function StarLight calls.
        val isFunction = event.usage.startsWith(event.functionName + "(")
        body.append("// Received with: ").append(event.usage)
        if (!isFunction)
            body.append(", passing a listener of the type below; StarLight calls no function by name")
        body.append('\n')
        jsDoc(body, "", doc, full = true, params = event.arguments.map { Param(it.first, TypeRef.of(it.second), false, false) })
        val parameters = event.arguments.joinToString { (name, cls) -> "$name: ${ts(TypeRef.of(cls))}" }
        if (isFunction)
            body.append("declare function ").append(event.functionName).append('(').append(parameters).append("): void;\n")
        else
            body.append("type ").append(listenerName(event)).append(" = (").append(parameters).append(") => void;\n")
        for (type in types) {
            body.append('\n')
            declareInterface(body, type, full = false)
        }
        val further = types.flatMap { it.references }.toSet() - references
        appendFurther(body, types, further, (types.flatMap { it.external } + event.arguments.map { it.second }).toSet())
        appendRelated(body, listOfNotNull(doc) + types.mapNotNull { it.doc })
        return prelude(body.toString()) + body
    }

    private fun document(header: String, root: TypeInfo, instance: TypeInfo?, declare: (StringBuilder) -> Unit): String {
        val body = StringBuilder(header).append('\n')
        declare(body)
        val views = listOfNotNull(root, instance)
        val references = views.flatMap { it.references }.toSet() - root.cls
        val first = references.map { catalog.describe(it) }
        for (type in first) {
            body.append('\n')
            declareInterface(body, type, full = false)
        }
        val further = first.flatMap { it.references }.toSet() - references - root.cls
        appendFurther(body, first, further, (views + first).flatMap { it.external }.toSet())
        appendRelated(body, listOfNotNull(root.doc) + first.mapNotNull { it.doc } + views.flatMap { view ->
            (view.methods + view.constructors).mapNotNull { it.doc } + view.properties.mapNotNull { it.doc }
        })
        return prelude(body.toString()) + body
    }

    private fun declareConst(out: StringBuilder, name: String, type: TypeInfo) {
        jsDoc(out, "", type.doc, full = true)
        out.append("declare const ").append(name).append(": ")
        val collection = type.collection
        if (collection != null && type.properties.isEmpty() && type.methods.isEmpty()) {
            out.append(ts(collection)).append(";\n")
            return
        }
        collection?.let { out.append(ts(it)).append(" & ") }
        out.append("{\n")
        members(out, type, full = true, static = false)
        out.append("};\n")
    }

    private fun declareClass(out: StringBuilder, name: String, type: TypeInfo, instance: TypeInfo) {
        jsDoc(out, "", type.doc, full = true)
        out.append("declare class ").append(name)
        instance.collection?.let { out.append(" implements ").append(ts(it)) }
        out.append(" {\n")
        for (constructor in type.constructors) {
            // A primary constructor without docs of its own has the class's (docs/agent-docs.md §4): only its
            // parameters are worth repeating.
            val doc = constructor.doc?.takeUnless { it.summary == type.doc?.summary }
                ?: constructor.doc?.let { DocText(params = it.params) }
            signature(out, Signature(constructor.name, constructor.params, constructor.returns, doc), full = true,
                prefix = "constructor", static = false)
        }
        members(out, type, full = true, static = true)
        members(out, instance, full = true, static = false)
        out.append("}\n")
    }

    private fun declareInterface(out: StringBuilder, type: TypeInfo, full: Boolean) {
        jsDoc(out, "", type.doc, full = full)
        out.append("interface ").append(type.name)
        type.collection?.let { out.append(" extends ").append(ts(it)) }
        out.append(" {\n")
        members(out, type, full, static = false)
        out.append("}\n")
    }

    private fun members(out: StringBuilder, type: TypeInfo, full: Boolean, static: Boolean) {
        val prefix = if (static) "static " else ""
        for (property in type.properties) {
            jsDoc(out, INDENT, property.doc, full)
            out.append(INDENT).append(prefix)
            if (property.isReadOnly)
                out.append("readonly ")
            val type = property.value?.let(::stringLiteral) ?: ts(property.type)
            out.append(property.name).append(": ").append(type).append(";\n")
        }
        for (method in type.methods) {
            listenerOverloads(out, method, static)
            signature(out, method, full, prefix = method.name, static = static)
        }
    }

    /**
     * For a function taking an event name and a listener (`@listeners`, as API2's `bot.on`): one declaration per
     * listener event, pairing its name with its listener's arguments, before the general one.
     */
    private fun listenerOverloads(out: StringBuilder, method: Signature, static: Boolean) {
        val prefix = method.doc?.listeners ?: return
        val (name, listener) = method.params.takeIf { it.size >= 2 } ?: return
        for (event in catalog.events) {
            val eventName = event.listenerName ?: continue
            if (!event.id.startsWith(prefix))
                continue
            val arguments = event.arguments.joinToString { (argument, cls) -> "$argument: ${ts(TypeRef.of(cls))}" }
            out.append(INDENT)
            if (static)
                out.append("static ")
            out.append(method.name).append('(').append(name.name).append(": \"").append(eventName).append("\", ")
                .append(listener.name).append(": (").append(arguments).append(") => void): ")
                .append(ts(method.returns)).append(";\n")
        }
    }

    private fun stringLiteral(value: String): String =
        '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"'

    private fun signature(out: StringBuilder, signature: Signature, full: Boolean, prefix: String, static: Boolean) {
        jsDoc(out, INDENT, signature.doc, full, signature.params, signature.returns)
        out.append(INDENT)
        if (static)
            out.append("static ")
        out.append(prefix).append('(')
        out.append(signature.params.joinToString { param ->
            buildString {
                if (param.vararg) append("...")
                append(param.name)
                if (param.optional) append('?')
                append(": ")
                append(if (param.vararg) ts(param.type) else parameterType(param.type))
                if (param.vararg) append("[]")
            }
        })
        out.append(')')
        if (prefix != "constructor")
            out.append(": ").append(ts(signature.returns))
        out.append(";\n")
    }

    /**
     * A JSDoc comment: the summary, and with [full] the details, parameters, result, errors and `@since`. Nothing
     * for an undocumented declaration.
     */
    private fun jsDoc(
        out: StringBuilder,
        indent: String,
        doc: DocText?,
        full: Boolean,
        params: List<Param> = emptyList(),
        returns: TypeRef? = null,
    ) {
        doc ?: return
        val lines = mutableListOf<String>()
        if (doc.summary.isNotEmpty())
            lines += doc.summary
        if (full) {
            doc.details?.let { lines += ""; lines += it.lines() }
            for (param in params)
                doc.params[param.name]?.let { lines += "@param ${param.name} $it" }
            if (returns != null && returns.name != "kotlin.Unit")
                doc.returns?.let { lines += "@returns $it" }
            doc.throws.forEach { (name, text) -> lines += "@throws $name $text" }
            doc.since?.let { lines += "@since $it" }
        }
        if (lines.isEmpty())
            return
        if (lines.size == 1 && '\n' !in lines[0]) {
            out.append(indent).append("/** ").append(lines[0].replace("*/", "*\\/")).append(" */\n")
            return
        }
        out.append(indent).append("/**\n")
        for (line in lines.flatMap(String::lines))
            out.append(indent).append(" *").append(if (line.isEmpty()) "" else " ").append(line.replace("*/", "*\\/")).append('\n')
        out.append(indent).append(" */\n")
    }

    /**
     * Where to read on: the types declared [inShort] above, the documented ones [further] away, and those without docs
     * among [external] that describe_api still shows.
     */
    private fun appendFurther(out: StringBuilder, inShort: List<TypeInfo>, further: Set<Class<*>>, external: Set<Class<*>>) {
        val undocumented = external.filter { it in catalog.undocumentedTypes && catalog.isOwn(it) }
        if (inShort.isEmpty() && further.isEmpty() && undocumented.isEmpty())
            return
        out.append('\n')
        if (inShort.isNotEmpty())
            out.append("// Declared in short above: ").append(inShort.map { it.name }.sorted().joinToString())
                .append(". describe_api with a name shows one in full.\n")
        if (further.isNotEmpty())
            out.append("// Other types: ").append(further.map(catalog::nameOf).sorted().joinToString())
                .append(" (describe_api with a type's name shows it)\n")
        if (undocumented.isNotEmpty())
            out.append("// Without docs: ").append(undocumented.map(catalog::nameOf).sorted().joinToString())
                .append(" (describe_api with a name shows its members)\n")
    }

    /** The type an API2 event's listener has: `onMessage` gives `MessageListener`. */
    private fun listenerName(event: EventInfo): String =
        event.functionName.removePrefix("on").replaceFirstChar(Char::uppercaseChar) + "Listener"

    private fun appendRelated(out: StringBuilder, docs: List<DocText>) {
        val refs = docs.flatMap(DocText::see).distinct()
        if (refs.isEmpty())
            return
        out.append('\n')
        for (ref in refs)
            out.append("// Related: ").append(ref).append(guides.summaryOf(ref)?.let { " — $it" }.orEmpty()).append('\n')
    }

    /** The declarations of [JAVA_LIST] and [JAVA_MAP], for a text that uses them. */
    private fun prelude(text: String): String = buildString {
        if ("JavaList<" in text)
            append(JAVA_LIST).append('\n')
        if ("JavaMap<" in text)
            append(JAVA_MAP).append('\n')
        if ("JavaSet<" in text)
            append(JAVA_SET).append('\n')
        if (isNotEmpty())
            append('\n')
    }

    /** The TypeScript a script sees for [type] through NeonJS (docs/agent-docs.md §6.3). */
    fun ts(type: TypeRef): String {
        val base = when (type.name) {
            "kotlin.String", "kotlin.Char", "java.lang.String" -> "string"
            "kotlin.Int", "kotlin.Short", "kotlin.Byte", "kotlin.Float", "kotlin.Double", "kotlin.Number" -> "number"
            "kotlin.Long" -> "number | bigint"
            "kotlin.Boolean" -> "boolean"
            "java.math.BigInteger" -> "bigint"
            "kotlin.Unit" -> "void"
            // Only ever null, such as the session manager API2 notification listeners get.
            "kotlin.Nothing" -> "null"
            "kotlin.Any" -> "any"
            "kotlin.Array", "kotlin.collections.List", "kotlin.collections.MutableList", "kotlin.collections.ArrayList",
            "java.util.LinkedList" ->
                "JavaList<${type.args.firstOrNull()?.let(::ts) ?: "any"}>"
            "kotlin.IntArray", "kotlin.LongArray", "kotlin.ShortArray", "kotlin.ByteArray", "kotlin.FloatArray",
            "kotlin.DoubleArray" -> "JavaList<number>"
            "kotlin.BooleanArray" -> "JavaList<boolean>"
            "kotlin.CharArray" -> "JavaList<string>"
            "kotlin.collections.Map", "kotlin.collections.MutableMap", "kotlin.collections.HashMap",
            "kotlin.collections.LinkedHashMap" -> "JavaMap<${type.args.getOrNull(1)?.let(::ts) ?: "any"}>"
            "kotlin.collections.Set", "kotlin.collections.MutableSet", "kotlin.collections.HashSet",
            "kotlin.collections.LinkedHashSet" -> "JavaSet<${type.args.firstOrNull()?.let(::ts) ?: "any"}>"
            "java.util.concurrent.CompletionStage", "java.util.concurrent.CompletableFuture" ->
                "Promise<${type.args.firstOrNull()?.let(::ts) ?: "any"}>"
            else -> function(type) ?: documented(type) ?: singleMethod(type) ?: ScriptCatalog.KOTLIN_NAMES[type.name] ?: type.name
        }
        if (!type.nullable || base == "any")
            return base
        return if ('|' in base || '=' in base) "($base) | null" else "$base | null"
    }

    /** A parameter's type: as [ts], but an array or list parameter takes a JS array too, as both engines convert it. */
    private fun parameterType(type: TypeRef): String {
        val base = ts(type.copy(nullable = false))
        if (!base.startsWith("JavaList<"))
            return ts(type)
        val element = base.removePrefix("JavaList<").removeSuffix(">")
        val array = if ('|' in element || '=' in element) "($element)[]" else "$element[]"
        return if (type.nullable) "$array | $base | null" else "$array | $base"
    }

    /** `kotlin.Function2<A, B, R>` (or `kotlin.jvm.functions.Function2`) as `(a: A, b: B) => R`. */
    private fun function(type: TypeRef): String? {
        val simple = type.name.substringAfterLast('.')
        if (!type.name.startsWith("kotlin.") || !FUNCTION.matches(simple) || type.args.isEmpty())
            return null
        val params = type.args.dropLast(1).mapIndexed { i, arg -> "p${i + 1}: ${ts(arg)}" }
        // ts() puts a nullable one in parentheses.
        return "(${params.joinToString()}) => ${ts(type.args.last())}"
    }

    private fun documented(type: TypeRef): String? =
        catalog.classForKotlinName(type.name)?.let(catalog::nameOf)

    /**
     * A Java interface with one abstract method, such as `java.util.concurrent.Callable`, as `(p1: A) => R`: NeonJS
     * passes a JS function for any interface, calling it for the interface's methods.
     */
    private fun singleMethod(type: TypeRef): String? {
        // Java's and Android's callbacks; a Kotlin interface such as `Flow` is an object to use, not a function.
        if (JAVA_CALLBACK_PACKAGES.none(type.name::startsWith))
            return null
        val cls = runCatching { Class.forName(type.name, false, javaClass.classLoader) }.getOrNull()
            ?.takeIf(Class<*>::isInterface)
            ?: return null
        val method = cls.methods
            .filter { Modifier.isAbstract(it.modifiers) && runCatching { Any::class.java.getMethod(it.name, *it.parameterTypes) }.isFailure }
            .singleOrNull()
            ?: return null
        // The interface's type arguments, for the type variables its method uses: Callable<V>.call(): V.
        fun resolve(t: Type): TypeRef =
            (t as? TypeVariable<*>)?.let { variable -> type.args.getOrNull(cls.typeParameters.indexOfFirst { it.name == variable.name }) }
                ?: TypeRef.of(t)
        val params = method.genericParameterTypes.mapIndexed { i, t -> "p${i + 1}: ${ts(resolve(t))}" }
        return "(${params.joinToString()}) => ${ts(resolve(method.genericReturnType))}"
    }

    companion object {

        const val STARLIGHT = "starlight"

        private const val INDENT = "  "

        private val FUNCTION = Regex("Function\\d+")

        private val JAVA_CALLBACK_PACKAGES = listOf("java.", "javax.", "android.")

        const val JAVA_LIST =
            "/** A Java array or List: elements by index, `length` and `for...of`. It isn't a JS array: `Array.from(x)` makes one, for array methods. */\n" +
                "interface JavaList<T> { readonly length: number; readonly [index: number]: T; [Symbol.iterator](): Iterator<T> }"

        const val JAVA_SET =
            "/** A Java Set: `for...of`, `size()` and `contains(x)`, but no index or `length`. `Array.from(x)` makes a JS array of it. */\n" +
                "interface JavaSet<T> { size(): number; contains(value: T): boolean; [Symbol.iterator](): Iterator<T> }"

        const val JAVA_MAP =
            "/** A Java Map: its entries are properties, read as `map[key]` or `map.key`. */\n" +
                "interface JavaMap<V> { readonly [key: string]: V }"
    }
}
