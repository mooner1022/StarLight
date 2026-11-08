package dev.mooner.test.greeter

/** A plugin's API, as the tests of plugin docs load it. Its docs are in PluginDocsTest. */
class Greeter {

    fun make(name: String): Greeting = Greeting("Hello, $name!")
}

class Greeting(val text: String)
