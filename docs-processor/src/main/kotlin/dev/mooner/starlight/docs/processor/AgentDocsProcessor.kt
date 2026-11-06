package dev.mooner.starlight.docs.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class AgentDocsProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        AgentDocsProcessor(environment.codeGenerator, environment.logger, environment.options)
}

/**
 * Writes `starlight/agent/<module>.api.json`: the KDoc and the Kotlin signatures of the public declarations in the
 * documented packages, keyed by their JVM names and descriptors so that the app can join them to what reflection
 * finds. See docs/agent-docs.md §4.
 */
@OptIn(KspExperimental::class)
class AgentDocsProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {

    private var done = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (done)
            return emptyList()
        done = true

        val module = options[OPTION_MODULE]
        if (module.isNullOrBlank()) {
            logger.error("$OPTION_MODULE isn't set")
            return emptyList()
        }
        val packages = options[OPTION_PACKAGES]?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()

        val classes = resolver.getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .flatMap(::withNested)
            .filter { cls -> cls.isPublic() && inScope(cls, packages) }
            .sortedBy { binaryName(it) }
            .toList()

        val json = buildJsonObject {
            put("format", FORMAT)
            put("module", module)
            putJsonArray("packages") { packages.forEach(::add) }
            putJsonObject("classes") {
                for (cls in classes)
                    put(binaryName(cls), describeClass(resolver, cls))
            }
        }

        // An unresolved type gives a wrong descriptor, so a member would silently lose its docs at run time.
        val text = JSON.encodeToString(JsonObject.serializer(), json)
        if (UNRESOLVED.any(text::contains)) {
            logger.error("Unresolved types in the agent docs of $module (${UNRESOLVED.filter(text::contains).joinToString()}): " +
                "KSP ran without the full classpath. Build again; see docs/agent-docs.md §4.")
            return emptyList()
        }

        val sources = classes.mapNotNull { it.containingFile }.distinct().toTypedArray()
        // Aggregating, with every source it read: any change to them writes the file again from all of them.
        codeGenerator.createNewFile(Dependencies(true, *sources), "starlight.agent", "$module.api", "json")
            .bufferedWriter()
            .use { it.write(text) }
        return emptyList()
    }

    private fun describeClass(resolver: Resolver, cls: KSClassDeclaration): JsonObject = buildJsonObject {
        put("kind", when (cls.classKind) {
            ClassKind.INTERFACE   -> "interface"
            ClassKind.OBJECT      -> "object"
            ClassKind.ENUM_CLASS  -> "enum"
            ClassKind.ANNOTATION_CLASS -> "annotation"
            else                  -> "class"
        })
        putDoc(KDoc.parse(cls.docString))
        putJsonObject("members") {
            // A primary constructor's KDoc is the class's, whose @param tags name its parameters.
            for (constructor in cls.getConstructors().filter { it.isPublic() })
                member(resolver, constructor, "<init>", "constructor",
                    fallback = cls.docString.takeIf { constructor == cls.primaryConstructor })
            for (function in cls.getDeclaredFunctions().filter { it.isPublic() && !it.isConstructor() })
                member(resolver, function, resolver.getJvmName(function) ?: function.simpleName.asString(), "function")
            for (property in cls.getDeclaredProperties().filter { it.isPublic() })
                property(resolver, property)
        }
    }

    private fun JsonObjectBuilder.member(
        resolver: Resolver,
        function: KSFunctionDeclaration,
        jvmName: String,
        kind: String,
        fallback: String? = null,
    ) {
        val descriptor = resolver.mapToJvmSignature(function)?.let { varargsAsArray(it, function) }?.let { suspended(it, function) }
            ?: return
        put(jvmName + descriptor, buildJsonObject {
            put("kind", kind)
            put("name", function.simpleName.asString())
            putJsonArray("params") {
                for (parameter in function.parameters)
                    addJsonObject {
                        put("name", parameter.name?.asString() ?: "arg")
                        put("type", typeName(parameter.type))
                        if (parameter.hasDefault)
                            put("optional", true)
                        if (parameter.isVararg)
                            put("vararg", true)
                    }
            }
            function.returnType?.let { put("returns", typeName(it)) }
            putDoc(KDoc.parse(function.docString) ?: KDoc.parse(fallback))
        })
    }

    /** KSP gives a vararg parameter its element's descriptor; the JVM method takes an array of them. */
    /**
     * A suspend function's descriptor as the JVM has it: KSP's leaves out the `Continuation` the compiler adds as the
     * last parameter, and the `Object` it returns.
     */
    private fun suspended(descriptor: String, function: KSFunctionDeclaration): String {
        if (Modifier.SUSPEND !in function.modifiers)
            return descriptor
        val close = descriptor.indexOf(')')
        return descriptor.substring(0, close) + "Lkotlin/coroutines/Continuation;)Ljava/lang/Object;"
    }

    private fun varargsAsArray(descriptor: String, function: KSFunctionDeclaration): String {
        val varargs = function.parameters.withIndex().filter { it.value.isVararg }.map { it.index }
        if (varargs.isEmpty())
            return descriptor
        val close = descriptor.indexOf(')')
        val params = mutableListOf<String>()
        var i = 1
        while (i < close) {
            val start = i
            while (descriptor[i] == '[')
                i++
            i = if (descriptor[i] == 'L') descriptor.indexOf(';', i) + 1 else i + 1
            params += descriptor.substring(start, i)
        }
        for (index in varargs)
            if (index < params.size)
                params[index] = "[" + params[index]
        return "(" + params.joinToString("") + descriptor.substring(close)
    }

    private fun JsonObjectBuilder.property(resolver: Resolver, property: KSPropertyDeclaration) {
        val doc = KDoc.parse(property.docString)
        val type = typeName(property.type)
        val typeDescriptor = resolver.mapToJvmSignature(property) ?: return
        // A const val or @JvmField is a field; any other property is reached through its accessors.
        val isField = Modifier.CONST in property.modifiers ||
            property.annotations.any { it.shortName.asString() == "JvmField" }
        if (isField) {
            put("field:" + property.simpleName.asString(), buildJsonObject {
                put("kind", "field")
                put("name", property.simpleName.asString())
                put("returns", type)
                put("mutable", property.isMutable && Modifier.CONST !in property.modifiers)
                putDoc(doc)
            })
            return
        }
        property.getter?.let { getter ->
            put(resolver.getJvmName(getter) + "()" + typeDescriptor, buildJsonObject {
                put("kind", "getter")
                put("name", property.simpleName.asString())
                put("returns", type)
                putDoc(doc)
            })
        }
        property.setter?.takeIf { property.isMutable && it.modifiers.none { m -> m == Modifier.PRIVATE || m == Modifier.PROTECTED || m == Modifier.INTERNAL } }?.let { setter ->
            put(resolver.getJvmName(setter) + "(" + typeDescriptor + ")V", buildJsonObject {
                put("kind", "setter")
                put("name", property.simpleName.asString())
                putJsonArray("params") {
                    addJsonObject {
                        put("name", "value")
                        put("type", type)
                    }
                }
                putDoc(doc)
            })
        }
    }

    private fun JsonObjectBuilder.putDoc(doc: KDoc?) {
        doc ?: return
        putJsonObject("doc") {
            put("summary", doc.summary)
            doc.details?.let { put("details", it) }
            if (doc.params.isNotEmpty())
                putJsonObject("params") { doc.params.forEach { (name, text) -> put(name, text) } }
            doc.returns?.let { put("returns", it) }
            if (doc.throws.isNotEmpty())
                putJsonObject("throws") { doc.throws.forEach { (name, text) -> put(name, text) } }
            doc.since?.let { put("since", it) }
            if (doc.suppress)
                put("suppress", true)
            if (doc.see.isNotEmpty())
                putJsonArray("see") { doc.see.forEach(::add) }
            doc.listeners?.let { put("listeners", it) }
        }
    }

    /** A Kotlin type as written: qualified names, type arguments and `?`; `*` for a star projection. */
    private fun typeName(reference: KSTypeReference): String =
        typeName(reference.resolve())

    private fun typeName(type: KSType): String {
        val declaration = type.declaration
        // A script gets the aliased type, and the alias may not be public: `typealias EventCallback = (Array<out Any>) -> Unit`.
        if (declaration is KSTypeAlias) {
            val aliased = declaration.type.resolve()
            return typeName(if (type.isMarkedNullable) aliased.makeNullable() else aliased)
        }
        val name = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
        val arguments = type.arguments.joinToString(", ") { argument ->
            if (argument.variance == Variance.STAR) "*" else argument.type?.let(::typeName) ?: "*"
        }
        return buildString {
            append(name)
            if (arguments.isNotEmpty())
                append('<').append(arguments).append('>')
            if (type.isMarkedNullable)
                append('?')
        }
    }

    private fun withNested(cls: KSClassDeclaration): Sequence<KSClassDeclaration> =
        sequenceOf(cls) + cls.declarations.filterIsInstance<KSClassDeclaration>().flatMap(::withNested)

    /** Whether [cls] is in one of [entries]: a package with its subpackages, or a class with its nested classes. */
    private fun inScope(cls: KSClassDeclaration, entries: List<String>): Boolean {
        val packageName = cls.packageName.asString()
        val name = cls.qualifiedName?.asString() ?: return false
        return entries.isEmpty() || entries.any { entry ->
            packageName == entry || packageName.startsWith("$entry.") || name == entry || name.startsWith("$entry.")
        }
    }

    /** The JVM's name for [cls]: nested classes joined with `$`, as `Class.getName()` gives it. */
    private fun binaryName(cls: KSClassDeclaration): String {
        val names = generateSequence(cls) { it.parentDeclaration as? KSClassDeclaration }
            .map { it.simpleName.asString() }
            .toList()
            .asReversed()
        val packageName = cls.packageName.asString()
        return (if (packageName.isEmpty()) "" else "$packageName.") + names.joinToString("$")
    }

    private companion object {
        const val FORMAT = 1
        const val OPTION_MODULE = "starlight.docs.module"
        const val OPTION_PACKAGES = "starlight.docs.packages"
        val JSON = Json { prettyPrint = false }
        /** What KSP writes for a type it couldn't resolve, in Kotlin types and in JVM descriptors. */
        val UNRESOLVED = listOf("<ERROR", "error/NonExistentClass")
    }
}
