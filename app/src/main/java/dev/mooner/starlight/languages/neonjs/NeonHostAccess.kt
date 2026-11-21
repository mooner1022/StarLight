package dev.mooner.starlight.languages.neonjs

import dev.mooner.neonjs.HostAccess
import dev.mooner.neonjs.SandboxPolicy
import dev.mooner.starlight.languages.ScriptHostPolicy

/**
 * Scripts reach any public Java API, or, when [restricted], all but what [ScriptHostPolicy] denies. A Java exception a
 * script catches has its methods too (`e.getCause()`, `e.getStackTrace()`), as the policy shows them for its class.
 */
internal fun buildHostAccess(restricted: Boolean): HostAccess {
    val builder = HostAccess.builder(HostAccess.Level.ALL)
        .defaultDenyList(false)
        .allowExceptionAccess(true)
    if (!restricted)
        return builder.allowLookup { true }.build()
    return builder
        .allowLookup { name -> !ScriptHostPolicy.isClassNameDenied(name) }
        .classFilter { cls -> !ScriptHostPolicy.isClassDenied(cls) }
        .memberFilter { member -> !ScriptHostPolicy.isMemberDenied(member.declaringClass, member.name) }
        .build()
}

/**
 * What a script's global scope has besides the language, as GraalJS and Rhino give it: with [javaAccess], the `Java`
 * global and Java's packages (`java.util.ArrayList`, `android.widget.Toast`, `Packages.…`), whose classes resolve only
 * where the host access lets `Java.type` load them; and `print`/`printErr`, to the console.
 */
internal fun buildSandboxPolicy(javaAccess: Boolean = true): SandboxPolicy =
    SandboxPolicy.builder()
        .exposeJavaGlobal(javaAccess)
        .exposeJavaPackages(javaAccess)
        .exposePrint(true)
        .build()
