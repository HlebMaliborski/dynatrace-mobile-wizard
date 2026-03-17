package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.model.SkillClient
import com.dynatrace.wizard.model.SkillInstallScope
import com.dynatrace.wizard.model.SkillsExportConfig
import com.dynatrace.wizard.service.ProjectDetectionService
import com.dynatrace.wizard.service.SkillsExportService
import com.dynatrace.wizard.util.DocumentationLinks
import com.dynatrace.wizard.util.WizardColors
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * Dedicated wizard step for exporting reusable AI skill files.
 *
 * @param project  the IntelliJ [Project] used for file-system detection; may be null in tests.
 */
class SkillsStep(private val project: Project? = null) {

    val exportSkillFileCheckBox = JBCheckBox("Export AI skill file", false)
    val skillClientComboBox = JComboBox(SkillClient.entries.toTypedArray())
    val skillInstallScopeComboBox = JComboBox(SkillInstallScope.entries.toTypedArray())
    val skillFilePathField = JBTextField()

    private var storedProjectInfo: ProjectDetectionService.ProjectInfo? = null
    private var isPathUserEdited = false
    /** True only during the first [createPanel] call; gates the initial auto-check on detection. */
    private var initialLoad = true

    private val resetPathButton = JButton("\u21ba Reset").apply {
        toolTipText = "Reset to the default path for the selected client and scope"
        isFocusable = false
    }

    private val skillInstallLocationsLabel = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
    }

    /** Dynamic label updated whenever the target path changes; shows found / missing skill files. */
    private val detectionStatusLabel = JBLabel().apply {
        font = JBUI.Fonts.smallFont()
        border = JBUI.Borders.emptyTop(2)
    }

    fun createPanel(projectInfo: ProjectDetectionService.ProjectInfo? = null): JComponent {
        storedProjectInfo = projectInfo
        skillFilePathField.emptyText.setText("Select a client and scope to see the install path")

        exportSkillFileCheckBox.addActionListener { syncAvailability() }
        skillClientComboBox.addActionListener { syncSkillExportPath() }
        skillInstallScopeComboBox.addActionListener { syncSkillExportPath() }

        skillFilePathField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!isSyncingPath) isPathUserEdited = true
            }
        })

        resetPathButton.addActionListener {
            isPathUserEdited = false
            syncSkillExportPath()
        }

        val pathRow = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(skillFilePathField, BorderLayout.CENTER)
            add(resetPathButton, BorderLayout.EAST)
        }

        val panel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("AI skills").apply {
                font = JBUI.Fonts.label(16f).asBold()
                foreground = WizardColors.accent
                border = JBUI.Borders.emptyBottom(2)
            })
            .addComponent(JBLabel("Export a reusable skill file that other AI coding agents can install and reuse.").apply {
                foreground = UIUtil.getContextHelpForeground()
                border = JBUI.Borders.emptyBottom(4)
            })
            .addComponent(TitledSeparator("Skill Export"))
            .addComponent(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(exportSkillFileCheckBox, BorderLayout.NORTH)
                add(JBLabel(
                    "<html>Exports a reusable AI skill set. Generates <b>5 files</b> into the same directory: " +
                    "<code>skills.md</code> (project-specific index with credentials and config) plus " +
                    "<code>setup.md</code>, <code>sdk-apis.md</code>, <code>monitoring.md</code>, and " +
                    "<code>troubleshooting.md</code> (static reference files).</html>"
                ).apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.empty(1, JBUI.scale(20), 4, 0)
                }, BorderLayout.CENTER)
            })
            .addLabeledComponent(JBLabel("Target client:"),
                fieldWithHint(skillClientComboBox, "Choose where the exported skill should be installed."), true)
            .addVerticalGap(4)
            .addLabeledComponent(JBLabel("Install scope:"),
                fieldWithHint(skillInstallScopeComboBox,
                    "User-level = available to all projects; project-level = repository-only."), true)
            .addVerticalGap(4)
            .addLabeledComponent(JBLabel("Output path:"),
                fieldWithHint(pathRow,
                    "Auto-computed from the selected client and scope. Edit to override, or click Reset to revert."), true)
            .addVerticalGap(4)
            .addComponent(skillInstallLocationsLabel)
            .addComponent(TitledSeparator("Detected Skills"))
            .addComponent(detectionStatusLabel)
            .addVerticalGap(6)
            .addComponent(TitledSeparator("Documentation"))
            .addComponent(DocumentationLinks.createLinkLabel(
                "Configure Plugin for Instrumentation", DocumentationLinks.CONFIGURE_PLUGIN))
            .addComponent(DocumentationLinks.createLinkLabel(
                "Multi-Module Projects", DocumentationLinks.MULTI_MODULE))
            .addVerticalGap(8)
            .panel
            .also { it.border = JBUI.Borders.empty(8, 12, 12, 12) }

        syncSkillExportPath()   // sets path + install-locations label + initial detection
        syncAvailability()
        initialLoad = false
        return panel
    }

    private var isSyncingPath = false

    private fun fieldWithHint(field: JComponent, hint: String): JComponent =
        JPanel(BorderLayout(0, JBUI.scale(2))).apply {
            isOpaque = false
            field.maximumSize = Dimension(Int.MAX_VALUE, field.preferredSize.height)
            add(field, BorderLayout.NORTH)
            add(JBLabel("<html>$hint</html>").apply {
                font = JBUI.Fonts.smallFont()
                foreground = UIUtil.getContextHelpForeground()
            }, BorderLayout.CENTER)
        }

    private fun syncSkillExportPath() {
        if (!isPathUserEdited) {
            val path = SkillsExportService(project).resolveOutputPath(
                SkillsExportConfig(
                    skillClient       = getSkillClient(),
                    skillInstallScope = getSkillInstallScope(),
                    skillFilePath     = ""
                )
            )
            isSyncingPath = true
            skillFilePathField.text = path
            isSyncingPath = false
        }
        updateInstallLocationsLabel()
        updateDetectionStatus()
    }

    private fun updateInstallLocationsLabel() {
        val location = SkillsExportService(project).buildInstallLocations()
            .find { it.client == getSkillClient() }
        skillInstallLocationsLabel.text = if (location != null) {
            "<html>User-level (all projects): <code>${location.userLevelPath}</code><br>" +
            "Project-level (repo only): <code>${location.projectLevelPath}</code></html>"
        } else {
            "<html>Select a client and scope to see the install path.</html>"
        }
    }

    /**
     * Scans the target directory for existing skill files and updates [detectionStatusLabel].
     * On the very first load, auto-checks the export checkbox when any files are already present.
     */
    private fun updateDetectionStatus() {
        val info = storedProjectInfo
        if (info == null || project == null) {
            detectionStatusLabel.text =
                "<html><i>Detection unavailable — open the wizard from an Android project.</i></html>"
            detectionStatusLabel.foreground = UIUtil.getContextHelpForeground()
            return
        }

        val client = getSkillClient()
        val scope  = getSkillInstallScope()
        val config = SkillsExportConfig(
            skillClient       = client,
            skillInstallScope = scope,
            skillFilePath     = skillFilePathField.text.trim()
        )
        val result = SkillsExportService(project).detectExistingSkills(info, config)

        // One-line context shown in every state: "Claude Code · Project-level · path/"
        val context = "<span style='color:gray'>${client.label} &middot; ${scope.label}" +
                      " &middot; <code>${result.directory}/</code></span>"

        when {
            result.isFullInstall -> {
                detectionStatusLabel.text =
                    "<html><b>${result.foundFiles.size} / ${result.totalFiles} files already installed</b>" +
                    " &nbsp; $context<br>" +
                    "<span style='color:gray'>${result.foundFiles.joinToString(" \u00b7 ")}</span></html>"
                detectionStatusLabel.foreground = WizardColors.success
            }
            result.isPartialInstall -> {
                val missing = SkillsExportService.ALL_SKILL_FILES - result.foundFiles.toSet()
                detectionStatusLabel.text =
                    "<html><b>${result.foundFiles.size} / ${result.totalFiles} files found</b>" +
                    " (partial install) &nbsp; $context<br>" +
                    "<span style='color:gray'>Found: ${result.foundFiles.joinToString(", ")}</span><br>" +
                    "<span style='color:gray'>Missing: ${missing.joinToString(", ")}</span></html>"
                detectionStatusLabel.foreground = WizardColors.warning
            }
            else -> {
                detectionStatusLabel.text =
                    "<html><span style='color:gray'>No existing skill files found &nbsp; $context</span></html>"
                detectionStatusLabel.foreground = UIUtil.getContextHelpForeground()
            }
        }

        // First-load only: auto-check the export checkbox when skills already exist.
        if (initialLoad && result.hasAny && !exportSkillFileCheckBox.isSelected) {
            exportSkillFileCheckBox.isSelected = true
            syncAvailability()
        }
    }

    private fun syncAvailability() {
        val enabled = exportSkillFileCheckBox.isSelected
        skillClientComboBox.isEnabled        = enabled
        skillInstallScopeComboBox.isEnabled   = enabled
        skillFilePathField.isEnabled          = enabled
        resetPathButton.isEnabled             = enabled
        skillInstallLocationsLabel.isEnabled  = enabled
    }

    fun shouldExportSkillFile(): Boolean = exportSkillFileCheckBox.isSelected
    fun getSkillClient(): SkillClient =
        skillClientComboBox.selectedItem as? SkillClient ?: SkillClient.COPILOT
    fun getSkillInstallScope(): SkillInstallScope =
        skillInstallScopeComboBox.selectedItem as? SkillInstallScope ?: SkillInstallScope.PROJECT_LEVEL
    fun getSkillFilePath(): String = skillFilePathField.text.trim()

    fun getSkillFileValidationComponent(): JComponent = skillFilePathField

    fun getSkillFileValidationMessage(): String? {
        if (!shouldExportSkillFile()) return null
        val path = getSkillFilePath()
        if (path.isBlank()) return "Output path must not be empty."
        if (path.endsWith("/")) return "Output path must point to a file, not a directory."
        if (path.startsWith("/")) return "Output path must be relative (e.g. .github/skills/...) or start with ~/ for a user-level path."
        if (!path.contains("/")) return "Output path must include a directory (e.g. .github/skills/dynatrace-android-sdk/skills.md)."
        return null
    }

    fun buildConfig(): SkillsExportConfig = SkillsExportConfig(
        exportSkillFile   = shouldExportSkillFile(),
        skillClient       = getSkillClient(),
        skillInstallScope = getSkillInstallScope(),
        skillFilePath     = getSkillFilePath()
    )

    fun prefill(config: SkillsExportConfig) {
        exportSkillFileCheckBox.isSelected     = config.exportSkillFile
        skillClientComboBox.selectedItem       = config.skillClient
        skillInstallScopeComboBox.selectedItem = config.skillInstallScope
        val computedDefault = SkillsExportService(project).resolveOutputPath(
            SkillsExportConfig(
                skillClient       = config.skillClient,
                skillInstallScope = config.skillInstallScope,
                skillFilePath     = ""
            )
        )
        if (config.skillFilePath.isNotBlank() && config.skillFilePath != computedDefault) {
            isSyncingPath = true
            skillFilePathField.text = config.skillFilePath
            isSyncingPath = false
            isPathUserEdited = true
        } else {
            syncSkillExportPath()
        }
        syncAvailability()
    }
}
