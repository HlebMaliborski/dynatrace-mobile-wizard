package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.model.SkillClient
import com.dynatrace.wizard.model.SkillInstallScope
import com.dynatrace.wizard.model.SkillsExportConfig
import com.dynatrace.wizard.service.SkillsExportService
import com.dynatrace.wizard.util.DocumentationLinks
import com.dynatrace.wizard.util.WizardColors
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
 */
class SkillsStep {

    val exportSkillFileCheckBox = JBCheckBox("Export AI skill file", false)
    val skillClientComboBox = JComboBox(SkillClient.entries.toTypedArray())
    val skillInstallScopeComboBox = JComboBox(SkillInstallScope.entries.toTypedArray())
    val skillFilePathField = JBTextField()

    /**
     * True once the user has manually edited the path field.
     * While false the field is kept in sync with the auto-computed path whenever
     * the client or scope selection changes.
     */
    private var isPathUserEdited = false

    private val resetPathButton = JButton("↺ Reset").apply {
        toolTipText = "Reset to the default path for the selected client and scope"
        isFocusable = false
    }

    private val skillInstallLocationsLabel = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
    }

    fun createPanel(): JComponent {
        skillFilePathField.emptyText.setText("Select a client and scope to see the install path")

        exportSkillFileCheckBox.addActionListener { syncAvailability() }
        skillClientComboBox.addActionListener { syncSkillExportPath() }
        skillInstallScopeComboBox.addActionListener { syncSkillExportPath() }

        // Track manual edits — once the user types, stop auto-updating the field.
        skillFilePathField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!isSyncingPath) isPathUserEdited = true
            }
        })

        resetPathButton.addActionListener {
            isPathUserEdited = false
            syncSkillExportPath()
        }

        // Path row: field + Reset button side-by-side
        val pathRow = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(skillFilePathField, BorderLayout.CENTER)
            add(resetPathButton, BorderLayout.EAST)
        }

        val panel = FormBuilder.createFormBuilder()
            .addComponent(
                JBLabel("AI skills").apply {
                    font = JBUI.Fonts.label(16f).asBold()
                    foreground = WizardColors.accent
                    border = JBUI.Borders.emptyBottom(2)
                }
            )
            .addComponent(
                JBLabel("Export a reusable skill file that other AI coding agents can install and reuse.").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.emptyBottom(4)
                }
            )
            .addComponent(TitledSeparator("Skill Export"))
            .addComponent(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(exportSkillFileCheckBox, BorderLayout.NORTH)
                    add(JBLabel("<html>Exports a reusable AI skill file. The generated file is always <code>skills.md</code> and includes the complete wizard context for reuse by coding agents.</html>").apply {
                        font = JBUI.Fonts.smallFont()
                        foreground = UIUtil.getContextHelpForeground()
                        border = JBUI.Borders.empty(1, JBUI.scale(20), 4, 0)
                    }, BorderLayout.CENTER)
                }
            )
            .addLabeledComponent(JBLabel("Target client:"), fieldWithHint(skillClientComboBox,
                "Choose where the exported skill should be installed."), true)
            .addVerticalGap(4)
            .addLabeledComponent(JBLabel("Install scope:"), fieldWithHint(skillInstallScopeComboBox,
                "User-level = available to all projects; project-level = repository-only."), true)
            .addVerticalGap(4)
            .addLabeledComponent(JBLabel("Output path:"), fieldWithHint(pathRow,
                "Auto-computed from the selected client and scope. Edit to override, or click ↺ to reset."), true)
            .addVerticalGap(4)
            .addComponent(skillInstallLocationsLabel)
            .addComponent(TitledSeparator("Documentation"))
            .addComponent(DocumentationLinks.createLinkLabel("Configure Plugin for Instrumentation", DocumentationLinks.CONFIGURE_PLUGIN))
            .addComponent(DocumentationLinks.createLinkLabel("Multi-Module Projects", DocumentationLinks.MULTI_MODULE))
            .addVerticalGap(8)
            .panel
            .also { it.border = JBUI.Borders.empty(8, 12, 12, 12) }

        syncSkillExportPath()
        syncAvailability()
        return panel
    }

    /** Guard flag to suppress [isPathUserEdited] being set during programmatic text changes. */
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
        // Only auto-update the path when the user has not customised it.
        if (isPathUserEdited) {
            updateInstallLocationsLabel()
            return
        }
        val service = SkillsExportService()
        val path = service.resolveOutputPath(
            SkillsExportConfig(
                skillClient = getSkillClient(),
                skillInstallScope = getSkillInstallScope(),
                skillFilePath = ""
            )
        )
        isSyncingPath = true
        skillFilePathField.text = path
        isSyncingPath = false
        updateInstallLocationsLabel()
    }

    private fun updateInstallLocationsLabel() {
        val locations = SkillsExportService().buildInstallLocations()
        val rows = locations.joinToString("<br>") {
            "<b>${it.client.label}</b>: ${it.userLevelPath} &nbsp;|&nbsp; ${it.projectLevelPath}"
        }
        skillInstallLocationsLabel.text = "<html>User-level = available to all projects; project-level = repository-only.<br><br>$rows</html>"
    }

    private fun syncAvailability() {
        val enabled = exportSkillFileCheckBox.isSelected
        skillClientComboBox.isEnabled = enabled
        skillInstallScopeComboBox.isEnabled = enabled
        skillFilePathField.isEnabled = enabled
        resetPathButton.isEnabled = enabled
        skillInstallLocationsLabel.isEnabled = enabled
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
        if (path.startsWith("/")) return "Output path must be relative (e.g. .github/skills/…) or start with ~/ for a user-level path."
        if (!path.contains("/")) return "Output path must include a directory (e.g. .github/skills/dynatrace-android-sdk/skills.md)."
        return null
    }

    fun buildConfig(): SkillsExportConfig = SkillsExportConfig(
        exportSkillFile = shouldExportSkillFile(),
        skillClient = getSkillClient(),
        skillInstallScope = getSkillInstallScope(),
        skillFilePath = getSkillFilePath()
    )

    fun prefill(config: SkillsExportConfig) {
        exportSkillFileCheckBox.isSelected = config.exportSkillFile
        skillClientComboBox.selectedItem = config.skillClient
        skillInstallScopeComboBox.selectedItem = config.skillInstallScope
        // Restore a previously-saved custom path without triggering auto-sync.
        // If the path matches what auto-sync would produce, leave isPathUserEdited false
        // so future client/scope changes continue to update the field automatically.
        val computedDefault = SkillsExportService().resolveOutputPath(
            SkillsExportConfig(skillClient = config.skillClient,
                               skillInstallScope = config.skillInstallScope,
                               skillFilePath = "")
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
