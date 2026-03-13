package com.dynatrace.wizard

import com.dynatrace.wizard.model.DynatraceConfig
import com.dynatrace.wizard.service.GradleModificationService
import com.dynatrace.wizard.service.ProjectDetectionService
import com.dynatrace.wizard.wizard.DynatraceWizardDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * Action that opens the Dynatrace Wizard dialog.
 * Registered in the Tools menu and editor right-click context menu via plugin.xml.
 */
class DynatraceWizardAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val detection    = ProjectDetectionService(project)
        val gradleService = GradleModificationService(project)
        val projectInfo  = detection.detectProject()

        // Check whether Dynatrace is already configured in any build file
        val configuredFile = sequenceOf(
            projectInfo.projectBuildFile,
            projectInfo.appBuildFile,
            *projectInfo.appModules.map { it.buildFile }.toTypedArray()
        ).filterNotNull().firstOrNull { detection.isDynatraceAlreadyConfigured(it) }

        var existingConfig: DynatraceConfig? = null

        if (configuredFile != null) {
            val answer = Messages.showYesNoDialog(
                project,
                "Dynatrace is already configured in this project.\n\n" +
                "Do you want to review or update the existing setup?",
                "Dynatrace Already Configured",
                "Update Setup",
                "Cancel",
                Messages.getQuestionIcon()
            )
            if (answer == Messages.NO) return

            // Read existing values so wizard fields are pre-populated.
            // readExistingConfig returns null when the located file has no dynatrace {} block
            // (e.g. for the per-module approach the root file only has the buildscript classpath
            // entry — the actual config lives in each app module's build file).
            // In that case fall back to the first app module that has a dynatrace {} block.
            existingConfig = gradleService.readExistingConfig(configuredFile)
                ?: projectInfo.appModules
                    .mapNotNull { gradleService.readExistingConfig(it.buildFile) }
                    .firstOrNull()

            // For multi-app projects using the per-module approach, each module has its own
            // applicationId + beaconUrl — read them so the Environment tab can pre-fill them.
            if (existingConfig != null && projectInfo.appModules.size > 1) {
                val moduleCreds = gradleService.readExistingModuleCredentials(projectInfo.appModules)
                if (moduleCreds.isNotEmpty()) {
                    existingConfig = existingConfig.copy(moduleCredentials = moduleCreds)
                }
            }
        }

        DynatraceWizardDialog(project, existingConfig).show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
