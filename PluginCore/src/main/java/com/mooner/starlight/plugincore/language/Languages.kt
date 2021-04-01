package com.mooner.starlight.plugincore.language

enum class Languages(
    val id: String,
    val name_kr: String,
) {
    JS_V8(
        id = "NodeJS",
        name_kr = "자바스크립트 (V8)"
    ),
    JS_RHINO(
        id = "RhinoJS",
        name_kr = "자바스크립트 (라이노)"
    ),
    PYTHON(
        id = "Jython",
        name_kr = "파이썬 (Jython)"
    ),
    CUSTOM(
        id = "",
        name_kr = ""
    )
}