package com.dynatrace.wizard.model

/**
 * Separate model for the dedicated Skills tab.
 * Keeps AI skill export settings out of DynatraceConfig, which is Gradle-focused.
 */
data class SkillsExportConfig(
    val exportSkillFile: Boolean = false,
    val skillInstallScope: SkillInstallScope = SkillInstallScope.PROJECT_LEVEL,
    val skillClient: SkillClient = SkillClient.COPILOT,
    val skillFilePath: String = ".github/skills/dynatrace-android-sdk/skills.md"
)

