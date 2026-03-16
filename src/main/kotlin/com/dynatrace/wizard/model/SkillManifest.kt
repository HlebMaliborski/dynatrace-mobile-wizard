package com.dynatrace.wizard.model

/**
 * Shared Markdown skill-export metadata used by the wizard.
 */
enum class SkillInstallScope(val label: String) {
    USER_LEVEL("User-level"),
    PROJECT_LEVEL("Project-level");

    override fun toString(): String = label
}

enum class SkillClient(
    val label: String,
    val userLevelDirectory: String,
    val projectLevelDirectory: String
) {
    CLAUDE_CODE("Claude Code", "~/.claude/skills/", ".claude/skills/"),
    CODEX("Codex", "~/.codex/skills/", ".codex/skills/"),
    COPILOT("Copilot", "~/.copilot/skills/", ".github/skills/"),
    CURSOR("Cursor", "~/.cursor/skills/", ".cursor/skills/"),
    OPENCODE("OpenCode", "~/.config/opencode/skill/", ".opencode/skill/"),
    AMPCODE("AmpCode", "~/.config/agents/skills/", ".agents/skills/");

    override fun toString(): String = label
}

data class SkillInstallLocation(
    val client: SkillClient,
    val userLevelPath: String,
    val projectLevelPath: String
)


enum class SkillCapability {
    CONFIGURE_DYNATRACE_GRADLE,
    UPDATE_DYNATRACE_SETTINGS,
    ENABLE_CRASH_REPORTING,
    ENABLE_SESSION_REPLAY,
    ENFORCE_PRIVACY_MODE,
    EXPORT_SUMMARY_PREVIEW
}

