import com.vanniktech.maven.publish.SonatypeHost

plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "games.qore"
version = "2.1.0"

val githubOwner = findProperty("githubOwner") as String? ?: "qore-games"
val githubRepo = findProperty("githubRepo") as String? ?: "GlowingEntities"
val githubBranch = findProperty("githubBranch") as String? ?: "main"
val githubUrl = "https://github.com/$githubOwner/$githubRepo"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("26.1.1.build.+")
    compileOnly("io.netty:netty-all:4.1.97.Final")
    compileOnly("org.jetbrains:annotations:24.0.0")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("GlowingEntities")
        description.set("A utility to easily make entities glow.")
        url.set(githubUrl)

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("skytasul")
                name.set("SkytAsul")
                email.set("skytasul@gmail.com")
                url.set("https://skytasul.fr")
                roles.set(listOf("Original author"))
            }
            developer {
                id.set("qore")
                name.set("qore")
                roles.set(listOf("Modified by", "Maintainer"))
            }
        }

        scm {
            connection.set("scm:git:git://github.com/$githubOwner/$githubRepo.git")
            developerConnection.set("scm:git:ssh://github.com:$githubOwner/$githubRepo.git")
            url.set("$githubUrl/tree/$githubBranch")
        }
    }
}
