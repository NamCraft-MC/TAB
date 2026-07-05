import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar

plugins {
    id("com.gradleup.shadow")
}

val brokenPlatformPaths = emptySet<String>()

val platformPaths = setOf(
    ":bukkit",
    ":bukkit:paper_1_21_11",
    ":bukkit:v26_1"
)

val moddedPaths = emptySet<String>()

val brokenPlatforms: List<Project> = brokenPlatformPaths.map { rootProject.project(it) }
val platforms: List<Project> = platformPaths.map { rootProject.project(it) }
val moddedPlatforms: List<Project> = moddedPaths.map { rootProject.project(it) }

tasks {
    shadowJar {
        archiveFileName.set("TAB v${project.version}.jar")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        fun registerPlatform(project: Project, jarTask: AbstractArchiveTask) {
            dependsOn(jarTask)
            dependsOn(project.tasks.withType<Jar>())
            from(zipTree(jarTask.archiveFile))
        }

        platforms.forEach { p ->
            val task = p.tasks.named<ShadowJar>("shadowJar").get()
            registerPlatform(p, task)
        }

        moddedPlatforms.forEach { p ->
            val task = p.tasks.named<Jar>("jar").get()
            registerPlatform(p, task)
        }
    }

    val shadowJarBrokenPaper = register<ShadowJar>("shadowJarBrokenPaper") {
        description = "Shadows only Paper versions 1.20.5 - 1.21.4, which break if jar has classes compiled with Java 24+."
        archiveFileName.set("TAB v${project.version} - Paper 1.20.5 - 1.21.4.jar")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        fun registerPlatform(project: Project, jarTask: AbstractArchiveTask) {
            dependsOn(jarTask)
            dependsOn(project.tasks.withType<Jar>())
            from(zipTree(jarTask.archiveFile))
        }

        brokenPlatforms.forEach { p ->
            val task = p.tasks.named<ShadowJar>("shadowJar").get()
            registerPlatform(p, task)
        }
    }

    build.get().dependsOn(shadowJar)
}
