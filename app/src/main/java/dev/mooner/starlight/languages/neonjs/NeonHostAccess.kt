package dev.mooner.starlight.languages.neonjs

import dev.mooner.neonjs.HostAccess
import dev.mooner.starlight.languages.ScriptHostPolicy

/** Scripts reach any public Java API, or, when [restricted], all but what [ScriptHostPolicy] denies. */
internal fun buildHostAccess(restricted: Boolean): HostAccess {
    val builder = HostAccess.builder(HostAccess.Level.ALL)
        .defaultDenyList(false)
    if (!restricted)
        return builder.allowLookup { true }.build()
    return builder
        .allowLookup { name -> !ScriptHostPolicy.isClassNameDenied(name) }
        .classFilter { cls -> !ScriptHostPolicy.isClassDenied(cls) }
        .memberFilter { member -> !ScriptHostPolicy.isMemberDenied(member.declaringClass, member.name) }
        .build()
}
