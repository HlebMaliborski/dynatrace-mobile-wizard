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
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * Main wizard dialog — 7-step tab-based wizard.
 *
 *  Tab 0  Welcome         — project detection overview
 *  Tab 1  Modules         — per-module selection (checkboxes for MULTI_APP)
 *  Tab 2  Environment     — Application ID + Beacon URL
 *  Tab 3  Technologies    — supported technologies
 *  Tab 4  Features        — feature toggles
 *  Tab 6  Skills          — AI skill export configuration
 *  Tab 7  Summary         — change preview + Finish
 */
class DynatraceWizardDialog(
    private val project: Project,
    /** Non-null when the user chose "Update Setup" for an already-configured project. */
    existingConfig: DynatraceConfig? = null
) : DialogWrapper(project) {

    private val welcomeStep         = WelcomeStep(project)
    private val environmentStep     = EnvironmentConfigStep()
    private val moduleSelectionStep = ModuleSelectionStep()
    private val supportedTechStep   = SupportedTechnologiesStep()
    private val featureStep         = FeatureToggleStep()
    private val skillsStep          = SkillsStep()
    private val summaryStep         = SummaryStep()

    private val tabbedPane   = JBTabbedPane()
    private val gradleService = GradleModificationService(project)
    private val skillsExportService = SkillsExportService(project)

    private lateinit var projectInfo: ProjectDetectionService.ProjectInfo
    private lateinit var prevAction:  DialogWrapperAction
    private lateinit var nextAction:  DialogWrapperAction

    // Tab index constants
    private val TAB_MODULES      = 1
    private val TAB_ENVIRONMENT  = 2
    private val TAB_SKILLS       = 5
    private val TAB_SUMMARY      = 6

    init {
        title = if (existingConfig != null) "Dynatrace Wizard — Update Configuration" else "Dynatrace Wizard"
        setOKButtonText("Finish")
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
    }

    override fun createCenterPanel(): JComponent {
        val welcomePanel      = welcomeStep.createPanel()
        projectInfo           = welcomeStep.getProjectInfo()
        val environmentPanel  = environmentStep.createPanel(projectInfo.appModules)
        val modulePanel       = moduleSelectionStep.createPanel(projectInfo)
        val techPanel         = supportedTechStep.createPanel(projectInfo)
        val featurePanel      = featureStep.createPanel()
        val skillsPanel       = skillsStep.createPanel()
        val summaryPanel      = summaryStep.createPanel()

        tabbedPane.addTab("1. Welcome",      scrollable(welcomePanel))
        tabbedPane.addTab("2. Modules",      scrollable(modulePanel))
        tabbedPane.addTab("3. Environment",  scrollable(environmentPanel))
        tabbedPane.addTab("4. Technologies", scrollable(techPanel))
        tabbedPane.addTab("5. Features",     scrollable(featurePanel))
        tabbedPane.addTab("6. Skills",       scrollable(skillsPanel))
        tabbedPane.addTab("7. Summary",      scrollable(summaryPanel))

        // Wire approach-change listener so the per-module toggle reacts immediately
        // when the user flips the Plugin DSL / per-module radio on the Modules tab.
        moduleSelectionStep.setOnApproachChanged {
            if (projectInfo.setupFlow == SetupFlow.MULTI_APP) {
                val usePluginDsl = moduleSelectionStep.getUsePluginDslForMultiApp()
                environmentStep.setPerModuleToggleVisible(!usePluginDsl)
                if (!usePluginDsl) {
                    environmentStep.updateVisibleModules(selectedModuleNames())
                }
            }
        }

        // Set initial toggle state based on the detected/default approach.
        if (projectInfo.setupFlow == SetupFlow.MULTI_APP) {
            val usePluginDsl = moduleSelectionStep.getUsePluginDslForMultiApp()
            environmentStep.setPerModuleToggleVisible(!usePluginDsl)
            if (!usePluginDsl) {
                environmentStep.updateVisibleModules(selectedModuleNames())
            }
        }

        tabbedPane.addChangeListener {
            setErrorText(null)
            if (tabbedPane.selectedIndex == TAB_SUMMARY) {
                val effectiveInfo = getEffectiveProjectInfo()
                val deselectedModules = computeDeselectedModules(effectiveInfo)
                val config = buildConfig()
                val skillsConfig = buildSkillsConfig()
                val skillPreview = if (skillsConfig.exportSkillFile) {
                    SkillsExportService().generateSkillsMarkdown(
                        effectiveInfo,
                        config,
                        skillsConfig,
                        moduleSelectionStep.getLibraryModulesForSdk(),
                        deselectedModules
                    )
                } else null
                summaryStep.updateSummary(
                    effectiveInfo,
                    config,
                    skillsConfig,
                    gradleService,
                    moduleSelectionStep.getLibraryModulesForSdk(),
                    deselectedModules,
                    skillPreview
                )
            }
            // Re-evaluate per-module toggle visibility and visible module list every
            // time the Environment tab is shown — catches checkbox changes made on the
            // Modules tab while the Environment tab was not in view.
            if (tabbedPane.selectedIndex == TAB_ENVIRONMENT && projectInfo.setupFlow == SetupFlow.MULTI_APP) {
                val usePluginDsl = moduleSelectionStep.getUsePluginDslForMultiApp()
                environmentStep.setPerModuleToggleVisible(!usePluginDsl)
                if (!usePluginDsl) {
                    environmentStep.updateVisibleModules(selectedModuleNames())
                }
            }
            updateNavButtons()
        }

        tabbedPane.preferredSize = Dimension(720, 580)
        return tabbedPane
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * Wraps [panel] in a JBScrollPane so its content is top-aligned and scrollable.
     * Pinning to BorderLayout.NORTH ensures the scroll pane reports the correct
     * preferred height instead of stretching the panel to fill the viewport.
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

    /** Shows/hides Back, Next, and Finish depending on which tab is active. */
    private fun updateNavButtons() {
        val idx  = tabbedPane.selectedIndex
        val last = tabbedPane.tabCount - 1
        getButton(prevAction)?.isVisible = idx > 0
        getButton(nextAction)?.isVisible = idx < last
        getButton(okAction)?.isVisible   = idx == last
    }

    override fun createActions(): Array<Action> {
        prevAction = object : DialogWrapperAction("← Back") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                if (tabbedPane.selectedIndex > 0) tabbedPane.selectedIndex -= 1
            }
        }

        nextAction = object : DialogWrapperAction("Next →") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                val idx = tabbedPane.selectedIndex
                if (!validateTab(idx)) return
                if (idx < tabbedPane.tabCount - 1) tabbedPane.selectedIndex += 1
            }
        }

        return arrayOf(prevAction, nextAction, okAction, cancelAction)
    }

    /** Returns `true` when the tab at [idx] passes its validation rules. */
    private fun validateTab(idx: Int): Boolean {
        return when (idx) {
            TAB_ENVIRONMENT -> {
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
            TAB_MODULES -> {
                if (projectInfo.setupFlow == SetupFlow.MULTI_APP &&
                    !moduleSelectionStep.hasSelection(projectInfo.appModules)
                ) {
                    val target = moduleSelectionStep.getValidationComponent()
                    setErrorText("Please select at least one application module to instrument.", target)
                    target?.requestFocusInWindow()
                    false
                } else true
            }
            TAB_SKILLS -> {
                val validationMessage = skillsStep.getSkillFileValidationMessage()
                if (validationMessage != null) {
                    val target = skillsStep.getSkillFileValidationComponent()
                    setErrorText(validationMessage, target)
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
            tabbedPane.selectedIndex = TAB_ENVIRONMENT
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
            tabbedPane.selectedIndex = TAB_MODULES
            val target = moduleSelectionStep.getValidationComponent()
            setErrorText("Please select at least one application module to instrument.", target)
            target?.requestFocusInWindow()
            return
        }
        skillsStep.getSkillFileValidationMessage()?.let { validationMessage ->
            tabbedPane.selectedIndex = TAB_SKILLS
            val target = skillsStep.getSkillFileValidationComponent()
            setErrorText(validationMessage, target)
            target.requestFocusInWindow()
            return
        }
        setErrorText(null)
        applyChanges(buildConfig())
        super.doOKAction()
    }

    // ── Config helpers ────────────────────────────────────────────────────────

    /**
     * Returns a [ProjectDetectionService.ProjectInfo] where the module list is filtered to only the
     * app modules the user chose on the Modules tab.  All other flows return
     * [projectInfo] unchanged.
     */
    private fun getEffectiveProjectInfo(): ProjectDetectionService.ProjectInfo {
        val selectedAppModules = moduleSelectionStep.getSelectedAppModules(projectInfo.appModules)

        // For MULTI_APP, override usesPluginDsl from the approach the user chose on the Modules tab
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

    /**
     * Returns the names of the app modules currently checked on the Modules tab.
     * Falls back to all detected app modules when no checkboxes are shown (single-app flows).
     */
    private fun selectedModuleNames(): Set<String> =
        moduleSelectionStep.getSelectedAppModules(projectInfo.appModules).map { it.name }.toSet()

    /**
     * Returns the app modules that the user has explicitly deselected on the Modules tab.
     * Only relevant for MULTI_APP + per-module approach; returns an empty list otherwise.
     */
    private fun computeDeselectedModules(
        effective: ProjectDetectionService.ProjectInfo
    ): List<ProjectDetectionService.ModuleInfo> {
        if (projectInfo.setupFlow != SetupFlow.MULTI_APP) return emptyList()
        if (effective.usesPluginDsl) return emptyList()   // Plugin DSL coordinator handles all modules
        val selectedNames = effective.appModules.map { it.name }.toSet()
        return projectInfo.appModules.filter { it.name !in selectedNames }
    }

    private fun buildConfig() = DynatraceConfig(
        applicationId          = environmentStep.getAppId(),
        beaconUrl              = environmentStep.getBeaconUrl(),
        moduleCredentials      = environmentStep.getModuleCredentials(),
        autoStartEnabled       = featureStep.isAutoStartEnabled(),
        userOptIn              = featureStep.isUserOptIn(),
        autoInstrument         = featureStep.isAutoInstrument(),
        pluginEnabled          = featureStep.isPluginEnabled(),
        crashReporting         = featureStep.isCrashReporting(),
        anrReporting           = featureStep.isAnrReporting(),
        nativeCrashReporting   = featureStep.isNativeCrashReporting(),
        hybridMonitoring       = featureStep.isHybridMonitoring(),
        userActionsEnabled     = featureStep.isUserActionsEnabled(),
        webRequestsEnabled     = featureStep.isWebRequestsEnabled(),
        lifecycleEnabled       = featureStep.isLifecycleEnabled(),
        locationMonitoring     = featureStep.isLocationMonitoring(),
        namePrivacy            = featureStep.isNamePrivacy(),
        composeEnabled         = featureStep.isComposeEnabled(),
        rageTapDetection       = featureStep.isRageTapDetection(),
        agentBehaviorLoadBalancing = featureStep.isAgentBehaviorLoadBalancing(),
        agentBehaviorGrail     = featureStep.isAgentBehaviorGrail(),
        sessionReplayEnabled   = featureStep.isSessionReplayEnabled(),
        agentLogging           = featureStep.isAgentLogging(),
        excludePackages        = featureStep.getExcludePackages(),
        excludeClasses         = featureStep.getExcludeClasses(),
        excludeMethods         = featureStep.getExcludeMethods(),
        buildVariant           = featureStep.getBuildVariant(),
        strictMode             = featureStep.isStrictMode()
    )

    private fun buildSkillsConfig(): SkillsExportConfig = skillsStep.buildConfig()

    private fun applyChanges(config: DynatraceConfig) {
        try {
            val effective = getEffectiveProjectInfo()
            val skillsConfig = buildSkillsConfig()
            var exportedSkillPath: String? = null

            effective.settingsFile?.let { gradleService.ensureMavenCentral(it, effective.isKotlinDsl) }
                ?: effective.projectBuildFile?.let { gradleService.ensureMavenCentral(it, effective.isKotlinDsl) }

            // Remove Dynatrace from modules the user deselected (per-module MULTI_APP only).
            // Must happen BEFORE configureGradleFiles so the root buildscript classpath check
            // isn't confused by stale declarations left in deselected module files.
            computeDeselectedModules(effective).forEach { module ->
                gradleService.removeModuleInstrumentation(module.buildFile)
            }

            gradleService.configureGradleFiles(effective, config)

            // Optional: add OneAgent SDK to library modules whose code needs Dynatrace APIs.
            // The SDK is injected via a subprojects{} block in the ROOT build file using
            // pluginManager.withPlugin("com.android.library") + agentDependency().
            val sdkModules = moduleSelectionStep.getLibraryModulesForSdk()
            if (sdkModules.isNotEmpty()) {
                val projectFile = effective.projectBuildFile ?: effective.appBuildFile
                projectFile?.let {
                    gradleService.addOneAgentSdkToProjectBuild(
                        it,
                        effective.isKotlinDsl,
                        sdkModules.map { m -> m.name },
                        projectInfo.libraryModules.map { m -> m.name }
                    )
                }
            }

            if (skillsConfig.exportSkillFile) {
                exportedSkillPath = skillsExportService.writeSkillsFile(
                    effective,
                    config,
                    skillsConfig,
                    sdkModules,
                    computeDeselectedModules(effective)
                )
            }

            showNotification(
                "Dynatrace configuration applied successfully!\n" +
                (exportedSkillPath?.let { "AI skill file exported to: $it\n" } ?: "") +
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
