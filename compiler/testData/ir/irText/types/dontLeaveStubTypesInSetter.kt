// FIR_IDENTICAL
// WITH_STDLIB
// IGNORE_BACKEND: JS_IR
// IGNORE_BACKEND: JS_IR_ES6

// MUTE_SIGNATURE_COMPARISON_K2: ANY
// ^ KT-57429, KT-57755

class Foo<T>(var x: T)

fun <K> foo(x: MutableList<K>): Foo<K> = TODO()

fun main() {
    val x = buildList {
        add("")
        val foo = foo(this)
        foo.x = ""
    }
}
