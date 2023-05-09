fun box(stepId: Int): String {
    val expected = when (stepId) {
        0, 3 -> 4
        1 -> 6
        2 -> 8
        else -> return "Unknown"
    }

    val x = test()
    if (expected != x) {
        return "Fail $expected != $x"
    }

    return "OK"
}
