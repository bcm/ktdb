import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion = "1.3.21"
val spekVersion = "2.0.1"

plugins {
    id("org.jetbrains.kotlin.jvm").version("1.3.21")
    application
}

repositories {
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
}

application {
    mainClassName = "ktdb.AppKt"
}

tasks.test {
    useJUnitPlatform {
        includeEngines("spek2")
    }
}

val run by tasks.getting(JavaExec::class) {
    standardInput = System.`in`
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
