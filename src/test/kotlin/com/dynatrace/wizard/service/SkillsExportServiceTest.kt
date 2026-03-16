package com.dynatrace.wizard.service

import com.dynatrace.wizard.model.DynatraceConfig
import com.dynatrace.wizard.model.SkillClient
import com.dynatrace.wizard.model.SkillInstallScope
import com.dynatrace.wizard.model.SkillsExportConfig
import com.dynatrace.wizard.service.ProjectDetectionService.SetupFlow
import com.dynatrace.wizard.wizard.SkillsStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SkillsExportServiceTest {

    private val projectInfo = ProjectDetectionService.ProjectInfo(
        isAndroidProject = true,
        projectBuildFile = null,
        appBuildFile = null,
        settingsFile = null,
        isKotlinDsl = true,
        appModuleName = "app",
        setupFlow = SetupFlow.SINGLE_APP,
        allModules = emptyList(),
        isSingleBuildFile = false,
        usesPluginDsl = true,
        hasBuildscriptBlock = false
    )

    @Test
    fun `generateSkillsMarkdown emits frontmatter and install table`() {
        val service = SkillsExportService()
        val markdown = service.generateSkillsMarkdown(
            projectInfo,
            DynatraceConfig(
                crashReporting = true,
                sessionReplayEnabled = true,
                userOptIn = true
            ),
            SkillsExportConfig(
                exportSkillFile = true,
                skillClient = SkillClient.COPILOT,
                skillInstallScope = SkillInstallScope.PROJECT_LEVEL
            ),
            generatedAt = Instant.parse("2026-01-01T00:00:00Z")
        )

        assertTrue(markdown.contains("name: dynatrace-android-sdk"))
        assertTrue(markdown.contains("# Dynatrace Android SDK"))
        assertTrue(markdown.contains("| Claude Code | `~/.claude/skills/dynatrace-android-sdk/skills.md` | `.claude/skills/dynatrace-android-sdk/skills.md` |"))
        assertTrue(markdown.contains("| Copilot | `~/.copilot/skills/dynatrace-android-sdk/skills.md` | `.github/skills/dynatrace-android-sdk/skills.md` |"))
        assertTrue(markdown.contains("User-level = available to all projects; project-level = repository-only."))
        assertTrue(markdown.contains("Crash reporting: Enabled"))
        assertTrue(markdown.contains("Session Replay: Enabled"))
        assertTrue(markdown.contains("User opt-in: Enabled"))
    }

    @Test
    fun `resolveOutputPath follows selected client and scope`() {
        val service = SkillsExportService()
        val path = service.resolveOutputPath(
            SkillsExportConfig(
                exportSkillFile = true,
                skillClient = SkillClient.CLAUDE_CODE,
                skillInstallScope = SkillInstallScope.USER_LEVEL,
                skillFilePath = ""
            )
        )

        assertTrue(path == "~/.claude/skills/dynatrace-android-sdk/skills.md")
    }

    @Test
    fun `generateSkillsMarkdown includes per module credentials and no legacy manifest markers`() {
        val service = SkillsExportService()
        val markdown = service.generateSkillsMarkdown(
            projectInfo,
            DynatraceConfig(
                moduleCredentials = mapOf(
                    "app" to com.dynatrace.wizard.model.ModuleCredentials(
                        appId = "mobile-app-id",
                        beaconUrl = "https://tenant.example/mbeacon"
                    )
                )
            ),
            SkillsExportConfig(exportSkillFile = true),
            generatedAt = Instant.parse("2026-01-01T00:00:00Z")
        )

        assertTrue(markdown.contains("## Per-Module Credentials"))
        assertTrue(markdown.contains("mobile-app-id"))
        assertFalse(markdown.contains("SKILL_MANIFEST"))
    }
}

class SkillsStepValidationTest {

    private fun stepWithExport(path: String): SkillsStep {
        val step = SkillsStep()
        step.exportSkillFileCheckBox.isSelected = true
        step.skillFilePathField.text = path
        return step
    }

    @Test
    fun `no validation error when export is disabled regardless of path`() {
        val step = SkillsStep()
        step.exportSkillFileCheckBox.isSelected = false
        step.skillFilePathField.text = ""
        assertNull(step.getSkillFileValidationMessage())
    }

    @Test
    fun `error when path is blank and export is enabled`() {
        val step = stepWithExport("   ")
        assertNotNull(step.getSkillFileValidationMessage())
    }

    @Test
    fun `error when path ends with slash`() {
        val step = stepWithExport(".github/skills/")
        assertNotNull(step.getSkillFileValidationMessage())
    }

    @Test
    fun `error when path is absolute without tilde`() {
        val step = stepWithExport("/home/user/skills/skills.md")
        assertNotNull(step.getSkillFileValidationMessage())
    }

    @Test
    fun `no error for tilde home path`() {
        val step = stepWithExport("~/.copilot/skills/dynatrace-android-sdk/skills.md")
        assertNull(step.getSkillFileValidationMessage())
    }

    @Test
    fun `error when path has no directory component`() {
        val step = stepWithExport("skills.md")
        assertNotNull(step.getSkillFileValidationMessage())
    }

    @Test
    fun `no error for valid relative path`() {
        val step = stepWithExport(".github/skills/dynatrace-android-sdk/skills.md")
        assertNull(step.getSkillFileValidationMessage())
    }

    @Test
    fun `prefill with default path leaves isPathUserEdited false so auto-sync still works`() {
        val step = SkillsStep()
        step.createPanel()   // initialises the document listener
        val defaultPath = SkillsExportService().resolveOutputPath(
            SkillsExportConfig(
                skillClient = SkillClient.COPILOT,
                skillInstallScope = SkillInstallScope.PROJECT_LEVEL,
                skillFilePath = ""
            )
        )
        step.prefill(SkillsExportConfig(
            exportSkillFile = true,
            skillClient = SkillClient.COPILOT,
            skillInstallScope = SkillInstallScope.PROJECT_LEVEL,
            skillFilePath = defaultPath
        ))
        // Switching to a different client should auto-update the path
        step.skillClientComboBox.selectedItem = SkillClient.CLAUDE_CODE
        assertTrue(step.getSkillFilePath().contains("claude"))
    }

    @Test
    fun `prefill with custom path preserves it and does not auto-overwrite`() {
        val step = SkillsStep()
        step.createPanel()
        val customPath = "custom/dir/my-skill.md"
        step.prefill(SkillsExportConfig(
            exportSkillFile = true,
            skillClient = SkillClient.COPILOT,
            skillInstallScope = SkillInstallScope.PROJECT_LEVEL,
            skillFilePath = customPath
        ))
        // Switching client should NOT overwrite the custom path
        step.skillClientComboBox.selectedItem = SkillClient.CURSOR
        assertTrue(step.getSkillFilePath() == customPath)
    }
}
