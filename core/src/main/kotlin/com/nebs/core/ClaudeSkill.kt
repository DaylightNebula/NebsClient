package com.nebs.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The Claude Code skill that teaches Claude to use nebs (CLI, Java/Kotlin API, Gradle plugin).
 * It's embedded in this jar under [RESOURCE_DIR], so the instructions always match the version in
 * use. Front ends install it with `nebs-cli claude-skill` or the `nebsInstallClaudeSkill` task;
 * anything else can call [install] or run this class:
 *
 * ```
 * java -cp nebs-api-<version>-all.jar com.nebs.core.ClaudeSkill [skills dir]
 * ```
 */
object ClaudeSkill {
    const val NAME = "nebs-client"
    const val RESOURCE_DIR = "nebs-claude"

    /** Where a project's skills live: `<project>/.claude/skills`. */
    fun projectDir(project: Path = Path.of("")): Path = project.resolve(".claude").resolve("skills").toAbsolutePath().normalize()

    /** Where personal skills live: `~/.claude/skills`. */
    fun userDir(): Path = Path.of(System.getProperty("user.home"), ".claude", "skills")

    /**
     * Writes the skill into `<skillsDir>/nebs-client/`, replacing an older copy, and returns that
     * folder.
     *
     * @throws IllegalStateException if [anchor]'s class loader doesn't have the skill.
     */
    fun install(skillsDir: Path = projectDir(), anchor: Class<*> = ClaudeSkill::class.java): Path {
        val loader = anchor.classLoader
        val files = loader.getResourceAsStream("$RESOURCE_DIR/index.txt")?.use { it.reader().readLines() }
            ?.filter { it.isNotBlank() }
            ?: error("The Claude skill isn't bundled in this build of nebs")
        val target = skillsDir.resolve(NAME)
        if (Files.exists(target)) target.toFile().deleteRecursively() // drop files an older version had
        for (file in files) {
            val dest = skillsDir.resolve(file).normalize()
            require(dest.startsWith(skillsDir.normalize())) { "Bad skill file path: $file" }
            Files.createDirectories(dest.parent)
            val stream = loader.getResourceAsStream("$RESOURCE_DIR/skills/$file") ?: error("Missing skill file $file")
            stream.use { Files.copy(it, dest, StandardCopyOption.REPLACE_EXISTING) }
        }
        return target
    }

    /** `java -cp <nebs jar> com.nebs.core.ClaudeSkill [skills dir]`: installs into the given folder, or `./.claude/skills`. */
    @JvmStatic
    fun main(args: Array<String>) {
        val dir = args.firstOrNull()?.let { Path.of(it).toAbsolutePath().normalize() } ?: projectDir()
        println("Installed the Claude skill into ${install(dir)}")
    }
}
