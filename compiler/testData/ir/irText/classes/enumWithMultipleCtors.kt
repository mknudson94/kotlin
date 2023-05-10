// IGNORE_BACKEND: JS_IR
// IGNORE_BACKEND: JS_IR_ES6

// MUTE_SIGNATURE_COMPARISON_K2: ANY
// ^ KT-57775, KT-57777

enum class A {
    X("asd"),
    Y() {
        override fun f() = super.f() + "#Y"
    },
    Z(5);

    val prop1: String
    val prop2: String = "const2"
    var prop3: String = ""

    constructor(arg: String) {
        prop1 = arg
    }

    constructor() {
        prop1 = "default"
        prop3 = "empty"
    }

    constructor(x: Int): this(x.toString()) {
        prop3 = "int"
    }

    open fun f(): String = "$prop1#$prop2#$prop3"
}
