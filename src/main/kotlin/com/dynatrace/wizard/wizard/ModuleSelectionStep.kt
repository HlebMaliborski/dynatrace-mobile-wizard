package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.service.ProjectDetectionService
import com.dynatrace.wizard.service.ProjectDetectionService.SetupFlow
import com.dynatrace.wizard.util.WizardColors
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * Step 3 of the wizard: Module Selection.
 *
 * - For MULTI_APP projects: renders a checkbox per application module so the user
 *   can choose exactly which ones receive Dynatrace instrumentation.
 * - For all other flows: renders read-only informational rows explaining what will
 *   (and won't) happen to each detected module.
 */
class ModuleSelectionStep {

    /** Populated only for MULTI_APP projects (one entry per app module). */
    private val appCheckboxes = mutableListOf<Pair<ProjectDetectionService.ModuleInfo, JBCheckBox>>()
    private val appSelectionSummaryLabel = JBLabel().apply {
        font = JBUI.Fonts.smallFont()
        foreground = UIUtil.getContextHelpForeground()
    }

    // ── Multi-app approach toggle ─────────────────────────────────────────────
    private val rbPluginDsl  = JRadioButton("Plugin DSL — coordinator plugin at root instruments all app modules automatically")
    private val rbPerModule  = JRadioButton("Per-module — apply com.dynatrace.instrumentation.module to each app module individually")

    /**
     * Returns true when the user has chosen the Plugin DSL (coordinator) approach.
     * Only meaningful for MULTI_APP projects; always returns true for other flows.
     */
    fun getUsePluginDslForMultiApp(): Boolean = rbPluginDsl.isSelected

    /**
     * Registers [listener] to be called whenever the instrumentation approach
     * radio buttons change (Plugin DSL ↔ per-module).  Can be called at any time;
     * the listener is attached directly to both radio buttons.
     */
    fun setOnApproachChanged(listener: () -> Unit) {
        rbPluginDsl.addActionListener { listener() }
        rbPerModule.addActionListener { listener() }
    }

    /**
     * Opt-in checkboxes for library modules — unchecked by default.
     * When checked, `implementation(dynatrace.oneAgentSdk)` is added to that
     * module's build file so its code can call Dynatrace APIs directly.
     */
    private val libraryCheckboxes = mutableListOf<Pair<ProjectDetectionService.ModuleInfo, JBCheckBox>>()


    // ── Tech detection ────────────────────────────────────────────────────────

    private data class TechPattern(
        val name: String,
        val artifacts: List<String>,
        /** true = Dynatrace auto-instruments this; false = manual SDK required */
        val autoInstrumented: Boolean
    )

    companion object {
        private val DETECTABLE_TECHS = listOf(
            TechPattern("OkHttp",           listOf("okhttp3:okhttp", "squareup.okhttp3"), true),
            TechPattern("Retrofit",         listOf("retrofit2:retrofit"), true),
            TechPattern("Compose",          listOf("androidx.compose.ui", "compose-ui"), true),
            TechPattern("Coroutines",       listOf("kotlinx-coroutines"), true),
            TechPattern("Volley",           listOf("com.android.volley"), true),
            TechPattern("React Native",     listOf("com.facebook.react"), false),
            TechPattern("Flutter",          listOf("io.flutter"), false),
            TechPattern("Crashlytics",      listOf("firebase-crashlytics"), false),
            TechPattern("RxJava",           listOf("io.reactivex.rxjava2", "io.reactivex.rxjava3"), false),
            TechPattern("Ktor",             listOf("io.ktor:ktor-client"), false),
            TechPattern("Glide",            listOf("bumptech.glide:glide"), false),
            TechPattern("Picasso",          listOf("squareup.picasso"), false),
            TechPattern("Coil",             listOf("io.coil-kt"), false),
        )
    }

    private fun detectTechs(buildFile: com.intellij.openapi.vfs.VirtualFile): List<TechPattern> =
        try {
            val buildContent = String(buildFile.contentsToByteArray())

            // Build a content string that is scoped to this module only:
            //  1. The module's own build file (inline artifact strings + libs.X references).
            //  2. Only the TOML library entries for libs.X aliases that THIS module references —
            //     NOT the whole catalog, which would produce false positives for every module.
            val contentToScan = buildString {
                append(buildContent)

                // Collect all libs.X.Y aliases used in this build file,
                // then translate back to TOML key format (dots → dashes).
                val usedAliases: Set<String> = Regex("""libs\.([\w.]+)""")
                    .findAll(buildContent)
                    .flatMap { m ->
                        val alias = m.groupValues[1]
                        listOf(
                            alias.replace('.', '-'),   // primary: libs.okhttp3.okhttp → okhttp3-okhttp
                            alias.replace('.', '_'),   // fallback: snake_case keys
                            alias                      // exact match (some catalogs use dots)
                        )
                    }
                    .toSet()

                if (usedAliases.isNotEmpty()) {
                    buildFile.parent?.parent
                        ?.findChild("gradle")?.findChild("libs.versions.toml")
                        ?.let { toml ->
                            try {
                                val tomlContent = String(toml.contentsToByteArray())
                                // Append only the lines for aliases this module actually uses
                                usedAliases.forEach { alias ->
                                    Regex("""(?:^|\n)[^\S\n]*${Regex.escape(alias)}\s*=\s*[^\n]+""")
                                        .find(tomlContent)?.value
                                        ?.let { append("\n"); append(it) }
                                }
                            } catch (_: Exception) { }
                        }
                }
            }

            DETECTABLE_TECHS.filter { tech -> tech.artifacts.any { contentToScan.contains(it) } }
        } catch (_: Exception) { emptyList() }

    /**
     * Returns true when [moduleName] already has `agentDependency()` wired up in the
     * root build file.
     *
     * Two cases are handled:
     *
     * 1. **Filtered block** (multiple library modules, only some selected):
     *    `project.name == "moduleName"` appears within 300 chars of `agentDependency()`.
     *
     * 2. **Unfiltered block** (single library module, or all modules selected):
     *    `agentDependency()` is present but no `project.name ==` guard exists nearby.
     *    This is the `filterAll = true` path in [GradleModificationService] — the SDK
     *    applies unconditionally to every library subproject, so the checkbox must be
     *    pre-checked for any library module.
     */
    private fun hasAgentSdk(
        moduleName: String,
        projectBuildFile: com.intellij.openapi.vfs.VirtualFile?
    ): Boolean {
        projectBuildFile ?: return false
        return try {
            val content = String(projectBuildFile.contentsToByteArray())
            if (!content.contains("agentDependency()")) return false

            val agentRegex     = Regex("""agentDependency\(\)""")
            val nameRegex      = Regex("""["']${Regex.escape(moduleName)}["']""")
            val nameGuardRegex = Regex("""project\.name\s*==\s*["'][^"']+["']""")

            val agentPositions     = agentRegex.findAll(content).map { it.range.first }.toList()
            val namePositions      = nameRegex.findAll(content).map { it.range.first }.toList()
            val nameGuardPositions = nameGuardRegex.findAll(content).map { it.range.first }.toList()

            // Case 1: explicit module-name guard is near agentDependency()
            if (namePositions.any { np -> agentPositions.any { ap -> kotlin.math.abs(np - ap) < 300 } }) {
                return true
            }

            // Case 2: agentDependency() exists but has no project.name guard nearby →
            // the block was written with filterAll=true and covers all library modules.
            agentPositions.any { ap -> nameGuardPositions.none { ng -> kotlin.math.abs(ng - ap) < 500 } }
        } catch (_: Exception) { false }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun createPanel(info: ProjectDetectionService.ProjectInfo): JComponent {
        appCheckboxes.clear()
        libraryCheckboxes.clear()

        val isMultiApp       = info.setupFlow == SetupFlow.MULTI_APP
        val isFeatureModules = info.setupFlow == SetupFlow.FEATURE_MODULES

        val builder = FormBuilder.createFormBuilder()

        // ── Header ─────────────────────────────────────────────────────────────
        builder.addComponent(
            JBLabel("Module selection").apply {
                font = JBUI.Fonts.label(16f).asBold()
                foreground = WizardColors.accent
                border = JBUI.Borders.emptyBottom(2)
            }
        )
        builder.addComponent(
            JBLabel(when {
                isMultiApp       -> "<html>Multiple application modules detected. " +
                                    "Choose how to instrument them with Dynatrace.</html>"
                isFeatureModules -> "<html>One application module and one or more dynamic feature " +
                                    "modules were detected. Feature modules are auto-instrumented " +
                                    "when the main plugin is applied at the project root.</html>"
                else             -> "<html>Review the modules in your project and how the wizard " +
                                    "will configure each one.</html>"
            }).apply {
                foreground = UIUtil.getContextHelpForeground()
                border = JBUI.Borders.emptyBottom(8)
            }
        )

        // ── Multi-App approach toggle ───────────────────────────────────────────
        // appModulesContainer is non-null only for MULTI_APP; holds the swappable module list.
        var appModulesContainer: JPanel? = null

        if (isMultiApp) {
            builder.addComponent(appSelectionSummaryLabel.apply { border = JBUI.Borders.emptyBottom(4) })
            builder.addComponent(TitledSeparator("Instrumentation Approach"))

            // Default: prefer classpath when buildscript{} block exists,
            // otherwise default to Plugin DSL
            val defaultPluginDsl = !info.hasBuildscriptBlock
            rbPluginDsl.isSelected = defaultPluginDsl
            rbPerModule.isSelected = !defaultPluginDsl

            ButtonGroup().apply { add(rbPluginDsl); add(rbPerModule) }

            rbPluginDsl.isOpaque = false
            rbPerModule.isOpaque = false

            builder.addComponent(rbPluginDsl)
            builder.addComponent(
                JBLabel("<html><small>Adds <code>com.dynatrace.instrumentation</code> to root " +
                        "<code>plugins {}</code> block. No per-module changes needed.</small></html>").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.empty(0, JBUI.scale(20), 4, 0)
                }
            )
            builder.addComponent(rbPerModule)
            builder.addComponent(
                JBLabel("<html><small>Adds classpath to root <code>buildscript {}</code> and applies " +
                        "<code>com.dynatrace.instrumentation.module</code> in each app module.</small></html>").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.empty(0, JBUI.scale(20), 6, 0)
                }
            )

            // Pre-check modules based on their current state:
            //  - Re-run (any module already configured): only check the already-instrumented ones.
            //  - Fresh setup (nothing configured yet): check all as a convenience default.
            val anyAlreadyConfigured = info.appModules.any { it.hasDynatrace }
            info.appModules.forEach { m ->
                val initiallyChecked = if (anyAlreadyConfigured) m.hasDynatrace else true
                val checkBox = JBCheckBox(m.name, initiallyChecked)
                checkBox.addActionListener { updateAppSelectionSummary(info.appModules) }
                appCheckboxes += m to checkBox
            }

            // Build both sub-panels upfront.
            val pluginDslPanel = buildPluginDslAppModulesPanel(info)
            val perModulePanel = buildPerModuleCheckboxesPanel(info)

            // Container that shows the correct panel for the currently selected approach.
            val container = JPanel(BorderLayout()).apply { isOpaque = false }
            container.add(if (defaultPluginDsl) pluginDslPanel else perModulePanel, BorderLayout.NORTH)
            appModulesContainer = container

            // Wire radio buttons to swap the visible panel.
            rbPluginDsl.addActionListener {
                container.removeAll()
                container.add(pluginDslPanel, BorderLayout.NORTH)
                container.revalidate()
                container.repaint()
                updateAppSelectionSummary(info.appModules)
            }
            rbPerModule.addActionListener {
                container.removeAll()
                container.add(perModulePanel, BorderLayout.NORTH)
                container.revalidate()
                container.repaint()
                updateAppSelectionSummary(info.appModules)
            }

            updateAppSelectionSummary(info.appModules)
        }

        // ── Single-build-file (legacy) ─────────────────────────────────────────
        if (info.isSingleBuildFile) {
            builder.addComponent(TitledSeparator("Build File"))
            val hasDt = fileHasDynatrace(info.projectBuildFile)
            builder.addComponent(infoRow(
                name       = "(root)",
                typeTxt    = "📱  App module (single build file)",
                statusTxt  = if (hasDt) "✅  already configured" else "🔧  will be configured",
                statusColor = if (hasDt) WizardColors.success else WizardColors.accent,
                typeColor  = WizardColors.moduleApp
            ))
        }

        // ── Application modules ────────────────────────────────────────────────
        if (info.appModules.isNotEmpty()) {
            builder.addComponent(TitledSeparator("Application Modules"))

            if (isMultiApp && appModulesContainer != null) {
                // Dynamic container: swapped by radio button listeners above.
                builder.addComponent(appModulesContainer)
            } else {
                // Read-only: single app (SINGLE_APP / FEATURE_MODULES / UNKNOWN)
                info.appModules.forEach { m ->
                    val hasDt = m.hasDynatrace
                    builder.addComponent(infoRow(
                        name        = m.name,
                        typeTxt     = "📱  ${m.type.label}",
                        statusTxt   = if (hasDt) "✅  already configured" else "🔧  will be configured",
                        statusColor = if (hasDt) WizardColors.success else WizardColors.accent,
                        typeColor   = WizardColors.moduleApp
                    ))
                }
            }
        }

        // ── Dynamic feature modules ────────────────────────────────────────────
        if (info.featureModules.isNotEmpty()) {
            builder.addComponent(TitledSeparator("Dynamic Feature Modules"))
            builder.addComponent(
                JBLabel("<html>These modules are instrumented automatically when the main plugin " +
                        "is applied at the project root. No build file changes are needed.</html>").apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.emptyBottom(6)
                }
            )
            info.featureModules.forEach { m ->
                builder.addComponent(infoRow(
                    name        = m.name,
                    typeTxt     = "🧩  ${m.type.label}",
                    statusTxt   = "⚡  auto-instrumented",
                    statusColor = WizardColors.moduleFeature,
                    typeColor   = WizardColors.moduleFeature
                ))
            }
        }

        // ── Library modules ────────────────────────────────────────────────────
        if (info.libraryModules.isNotEmpty()) {
            builder.addComponent(TitledSeparator("Library Modules"))
            builder.addComponent(
                JBLabel(
                    "<html>Library modules are instrumented automatically when the parent " +
                    "application module is instrumented. No build file changes are needed — " +
                    "<code>com.dynatrace.instrumentation.module</code> can only be applied " +
                    "to <code>application</code> or <code>dynamic-feature</code> modules.</html>"
                ).apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.emptyBottom(6)
                }
            )

            // Info rows (always read-only)
            info.libraryModules.forEach { m ->
                val alreadyHasSdk = hasAgentSdk(m.name, info.projectBuildFile)
                builder.addComponent(infoRow(
                    name        = m.name,
                    typeTxt     = "📚  ${m.type.label}",
                    statusTxt   = if (alreadyHasSdk) "✅  SDK configured" else "⚡  auto-instrumented",
                    statusColor = if (alreadyHasSdk) WizardColors.success else WizardColors.moduleFeature,
                    typeColor   = WizardColors.moduleLib
                ))
            }

            // ── OneAgent SDK opt-in ────────────────────────────────────────────
            builder.addVerticalGap(6)
            builder.addComponent(
                JBLabel(
                    "<html><b>OneAgent SDK (optional)</b> — opt in to allow a library module's " +
                    "code to call Dynatrace APIs directly (custom actions, tagging, etc.). " +
                    "A <code>subprojects{}</code> block will be added to the root build file.</html>"
                ).apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.emptyBottom(4)
                }
            )

            // Build all SDK checkboxes first so the "Select all" can reference them
            val sdkPairs = info.libraryModules.map { m ->
                val label = if (info.libraryModules.size > 1) m.name else "Add OneAgent SDK dependency"
                val alreadyHasSdk  = hasAgentSdk(m.name, info.projectBuildFile)
                val hasDetectedDep = detectTechs(m.buildFile).isNotEmpty()
                // Pre-check when SDK is already configured OR a detectable dependency is present
                val cb = JBCheckBox(label, alreadyHasSdk || hasDetectedDep)
                libraryCheckboxes += m to cb
                Triple(m, cb, alreadyHasSdk)
            }

            if (info.libraryModules.size > 1) {
                val selectAllCb = JBCheckBox("Select all", false)
                selectAllCb.addActionListener {
                    sdkPairs.forEach { (_, cb, _) -> cb.isSelected = selectAllCb.isSelected }
                }
                sdkPairs.forEach { (_, cb, _) ->
                    cb.addActionListener {
                        selectAllCb.isSelected = sdkPairs.all { (_, c, _) -> c.isSelected }
                    }
                }
                builder.addComponent(selectAllCb)
                builder.addVerticalGap(4)
            }

            sdkPairs.forEach { (m, cb, alreadyHasSdk) ->
                builder.addComponent(checkboxWithHint(
                    cb,
                    if (alreadyHasSdk) "Already configured — will be updated if re-run."
                    else               "Adds agentDependency() via subprojects{} in the root build file.",
                    m.buildFile.path
                ))
                builder.addVerticalGap(4)
            }
        }

        builder.addVerticalGap(8)
        return builder.panel.also { it.border = JBUI.Borders.empty(12, 16, 12, 16) }
    }

    /**
     * Returns the app modules the user has selected.
     * Falls back to [allAppModules] when no checkboxes were created (single-app flows).
     */
    fun getSelectedAppModules(
        allAppModules: List<ProjectDetectionService.ModuleInfo>
    ): List<ProjectDetectionService.ModuleInfo> =
        if (appCheckboxes.isEmpty()) allAppModules
        else appCheckboxes.filter { it.second.isSelected }.map { it.first }


    /** `true` when at least one app module is selected (or no checkboxes were shown). */
    fun hasSelection(allAppModules: List<ProjectDetectionService.ModuleInfo>): Boolean =
        rbPluginDsl.isSelected || getSelectedAppModules(allAppModules).isNotEmpty()

    /**
     * Returns the library modules whose "Add OneAgent SDK dependency" checkbox is checked.
     * Returns an empty list when no library modules exist or none are opted in.
     */
    fun getLibraryModulesForSdk(): List<ProjectDetectionService.ModuleInfo> =
        libraryCheckboxes.filter { it.second.isSelected }.map { it.first }

    fun getValidationComponent(): JComponent? =
        appCheckboxes.firstOrNull()?.second ?: if (rbPerModule.isShowing) rbPerModule else rbPluginDsl

    // ── Module list sub-panel builders ────────────────────────────────────────

    /** Read-only rows shown when "Plugin DSL" approach is selected. */
    private fun buildPluginDslAppModulesPanel(info: ProjectDetectionService.ProjectInfo): JComponent {
        val b = FormBuilder.createFormBuilder()
        b.addComponent(
            JBLabel("<html>The coordinator plugin at root will instrument all app modules automatically. " +
                    "No changes are needed in individual module build files.</html>").apply {
                font = JBUI.Fonts.smallFont()
                foreground = UIUtil.getContextHelpForeground()
                border = JBUI.Borders.emptyBottom(6)
            }
        )
        info.appModules.forEach { m ->
            b.addComponent(infoRow(
                name        = m.name,
                typeTxt     = "📱  ${m.type.label}",
                statusTxt   = "⚡  handled by root coordinator",
                statusColor = WizardColors.moduleFeature,
                typeColor   = WizardColors.moduleApp
            ))
        }
        return b.panel.also { it.isOpaque = false }
    }

    /** Checkbox rows shown when "Per-module" approach is selected. */
    private fun buildPerModuleCheckboxesPanel(info: ProjectDetectionService.ProjectInfo): JComponent {
        val b = FormBuilder.createFormBuilder()
        if (info.appModules.size > 1) {
            val allChecked = appCheckboxes.all { it.second.isSelected }
            val selectAllCb = JBCheckBox("Select all app modules", allChecked)
            selectAllCb.addActionListener {
                appCheckboxes.forEach { (_, cb) -> cb.isSelected = selectAllCb.isSelected }
                updateAppSelectionSummary(info.appModules)
            }
            appCheckboxes.forEach { (_, cb) ->
                cb.addActionListener {
                    selectAllCb.isSelected = appCheckboxes.all { it.second.isSelected }
                }
            }
            b.addComponent(selectAllCb)
            b.addVerticalGap(4)
        }
        appCheckboxes.forEach { (m, cb) ->
            b.addComponent(checkboxWithHint(
                cb,
                if (m.hasDynatrace) "Already configured — will be updated if selected, removed if deselected."
                else                "Will receive com.dynatrace.instrumentation.module + dynatrace {} block.",
                m.buildFile.path
            ))
            b.addVerticalGap(4)
        }
        return b.panel.also { it.isOpaque = false }
    }

    private fun updateAppSelectionSummary(allAppModules: List<ProjectDetectionService.ModuleInfo>) {
        if (allAppModules.isEmpty()) {
            appSelectionSummaryLabel.text = ""
            return
        }

        val selectedCount = getSelectedAppModules(allAppModules).size
        val totalCount = allAppModules.size
        appSelectionSummaryLabel.text = when {
            rbPluginDsl.isSelected ->
                "Plugin DSL mode will let the root coordinator handle all $totalCount app module(s) automatically."
            selectedCount == 0 ->
                "Per-module mode is active, but no app modules are selected yet. Select at least one module to continue."
            selectedCount == totalCount ->
                "Per-module mode is active for all $totalCount app module(s). Each selected module will get its own Dynatrace plugin entry."
            else ->
                "Per-module mode is active for $selectedCount of $totalCount app module(s). Deselected modules will be left unchanged or cleaned up on re-run."
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    /**
     * Checkbox stacked above two small hint lines:
     *  - a description of what will happen to this module
     *  - the build file path (dimmer)
     */
    private fun checkboxWithHint(cb: JBCheckBox, description: String, path: String): JComponent =
        JPanel(BorderLayout()).apply {
            isOpaque = false
            add(cb, BorderLayout.NORTH)
            add(JPanel(GridLayout(0, 1, 0, JBUI.scale(1))).apply {
                isOpaque = false
                border = JBUI.Borders.emptyLeft(JBUI.scale(20))
                add(JBLabel(description).apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                })
                add(JBLabel(path).apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getInactiveTextColor()
                })
            }, BorderLayout.CENTER)
        }

    /**
     * A single read-only row with three columns: name | type | status.
     * Mirrors the module grid on the Welcome tab.
     */
    private fun infoRow(
        name: String,
        typeTxt: String,
        statusTxt: String,
        statusColor: Color,
        typeColor: Color
    ): JComponent =
        JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(4)
            add(JPanel(GridLayout(1, 3, JBUI.scale(20), 0)).apply {
                isOpaque = false
                add(JBLabel(name).apply { font = JBUI.Fonts.label().asBold() })
                add(JBLabel(typeTxt).apply { foreground = typeColor })
                add(JBLabel(statusTxt).apply { foreground = statusColor })
            }, BorderLayout.NORTH)
        }

    private fun fileHasDynatrace(file: com.intellij.openapi.vfs.VirtualFile?): Boolean = try {
        file?.let {
            com.dynatrace.wizard.service.GradleModificationService.stripComments(
                String(it.contentsToByteArray())
            ).contains("dynatrace {")
        } == true
    } catch (_: Exception) { false }
}
