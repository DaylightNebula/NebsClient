package com.nebs.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class ClaudeSkillTest {
    private val skills: Path = Files.createTempDirectory("nebs-skills")

    @AfterTest
    fun tearDown() = skills.deleteRecursively()

    @Test
    fun `installs the bundled skill`() {
        val dir = ClaudeSkill.install(skills)

        assertEquals(skills.resolve(ClaudeSkill.NAME), dir)
        assertTrue(dir.resolve("SKILL.md").readText().startsWith("---\nname: ${ClaudeSkill.NAME}\n"))
        // Every file of the source skill is installed, and nothing else.
        val source = Path.of(System.getProperty("user.dir")).resolveSibling("claude-plugin/skills/${ClaudeSkill.NAME}")
        fun files(root: Path) = root.walk().map { root.relativize(it).toString() }.toSortedSet()
        assertEquals(files(source), files(dir))
    }

    @Test
    fun `replaces an older copy`() {
        val stale = skills.resolve(ClaudeSkill.NAME).resolve("references/removed.md")
        Files.createDirectories(stale.parent)
        stale.writeText("old")

        ClaudeSkill.install(skills)

        assertFalse(stale.exists())
        assertTrue(skills.resolve(ClaudeSkill.NAME).resolve("references/commands.md").exists())
    }
}
