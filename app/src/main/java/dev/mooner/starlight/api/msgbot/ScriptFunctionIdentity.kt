/*
 * ScriptFunctionIdentity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot

import java.lang.reflect.Proxy

/**
 * The script function behind [function], to tell whether two values passed to host code are the same script function.
 *
 * NeonJS hands host code a new proxy each time a JS function is passed, and its proxies equal only themselves, so the
 * same function passed to a registering call and to the unregistering one would not match; the function each proxy
 * stands for does. Rhino reuses one adapter per function, so there the value itself is the identity.
 */
internal fun scriptFunctionIdentity(function: Any): Any {
    if (!Proxy.isProxyClass(function.javaClass))
        return function
    val handler = Proxy.getInvocationHandler(function)
    return runCatching { handler.javaClass.getMethod("getTarget").invoke(handler) }.getOrNull() ?: function
}
