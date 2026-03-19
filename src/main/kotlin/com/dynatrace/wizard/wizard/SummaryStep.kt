package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.model.DynatraceConfig
import com.dynatrace.wizard.model.SkillsExportConfig
import com.dynatrace.wizard.service.GradleModificationService
import com.dynatrace.wizard.service.ProjectDetectionService
import com.dynatrace.wizard.util.DocumentationLinks
import com.dynatrace.wizard.util.WizardColors
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.JBColor
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Final step of the wizard — diff-style change preview + optional details.
 *
 * Primary content (always visible):
 *  - Warnings (if any)
 *  - One card per affected file: path, change bullets, generated code prefixed with `+`
 *  - Manual-startup snippet (only when auto-start is disabled — it requires user action)
 *
 * Secondary content (collapsed, revealed via toggle):
 *  - At a Glance summary
 *  - Full configuration values
 *  - Library SDK info
 *  - AI skill preview
 */
class SummaryStep {

    /** Subtle separator color for card top-borders and code-block outlines (light/dark). */
    private val cardBorderColor: Color = JBColor(Color(0xD1D1D1), Color(0x3D3D3D))

    // ── Dynamic containers — repopulated on every updateSummary() call ────────
    private val warningsContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
    }
    private val filesContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
    }
    private val startupContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
    }

    // ── Details panel (collapsed by default) ─────────────────────────────────
    private lateinit var detailsPanel: JPanel
    private val detailsArea = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11f))
        background = UIUtil.getPanelBackground()
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(6)
        lineWrap = true; wrapStyleWord = true
        alignmentX = Component.LEFT_ALIGNMENT
    }
    private var showDetails = false
    private val detailsToggleButton = JButton("▼  Show configuration details").apply {
        isContentAreaFilled = false; isBorderPainted = false; isFocusPainted = false
        cursor = java.awt.Cursor(java.awt.Cursor.HAND_CURSOR)
        font = JBUI.Fonts.smallFont(); foreground = WizardColors.accent
        toolTipText = "Show at-a-glance info, full configuration values, and optional sections"
        addActionListener {
            showDetails = !showDetails
            if (::detailsPanel.isInitialized) {
                detailsPanel.isVisible = showDetails
                detailsPanel.parent?.revalidate(); detailsPanel.parent?.repaint()
            }
            text = if (showDetails) "▲  Hide configuration details" else "▼  Show configuration details"
        }
    }

    // ── Copy button ───────────────────────────────────────────────────────────
    private var fullPreviewText = ""
    private val copyPreviewButton = JButton("Copy full preview").apply {
        toolTipText = "Copy the complete summary and Gradle preview to the clipboard"
        addActionListener { CopyPasteManager.getInstance().setContents(StringSelection(fullPreviewText)) }
    }

    // ── Outer panel ref (needed for revalidate after dynamic updates) ─────────
    private lateinit var outerPanel: JPanel

    // ── Sealed type for parsed preview entries ────────────────────────────────
    private sealed class PreviewItem {
        data class FileChange(
            val path: String,
            val isCleanup: Boolean = false,
            val actions: List<String>,
            val codeBlock: String
        ) : PreviewItem()
        data class InfoNote(val text: String) : PreviewItem()
    }

    // ── Panel construction ────────────────────────────────────────────────────
    fun createPanel(): JComponent {
        detailsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false; isVisible = false
        }
        detailsPanel.add(sep("Configuration Details"))
        detailsPanel.add(detailsArea)

        outerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(8, 12, 12, 12)
        }
        fun row(c: JComponent) { c.alignmentX = Component.LEFT_ALIGNMENT; outerPanel.add(c) }
        fun gap(px: Int) = outerPanel.add(Box.createVerticalStrut(JBUI.scale(px)))

        // Header
        row(JBLabel("Summary & Confirmation").apply {
            font = JBUI.Fonts.label(16f).asBold(); foreground = WizardColors.accent
            border = JBUI.Borders.emptyBottom(2)
        })
        row(JBLabel("<html>Review the changes below. Click <b>Finish</b> to apply them to your Gradle files.</html>").apply {
            foreground = UIUtil.getContextHelpForeground(); border = JBUI.Borders.emptyBottom(6)
        })

        row(warningsContainer)                          // warnings (hidden when empty)
        row(sep("Files to be changed"))
        row(filesContainer)                             // per-file diff cards
        row(startupContainer)                           // startup snippet (conditional)

        // Actions row
        gap(8)
        row(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(copyPreviewButton)
            add(Box.createHorizontalStrut(JBUI.scale(8)))
            add(detailsToggleButton)
        })

        gap(4)
        row(detailsPanel)                               // collapsed details

        // Documentation
        gap(8)
        row(sep("Documentation"))
        listOf(
            "Instrumentation via Plugin"             to DocumentationLinks.GETTING_STARTED,
            "Configure Plugin for Instrumentation"   to DocumentationLinks.CONFIGURE_PLUGIN,
            "OneAgent SDK — Manual Instrumentation" to DocumentationLinks.MANUAL_SDK_INSTRUMENTATION,
            "Adjust Communication with OneAgent SDK" to DocumentationLinks.ADJUST_COMMUNICATION,
            "Standalone Manual Instrumentation"      to DocumentationLinks.STANDALONE_INSTRUMENTATION,
            "Monitoring Capabilities"                to DocumentationLinks.MONITORING_CAPABILITIES,
            "Adjust OneAgent Configuration"          to DocumentationLinks.ADJUST_ONEAGENT,
            "Release Notes"                          to DocumentationLinks.RELEASE_NOTES
        ).forEach { (label, url) -> row(DocumentationLinks.createLinkLabel(label, url)) }
        gap(8)

        return outerPanel
    }

    // ── Preview parser ────────────────────────────────────────────────────────
    /**
     * Parses the text produced by [GradleModificationService.generateChangePreview] into
     * a list of typed items without touching the service itself.
     *
     * Recognised markers:
     *  - Lines starting with `📄` → [PreviewItem.FileChange] (add/modify)
     *  - Lines starting with `🧹` → cleanup block; nested `📄` sub-items become
     *    [PreviewItem.FileChange] with `isCleanup = true`; otherwise [PreviewItem.InfoNote]
     *  - Lines starting with `ℹ️` (at start, not indented) → [PreviewItem.InfoNote]
     */
    private fun parsePreview(preview: String): List<PreviewItem> {
        val result = mutableListOf<PreviewItem>()
        val lines = preview.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("📄") -> {
                    val path = line.removePrefix("📄").trim()
                    val actions = mutableListOf<String>()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].startsWith("📄") && !lines[i].startsWith("🧹")) {
                        val l = lines[i]
                        when {
                            l.trimStart().startsWith("→") ||
                            (l.trimStart().startsWith("ℹ️") && !l.startsWith("ℹ️  No changes")) ->
                                actions.add(l.trim())
                            l.startsWith("    ") -> codeLines.add(l.removePrefix("    "))
                        }
                        i++
                    }
                    result += PreviewItem.FileChange(
                        path, false, actions,
                        codeLines.dropWhile { it.isBlank() }.joinToString("\n").trimEnd()
                    )
                }
                line.startsWith("🧹") -> {
                    val title = line.removePrefix("🧹").trim().removeSuffix(":").trim()
                    val subFiles = mutableListOf<PreviewItem.FileChange>()
                    i++
                    while (i < lines.size && !lines[i].startsWith("📄") && !lines[i].startsWith("🧹")) {
                        val l = lines[i]
                        if (l.trimStart().startsWith("📄")) {
                            val subPath = l.trimStart().removePrefix("📄").trim()
                            val subActions = mutableListOf<String>()
                            i++
                            while (i < lines.size
                                && !lines[i].trimStart().startsWith("📄")
                                && !lines[i].startsWith("📄")
                                && !lines[i].startsWith("🧹")) {
                                val sl = lines[i]
                                if (sl.trimStart().startsWith("→")) subActions.add(sl.trim())
                                i++
                            }
                            subFiles += PreviewItem.FileChange(subPath, true, subActions, "")
                        } else i++
                    }
                    if (subFiles.isNotEmpty()) result.addAll(subFiles)
                    else result += PreviewItem.InfoNote("🧹 $title")
                }
                line.startsWith("ℹ️") -> { result += PreviewItem.InfoNote(line.trim()); i++ }
                else -> i++
            }
        }
        return result
    }

    // ── Rendering helpers ─────────────────────────────────────────────────────
    private fun sep(title: String) =
        TitledSeparator(title).also { it.alignmentX = Component.LEFT_ALIGNMENT }

    /**
     * Builds a card panel for one affected file:
     * - Bold file path with emoji
     * - Each `→` action as a small-font label
     * - Generated code block with `+ ` prefix in [WizardColors.success] green
     *   (every line is a net addition for a first-setup; for update flows the old
     *    block is replaced wholesale, so all written lines are still additions)
     */
    private fun makeFileCard(item: PreviewItem.FileChange): JPanel {
        val card = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false; alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(cardBorderColor, 1, 0, 0, 0),
                JBUI.Borders.empty(8, 4, 10, 4)
            )
        }
        fun row(c: JComponent) { c.alignmentX = Component.LEFT_ALIGNMENT; card.add(c) }

        val emoji = if (item.isCleanup) "🧹" else "📄"
        val pathColor = if (item.isCleanup) WizardColors.warning else UIUtil.getLabelForeground()
        row(JBLabel("<html><b>$emoji &nbsp;${esc(item.path)}</b></html>").apply { foreground = pathColor })

        item.actions.forEach { action ->
            card.add(Box.createVerticalStrut(JBUI.scale(3)))
            row(JBLabel("<html>${esc(action)}</html>").apply {
                font = JBUI.Fonts.smallFont(); foreground = UIUtil.getContextHelpForeground()
                border = JBUI.Borders.emptyLeft(JBUI.scale(18))
            })
        }

        if (item.codeBlock.isNotBlank()) {
            card.add(Box.createVerticalStrut(JBUI.scale(6)))
            val codeText = item.codeBlock.lines().joinToString("\n") { "+ $it" }
            row(JTextArea(codeText).apply {
                isEditable = false
                font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11f))
                background = UIUtil.getPanelBackground(); foreground = WizardColors.success
                border = JBUI.Borders.compound(
                    JBUI.Borders.customLine(cardBorderColor),
                    JBUI.Borders.empty(4, 6)
                )
                lineWrap = false
                rows = minOf(codeText.lines().size, 20)
                // Prevent the textarea from imposing a minimum width wider than the dialog.
                minimumSize = Dimension(0, preferredSize.height)
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            })
        }
        return card
    }

    private fun makeInfoNote(text: String) = JBLabel("<html>${esc(text)}</html>").apply {
        font = JBUI.Fonts.smallFont(); foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(2, 4); alignmentX = Component.LEFT_ALIGNMENT
    }

    /** Minimal HTML entity escaping so labels don't interpret angle brackets as tags. */
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ── Update ────────────────────────────────────────────────────────────────
    fun updateSummary(
        projectInfo: ProjectDetectionService.ProjectInfo,
        config: DynatraceConfig,
        skillsConfig: SkillsExportConfig,
        gradleService: GradleModificationService,
        sdkLibraryModules: List<ProjectDetectionService.ModuleInfo> = emptyList(),
        deselectedModules: List<ProjectDetectionService.ModuleInfo> = emptyList(),
        skillsPreview: String? = null
    ) {
        val preview = gradleService.generateChangePreview(projectInfo, config, deselectedModules)
        val selectedAppModules = projectInfo.appModules.map { it.name }
        val sharedCredentials = config.moduleCredentials.isEmpty()

        // ── 1. Warnings ───────────────────────────────────────────────────────
        val warnings = buildList {
            if (!config.pluginEnabled)       add("Plugin enabled is turned off — automatic capture stays configured but inactive until re-enabled.")
            if (!config.autoInstrument)      add("Auto-instrumentation is disabled. Bytecode-based capture will not run.")
            if (config.sessionReplayEnabled) add("Session Replay is enabled. Verify environment and privacy approvals before release.")
            if (deselectedModules.isNotEmpty())
                add("${deselectedModules.size} previously configured module(s) will have Dynatrace removed: ${deselectedModules.joinToString { it.name }}.")
        }
        warningsContainer.removeAll()
        if (warnings.isNotEmpty()) {
            warningsContainer.add(sep("Warnings"))
            warnings.forEach { msg ->
                warningsContainer.add(Box.createVerticalStrut(JBUI.scale(2)))
                warningsContainer.add(JBLabel("<html>⚠️ &nbsp;${esc(msg)}</html>").apply {
                    font = JBUI.Fonts.smallFont(); foreground = WizardColors.warning
                    border = JBUI.Borders.empty(1, 4); alignmentX = Component.LEFT_ALIGNMENT
                })
            }
            warningsContainer.add(Box.createVerticalStrut(JBUI.scale(8)))
        }

        // ── 2. Per-file change cards ───────────────────────────────────────────
        filesContainer.removeAll()
        val items = try { parsePreview(preview) } catch (_: Exception) { emptyList() }
        if (items.isEmpty()) {
            // Safe fallback: raw monospace text if parsing unexpectedly yields nothing
            filesContainer.add(JTextArea(preview).apply {
                isEditable = false
                font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11f))
                background = UIUtil.getPanelBackground(); foreground = UIUtil.getLabelForeground()
                border = JBUI.Borders.empty(8); lineWrap = true; wrapStyleWord = true
                alignmentX = Component.LEFT_ALIGNMENT
            })
        } else {
            items.forEach { item ->
                when (item) {
                    is PreviewItem.FileChange -> filesContainer.add(makeFileCard(item))
                    is PreviewItem.InfoNote   -> filesContainer.add(makeInfoNote(item.text))
                }
            }
        }
        filesContainer.revalidate(); filesContainer.repaint()

        // ── 3. Manual startup snippet (only when auto-start is disabled) ───────
        startupContainer.removeAll()
        if (!config.autoStartEnabled) {
            startupContainer.add(sep("Manual Startup Required"))
            startupContainer.add(JBLabel(
                "<html>Auto-start is disabled. Add the following to your <code>Application.onCreate()</code>:</html>"
            ).apply {
                font = JBUI.Fonts.smallFont(); foreground = UIUtil.getContextHelpForeground()
                border = JBUI.Borders.empty(2, 4, 6, 0); alignmentX = Component.LEFT_ALIGNMENT
            })
            val snippet = "// Kotlin\nDynatrace.startup(\n    this,\n    DynatraceConfigurationBuilder(\n        \"${config.applicationId}\",\n        \"${config.beaconUrl}\"\n    ).buildConfiguration()\n)"
            startupContainer.add(JTextArea(snippet).apply {
                isEditable = false
                font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11f))
                background = UIUtil.getPanelBackground(); foreground = UIUtil.getLabelForeground()
                border = JBUI.Borders.compound(
                    JBUI.Borders.customLine(cardBorderColor), JBUI.Borders.empty(4, 6)
                )
                lineWrap = false; rows = snippet.lines().size
                minimumSize = Dimension(0, preferredSize.height)
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                alignmentX = Component.LEFT_ALIGNMENT
            })
            startupContainer.add(Box.createVerticalStrut(JBUI.scale(4)))
            startupContainer.add(JButton("Copy snippet").apply {
                alignmentX = Component.LEFT_ALIGNMENT
                addActionListener { CopyPasteManager.getInstance().setContents(StringSelection(snippet)) }
            })
            startupContainer.add(Box.createVerticalStrut(JBUI.scale(8)))
        }
        startupContainer.revalidate(); startupContainer.repaint()

        // ── 4. Secondary details (collapsed) ──────────────────────────────────
        val atAGlance = buildString {
            appendLine("=== At a Glance ===\n")
            appendLine("Setup flow:                ${projectInfo.setupFlow.title}")
            appendLine("Application modules:       ${selectedAppModules.ifEmpty { listOf("None selected") }.joinToString()}")
            appendLine("Credential mode:           ${if (sharedCredentials) "Shared" else "Per-module (${config.moduleCredentials.size} modules)"}")
            appendLine("Library SDK opt-in:        ${if (sdkLibraryModules.isEmpty()) "None" else sdkLibraryModules.joinToString { it.name }}")
            appendLine("AI skill export:           ${if (skillsConfig.exportSkillFile) "${skillsConfig.skillClient.label} · ${skillsConfig.skillInstallScope.label}" else "Disabled"}")
            appendLine()
        }
        val configSummary = buildString {
            appendLine("=== Configuration Summary ===\n")
            appendLine("Application ID:           ${config.applicationId}")
            appendLine("Beacon URL:               ${config.beaconUrl}")
            appendLine("Plugin enabled:           ${if (config.pluginEnabled) "Yes" else "No"}")
            appendLine("Auto-start:               ${if (config.autoStartEnabled) "Enabled" else "Disabled"}")
            appendLine("Auto-instrumentation:     ${if (config.autoInstrument) "Enabled" else "Disabled"}")
            appendLine("Crash reporting:          ${if (config.crashReporting) "Enabled" else "Disabled"}")
            appendLine("ANR reporting:            ${if (config.anrReporting) "Enabled (Android 11+)" else "Disabled"}")
            appendLine("Native crash reporting:   ${if (config.nativeCrashReporting) "Enabled (Android 11+)" else "Disabled"}")
            appendLine("User opt-in mode:         ${if (config.userOptIn) "Enabled" else "Disabled"}")
            appendLine("Name privacy:             ${if (config.namePrivacy) "Enabled" else "Disabled"}")
            appendLine("Compose instrumentation:  ${if (config.composeEnabled) "Enabled" else "Disabled"}")
            appendLine("Rage tap detection:       ${if (config.rageTapDetection) "Enabled" else "Disabled"}")
            appendLine("Web request monitoring:   ${if (config.webRequestsEnabled) "Enabled" else "Disabled"}")
            appendLine("Lifecycle monitoring:     ${if (config.lifecycleEnabled) "Enabled" else "Disabled"}")
            appendLine("Hybrid WebView:           ${if (config.hybridMonitoring) "Enabled" else "Disabled"}")
            appendLine("Location monitoring:      ${if (config.locationMonitoring) "Enabled" else "Disabled"}")
            if (config.agentBehaviorLoadBalancing || config.agentBehaviorGrail) {
                appendLine("Agent behavior:           " +
                    listOfNotNull(
                        "load balancing".takeIf { config.agentBehaviorLoadBalancing },
                        "Grail / New RUM".takeIf { config.agentBehaviorGrail }
                    ).joinToString(", "))
            }
            if (config.agentLogging) appendLine("⚠️  Debug logging:         ENABLED — Remove before production build!")
            if (config.excludePackages.isNotBlank() || config.excludeClasses.isNotBlank() || config.excludeMethods.isNotBlank()) {
                appendLine("Exclusions:")
                if (config.excludePackages.isNotBlank()) appendLine("  Packages: ${config.excludePackages}")
                if (config.excludeClasses.isNotBlank())  appendLine("  Classes:  ${config.excludeClasses}")
                if (config.excludeMethods.isNotBlank())  appendLine("  Methods:  ${config.excludeMethods}")
            }
            appendLine("Strict mode:              ${if (config.strictMode) "Enabled" else "Disabled"}")
            appendLine()
        }
        val sdkSection = if (sdkLibraryModules.isNotEmpty()) buildString {
            appendLine("=== OneAgent SDK (Library Modules) ===\n")
            val names = sdkLibraryModules.map { it.name }
            if (names.size == projectInfo.libraryModules.size) appendLine("Selected: all library modules.")
            else { appendLine("Selected modules:"); names.forEach { appendLine("  ✓ $it") } }
            appendLine()
        } else ""
        val skillsSection = if (!skillsPreview.isNullOrBlank()) buildString {
            appendLine("=== AI Skill Preview (first 35 lines) ===\n")
            val ls = skillsPreview.lines()
            appendLine(ls.take(35).joinToString("\n"))
            if (ls.size > 35) appendLine("\n... [${ls.size - 35} more lines — full content → ${skillsConfig.skillFilePath}]")
            appendLine()
        } else ""

        detailsArea.text = atAGlance + configSummary + sdkSection + skillsSection
        detailsArea.caretPosition = 0

        // ── 5. Full text for copy button ───────────────────────────────────────
        fullPreviewText = buildString {
            if (warnings.isNotEmpty()) { appendLine("=== Warnings ==="); warnings.forEach { appendLine("• $it") }; appendLine() }
            append(preview); appendLine()
            append(atAGlance); append(configSummary)
            if (sdkSection.isNotBlank()) append(sdkSection)
            if (skillsSection.isNotBlank()) append(skillsSection)
        }

        outerPanel.revalidate(); outerPanel.repaint()
    }
}
