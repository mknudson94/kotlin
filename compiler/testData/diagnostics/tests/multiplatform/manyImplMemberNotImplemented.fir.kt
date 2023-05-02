// TARGET_BACKEND: JVM
// !LANGUAGE: +MultiPlatformProjects

// MODULE: common
// TARGET_PLATFORM: Common
// FILE: common.kt

expect open class C1()
expect interface I1

<!MANY_IMPL_MEMBER_NOT_IMPLEMENTED{JVM}!>open class A : C1(), I1<!>
<!MANY_IMPL_MEMBER_NOT_IMPLEMENTED{JVM}!>open class B : I1, C1()<!>

// MODULE: jvm()()(common)
// TARGET_PLATFORM: JVM
// FILE: main.kt

actual open class C1 {
    fun f() {}
}

actual interface I1 {
    fun f() {}
}
