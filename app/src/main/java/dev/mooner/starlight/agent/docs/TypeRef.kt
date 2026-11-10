package dev.mooner.starlight.agent.docs

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

/**
 * A type as the docs name it, from a doc file's Kotlin type (`kotlin.Array<a.b.Room>?`) or from reflection. Names
 * are Kotlin's: `kotlin.String`, `kotlin.Int`, nested classes joined with `.`.
 */
data class TypeRef(
    val name: String,
    val args: List<TypeRef> = emptyList(),
    val nullable: Boolean = false,
) {

    companion object {

        val ANY = TypeRef("kotlin.Any")

        /** Reads a Kotlin type as the docs processor writes it; `*` reads as [ANY]. */
        fun parse(text: String): TypeRef =
            Parser(text.trim()).type()

        /** A reflected type, with Java names turned into Kotlin's; nullability isn't known, so none is nullable. */
        fun of(type: Type): TypeRef = when (type) {
            is Class<*> -> when {
                type.isArray -> TypeRef("kotlin.Array", listOf(of(type.componentType!!)))
                else -> TypeRef(KOTLIN_NAMES[type.name] ?: type.name.replace('$', '.'))
            }
            is ParameterizedType -> of(type.rawType).copy(args = type.actualTypeArguments.map(::of))
            is GenericArrayType -> TypeRef("kotlin.Array", listOf(of(type.genericComponentType)))
            // `(Throwable) -> Unit` takes `? super Throwable`: the lower bound is what the function receives.
            is WildcardType -> (type.lowerBounds.firstOrNull() ?: type.upperBounds.firstOrNull())?.let(::raw) ?: ANY
            // Only the bound's class: a bound may name the variable itself, as in Enum<E extends Enum<E>>.
            is TypeVariable<*> -> type.bounds.firstOrNull()?.let(::raw) ?: ANY
            else -> ANY
        }

        private fun raw(type: Type): TypeRef = when (type) {
            is Class<*> -> of(type)
            is ParameterizedType -> of(type.rawType)
            else -> ANY
        }

        private val KOTLIN_NAMES = mapOf(
            "void" to "kotlin.Unit", "java.lang.Void" to "kotlin.Nothing",
            "boolean" to "kotlin.Boolean", "java.lang.Boolean" to "kotlin.Boolean",
            "int" to "kotlin.Int", "java.lang.Integer" to "kotlin.Int",
            "long" to "kotlin.Long", "java.lang.Long" to "kotlin.Long",
            "short" to "kotlin.Short", "java.lang.Short" to "kotlin.Short",
            "byte" to "kotlin.Byte", "java.lang.Byte" to "kotlin.Byte",
            "float" to "kotlin.Float", "java.lang.Float" to "kotlin.Float",
            "double" to "kotlin.Double", "java.lang.Double" to "kotlin.Double",
            "char" to "kotlin.Char", "java.lang.Character" to "kotlin.Char",
            "java.lang.String" to "kotlin.String",
            "java.lang.Object" to "kotlin.Any",
            "java.util.List" to "kotlin.collections.List",
            "java.util.ArrayList" to "kotlin.collections.ArrayList",
            "java.util.Collection" to "kotlin.collections.Collection",
            "java.util.Map" to "kotlin.collections.Map",
            "java.util.Set" to "kotlin.collections.Set",
            "java.util.HashSet" to "kotlin.collections.HashSet",
            "java.util.LinkedHashSet" to "kotlin.collections.LinkedHashSet",
            "java.lang.Iterable" to "kotlin.collections.Iterable",
        )
    }

    private class Parser(private val text: String) {

        private var i = 0

        fun type(): TypeRef {
            skipSpaces()
            if (peek() == '*') {
                i++
                return ANY
            }
            val start = i
            while (i < text.length && text[i] !in "<>,? ")
                i++
            val name = text.substring(start, i)
            val args = mutableListOf<TypeRef>()
            skipSpaces()
            if (peek() == '<') {
                i++
                while (true) {
                    args += type()
                    skipSpaces()
                    when (peek()) {
                        ',' -> i++
                        '>' -> { i++; break }
                        else -> break
                    }
                }
            }
            skipSpaces()
            val nullable = peek() == '?'
            if (nullable)
                i++
            return TypeRef(name, args, nullable)
        }

        private fun peek(): Char? = text.getOrNull(i)

        private fun skipSpaces() {
            while (peek() == ' ')
                i++
        }
    }
}
