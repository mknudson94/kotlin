// MODULE: InterfaceModule
// FILE: MyInterface.kt
interface MyInterface {
    fun test() = 1

    val testProp: Int
        get() = 100
}

// MODULE: OpenClassModule(InterfaceModule)
// FILE: MyOpenClass.kt
open class MyOpenClass : MyInterface {
}

// MODULE: OpenClassWithOverrideModule(InterfaceModule)
// FILE: MyOpenClassWithOverrideModule.kt
open class MyOpenClassWithOverride : MyInterface {
    override fun test() = 2

    override val testProp: Int = 1000
}

// MODULE: main(InterfaceModule, OpenClassModule, OpenClassWithOverrideModule)
// FILE: main.kt
class MyFinalClass : MyOpenClass() {
}

class MyFinalClass2 : MyInterface, MyOpenClass() {
}

class MyFinalClassWithOverride : MyOpenClassWithOverride() {
}

class MyFinalClassWithOverride2 : MyInterface, MyOpenClassWithOverride() {
}

fun box(): String {
    if (MyFinalClass().test() != 1) return "Fail MyOpenClass fun"
    if (MyFinalClass().testProp != 100) return "Fail MyOpenClass prop"

    if (MyFinalClass2().test() != 1) return "Fail MyFinalClass2 fun"
    if (MyFinalClass2().testProp != 100) return "Fail MyFinalClass2 prop"

    if (MyFinalClassWithOverride().test() != 2) return "Fail MyFinalClassWithOverride fun"
    if (MyFinalClassWithOverride().testProp != 1000) return "Fail MyFinalClassWithOverride prop"

    if (MyFinalClassWithOverride2().test() != 2) return "Fail MyFinalClassWithOverride2 fun"
    if (MyFinalClassWithOverride2().testProp != 1000) return "Fail MyFinalClassWithOverride2 prop"

    return "OK"
}
