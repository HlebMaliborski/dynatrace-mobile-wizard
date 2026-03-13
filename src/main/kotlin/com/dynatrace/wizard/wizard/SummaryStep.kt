package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.model.DynatraceConfig
import com.dynatrace.wizard.service.GradleModificationService
import com.dynatrace.wizard.service.ProjectDetectionService
import com.dynatrace.wizard.util.DocumentationLinks
import com.dynatrace.wizard.util.WizardColors
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JTextArea

/**
 * Step 4 of the wizard: Summary and confirmation.
 * Shows a preview of all changes that will be applied.
 */
class SummaryStep {

    private val summaryArea = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(12f).toInt())
        background = UIUtil.getPanelBackground()
        foreground = UIUtil.getLabelForeground()
        border = JBUI.Borders.empty(8)
        lineWrap = true
        wrapStyleWord = true
    }

    fun createPanel(): JComponent {
        val scrollArea = JBScrollPane(summaryArea).apply {
            preferredSize = Dimension(560, 300)
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy   = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        return FormBuilder.createFormBuilder()
            // ── Header ─────────────────────────────────────────────────────────
            .addComponent(
                JBLabel("Summary & Confirmation").apply {
                    font = JBUI.Fonts.label(16f).asBold()
                    foreground = WizardColors.accent
                    border = JBUI.Borders.emptyBottom(2)
                }
            )
            .addComponent(
                JBLabel(
                    "<html>Review the changes below. Click <b>Finish</b> to apply them to your Gradle files.</html>"
                ).apply {
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.emptyBottom(8)
                }
            )
            .addComponentFillVertically(scrollArea, 0)
            // ── Documentation ──────────────────────────────────────────────────
            .addComponent(TitledSeparator("Documentation"))
            .addComponent(DocumentationLinks.createLinkLabel("Instrumentation via Plugin", DocumentationLinks.GETTING_STARTED))
            .addComponent(DocumentationLinks.createLinkLabel("Configure Plugin for Instrumentation", DocumentationLinks.CONFIGURE_PLUGIN))
            .addComponent(DocumentationLinks.createLinkLabel("Monitoring Capabilities", DocumentationLinks.MONITORING_CAPABILITIES))
            .addComponent(DocumentationLinks.createLinkLabel("Adjust OneAgent Configuration", DocumentationLinks.ADJUST_ONEAGENT))
            .addComponent(DocumentationLinks.createLinkLabel("Release Notes", DocumentationLinks.RELEASE_NOTES))
            .addVerticalGap(8)
            .panel
            .also { it.border = JBUI.Borders.empty(12, 16, 12, 16) }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    fun updateSummary(
        projectInfo: ProjectDetectionService.ProjectInfo,
        config: DynatraceConfig,
        gradleService: GradleModificationService,
        sdkLibraryModules: List<ProjectDetectionService.ModuleInfo> = emptyList(),
        deselectedModules: List<ProjectDetectionService.ModuleInfo> = emptyList()
    ) {
        val preview = gradleService.generateChangePreview(projectInfo, config, deselectedModules)

        val configSummary = buildString {
            appendLine("=== Configuration Summary ===\n")
            appendLine("Application ID:           ${config.applicationId}")
            appendLine("Beacon URL:               ${config.beaconUrl}")
            appendLine("Plugin enabled:           ${if (config.pluginEnabled) "Yes" else "No (bytecode instrumentation disabled)"}")
            appendLine("Auto-start:               ${if (config.autoStartEnabled) "Enabled" else "Disabled — manual startup required"}")
            appendLine("Auto-instrumentation:     ${if (config.autoInstrument) "Enabled" else "Disabled"}")
            appendLine("Crash reporting:          ${if (config.crashReporting) "Enabled" else "Disabled"}")
            appendLine("User opt-in mode:         ${if (config.userOptIn) "Enabled" else "Disabled"}")
            appendLine("Name privacy:             ${if (config.namePrivacy) "Enabled (action names masked)" else "Disabled"}")
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
            if (config.excludePackages.isNotBlank() || config.excludeClasses.isNotBlank() || config.excludeMethods.isNotBlank()) {
                appendLine("Exclusions:")
                if (config.excludePackages.isNotBlank()) appendLine("  Packages: ${config.excludePackages}")
                if (config.excludeClasses.isNotBlank())  appendLine("  Classes:  ${config.excludeClasses}")
                if (config.excludeMethods.isNotBlank())  appendLine("  Methods:  ${config.excludeMethods}")
            }
            appendLine("Strict mode:              ${if (config.strictMode) "Enabled (build fails for unmatched variants)" else "Disabled"}")
            appendLine()
        }

        val manualStartupSnippet = if (!config.autoStartEnabled) buildString {
            appendLine("=== Manual OneAgent Startup ===\n")
            appendLine("Auto-start is disabled. Add the following to your Application.onCreate():\n")
            appendLine("// Kotlin")
            appendLine("import com.dynatrace.android.agent.Dynatrace")
            appendLine("import com.dynatrace.android.agent.conf.DynatraceConfigurationBuilder\n")
            appendLine("Dynatrace.startup(")
            appendLine("    this,")
            appendLine("    DynatraceConfigurationBuilder(")
            appendLine("        \"${config.applicationId}\",")
            appendLine("        \"${config.beaconUrl}\"")
            appendLine("    ).buildConfiguration()")
            appendLine(")\n")
            appendLine("// Java")
            appendLine("Dynatrace.startup(this, new DynatraceConfigurationBuilder(")
            appendLine("    \"${config.applicationId}\",")
            appendLine("    \"${config.beaconUrl}\"")
            appendLine(").buildConfiguration());\n")
            appendLine("// Note: values set in DynatraceConfigurationBuilder override DSL values.")
            appendLine()
        } else ""

        val sdkPreview = if (sdkLibraryModules.isNotEmpty()) buildString {
            appendLine()
            appendLine("=== OneAgent SDK (Library Modules) ===\n")
            appendLine("The following subprojects {} block will be appended to the project")
            appendLine("root build file. It uses pluginManager.withPlugin(\"com.android.library\")")
            appendLine("to inject agentDependency() only into the selected module(s),")
            appendLine("so their code can call Dynatrace APIs directly.\n")
            val names = sdkLibraryModules.map { it.name }
            val allNames = projectInfo.libraryModules.map { it.name }
            val filterAll = names.size == allNames.size
            if (filterAll) {
                appendLine("Selected: all library modules — no name filter will be emitted.")
            } else {
                appendLine("Selected modules:")
                names.forEach { appendLine("  ✓ $it") }
            }
        } else ""

        summaryArea.text = configSummary + preview + sdkPreview + manualStartupSnippet
        summaryArea.caretPosition = 0
    }
}
