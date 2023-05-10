// !LANGUAGE: +ContextReceivers
// TARGET_BACKEND: JVM_IR

// MUTE_SIGNATURE_COMPARISON_K2: ANY
// ^ KT-57429, KT-57435

data class Pair<A, B>(val first: A, val second: B)

context(Comparator<T>)
infix operator fun <T> T.compareTo(other: T) = compare(this, other)

context(Comparator<T>)
val <T> Pair<T, T>.min get() = if (first < second) first else second

fun box(): String {
    val comparator = Comparator<String> { a, b ->
        if (a == null || b == null) 0 else a.length.compareTo(b.length)
    }
    return with(comparator) {
        Pair("OK", "fail").min
    }
}
