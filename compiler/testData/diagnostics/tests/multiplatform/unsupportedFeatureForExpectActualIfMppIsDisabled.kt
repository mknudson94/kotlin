// !LANGUAGE: -MultiPlatformProjects
// ISSUE: KT-57243
// FIR_IDENTICAL

// MODULE: main
// FILE: main.kt

<!UNSUPPORTED_FEATURE!>expect<!> class A

<!UNSUPPORTED_FEATURE!>expect<!> fun foo()

<!UNSUPPORTED_FEATURE!>actual<!> class B

<!UNSUPPORTED_FEATURE!>actual<!> fun foo() {}

<!UNSUPPORTED_FEATURE!>actual<!> fun bar() {}
