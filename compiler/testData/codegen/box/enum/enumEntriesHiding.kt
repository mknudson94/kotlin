// !LANGUAGE: +EnumEntries
// IGNORE_BACKEND: JS, JVM
// IGNORE_LIGHT_ANALYSIS
// FULL_JDK
// WITH_STDLIB

interface Hidden {
    val entries: Array<String> get() = arrayOf("OK")
}

@OptIn(ExperimentalStdlibApi::class)
enum class MyEnum : Hidden {
    FAIL;
    fun test() = entries
}

@OptIn(ExperimentalStdlibApi::class)
fun box(): String {
    if (MyEnum.FAIL.test()[0].toString() != "OK") return "fail: wrong hiddening inside enum entry"
    if (MyEnum.entries[0].toString() != "FAIL") return "fail: wrong hiddening inside enum class"
    return "OK"
}
