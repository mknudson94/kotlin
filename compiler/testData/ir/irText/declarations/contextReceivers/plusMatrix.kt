// FIR_IDENTICAL
// !LANGUAGE: +ContextReceivers
// !DIAGNOSTICS: -UNUSED_PARAMETER
// IGNORE_BACKEND: JS_IR
// IGNORE_BACKEND: JS_IR_ES6

// MUTE_SIGNATURE_COMPARISON_K2: ANY
// ^ KT-57435

interface NumberOperations {
    fun Number.plus(other: Number): Number
}

class Matrix

context(NumberOperations) fun Matrix.plus(other: Matrix): Matrix = TODO()

fun NumberOperations.plusMatrix(m1: Matrix, m2: Matrix) {
    m1.plus(m2)
    m2.plus(m1)
}
