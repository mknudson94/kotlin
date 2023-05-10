// FIR_IDENTICAL
// !LANGUAGE: +ContextReceivers
// TARGET_BACKEND: JVM_IR

// MUTE_SIGNATURE_COMPARISON_K2: ANY
// ^ KT-57435

interface Canvas {
    val suffix: String
}

interface Shape {
    context(Canvas)
    fun draw(): String
}

class Circle : Shape {
    context(Canvas)
    override fun draw() = "OK" + suffix
}

object MyCanvas : Canvas {
    override val suffix = ""
}

fun box() = with(MyCanvas) { Circle().draw() }
