package com.dynatrace.wizard.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * Service responsible for detecting the Android project structure and locating Gradle build files.
 */
class ProjectDetectionService(private val project: Project) {

    // -------------------------------------------------------------------------
    // Public types
    // -------------------------------------------------------------------------

    enum class ModuleType(val label: String, val icon: String) {
        APPLICATION("App module",     "📱"),
        LIBRARY    ("Library module", "📚"),
        FEATURE    ("Feature module", "🧩"),
        UNKNOWN    ("Unknown",        "❓")
    }

    enum class SetupFlow(val title: String, val description: String) {
        /** One app module — standard Plugin DSL or buildscript path. */
        SINGLE_APP(
            "Single application module",
            "Plugin applied at project root. dynatrace {} config goes in the root build file (Plugin DSL) or the app module (buildscript path)."
        ),
        /** Base app + one or more dynamic-feature modules. */
        FEATURE_MODULES(
            "Base app + feature modules",
            "Plugin applied at project root. dynatrace {} config goes in the root build file (Plugin DSL) or the app module (buildscript path). Dynamic feature and library modules require no changes — they are instrumented automatically."
        ),
        /** More than one com.android.application in the project. */
        MULTI_APP(
            "Multiple application modules",
            "Detected automatically — see the Plugin Approach row for which strategy will be used."
        ),
        /** Root build file also acts as the module build file (no sub-dirs). */
        SINGLE_BUILD_FILE(
            "Single build file (legacy)",
            "All plugin + config changes are applied to the single root build file."
        ),
        /** Structure unclear — best-effort single-app treatment. */
        UNKNOWN(
            "Unknown structure",
            "Project structure could not be determined. Proceeding with best-effort single-module setup."
        )
    }

    data class ModuleInfo(
        val name: String,
        val buildFile: VirtualFile,
        val type: ModuleType,
        val hasDynatrace: Boolean
    )

    data class ProjectInfo(
        val isAndroidProject: Boolean,
        val projectBuildFile: VirtualFile?,
        val appBuildFile: VirtualFile?,         // primary (first) app module build file
        val settingsFile: VirtualFile?,
        val isKotlinDsl: Boolean,
        val appModuleName: String,
        // --- multi-module fields ---
        val setupFlow: SetupFlow,
        val allModules: List<ModuleInfo>,
        val isSingleBuildFile: Boolean,
        /** True when the root build file contains a `plugins {}` block (Plugin DSL approach).
         *  False = legacy buildscript classpath approach. */
        val usesPluginDsl: Boolean,
        /** True when the root build file contains a `buildscript {}` block.
         *  Indicates the user intends to use (or already uses) the classpath approach. */
        val hasBuildscriptBlock: Boolean
    ) {
        val appModules     get() = allModules.filter { it.type == ModuleType.APPLICATION }
        val featureModules get() = allModules.filter { it.type == ModuleType.FEATURE }
        val libraryModules get() = allModules.filter { it.type == ModuleType.LIBRARY }
    }

    // -------------------------------------------------------------------------
    // Detection
    // -------------------------------------------------------------------------

    /**
     * Detects the Android project structure and returns relevant info.
     */
    fun detectProject(): ProjectInfo {
        val baseDir = getProjectBaseDir() ?: return notAndroid()
        baseDir.refresh(false, true)

        val projectBuildFile = findProjectBuildFile(baseDir)
        val settingsFile     = findSettingsFile(baseDir)
        val allModules       = scanAllModules(baseDir)

        val isKotlinDsl = projectBuildFile?.name?.endsWith(".kts") == true
                || allModules.any { it.buildFile.name.endsWith(".kts") }

        // Read and strip comments once — all presence checks below use this stripped content
        // so that commented-out plugins{} / buildscript{} blocks are never false-positives.
        val rootStripped: String = try {
            projectBuildFile?.let {
                GradleModificationService.stripComments(String(it.contentsToByteArray()))
            } ?: ""
        } catch (_: Exception) { "" }

        val usesPluginDsl      = rootStripped.contains(Regex("""plugins\s*\{"""))
        val hasBuildscriptBlock = rootStripped.contains(Regex("""buildscript\s*\{"""))

        val appModules     = allModules.filter { it.type == ModuleType.APPLICATION }
        val featureModules = allModules.filter { it.type == ModuleType.FEATURE }
        val isAndroid      = appModules.isNotEmpty()

        // Single build file: no module sub-directories found at all
        val isSingleBuildFile = allModules.isEmpty() && projectBuildFile != null
                && isAndroidBuildFileContent(projectBuildFile)

        val appBuildFile = when {
            isSingleBuildFile           -> projectBuildFile
            appModules.isNotEmpty()     -> appModules.first().buildFile
            else                        -> null
        }
        val appModuleName = appBuildFile?.parent?.name?.takeUnless { isSingleBuildFile } ?: "app"

        val setupFlow = determineSetupFlow(appModules, featureModules, isSingleBuildFile)

        return ProjectInfo(
            isAndroidProject   = isAndroid || isSingleBuildFile,
            projectBuildFile   = projectBuildFile,
            appBuildFile       = appBuildFile,
            settingsFile       = settingsFile,
            isKotlinDsl        = isKotlinDsl,
            appModuleName      = appModuleName,
            setupFlow          = setupFlow,
            allModules         = allModules,
            isSingleBuildFile  = isSingleBuildFile,
            usesPluginDsl      = usesPluginDsl,
            hasBuildscriptBlock = hasBuildscriptBlock
        )
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun determineSetupFlow(
        appModules: List<ModuleInfo>,
        featureModules: List<ModuleInfo>,
        isSingleBuildFile: Boolean
    ): SetupFlow = when {
        isSingleBuildFile          -> SetupFlow.SINGLE_BUILD_FILE
        appModules.size > 1        -> SetupFlow.MULTI_APP
        featureModules.isNotEmpty() -> SetupFlow.FEATURE_MODULES
        appModules.size == 1       -> SetupFlow.SINGLE_APP
        else                       -> SetupFlow.UNKNOWN
    }

    /** Scans all immediate subdirectories and classifies each Android module. */
    private fun scanAllModules(baseDir: VirtualFile): List<ModuleInfo> {
        val modules = mutableListOf<ModuleInfo>()
        for (child in baseDir.children) {
            if (!child.isDirectory) continue
            val buildFile = child.findChild("build.gradle.kts")
                ?: child.findChild("build.gradle")
                ?: continue
            val type = detectModuleType(buildFile) ?: continue
            modules += ModuleInfo(
                name        = child.name,
                buildFile   = buildFile,
                type        = type,
                hasDynatrace = containsDynatrace(buildFile)
            )
        }
        return modules
    }

    private fun detectModuleType(file: VirtualFile): ModuleType? {
        return try {
            val c = GradleModificationService.stripComments(String(file.contentsToByteArray()))
            when {
                // ── Explicit Gradle plugin IDs ───────────────────────────────
                c.contains("com.android.application")      ||
                c.contains("androidApplication()")         -> ModuleType.APPLICATION

                c.contains("com.android.dynamic-feature")  -> ModuleType.FEATURE

                c.contains("com.android.library")          ||
                c.contains("androidLibrary()")             -> ModuleType.LIBRARY

                // ── Version-catalog aliases ──────────────────────────────────
                // e.g. alias(libs.plugins.android.application)
                Regex("""alias\s*\(\s*\S*android[._]application\S*\s*\)""")
                    .containsMatchIn(c)                    -> ModuleType.APPLICATION

                Regex("""alias\s*\(\s*\S*android[._]dynamic[._-]feature\S*\s*\)""")
                    .containsMatchIn(c)                    -> ModuleType.FEATURE

                Regex("""alias\s*\(\s*\S*android[._]library\S*\s*\)""")
                    .containsMatchIn(c)                    -> ModuleType.LIBRARY

                // ── Short-hand id("android") = com.android.application ───────
                c.contains("\"android\"")                  -> ModuleType.APPLICATION

                // ── android {} block present but no explicit plugin ───────────
                // Use applicationId as reliable heuristic: only app modules declare it.
                Regex("""android\s*\{""").containsMatchIn(c) -> {
                    if (c.contains("applicationId")) ModuleType.APPLICATION
                    else null   // likely a library or unknown module; skip safely
                }

                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun isAndroidBuildFileContent(file: VirtualFile): Boolean =
        detectModuleType(file) != null

    private fun containsDynatrace(file: VirtualFile): Boolean = try {
        val c = GradleModificationService.stripComments(String(file.contentsToByteArray()))
        c.contains("com.dynatrace.instrumentation") ||
        c.contains("com.dynatrace.tools.android") ||
        c.contains("dynatrace {")
    } catch (_: Exception) { false }

    private fun getProjectBaseDir(): VirtualFile? {
        project.guessProjectDir()?.let { return it }
        project.basePath?.let { path ->
            LocalFileSystem.getInstance().findFileByNioFile(Path.of(path))?.let { return it }
        }
        project.basePath?.let { path ->
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(path))?.let { return it }
        }
        return null
    }

    private fun findProjectBuildFile(baseDir: VirtualFile): VirtualFile? =
        baseDir.findChild("build.gradle.kts") ?: baseDir.findChild("build.gradle")

    private fun findSettingsFile(baseDir: VirtualFile): VirtualFile? =
        baseDir.findChild("settings.gradle.kts") ?: baseDir.findChild("settings.gradle")

    /**
     * Checks whether the Dynatrace plugin is already configured in the given build file.
     */
    fun isDynatraceAlreadyConfigured(file: VirtualFile): Boolean = containsDynatrace(file)

    private fun notAndroid() = ProjectInfo(
        isAndroidProject    = false,
        projectBuildFile    = null,
        appBuildFile        = null,
        settingsFile        = null,
        isKotlinDsl         = false,
        appModuleName       = "app",
        setupFlow           = SetupFlow.UNKNOWN,
        allModules          = emptyList(),
        isSingleBuildFile   = false,
        usesPluginDsl       = false,
        hasBuildscriptBlock = false
    )
}