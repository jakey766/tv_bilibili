plugins { kotlin("jvm") }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test { testLogging { events("failed", "skipped", "passed") } }
