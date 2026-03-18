package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.model.ModuleCredentials
import com.dynatrace.wizard.service.ProjectDetectionService
import com.dynatrace.wizard.util.DocumentationLinks
import com.dynatrace.wizard.util.ValidationUtil
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
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * Step 2 of the wizard: Dynatrace environment configuration.
 *
 * For single-app projects: shows one Application ID + Beacon URL pair.
 * For multi-app (MULTI_APP) projects: adds a toggle that lets the user either
 *   • use the same credentials for all modules (default), or
 *   • configure a separate Application ID + Beacon URL per module.
 */
class EnvironmentConfigStep {

    private data class ValidationIssue(
        val message: String,
        val component: JBTextField
    )

    // ── Shared credential fields (always present) ─────────────────────────────
    private val sharedAppIdField          = JBTextField()
    private val sharedBeaconUrlField      = JBTextField()
    private val sharedAppIdErrorLabel     = errorLabel()
    private val sharedBeaconUrlErrorLabel = errorLabel()
    private val sharedAppIdStatusIcon     = statusIcon()
    private val sharedBeaconUrlStatusIcon = statusIcon()

    // ── Per-module state ──────────────────────────────────────────────────────

    private data class ModuleEntry(
        val moduleName: String,
        val appIdField: JBTextField,
        val beaconUrlField: JBTextField,
        val appIdErrorLabel: JBLabel,
        val beaconUrlErrorLabel: JBLabel,
        val appIdStatusIcon: JBLabel,
        val beaconUrlStatusIcon: JBLabel
    )

    private val moduleEntries = mutableListOf<ModuleEntry>()

    /**
     * Names of the modules currently selected on the Modules tab.
     * The per-module credential section shows only these modules.
     * Empty means "all entries" (initial state before any selection event fires).
     */
    private var visibleModuleNames: Set<String> = emptySet()

    /** Toggle only created for MULTI_APP (≥2 app modules). */
    private var perModuleToggle: JBCheckBox? = null

    /** Container swapped between sharedSection and perModuleSection. */
    private val credentialContainer = JPanel(BorderLayout()).apply { isOpaque = false }

    /** Cached built panels — stored so prefillModuleCredentials can switch them. */
    private var builtSharedSection: JComponent? = null
    private var builtPerModuleSection: JComponent? = null

    /** Lightweight context hints shown above the credentials area. */
    private val modeSummaryLabel = helperLabel()
    private val scopeSummaryLabel = helperLabel()

    // ── Panel construction ────────────────────────────────────────────────────

    fun createPanel(appModules: List<ProjectDetectionService.ModuleInfo> = emptyList()): JComponent {
        val isMultiModule = appModules.size > 1

        // Initialise the visible set to ALL modules — narrowed later via updateVisibleModules()
        // once the user makes (or leaves) a selection on the Modules tab.
        visibleModuleNames = appModules.map { it.name }.toSet()

        // Shared fields defaults + validators
        sharedAppIdField.emptyText.setText("E.g. com.example.myapp")
        sharedBeaconUrlField.text = "https://"
        sharedAppIdField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updateValidation(
                sharedAppIdField.text, { ValidationUtil.validateApplicationId(it) }, { it.isBlank() },
                sharedAppIdErrorLabel, sharedAppIdStatusIcon
            )
        })
        sharedBeaconUrlField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updateValidation(
                sharedBeaconUrlField.text, { ValidationUtil.validateBeaconUrl(it) },
                { it.isBlank() || it == "https://" },
                sharedBeaconUrlErrorLabel, sharedBeaconUrlStatusIcon
            )
        })

        val sharedSection = buildSharedSection()
        builtSharedSection = sharedSection
        credentialContainer.add(sharedSection, BorderLayout.NORTH)

        val builder = FormBuilder.createFormBuilder()
            .addComponent(
                JBLabel("Environment configuration").apply {
                    font = JBUI.Fonts.label(16f).asBold()
                    foreground = WizardColors.accent
                    border = JBUI.Borders.emptyBottom(2)
                }
            )
            .addComponent(
                JBLabel("Enter your Application ID and Beacon URL from the Dynatrace portal.").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.emptyBottom(3)
                }
            )
            .addComponent(
                JBLabel(
                    "<html><b>Already have a mobile app:</b> Dynatrace portal → " +
                    "Mobile → (your app) → Settings → Instrumentation</html>"
                ).apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.empty(0, 0, 2, 0)
                }
            )
            .addComponent(
                JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    border = JBUI.Borders.emptyBottom(4)
                    add(JBLabel("New to Dynatrace?  ").apply {
                        font = JBUI.Fonts.smallFont()
                        foreground = UIUtil.getContextHelpForeground()
                    })
                    add(DocumentationLinks.createLinkLabel(
                        "Create your first mobile app →",
                        DocumentationLinks.CREATE_MOBILE_APP
                    ).also { (it as? JBLabel)?.font = JBUI.Fonts.smallFont() })
                }
            )
            .addComponent(modeSummaryLabel)
            .addComponent(scopeSummaryLabel.apply { border = JBUI.Borders.emptyBottom(4) })

        if (isMultiModule) {
            // Build per-module entries
            appModules.forEach { module ->
                val entry = ModuleEntry(
                    moduleName        = module.name,
                    appIdField        = JBTextField().also { it.emptyText.setText("E.g. com.example.${module.name}") },
                    beaconUrlField    = JBTextField().also { it.text = "https://" },
                    appIdErrorLabel   = errorLabel(),
                    beaconUrlErrorLabel = errorLabel(),
                    appIdStatusIcon   = statusIcon(),
                    beaconUrlStatusIcon = statusIcon()
                )
                entry.appIdField.document.addDocumentListener(object : DocumentAdapter() {
                    override fun textChanged(e: DocumentEvent) = updateValidation(
                        entry.appIdField.text, { ValidationUtil.validateApplicationId(it) }, { it.isBlank() },
                        entry.appIdErrorLabel, entry.appIdStatusIcon
                    )
                })
                entry.beaconUrlField.document.addDocumentListener(object : DocumentAdapter() {
                    override fun textChanged(e: DocumentEvent) = updateValidation(
                        entry.beaconUrlField.text, { ValidationUtil.validateBeaconUrl(it) },
                        { it.isBlank() || it == "https://" },
                        entry.beaconUrlErrorLabel, entry.beaconUrlStatusIcon
                    )
                })
                moduleEntries.add(entry)
            }

            val perModuleSection = buildPerModuleSection()
            builtPerModuleSection = perModuleSection

            val toggle = JBCheckBox("Configure each app module individually").apply {
                addActionListener { switchCredentialContainer() }
            }
            perModuleToggle = toggle

            builder
                .addVerticalGap(4)
                .addComponent(toggle)
                .addVerticalGap(8)
        } else {
            builder.addVerticalGap(8)
        }

        refreshContextHints()

        return builder
            .addComponent(credentialContainer)
            .addComponent(TitledSeparator("Documentation"))
            .addComponent(DocumentationLinks.createLinkLabel("Instrumentation via Plugin", DocumentationLinks.GETTING_STARTED))
            .addComponent(DocumentationLinks.createLinkLabel("Configure Plugin for Instrumentation", DocumentationLinks.CONFIGURE_PLUGIN))
            .addVerticalGap(8)
            .panel
            .also { it.border = JBUI.Borders.empty(12, 16, 12, 16) }
    }

    private fun switchCredentialContainer() {
        val switchingToPerModule = perModuleToggle?.isSelected == true
        val panel = if (switchingToPerModule) builtPerModuleSection else builtSharedSection
        panel ?: return

        // When activating per-module mode, seed any module whose fields are still
        // blank / default with the shared credentials.  Modules that already have
        // dedicated values (e.g. pre-filled from an existing per-module config) are
        // left untouched.
        if (switchingToPerModule) {
            val sharedAppId     = sharedAppIdField.text.trim()
            val sharedBeaconUrl = sharedBeaconUrlField.text.trim()
            moduleEntries.forEach { entry ->
                if (entry.appIdField.text.isBlank())
                    entry.appIdField.text = sharedAppId
                if (entry.beaconUrlField.text.isBlank() || entry.beaconUrlField.text.trim() == "https://")
                    entry.beaconUrlField.text = sharedBeaconUrl
            }
        }

        credentialContainer.removeAll()
        credentialContainer.add(panel, BorderLayout.NORTH)
        credentialContainer.revalidate()
        credentialContainer.repaint()
        refreshContextHints()
    }

    private fun buildSharedSection(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Application ID:"), fieldRow(sharedAppIdField, sharedAppIdStatusIcon), true)
            .addComponent(sharedAppIdErrorLabel.apply { border = JBUI.Borders.empty(1, 4, 4, 0) })
            .addLabeledComponent(JBLabel("Beacon URL:"), fieldRow(sharedBeaconUrlField, sharedBeaconUrlStatusIcon), true)
            .addComponent(sharedBeaconUrlErrorLabel.apply { border = JBUI.Borders.empty(1, 4, 2, 0) })
            .addComponent(
                JBLabel("<html>Format: <code>https://&lt;env-id&gt;.beacon.dynatrace.com</code></html>").apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.empty(0, 4, 4, 0)
                }
            )
            .panel.also { it.isOpaque = false }

    private fun buildPerModuleSection(): JComponent {
        val builder = FormBuilder.createFormBuilder()
        builder.addComponent(
            JBLabel(
                "<html>Enter individual credentials for each selected module. " +
                "When you switch into this mode, empty fields are pre-filled from the shared values to speed up setup.</html>"
            ).apply {
                foreground = UIUtil.getContextHelpForeground()
                border = JBUI.Borders.emptyBottom(4)
            }
        )
        // Show only modules the user has selected on the Modules tab.
        val entriesToShow = if (visibleModuleNames.isEmpty()) moduleEntries
                            else moduleEntries.filter { it.moduleName in visibleModuleNames }
        if (entriesToShow.isEmpty()) {
            builder.addComponent(
                JBLabel("No application modules are currently selected. Go back to the Modules step and choose at least one module to instrument.").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.emptyBottom(4)
                }
            )
        }
        entriesToShow.forEach { entry ->
            builder.addComponent(
                JBLabel("📱  ${entry.moduleName}").apply {
                    font = JBUI.Fonts.label().asBold()
                    border = JBUI.Borders.empty(8, 0, 4, 0)
                }
            )
            builder.addLabeledComponent(JBLabel("Application ID:"), fieldRow(entry.appIdField, entry.appIdStatusIcon), true)
            builder.addComponent(entry.appIdErrorLabel.apply { border = JBUI.Borders.empty(1, 4, 4, 0) })
            builder.addLabeledComponent(JBLabel("Beacon URL:"), fieldRow(entry.beaconUrlField, entry.beaconUrlStatusIcon), true)
            builder.addComponent(entry.beaconUrlErrorLabel.apply { border = JBUI.Borders.empty(1, 4, 2, 0) })
            builder.addComponent(
                JBLabel("<html>Format: <code>https://&lt;env-id&gt;.beacon.dynatrace.com</code></html>").apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.empty(0, 4, 8, 0)
                }
            )
        }
        return builder.panel.also { it.isOpaque = false }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fieldRow(field: JBTextField, icon: JBLabel): JPanel =
        JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            add(field, BorderLayout.CENTER)
            add(icon,  BorderLayout.EAST)
        }

    private fun updateValidation(
        text: String,
        validate: (String) -> ValidationUtil.ValidationResult,
        blank: (String) -> Boolean,
        error: JBLabel,
        icon: JBLabel
    ) {
        if (blank(text)) { error.text = ""; icon.text = ""; return }
        val result = validate(text)
        error.text = result.errorMessage ?: ""
        icon.text  = if (result.isSuccess) "✅" else "❌"
    }

    private fun errorLabel() = JBLabel("").apply {
        foreground = UIUtil.getErrorForeground()
        font = JBUI.Fonts.smallFont()
    }

    private fun helperLabel() = JBLabel("").apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
    }

    private fun statusIcon() = JBLabel("").apply {
        border = JBUI.Borders.emptyLeft(4)
    }

    private fun isPerModuleMode(): Boolean =
        perModuleToggle?.isSelected == true && moduleEntries.isNotEmpty()

    private fun visibleEntries(): List<ModuleEntry> =
        if (visibleModuleNames.isEmpty()) moduleEntries
        else moduleEntries.filter { it.moduleName in visibleModuleNames }

    private fun refreshContextHints() {
        val visibleCount = visibleEntries().size
        val totalCount = moduleEntries.size

        when {
            totalCount == 0 -> {
                modeSummaryLabel.text = "One application module will use the shared Dynatrace mobile app credentials below."
                scopeSummaryLabel.text = "Use the Application ID and Beacon URL from the Dynatrace portal for this app."
            }
            isPerModuleMode() && visibleCount == 0 -> {
                modeSummaryLabel.text = "Per-module mode is enabled, but no application modules are currently selected."
                scopeSummaryLabel.text = "Go back to the Modules step to select at least one app module."
            }
            isPerModuleMode() -> {
                modeSummaryLabel.text = "Per-module mode is enabled for $visibleCount of $totalCount detected app module(s)."
                scopeSummaryLabel.text = "Each selected module needs its own Application ID and Beacon URL."
            }
            else -> {
                modeSummaryLabel.text = "Shared mode is enabled — one credential set will be reused for $visibleCount selected app module(s)."
                scopeSummaryLabel.text = "Switch to per-module mode only if different app modules map to different Dynatrace mobile apps."
            }
        }
    }

    private fun validateRequiredField(
        text: String,
        validate: (String) -> ValidationUtil.ValidationResult,
        blank: (String) -> Boolean,
        blankMessage: String,
        error: JBLabel,
        icon: JBLabel
    ): String? {
        if (blank(text)) {
            error.text = blankMessage
            icon.text = "❌"
            return blankMessage
        }
        val result = validate(text)
        error.text = result.errorMessage ?: ""
        icon.text = if (result.isSuccess) "✅" else "❌"
        return result.errorMessage
    }

    private fun findFirstValidationIssue(): ValidationIssue? {
        if (isPerModuleMode()) {
            visibleEntries().forEach { entry ->
                val appIdError = validateRequiredField(
                    text = entry.appIdField.text,
                    validate = { ValidationUtil.validateApplicationId(it) },
                    blank = { it.isBlank() },
                    blankMessage = "Application ID is required for ${entry.moduleName}.",
                    error = entry.appIdErrorLabel,
                    icon = entry.appIdStatusIcon
                )
                if (appIdError != null) {
                    return ValidationIssue("${entry.moduleName}: $appIdError", entry.appIdField)
                }

                val beaconError = validateRequiredField(
                    text = entry.beaconUrlField.text,
                    validate = { ValidationUtil.validateBeaconUrl(it) },
                    blank = { it.isBlank() || it == "https://" },
                    blankMessage = "Beacon URL is required for ${entry.moduleName}.",
                    error = entry.beaconUrlErrorLabel,
                    icon = entry.beaconUrlStatusIcon
                )
                if (beaconError != null) {
                    return ValidationIssue("${entry.moduleName}: $beaconError", entry.beaconUrlField)
                }
            }
            return null
        }

        val sharedAppIdError = validateRequiredField(
            text = sharedAppIdField.text,
            validate = { ValidationUtil.validateApplicationId(it) },
            blank = { it.isBlank() },
            blankMessage = "Application ID is required.",
            error = sharedAppIdErrorLabel,
            icon = sharedAppIdStatusIcon
        )
        if (sharedAppIdError != null) {
            return ValidationIssue(sharedAppIdError, sharedAppIdField)
        }

        val sharedBeaconError = validateRequiredField(
            text = sharedBeaconUrlField.text,
            validate = { ValidationUtil.validateBeaconUrl(it) },
            blank = { it.isBlank() || it == "https://" },
            blankMessage = "Beacon URL is required.",
            error = sharedBeaconUrlErrorLabel,
            icon = sharedBeaconUrlStatusIcon
        )
        if (sharedBeaconError != null) {
            return ValidationIssue(sharedBeaconError, sharedBeaconUrlField)
        }

        return null
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun isValid(): Boolean = findFirstValidationIssue() == null

    fun getValidationMessage(): String? = findFirstValidationIssue()?.message

    fun focusFirstInvalidField(): JComponent? =
        findFirstValidationIssue()?.component?.also { it.requestFocusInWindow() }

    fun getAppId(): String     = sharedAppIdField.text.trim()
    fun getBeaconUrl(): String = sharedBeaconUrlField.text.trim()

    /**
     * Returns per-module credentials when individual mode is active.
     * Returns an empty map in shared mode — use [getAppId]/[getBeaconUrl] instead.
     */
    fun getModuleCredentials(): Map<String, ModuleCredentials> {
        if (!isPerModuleMode()) return emptyMap()
        return moduleEntries.associate { entry ->
            entry.moduleName to ModuleCredentials(
                appId      = entry.appIdField.text.trim(),
                beaconUrl  = entry.beaconUrlField.text.trim()
            )
        }
    }

    /**
     * Shows or hides the "Configure each app module individually" toggle.
     * When hidden (Plugin DSL approach), also resets to shared-credentials mode
     * so the per-module section is not left active.
     */
    fun setPerModuleToggleVisible(visible: Boolean) {
        val toggle = perModuleToggle ?: return
        toggle.isVisible = visible
        if (!visible && toggle.isSelected) {
            toggle.isSelected = false
            switchCredentialContainer()
        }
        refreshContextHints()
    }

    /**
     * Updates which app modules are displayed in the per-module credentials section.
     *
     * Called from [DynatraceWizardDialog] whenever:
     *  - the user switches between Plugin DSL and per-module approach, or
     *  - the Environment tab is shown (catches any checkbox changes made on the Modules tab).
     *
     * Entries for deselected modules are hidden but their typed values are retained so
     * they are restored immediately if the user re-selects those modules.
     * If per-module mode is currently active the container is refreshed in place.
     */
    fun updateVisibleModules(selectedModuleNames: Set<String>) {
        if (selectedModuleNames == visibleModuleNames) return   // nothing changed
        visibleModuleNames = selectedModuleNames
        // Rebuild the section panel for the new selection and cache it.
        builtPerModuleSection = buildPerModuleSection()
        // If the per-module panel is currently shown, swap it in immediately.
        if (isPerModuleMode()) {
            credentialContainer.removeAll()
            builtPerModuleSection?.let { credentialContainer.add(it, BorderLayout.NORTH) }
            credentialContainer.revalidate()
            credentialContainer.repaint()
        }
        refreshContextHints()
    }

    /** Pre-fills the shared fields with values from an existing configuration. */
    fun prefill(appId: String, beaconUrl: String) {
        sharedAppIdField.text    = appId
        sharedBeaconUrlField.text = beaconUrl
    }

    /**
     * Pre-fills per-module credentials and auto-switches to per-module mode
     * when [moduleCredentials] is non-empty (used in "Update Setup" flow).
     */
    fun prefillModuleCredentials(moduleCredentials: Map<String, ModuleCredentials>) {
        if (moduleCredentials.isEmpty() || moduleEntries.isEmpty()) return
        moduleEntries.forEach { entry ->
            moduleCredentials[entry.moduleName]?.let { creds ->
                entry.appIdField.text    = creds.appId
                entry.beaconUrlField.text = creds.beaconUrl
            }
        }
        perModuleToggle?.isSelected = true
        switchCredentialContainer()
    }
}
