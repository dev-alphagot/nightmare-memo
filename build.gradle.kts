import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.20-M1"
    id("com.github.johnrengelman.shadow") version "2.0.2"
}

repositories {
    mavenCentral()
    maven("https://papermc.io/repo/repository/maven-public/")
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    compileOnly("io.github.monun:kommand-api:2.10.0")
//    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
//    compileOnly("io.github.monun:heartbeat-coroutines:0.0.3")
}

val shade = configurations.create("shade")
shade.extendsFrom(configurations.implementation.get())

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
    }
    processResources {
        filesMatching("**/*.yml") {
            expand(project.properties)
        }
        filteringCharset = "UTF-8"
    }
    register<Jar>("outputJar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        archiveBaseName.set(project.name)
        archiveClassifier.set("")
        archiveVersion.set("")

        from(
            shade.map {
                if (it.isDirectory)
                    it
                else
                    zipTree(it)
            }
        )

        from(sourceSets["main"].output)

        doLast {
            copy {
                from(archiveFile)
                into("./out")
            }
        }
    }
    register<Jar>("paperJar") {
        archiveBaseName.set(project.name)
        archiveClassifier.set("")
        archiveVersion.set("")

        from(sourceSets["main"].output)

        doLast {
            copy {
                from(archiveFile)
                val plugins = File(rootDir, ".server/plugins/")
                into(if (File(plugins, archiveFileName.get()).exists()) File(plugins, "update") else plugins)
            }
        }
    }
}
