package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.service.GradleModificationService
import com.dynatrace.wizard.service.ProjectDetectionService
import com.dynatrace.wizard.service.ProjectDetectionService.ModuleType
import com.dynatrace.wizard.service.ProjectDetectionService.SetupFlow
import com.dynatrace.wizard.util.DocumentationLinks
import com.dynatrace.wizard.util.WizardColors
import com.intellij.openapi.project.Project
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.GridLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Step 1 of the wizard: Welcome screen and Android project detection.
 *
 * @param preDetectedInfo When non-null, this [ProjectInfo] is used directly and
 *   [ProjectDetectionService.detectProject] is not called again — avoids a
 *   redundant file-system scan when the caller (e.g. [DynatraceWizardAction])
 *   has already run detection.
 */
class WelcomeStep(
    private val project: Project,
    private val preDetectedInfo: ProjectDetectionService.ProjectInfo? = null
) {

    private val detectionService = ProjectDetectionService(project)
    private lateinit var projectInfo: ProjectDetectionService.ProjectInfo

    fun createPanel(): JComponent {
        projectInfo = preDetectedInfo ?: detectionService.detectProject()
        val isAndroid = projectInfo.isAndroidProject

        val builder = FormBuilder.createFormBuilder()

        // ── Header ─────────────────────────────────────────────────────────────
        builder.addComponent(
            JBLabel("${if (isAndroid) "✅" else "⚠️"}  Welcome to Dynatrace Wizard").apply {
                font = JBUI.Fonts.label(16f).asBold()
                foreground = WizardColors.accent
                border = JBUI.Borders.emptyBottom(2)
            }
        )
        builder.addComponent(
            JBLabel(
                if (isAndroid) "Android project detected — ready to configure."
                else "No Android project detected."
            ).apply {
                foreground = if (isAndroid) WizardColors.success else UIUtil.getErrorForeground()
                border = JBUI.Borders.emptyBottom(4)
            }
        )

        if (isAndroid) {
            // ── Project info ──────────────────────────────────────────────────
            builder.addComponent(TitledSeparator("Project Information"))
            builder.addComponent(infoRow("Project",   project.name))
            builder.addComponent(infoRow("Build DSL",
                if (projectInfo.isKotlinDsl) "Kotlin DSL (.kts)" else "Groovy DSL"))

            // ── Plugin approach ───────────────────────────────────────────────
            val approachLabel: String
            val approachHint: String
            val approachColor: java.awt.Color
            when {
                projectInfo.usesPluginDsl && projectInfo.hasBuildscriptBlock -> {
                    approachLabel = "⚠️  Plugin DSL + Buildscript classpath (both detected)"
                    approachHint  = "Both a plugins {} block and a buildscript { classpath } entry were found. " +
                                    "The wizard will remove the stale classpath entry when Plugin DSL is applied."
                    approachColor = WizardColors.warning
                }
                projectInfo.usesPluginDsl -> {
                    approachLabel = "Plugin DSL  (plugins {} block)"
                    approachHint  = when (projectInfo.setupFlow) {
                        SetupFlow.MULTI_APP ->
                            "Coordinator plugin applied at root — app modules need no individual changes."
                        else ->
                            "Coordinator plugin + dynatrace {} block will be added to the root build file."
                    }
                    approachColor = WizardColors.success
                }
                else -> {
                    approachLabel = "Buildscript classpath  (buildscript {})"
                    approachHint  = when (projectInfo.setupFlow) {
                        SetupFlow.MULTI_APP ->
                            "Classpath added to root; com.dynatrace.instrumentation.module applied to each app module."
                        else ->
                            "Classpath added to root buildscript; apply plugin in the app module."
                    }
                    approachColor = WizardColors.warning
                }
            }
            builder.addComponent(infoRow("Plugin approach", approachLabel))
            builder.addComponent(
                JBLabel(approachHint).apply {
                    font      = JBUI.Fonts.smallFont()
                    foreground = approachColor
                    border    = JBUI.Borders.empty(1, JBUI.scale(82), 4, 0)
                }
            )

            projectInfo.settingsFile?.let { sf ->
                val hasMaven = try {
                    GradleModificationService.stripComments(String(sf.contentsToByteArray()))
                        .contains("mavenCentral()")
                } catch (_: Exception) { false }
                builder.addComponent(infoRow("Settings file", sf.name))
                builder.addComponent(
                    JBLabel(
                        if (hasMaven) "✅  mavenCentral() is present"
                        else          "⚠️  mavenCentral() not found — will be added automatically"
                    ).apply {
                        font = JBUI.Fonts.smallFont()
                        foreground = if (hasMaven) WizardColors.success else WizardColors.warning
                        border = JBUI.Borders.empty(1, JBUI.scale(82), 4, 0)
                    }
                )
            }
            projectInfo.projectBuildFile?.let {
                builder.addComponent(infoRow("Root build file", it.name))
            }

            // ── Modules ───────────────────────────────────────────────────────
            builder.addComponent(TitledSeparator("Detected Modules"))
            builder.addComponent(buildModulePanel(projectInfo))

            // ── Setup flow ────────────────────────────────────────────────────
            builder.addComponent(TitledSeparator("Setup Flow"))
            builder.addComponent(buildFlowPanel(projectInfo))
        } else {
            builder.addVerticalGap(8)
            builder.addComponent(
                JBLabel(
                    "<html>The current project does not appear to be an Android project.<br>" +
                    "Make sure you have opened an Android project before running this wizard.<br><br>" +
                    "You can still proceed to configure Dynatrace manually.</html>"
                ).apply { foreground = UIUtil.getContextHelpForeground() }
            )
        }

        // ── What this wizard does ─────────────────────────────────────────────
        builder.addVerticalGap(8)
        builder.addComponent(TitledSeparator("What This Wizard Does"))
        builder.addComponent(
            JBLabel(
                "<html>This wizard will modify your Gradle build files to add the " +
                "<b>Dynatrace Mobile SDK</b>. You can review all changes on the " +
                "Summary tab before they are applied.</html>"
            ).apply { border = JBUI.Borders.emptyBottom(4) }
        )

        // ── Documentation ─────────────────────────────────────────────────────
        builder.addComponent(TitledSeparator("Documentation"))
        builder.addComponent(DocumentationLinks.createLinkLabel("Instrumentation via Plugin",            DocumentationLinks.GETTING_STARTED))
        builder.addComponent(DocumentationLinks.createLinkLabel("Configure Plugin for Instrumentation", DocumentationLinks.CONFIGURE_PLUGIN))
        builder.addComponent(DocumentationLinks.createLinkLabel("Multi-Module Projects",                DocumentationLinks.MULTI_MODULE))
        builder.addComponent(DocumentationLinks.createLinkLabel("Release Notes",                        DocumentationLinks.RELEASE_NOTES))
        builder.addVerticalGap(8)

        return builder.panel.also { it.border = JBUI.Borders.empty(12, 16, 12, 16) }
    }

    fun getProjectInfo(): ProjectDetectionService.ProjectInfo = projectInfo

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun infoRow(key: String, value: String): JBLabel =
        JBLabel("<html><span style='color:gray'>$key:&nbsp;&nbsp;</span>$value</html>").apply {
            border = JBUI.Borders.emptyLeft(4)
        }

    private fun buildModulePanel(info: ProjectDetectionService.ProjectInfo): JComponent {
        if (info.allModules.isEmpty() && !info.isSingleBuildFile) {
            return JBLabel("No Android modules found").apply {
                foreground = UIUtil.getContextHelpForeground()
                border = JBUI.Borders.emptyLeft(4)
            }
        }

        val grid = JPanel(GridLayout(0, 3, JBUI.scale(20), JBUI.scale(6)))
        grid.isOpaque = false
        grid.border = JBUI.Borders.emptyLeft(4)

        // Header row — dimmed column labels
        listOf("Module", "Type", "Status").forEach { col ->
            grid.add(JBLabel(col).apply {
                font = JBUI.Fonts.label().asBold()
                foreground = UIUtil.getContextHelpForeground()
            })
        }

        if (info.isSingleBuildFile) {
            val hasDt = info.projectBuildFile?.let { containsDynatrace(it) } == true
            grid.add(JBLabel("(root)").apply { font = JBUI.Fonts.label().asBold() })
            grid.add(JBLabel("📱  App module (single file)").apply { foreground = WizardColors.moduleApp })
            grid.add(moduleStatus(hasDt = hasDt, willConfigure = !hasDt, isAutoInstrumented = false))
        } else {
            info.allModules.forEach { m ->
                val (typeColor, typeText) = when (m.type) {
                    ModuleType.APPLICATION -> WizardColors.moduleApp     to "📱  ${m.type.label}"
                    ModuleType.FEATURE     -> WizardColors.moduleFeature to "🧩  ${m.type.label}"
                    ModuleType.LIBRARY     -> WizardColors.moduleLib     to "📚  ${m.type.label}"
                    ModuleType.UNKNOWN     -> UIUtil.getContextHelpForeground() to "❓  ${m.type.label}"
                }
                // App modules will be configured by the wizard (all flows).
                // Feature modules are auto-instrumented when flow is FEATURE_MODULES.
                // Library modules and unknown modules need no changes.
                val willConfigure = !m.hasDynatrace && m.type == ModuleType.APPLICATION &&
                        !(info.setupFlow == SetupFlow.MULTI_APP && info.usesPluginDsl)
                val isAutoInstrumented = m.type == ModuleType.FEATURE &&
                        info.setupFlow == SetupFlow.FEATURE_MODULES
                // MULTI_APP + Plugin DSL: coordinator at root handles app modules — flag them
                val isCoordinatorHandled = m.type == ModuleType.APPLICATION &&
                        info.setupFlow == SetupFlow.MULTI_APP && info.usesPluginDsl && !m.hasDynatrace
                // Library modules are shown as "optional" because the Modules tab offers opt-in checkboxes
                val isOptionalLib = m.type == ModuleType.LIBRARY && !m.hasDynatrace

                grid.add(JBLabel(m.name).apply { font = JBUI.Fonts.label().asBold() })
                grid.add(JBLabel(typeText).apply { foreground = typeColor })
                grid.add(moduleStatus(m.hasDynatrace, willConfigure, isAutoInstrumented, isOptionalLib, isCoordinatorHandled))
            }
        }

        return grid
    }

    /**
     * Returns a colored status label for a module row.
     *
     * | State             | Label                  | Color          |
     * |-------------------|------------------------|----------------|
     * | already set up    | ✅  already configured | success green  |
     * | wizard will touch | 🔧  will be configured | accent blue    |
     * | auto-instrumented | ⚡  auto-instrumented   | feature purple |
     * | no changes needed | —  no changes needed   | help gray      |
     */
    private fun moduleStatus(
        hasDt: Boolean,
        willConfigure: Boolean,
        isAutoInstrumented: Boolean,
        isOptionalLib: Boolean = false,
        isCoordinatorHandled: Boolean = false
    ): JBLabel =
        when {
            hasDt                -> JBLabel("✅  already configured").apply { foreground = WizardColors.success }
            isCoordinatorHandled -> JBLabel("⚡  handled by root coordinator").apply { foreground = WizardColors.moduleFeature }
            willConfigure        -> JBLabel("🔧  will be configured").apply { foreground = WizardColors.accent }
            isAutoInstrumented   -> JBLabel("⚡  auto-instrumented").apply  { foreground = WizardColors.moduleFeature }
            isOptionalLib        -> JBLabel("☑  opt-in on Modules tab").apply { foreground = WizardColors.moduleLib }
            else                 -> JBLabel("—  no changes needed").apply   { foreground = UIUtil.getContextHelpForeground() }
        }

    private fun buildFlowPanel(info: ProjectDetectionService.ProjectInfo): JComponent {
        val flow = info.setupFlow
        val flowColor = when (flow) {
            SetupFlow.SINGLE_APP, SetupFlow.SINGLE_BUILD_FILE -> WizardColors.success
            SetupFlow.FEATURE_MODULES, SetupFlow.MULTI_APP    -> WizardColors.warning
            SetupFlow.UNKNOWN                                 -> UIUtil.getContextHelpForeground()
        }

        val description = when {
            flow == SetupFlow.MULTI_APP && info.usesPluginDsl && info.hasBuildscriptBlock ->
                "Both a plugins {} block and a buildscript classpath entry were found. " +
                "Choose an approach on the Modules tab — the wizard will clean up whichever one is not used."
            flow == SetupFlow.MULTI_APP && info.usesPluginDsl ->
                "Plugin DSL detected — coordinator (com.dynatrace.instrumentation) will be applied " +
                "at the root with dynatrace {} config. App modules need no individual changes."
            flow == SetupFlow.MULTI_APP ->
                "Buildscript classpath detected — classpath added to root; " +
                "com.dynatrace.instrumentation.module applied to each app module."
            info.usesPluginDsl && info.hasBuildscriptBlock ->
                flow.description + " Both a plugins {} block and a buildscript classpath entry are present — " +
                "the wizard will remove the stale classpath entry when applying Plugin DSL."
            else -> flow.description
        }

        val titleLabel = JBLabel(flow.title).apply {
            font = JBUI.Fonts.label().asBold()
            foreground = flowColor
        }
        val descLabel = JBLabel("<html>$description</html>").apply {
            font = JBUI.Fonts.smallFont()
            foreground = UIUtil.getContextHelpForeground()
        }

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.emptyLeft(4)
            titleLabel.alignmentX = 0f
            descLabel.alignmentX  = 0f
            add(titleLabel)
            add(descLabel)
        }
    }

    private fun containsDynatrace(file: com.intellij.openapi.vfs.VirtualFile): Boolean = try {
        val c = com.dynatrace.wizard.service.GradleModificationService.stripComments(
            String(file.contentsToByteArray())
        )
        c.contains("com.dynatrace.instrumentation") || c.contains("dynatrace {")
    } catch (_: Exception) { false }
}
