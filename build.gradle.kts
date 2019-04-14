buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }
}

plugins {
    application
    checkstyle
    java
    eclipse
    idea
    id("io.freefair.lombok") version "3.1.4"
    jacoco
}

allprojects {
    repositories {
        mavenCentral()
    }
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "com.evst"
version = "1.0-SNAPSHOT"

object Versions {
    const val assertj = "3.11.1"
    const val jacoco = "0.8.2"
    const val guice = "4.2.2"
    const val config = "1.3.2"
    const val akkaHttp = "10.1.8"
    const val akkaHttpJackson = "10.1.8"
    const val akkaStream = "2.5.19"
    const val akkaPersistence = "2.5.19"
    const val leveldb = "1.8"

    const val junit4 = "4.11"
    const val junit5 = "5.3.2"
    const val junitVintage = "5.4.2"
    const val akkaTestKit = "2.5.19"
    const val akkaStreamTestKit = "2.5.19"
}

dependencies {
    compile("com.google.inject", "guice", Versions.guice)
    compile("com.typesafe", "config", Versions.config)

    compile("com.typesafe.akka", "akka-http_2.12", Versions.akkaHttp)
    compile("com.typesafe.akka", "akka-persistence_2.12", Versions.akkaPersistence)
    compile("org.fusesource.leveldbjni", "leveldbjni-all", Versions.leveldb)
    compile("com.typesafe.akka", "akka-stream_2.12", Versions.akkaStream)
    compile("com.typesafe.akka", "akka-http-jackson_2.12", Versions.akkaHttpJackson)

    testCompile("org.junit.jupiter", "junit-jupiter-api", Versions.junit5)
    testCompile("org.junit.jupiter", "junit-jupiter-params", Versions.junit5)
    testRuntime("org.junit.jupiter", "junit-jupiter-engine", Versions.junit5)
    testCompile("org.assertj", "assertj-core", Versions.assertj)
    testCompile("com.typesafe.akka", "akka-testkit_2.12", Versions.akkaTestKit)
    testCompile("com.typesafe.akka", "akka-stream-testkit_2.12", Versions.akkaStreamTestKit)
    testCompile("com.typesafe.akka", "akka-http-testkit_2.12", Versions.akkaHttp)

    // can be removed after the akka-http TestKit migration to the JUnit 5
    testCompile("junit", "junit", Versions.junit4) {
        because("Akka-http testkit doesn't support Junit 5 yet.")
    }
    testRuntime("org.junit.vintage", "junit-vintage-engine", Versions.junitVintage) {
        because("We need to run JUnit 4 for akka-http tests :(")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy("jacocoTestReport")
}

jacoco {
    toolVersion = Versions.jacoco
}

checkstyle {
    sourceSets = listOf(project.sourceSets.main.get())
}

tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
    }
}

application {
    mainClassName = "com.evst.account.Boot"
}

val run by tasks.getting(JavaExec::class) {
    standardInput = System.`in`
}
