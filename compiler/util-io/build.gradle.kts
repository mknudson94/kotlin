plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    implementation(kotlinStdlib())
    testImplementation(commonDependency("junit:junit"))
    testImplementation(kotlin("test"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            languageVersion = "1.5"
            apiVersion = "1.4"
            freeCompilerArgs += listOf("-Xsuppress-version-warnings")
        }
    }
}

publish()

standardPublicJars()
