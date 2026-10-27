package dev.mooner.starlight.diagnostics

import android.content.Context
import androidx.annotation.DrawableRes
import dev.mooner.starlight.plugincore.translation.TranslationBuilderBlock
import dev.mooner.starlight.plugincore.translation.translate

enum class DiagnosticLevel {
    OK,
    INFO,
    WARNING,
    ERROR;

    val isProblem: Boolean
        get() = this == WARNING || this == ERROR
}

data class DiagnosticResult(
    val level: DiagnosticLevel,
    val summary: String,
)

class Diagnostic internal constructor(
    val id: String,
    @DrawableRes val icon: Int,
    private val titleProvider: () -> String,
    private val checker: DiagnosticCheckScope.(Context) -> DiagnosticResult,
    private val fixer: ((Context) -> Unit)?,
) {

    val title: String
        get() = titleProvider()

    val isFixable: Boolean
        get() = fixer != null

    fun check(context: Context): DiagnosticResult =
        runCatching { DiagnosticCheckScope.checker(context) }
            .getOrElse { DiagnosticResult(DiagnosticLevel.ERROR, it.message ?: it.javaClass.simpleName) }

    fun fix(context: Context) {
        fixer?.invoke(context)
    }
}

@DslMarker
annotation class DiagnosticDsl

@DiagnosticDsl
object DiagnosticCheckScope {

    fun ok(block: TranslationBuilderBlock): DiagnosticResult =
        DiagnosticResult(DiagnosticLevel.OK, translate(block))

    fun info(block: TranslationBuilderBlock): DiagnosticResult =
        DiagnosticResult(DiagnosticLevel.INFO, translate(block))

    fun warning(block: TranslationBuilderBlock): DiagnosticResult =
        DiagnosticResult(DiagnosticLevel.WARNING, translate(block))

    fun error(block: TranslationBuilderBlock): DiagnosticResult =
        DiagnosticResult(DiagnosticLevel.ERROR, translate(block))
}

@DiagnosticDsl
class DiagnosticBuilder internal constructor(
    private val id: String,
) {

    private var titleProvider: (() -> String)? = null
    private var checker: (DiagnosticCheckScope.(Context) -> DiagnosticResult)? = null
    private var fixer: ((Context) -> Unit)? = null

    @DrawableRes
    var icon: Int = 0

    fun title(block: TranslationBuilderBlock) {
        titleProvider = { translate(block) }
    }

    fun check(block: DiagnosticCheckScope.(context: Context) -> DiagnosticResult) {
        checker = block
    }

    fun fix(block: (context: Context) -> Unit) {
        fixer = block
    }

    internal fun build(): Diagnostic =
        Diagnostic(
            id = id,
            icon = icon,
            titleProvider = requireNotNull(titleProvider) { "Diagnostic '$id' must have a title" },
            checker = requireNotNull(checker) { "Diagnostic '$id' must have a check" },
            fixer = fixer,
        )
}

fun diagnostic(id: String, block: DiagnosticBuilder.() -> Unit): Diagnostic =
    DiagnosticBuilder(id).apply(block).build()
