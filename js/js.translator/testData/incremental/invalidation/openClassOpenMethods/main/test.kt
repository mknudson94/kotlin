fun test(): Int {
    val b = getObjectB()
    val a = getObjectA()
    return b.test() + b.testProp + a.test() + a.testProp
}
