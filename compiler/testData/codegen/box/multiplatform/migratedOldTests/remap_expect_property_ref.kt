// LANGUAGE: +MultiPlatformProjects
// !OPT_IN: kotlin.ExperimentalMultiplatform
// TARGET_BACKEND: JS_IR
// TARGET_BACKEND: NATIVE
// TARGET_BACKEND: WASM
// TARGET_BACKEND: JVM_IR

// Expected class 'Foo' has no actual declaration in module <common> for JS
// IGNORE_BACKEND_K1: JS_IR
// NO_ACTUAL_FOR_EXPECT: Expected class 'Foo' has no actual declaration in module <common> for JVM (18,14) in /common.kt
// IGNORE_BACKEND_K1: JVM_IR

// Below are real problems
// Can't link symbol function Foo.<get-p>
// IGNORE_BACKEND_K1: WASM

// MODULE: common
// FILE: common.kt

expect class Foo {
    val p: Int
    fun bar(r: () -> Int = this::p): Int
}

// MODULE: actual()()(common)
// FILE: actual.kt

actual class Foo {
    actual val p = 42
    actual fun bar(r: () -> Int) = r()
}
fun box(): String {
    val bar = Foo().bar()
    if (bar != 42)
        return "bar is wrongly $bar"

    return "OK"
}
