// TARGET_BACKEND: JS_IR
// TARGET_BACKEND: NATIVE
// TARGET_BACKEND: WASM

// Declaration annotated with '@OptionalExpectation' can only be used in common module sources
// IGNORE_BACKEND_K1: JS_IR, NATIVE, WASM

// LANGUAGE: +MultiPlatformProjects

@file:Suppress("OPT_IN_USAGE_ERROR")

import kotlin.js.*

@OptionalExpectation
expect annotation class Optional()

@Optional
fun foo() = "42"

@JsName("jsBar")
fun bar() = "43"

fun box(): String {
    val foo = foo()
    if (foo != "42")
        return "foo is wrongly $foo"

    val bar = bar()
    if (bar != "43")
        return "bar is wrongly $bar"

    return "OK"
}
