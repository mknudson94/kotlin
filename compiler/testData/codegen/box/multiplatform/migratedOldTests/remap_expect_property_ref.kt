// LANGUAGE: +MultiPlatformProjects
// !OPT_IN: kotlin.ExperimentalMultiplatform
// TARGET_BACKEND: JS_IR
// TARGET_BACKEND: NATIVE
// TARGET_BACKEND: WASM

// Expected class 'Foo' has no actual declaration in module <common> for JS
// IGNORE_BACKEND_K1: JS_IR


// Can't link symbol function Foo.<get-p>
// IGNORE_BACKEND: WASM

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
