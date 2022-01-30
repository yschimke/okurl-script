
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.6.10"
  `maven-publish`
  distribution
  id("com.github.ben-manes.versions") version "0.28.0"
  id("net.nemerosa.versioning") version "2.12.1"
  id("com.diffplug.gradle.spotless") version "3.28.1"
}

repositories {
  jcenter()
  mavenCentral()
  maven(url = "https://jitpack.io")
}

group = "com.github.yschimke"
description = "OkHttp Kotlin CLI"
version = versioning.info.display

base {
  archivesBaseName = "okscript"
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks {
  withType(KotlinCompile::class) {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.apiVersion = "1.6"
    kotlinOptions.languageVersion = "1.6"

    kotlinOptions.allWarningsAsErrors = false
    kotlinOptions.freeCompilerArgs = listOf("-Xjsr305=strict", "-Xjvm-default=enable")
  }
}

dependencies {
  api("com.github.yschimke:okurl:4.3") {
    exclude(group = "org.slf4j")
    exclude(group = "com.squareup.okhttp3")
  }
  api("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.2")
  implementation(platform("com.squareup.okhttp3:okhttp-bom:5.0.0-alpha.2"))
  api("com.squareup.okhttp3:okhttp")
  api("com.squareup.okhttp3:okhttp-brotli")
  api("com.squareup.okhttp3:okhttp-dnsoverhttps")
  api("com.squareup.okhttp3:okhttp-sse")
  api("com.squareup.okhttp3:okhttp-tls")
  api("com.squareup.okio:okio:3.0.0")
  api("com.squareup.moshi:moshi:1.13.0")
  api("com.squareup.moshi:moshi-adapters:1.13.0")
  api("com.squareup.moshi:moshi-kotlin:1.13.0")
  api("org.jetbrains.kotlin:kotlin-reflect:1.4.31")
  api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.31")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.0")
  api("org.slf4j:slf4j-nop:2.0.0-alpha1")
  api("com.sun.activation:javax.activation:1.2.0")
}

val sourcesJar by tasks.creating(Jar::class) {
  classifier = "sources"
  from(kotlin.sourceSets["main"].kotlin)
}

val javadocJar by tasks.creating(Jar::class) {
  classifier = "javadoc"
  from("$buildDir/javadoc")
}

val jar = tasks["jar"] as org.gradle.jvm.tasks.Jar

spotless {
  kotlinGradle {
    ktlint("0.31.0").userData(mutableMapOf("indent_size" to "2", "continuation_indent_size" to "2"))
    trimTrailingWhitespace()
    endWithNewline()
  }
}

distributions {
  main {
    contents {
      from("${rootProject.projectDir}") {
        include("README.md", "LICENSE")
      }
      from("${rootProject.projectDir}/src/main/zsh") {
        into("zsh")
      }
      from("${rootProject.projectDir}/src/main/scripts") {
        into("bin")
      }
    }
  }
}

publishing {
  repositories {
    maven(url = "build/repository")
  }

  publications {
    create("mavenJava", MavenPublication::class) {
      from(components["java"])
      artifact(sourcesJar)
      artifact(tasks["distTar"])
    }
  }
}
