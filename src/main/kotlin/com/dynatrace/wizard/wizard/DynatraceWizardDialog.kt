package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.model.DynatraceConfig
import com.dynatrace.wizard.model.SkillsExportConfig
import com.dynatrace.wizard.service.GradleModificationService
import com.dynatrace.wizard.service.ProjectDetectionService
import com.dynatrace.wizard.service.ProjectDetectionService.ModuleType
import com.dynatrace.wizard.service.ProjectDetectionService.SetupFlow
import com.dynatrace.wizard.service.SkillsExportService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * Main wizard dialog — step-based wizard driven by [WizardStepBar] + Back / Next buttons.
 *
 * Each step is a card in a [CardLayout]; the [WizardStepBar] above the card area is the sole
 * visual navigation element (replacing the old [com.intellij.ui.components.JBTabbedPane] headers).
 * Clicking a step-bar circle navigates directly, subject to the same validation rules as Next →.
 *
 * Step count varies by project type:
 *  • MULTI_APP or project has library modules (7 steps):
 *      Welcome → Modules → Technologies → Environment → Features → Skills → Summary
 *  • All other flows (6 steps — Modules step omitted):
 *      Welcome → Technologies → Environment → Features → Skills → Summary
 */
class DynatraceWizardDialog(
    private val project: Project,
    /** Non-null when the user chose "Update Setup" for an already-configured project. */
    existingConfig: DynatraceConfig? = null,
    /**
     * When the caller (e.g. [com.dynatrace.wizard.DynatraceWizardAction]) has already run
     * project detection, pass the result here to avoid a redundant scan when
     * [WelcomeStep.createPanel] is called during dialog initialisation.
     */
    preDetectedInfo: ProjectDetectionService.ProjectInfo? = null
) : DialogWrapper(project) {

    private val welcomeStep         = WelcomeStep(project, preDetectedInfo)
    private val environmentStep     = EnvironmentConfigStep()
    private val moduleSelectionStep = ModuleSelectionStep()
    private val supportedTechStep   = SupportedTechnologiesStep()
    private val featureStep         = FeatureToggleStep()
    private val skillsStep          = SkillsStep(project)
    private val summaryStep         = SummaryStep()

    // ── Card-layout navigation state ─────────────────────────────────────────
    private val cardPanel        = JPanel(CardLayout())
    private var currentTabIndex  = 0
    private var totalTabs        = 0

    private val gradleService       = GradleModificationService(project)
    private val skillsExportService = SkillsExportService(project)

    private lateinit var projectInfo: ProjectDetectionService.ProjectInfo
    private lateinit var prevAction:  DialogWrapperAction
    private lateinit var nextAction:  DialogWrapperAction
    private lateinit var stepBar:     WizardStepBar

    // ── Dynamic step indices (set in createCenterPanel) ───────────────────────
    private var idxModules:     Int? = null
    private var idxEnvironment: Int  = -1
    private var idxSkills:      Int  = -1
    private var idxSummary:     Int  = -1

    init {
        title = if (existingConfig != null) "Configure Dynatrace Mobile SDK — Update"
                else                        "Configure Dynatrace Mobile SDK"
        setOKButtonText("Apply Configuration")
        init()
        // Pre-populate fields when updating an existing config
        existingConfig?.let {
            environmentStep.prefill(it.applicationId, it.beaconUrl)

            // The action's detectProject() may have run before the VirtualFile cache was
            // fully refreshed and could have missed some app modules, leaving moduleCredentials
            // empty even though every module already has a dynatrace {} block.
            // Re-read from the authoritative module list that WelcomeStep produced (which
            // ran after the dialog was created and the cache is warm).
            val moduleCreds = if (it.moduleCredentials.isNotEmpty()) {
                it.moduleCredentials
            } else if (projectInfo.appModules.size > 1) {
                gradleService.readExistingModuleCredentials(projectInfo.appModules)
            } else {
                emptyMap()
            }

            if (moduleCreds.isNotEmpty()) {
                environmentStep.prefillModuleCredentials(moduleCreds)
            }

            featureStep.prefill(it)
        }
        skillsStep.prefill(SkillsExportConfig())
        updateNavButtons()
        // Tooltip is set here (after init()) because the JButton only exists once
        // DialogWrapper has built the button panel.
        getButton(okAction)?.toolTipText =
            "Writes changes to your Gradle files — undoable with Ctrl+Z"
    }

    override fun createCenterPanel(): JComponent {
        val welcomePanel     = welcomeStep.createPanel()
        projectInfo          = welcomeStep.getProjectInfo()
        val environmentPanel = environmentStep.createPanel(projectInfo.appModules)
        val modulePanel      = moduleSelectionStep.createPanel(projectInfo)
        val techPanel        = supportedTechStep.createPanel(projectInfo)
        val featurePanel     = featureStep.createPanel()
        val skillsPanel      = skillsStep.createPanel(projectInfo)
        val summaryPanel     = summaryStep.createPanel()

        // Show the Modules tab only when the user has real decisions to make there:
        //  • MULTI_APP — user picks which app modules to instrument + approach choice
        //  • Library modules present — user opts in to the OneAgent SDK per library
        // For every other flow the module information shown on the Welcome tab is
        // sufficient; a separate read-only Modules tab would be wasted real estate.
        val showModulesTab = projectInfo.setupFlow == SetupFlow.MULTI_APP ||
                             projectInfo.libraryModules.isNotEmpty()

        // ── Register cards ────────────────────────────────────────────────────
        val stepNames = mutableListOf<String>()
        fun addCard(name: String, panel: JComponent) {
            stepNames += name
            cardPanel.add(scrollable(panel), totalTabs.toString())
            totalTabs++
        }

        addCard("Welcome",      welcomePanel)
        if (showModulesTab) {
            idxModules = totalTabs
            addCard("Modules",  modulePanel)
        }
        addCard("Technologies", techPanel)
        idxEnvironment = totalTabs
        addCard("Environment",  environmentPanel)
        addCard("Features",     featurePanel)
        idxSkills = totalTabs
        addCard("Skills",       skillsPanel)
        idxSummary = totalTabs
        addCard("Summary",      summaryPanel)

        // ── Step bar ──────────────────────────────────────────────────────────
        stepBar = WizardStepBar(stepNames)
        stepBar.onStepClicked = { targetIdx ->
            if (targetIdx <= currentTabIndex) {
                // Backward navigation: always allow
                showTab(targetIdx)
            } else {
                // Forward navigation: check required gates
                val idxM = idxModules
                when {
                    idxM != null &&
                    targetIdx > idxM &&
                    projectInfo.setupFlow == SetupFlow.MULTI_APP &&
                    !moduleSelectionStep.hasSelection(projectInfo.appModules) -> {
                        showTab(idxM)
                        val target = moduleSelectionStep.getValidationComponent()
                        setErrorText("Select at least one application module before continuing.", target)
                        target?.requestFocusInWindow()
                    }
                    targetIdx > idxEnvironment && !environmentStep.isValid() -> {
                        showTab(idxEnvironment)
                        val target = environmentStep.focusFirstInvalidField()
                        setErrorText(
                            environmentStep.getValidationMessage()
                                ?: "Enter your Application ID and Beacon URL before continuing.",
                            target
                        )
                    }
                    else -> showTab(targetIdx)
                }
            }
        }

        // ── Approach-change wiring (unchanged logic) ──────────────────────────
        moduleSelectionStep.setOnApproachChanged {
            if (projectInfo.setupFlow == SetupFlow.MULTI_APP) {
                val usePluginDsl = moduleSelectionStep.getUsePluginDslForMultiApp()
                environmentStep.setPerModuleToggleVisible(!usePluginDsl)
                if (!usePluginDsl) environmentStep.updateVisibleModules(selectedModuleNames())
            }
        }
        if (projectInfo.setupFlow == SetupFlow.MULTI_APP) {
            val usePluginDsl = moduleSelectionStep.getUsePluginDslForMultiApp()
            environmentStep.setPerModuleToggleVisible(!usePluginDsl)
            if (!usePluginDsl) environmentStep.updateVisibleModules(selectedModuleNames())
        }

        cardPanel.preferredSize = Dimension(760, 590)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(stepBar,    BorderLayout.NORTH)
        wrapper.add(cardPanel,  BorderLayout.CENTER)
        return wrapper
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * Switches the visible card, updates the step bar, refreshes nav buttons,
     * and fires [onTabShown] for any tab-specific side-effects.
     */
    private fun showTab(idx: Int) {
        currentTabIndex = idx.coerceIn(0, totalTabs - 1)
        (cardPanel.layout as CardLayout).show(cardPanel, currentTabIndex.toString())
        if (::stepBar.isInitialized) stepBar.currentStep = currentTabIndex
        updateNavButtons()
        onTabShown(currentTabIndex)
    }

    /**
     * Side-effects triggered whenever a step becomes visible.
     * Replaces the old [JBTabbedPane] ChangeListener.
     */
    private fun onTabShown(idx: Int) {
        setErrorText(null)

        if (idx == idxSummary) {
            val effectiveInfo     = getEffectiveProjectInfo()
            val deselectedModules = computeDeselectedModules(effectiveInfo)
            val config            = buildConfig()
            val skillsConfig      = buildSkillsConfig()
            val skillPreview = if (skillsConfig.exportSkillFile) {
                SkillsExportService().generateSkillsMarkdown(
                    effectiveInfo, config, skillsConfig,
                    moduleSelectionStep.getLibraryModulesForSdk(), deselectedModules
                )
            } else null
            summaryStep.updateSummary(
                effectiveInfo, config, skillsConfig, gradleService,
                moduleSelectionStep.getLibraryModulesForSdk(), deselectedModules, skillPreview
            )
        }

        if (idx == idxEnvironment && projectInfo.setupFlow == SetupFlow.MULTI_APP) {
            val usePluginDsl = moduleSelectionStep.getUsePluginDslForMultiApp()
            environmentStep.setPerModuleToggleVisible(!usePluginDsl)
            if (!usePluginDsl) environmentStep.updateVisibleModules(selectedModuleNames())
        }
    }

    /**
     * Wraps [panel] in a [JBScrollPane] that is top-aligned and vertically scrollable.
     */
    private fun scrollable(panel: JComponent): JComponent {
        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(panel, BorderLayout.NORTH)
        }
        return JBScrollPane(wrapper).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy   = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }
    }

    /** Finish is always visible so the user can apply defaults from any step. */
    private fun updateNavButtons() {
        getButton(prevAction)?.isVisible = currentTabIndex > 0
        getButton(nextAction)?.isVisible = currentTabIndex < totalTabs - 1
        getButton(okAction)?.isVisible   = true
    }

    override fun createActions(): Array<Action> {
        prevAction = object : DialogWrapperAction("← Back") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                if (currentTabIndex > 0) showTab(currentTabIndex - 1)
            }
        }
        nextAction = object : DialogWrapperAction("Next →") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                if (!validateTab(currentTabIndex)) return
                if (currentTabIndex < totalTabs - 1) showTab(currentTabIndex + 1)
            }
        }
        return arrayOf(prevAction, nextAction, okAction, cancelAction)
    }

    /** Returns `true` when the step at [idx] passes its validation rules. */
    private fun validateTab(idx: Int): Boolean {
        return when {
            idx == idxEnvironment -> {
                if (!environmentStep.isValid()) {
                    val target = environmentStep.focusFirstInvalidField()
                    setErrorText(
                        environmentStep.getValidationMessage()
                            ?: "Please fill in a valid Application ID and Beacon URL before proceeding.",
                        target
                    )
                    false
                } else true
            }
            idx == idxModules -> {
                if (projectInfo.setupFlow == SetupFlow.MULTI_APP &&
                    !moduleSelectionStep.hasSelection(projectInfo.appModules)
                ) {
                    val target = moduleSelectionStep.getValidationComponent()
                    setErrorText("Please select at least one application module to instrument.", target)
                    target?.requestFocusInWindow()
                    false
                } else true
            }
            idx == idxSkills -> {
                val msg = skillsStep.getSkillFileValidationMessage()
                if (msg != null) {
                    val target = skillsStep.getSkillFileValidationComponent()
                    setErrorText(msg, target)
                    target.requestFocusInWindow()
                    false
                } else true
            }
            else -> true
        }
    }

    // ── OK / Finish ───────────────────────────────────────────────────────────

    override fun doOKAction() {
        if (!environmentStep.isValid()) {
            showTab(idxEnvironment)
            val target = environmentStep.focusFirstInvalidField()
            setErrorText(
                environmentStep.getValidationMessage()
                    ?: "Please fill in a valid Application ID and Beacon URL before finishing.",
                target
            )
            return
        }
        if (projectInfo.setupFlow == SetupFlow.MULTI_APP &&
            !moduleSelectionStep.hasSelection(projectInfo.appModules)
        ) {
            showTab(idxModules ?: return)
            val target = moduleSelectionStep.getValidationComponent()
            setErrorText("Please select at least one application module to instrument.", target)
            target?.requestFocusInWindow()
            return
        }
        skillsStep.getSkillFileValidationMessage()?.let { msg ->
            showTab(idxSkills)
            val target = skillsStep.getSkillFileValidationComponent()
            setErrorText(msg, target)
            target.requestFocusInWindow()
            return
        }
        setErrorText(null)
        applyChanges(buildConfig())
        super.doOKAction()
    }

    // ── Config helpers ────────────────────────────────────────────────────────

    private fun getEffectiveProjectInfo(): ProjectDetectionService.ProjectInfo {
        val selectedAppModules = moduleSelectionStep.getSelectedAppModules(projectInfo.appModules)
        val base = if (projectInfo.setupFlow == SetupFlow.MULTI_APP) {
            projectInfo.copy(usesPluginDsl = moduleSelectionStep.getUsePluginDslForMultiApp())
        } else projectInfo
        if (selectedAppModules.size == base.appModules.size) return base
        val selectedNames   = selectedAppModules.map { it.name }.toSet()
        val filteredModules = base.allModules.filter {
            it.type != ModuleType.APPLICATION || it.name in selectedNames
        }
        return base.copy(
            allModules   = filteredModules,
            appBuildFile = selectedAppModules.firstOrNull()?.buildFile ?: base.appBuildFile
        )
    }

    private fun selectedModuleNames(): Set<String> =
        moduleSelectionStep.getSelectedAppModules(projectInfo.appModules).map { it.name }.toSet()

    private fun computeDeselectedModules(
        effective: ProjectDetectionService.ProjectInfo
    ): List<ProjectDetectionService.ModuleInfo> {
        if (projectInfo.setupFlow != SetupFlow.MULTI_APP) return emptyList()
        if (effective.usesPluginDsl) return emptyList()
        val selectedNames = effective.appModules.map { it.name }.toSet()
        return projectInfo.appModules.filter { it.name !in selectedNames }
    }

    private fun buildConfig() = DynatraceConfig(
        applicationId              = environmentStep.getAppId(),
        beaconUrl                  = environmentStep.getBeaconUrl(),
        moduleCredentials          = environmentStep.getModuleCredentials(),
        autoStartEnabled           = featureStep.isAutoStartEnabled(),
        userOptIn                  = featureStep.isUserOptIn(),
        autoInstrument             = featureStep.isAutoInstrument(),
        pluginEnabled              = featureStep.isPluginEnabled(),
        crashReporting             = featureStep.isCrashReporting(),
        anrReporting               = featureStep.isAnrReporting(),
        nativeCrashReporting       = featureStep.isNativeCrashReporting(),
        hybridMonitoring           = featureStep.isHybridMonitoring(),
        userActionsEnabled         = featureStep.isUserActionsEnabled(),
        webRequestsEnabled         = featureStep.isWebRequestsEnabled(),
        lifecycleEnabled           = featureStep.isLifecycleEnabled(),
        locationMonitoring         = featureStep.isLocationMonitoring(),
        namePrivacy                = featureStep.isNamePrivacy(),
        composeEnabled             = featureStep.isComposeEnabled(),
        rageTapDetection           = featureStep.isRageTapDetection(),
        agentBehaviorLoadBalancing = featureStep.isAgentBehaviorLoadBalancing(),
        agentBehaviorGrail         = featureStep.isAgentBehaviorGrail(),
        sessionReplayEnabled       = featureStep.isSessionReplayEnabled(),
        agentLogging               = featureStep.isAgentLogging(),
        excludePackages            = featureStep.getExcludePackages(),
        excludeClasses             = featureStep.getExcludeClasses(),
        excludeMethods             = featureStep.getExcludeMethods(),
        buildVariant               = featureStep.getBuildVariant(),
        strictMode                 = featureStep.isStrictMode()
    )

    private fun buildSkillsConfig(): SkillsExportConfig = skillsStep.buildConfig()

    private fun applyChanges(config: DynatraceConfig) {
        try {
            val effective    = getEffectiveProjectInfo()
            val skillsConfig = buildSkillsConfig()
            var exportedSkillPath: String? = null

            effective.settingsFile?.let { gradleService.ensureMavenCentral(it, effective.isKotlinDsl) }
                ?: effective.projectBuildFile?.let { gradleService.ensureMavenCentral(it, effective.isKotlinDsl) }

            computeDeselectedModules(effective).forEach { module ->
                gradleService.removeModuleInstrumentation(module.buildFile)
            }
            gradleService.configureGradleFiles(effective, config)

            val sdkModules = moduleSelectionStep.getLibraryModulesForSdk()
            if (sdkModules.isNotEmpty()) {
                val projectFile = effective.projectBuildFile ?: effective.appBuildFile
                projectFile?.let {
                    gradleService.addOneAgentSdkToProjectBuild(
                        it, effective.isKotlinDsl,
                        sdkModules.map { m -> m.name },
                        projectInfo.libraryModules.map { m -> m.name }
                    )
                }
            }

            if (skillsConfig.exportSkillFile) {
                exportedSkillPath = skillsExportService.writeSkillsFile(
                    effective, config, skillsConfig, sdkModules,
                    computeDeselectedModules(effective)
                )
            }

            showNotification(
                "Dynatrace configuration applied successfully!\n" +
                (exportedSkillPath?.let {
                    "AI skill files (5) exported to: ${it.substringBeforeLast("/")}/\n"
                } ?: "") +
                "Sync your Gradle project to activate the changes.",
                NotificationType.INFORMATION
            )
        } catch (e: Exception) {
            showNotification(
                "Failed to apply Dynatrace configuration: ${e.message}",
                NotificationType.ERROR
            )
        }
    }

    private fun showNotification(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Dynatrace Wizard")
            .createNotification(message, type)
            .notify(project)
    }
}
