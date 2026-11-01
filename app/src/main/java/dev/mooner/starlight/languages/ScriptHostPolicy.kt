package dev.mooner.starlight.languages

import dev.mooner.starlight.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Java APIs that scripts may not reach in Google Play builds.
 *
 * Play allows interpreted code loaded at run time only as long as it can't be used to break Play
 * policies (Device and Network Abuse). Scripts can otherwise call any Java API, so this keeps them
 * from loading dex or native code, starting processes, installing apps and from getting around these
 * checks through reflection or the script engines themselves. Everything else stays reachable.
 *
 * Both engines apply it: Rhino through a ClassShutter and its wrap factory, NeonJS through HostAccess.
 */
object ScriptHostPolicy {

    val isEnabled: Boolean
        get() = BuildConfig.RESTRICTED_SCRIPT_HOST_ACCESS

    private val deniedPackages = listOf(
        "dalvik.system.",              // DexClassLoader, PathClassLoader, InMemoryDexClassLoader, DexFile
        "java.lang.reflect.",
        "java.lang.invoke.",
        "kotlin.reflect.",
        "kotlin.jvm.internal.",
        "sun.",
        "com.sun.",
        "jdk.internal.",
        "com.android.dx.",             // turns class files into dex
        "org.mozilla.javascript.",     // Rhino: contexts without these checks, class generation
        "com.faendir.rhino_android.",
        "dev.mooner.neonjs.",          // NeonJS: engines without these checks, class generation
    )

    private val deniedClasses = setOf(
        "java.lang.ClassLoader",
        "java.lang.Runtime",
        "java.lang.Process",
        "java.lang.ProcessBuilder",
        "java.lang.ProcessHandle",
        "java.lang.SecurityManager",
        "android.content.pm.PackageInstaller",
        "android.content.pm.PackageInstaller\$Session",
        "android.content.pm.PackageInstaller\$SessionParams",
    )

    /** Members denied on a class and its subclasses, where the rest of the class is useful. */
    private val deniedMembers = mapOf(
        // getName() and the like stay usable; reflection and class loading don't.
        "java.lang.Class" to setOf(
            "forName", "getClassLoader", "newInstance",
            "getMethod", "getMethods", "getDeclaredMethod", "getDeclaredMethods",
            "getField", "getFields", "getDeclaredField", "getDeclaredFields",
            "getConstructor", "getConstructors", "getDeclaredConstructor", "getDeclaredConstructors",
            "getEnclosingMethod", "getEnclosingConstructor", "getRecordComponents",
        ),
        "java.lang.System" to setOf("load", "loadLibrary", "mapLibraryName", "setSecurityManager"),
        // A JavaScript interface on a WebView that loads untrusted content is a listed violation.
        "android.webkit.WebView" to setOf("addJavascriptInterface"),
    )

    private val classNameCache: MutableMap<String, Boolean> = ConcurrentHashMap()

    /** Whether scripts may not use [name] at all. Names of classes that don't exist aren't denied. */
    fun isClassNameDenied(name: String): Boolean =
        classNameCache.getOrPut(name) {
            isDeniedByName(name) || loadClassOrNull(name)?.let(::isClassDenied) == true
        }

    /** Whether scripts may not use [cls] at all: denied by name, or a class loader of any kind. */
    fun isClassDenied(cls: Class<*>): Boolean =
        isDeniedByName(cls.name) || ClassLoader::class.java.isAssignableFrom(cls)

    /** Whether scripts may not use the member [name] of [cls], including inherited ones. */
    fun isMemberDenied(cls: Class<*>, name: String): Boolean =
        superclassNames(cls).any { owner -> deniedMembers[owner]?.contains(name) == true }

    /** Whether [cls] has members denied by [isMemberDenied], so its objects need filtering. */
    fun hasDeniedMembers(cls: Class<*>): Boolean =
        superclassNames(cls).any(deniedMembers::containsKey)

    private fun isDeniedByName(name: String): Boolean {
        val outer = name.substringBefore('$')
        return name in deniedClasses || outer in deniedClasses || deniedPackages.any(name::startsWith)
    }

    private fun superclassNames(cls: Class<*>): Sequence<String> =
        generateSequence(cls) { it.superclass }.map(Class<*>::getName)

    private fun loadClassOrNull(name: String): Class<*>? =
        try {
            Class.forName(name, false, ScriptHostPolicy::class.java.classLoader)
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: LinkageError) {
            null
        }
}
