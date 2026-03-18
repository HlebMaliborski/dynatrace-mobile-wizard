package com.dynatrace.wizard.service

import com.dynatrace.wizard.model.DynatraceConfig
import com.dynatrace.wizard.service.GradleModificationService.Companion.buildDynatraceBlockKts
import com.dynatrace.wizard.service.ProjectDetectionService.SetupFlow
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.StandardCharsets

/**
 * Service responsible for modifying Gradle build files to add Dynatrace configuration.
 * Supports both Groovy DSL (build.gradle) and Kotlin DSL (build.gradle.kts).
 *
 * Routing logic:
 *  - Plugin DSL path  (project build file already has a `plugins {}` block):
 *      → plugin declaration + dynatrace {} block both go into the **project-level** file.
 *        The plugin must NOT be applied in a module-level file (enforced by the Dynatrace plugin itself).
 *  - Buildscript classpath path (no top-level `plugins {}` block):
 *      → classpath entry goes into the project-level buildscript block;
 *        `apply plugin` + dynatrace {} block go into the **app-level** file.
 */
class GradleModificationService(private val project: Project?) {

    companion object {
        /** Plugin DSL id used in `plugins {}` block and `apply plugin` statements. */
        private const val DYNATRACE_PLUGIN_ID = "com.dynatrace.instrumentation"

        /** Module-level plugin id — used in each app module for multi-app projects. */
        private const val DYNATRACE_MODULE_PLUGIN_ID = "com.dynatrace.instrumentation.module"

        /** Maven group/artifact used in buildscript classpath entries. */
        private const val DYNATRACE_MAVEN_ARTIFACT = "com.dynatrace.tools.android:gradle-plugin"

        /**
         * Use 8.+ to allow automatic minor-version updates.
         * Major version upgrades must be done manually per Dynatrace guidance.
         */
        const val DYNATRACE_PLUGIN_VERSION = "8.+"
        private const val DYNATRACE_CLASSPATH = "$DYNATRACE_MAVEN_ARTIFACT:$DYNATRACE_PLUGIN_VERSION"

        private val PLUGINS_BLOCK_REGEX = Regex("""(plugins\s*\{)""")

        /**
         * Strips both single-line (`// …`) and block (`/* … */`) comments from
         * Gradle DSL content before doing any presence checks.
         * This prevents false positives when a declaration is commented out.
         *
         * The negative lookbehind `(?<!:)` ensures that `://` inside URL string
         * literals (e.g. `https://beacon.dynatrace.com`) is never treated as a
         * comment marker, which would otherwise truncate the URL and cause
         * `readString("beaconUrl")` to return an empty string.
         */
        fun stripComments(content: String): String =
            content
                .replace(Regex("""/\*.*?\*/""", setOf(RegexOption.DOT_MATCHES_ALL)), "")
                .replace(Regex("""(?<!:)//[^\n]*"""), "")

        /** Generates the `dynatrace { }` block for Kotlin DSL from [config]. */
        internal fun buildDynatraceBlockKts(config: DynatraceConfig): String {
            val variantName =
                if (config.buildVariant == "all" || config.buildVariant.isBlank()) "sampleConfig" else config.buildVariant
            val variantFilter =
                if (config.buildVariant == "all" || config.buildVariant.isBlank()) ".*" else config.buildVariant
            return buildString {
                appendLine("dynatrace {")
                if (!config.strictMode) appendLine("    strictMode(false)")
                if (!config.pluginEnabled) appendLine("    pluginEnabled(false)")
                appendLine("    configurations {")
                appendLine("        create(\"$variantName\") {")
                appendLine("            variantFilter(\"$variantFilter\")")
                if (!config.autoInstrument) appendLine("            enabled(false)")
                appendLine("            autoStart {")
                appendLine("                applicationId(\"${config.applicationId}\")")
                appendLine("                beaconUrl(\"${config.beaconUrl}\")")
                if (config.userOptIn) appendLine("                userOptIn(true)")
                if (!config.autoStartEnabled) appendLine("                enabled(false)")
                appendLine("            }")
                val needsUserActionsBlock = !config.userActionsEnabled || config.namePrivacy || !config.composeEnabled
                if (needsUserActionsBlock) {
                    appendLine("            userActions {")
                    if (!config.userActionsEnabled) appendLine("                enabled(false)")
                    if (config.namePrivacy) appendLine("                namePrivacy(true)")
                    if (!config.composeEnabled) appendLine("                composeEnabled(false)")
                    appendLine("            }")
                }
                if (!config.webRequestsEnabled) appendLine("            webRequests { enabled(false) }")
                if (!config.lifecycleEnabled) appendLine("            lifecycle { enabled(false) }")
                if (!config.crashReporting) appendLine("            crashReporting(false)")
                if (!config.anrReporting) appendLine("            anrReporting(false)")
                if (!config.nativeCrashReporting) appendLine("            nativeCrashReporting(false)")
                if (config.hybridMonitoring) appendLine("            hybridMonitoring(true)")
                if (config.locationMonitoring) appendLine("            locationMonitoring(true)")
                if (config.rageTapDetection) {
                    appendLine("            behavioralEvents {")
                    appendLine("                detectRageTaps(true)")
                    appendLine("            }")
                }
                if (config.agentBehaviorLoadBalancing || config.agentBehaviorGrail) {
                    appendLine("            agentBehavior {")
                    if (config.agentBehaviorLoadBalancing) appendLine("                startupLoadBalancing(true)")
                    if (config.agentBehaviorGrail) appendLine("                startupWithGrailEnabled(true)")
                    appendLine("            }")
                }
                if (config.sessionReplayEnabled) appendLine("            sessionReplay.enabled(true)")
                if (config.agentLogging) {
                    appendLine("            debug {")
                    appendLine("                agentLogging(true)")
                    appendLine("            }")
                }
                val packages = config.excludePackages.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val classes = config.excludeClasses.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val methods = config.excludeMethods.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (packages.isNotEmpty() || classes.isNotEmpty() || methods.isNotEmpty()) {
                    appendLine("            exclude {")
                    if (packages.isNotEmpty()) appendLine("                packages(${packages.joinToString(", ") { "\"$it\"" }})")
                    if (classes.isNotEmpty()) appendLine("                classes(${classes.joinToString(", ") { "\"$it\"" }})")
                    if (methods.isNotEmpty()) appendLine("                methods(${methods.joinToString(", ") { "\"$it\"" }})")
                    appendLine("            }")
                }
                appendLine("        }")
                appendLine("    }")
                append("}")
            }
        }

        /** Generates the `dynatrace { }` block for Groovy DSL from [config]. */
        internal fun buildDynatraceBlockGroovy(config: DynatraceConfig): String {
            val variantName =
                if (config.buildVariant == "all" || config.buildVariant.isBlank()) "sampleConfig" else config.buildVariant
            val variantFilter =
                if (config.buildVariant == "all" || config.buildVariant.isBlank()) ".*" else config.buildVariant
            return buildString {
                appendLine("dynatrace {")
                if (!config.strictMode) appendLine("    strictMode false")
                if (!config.pluginEnabled) appendLine("    pluginEnabled false")
                appendLine("    configurations {")
                appendLine("        $variantName {")
                appendLine("            variantFilter '$variantFilter'")
                if (!config.autoInstrument) appendLine("            enabled false")
                appendLine("            autoStart {")
                appendLine("                applicationId '${config.applicationId}'")
                appendLine("                beaconUrl '${config.beaconUrl}'")
                if (config.userOptIn) appendLine("                userOptIn true")
                if (!config.autoStartEnabled) appendLine("                enabled false")
                appendLine("            }")
                val needsUserActionsBlock = !config.userActionsEnabled || config.namePrivacy || !config.composeEnabled
                if (needsUserActionsBlock) {
                    appendLine("            userActions {")
                    if (!config.userActionsEnabled) appendLine("                enabled false")
                    if (config.namePrivacy) appendLine("                namePrivacy true")
                    if (!config.composeEnabled) appendLine("                composeEnabled false")
                    appendLine("            }")
                }
                if (!config.webRequestsEnabled) appendLine("            webRequests { enabled false }")
                if (!config.lifecycleEnabled) appendLine("            lifecycle { enabled false }")
                if (!config.crashReporting) appendLine("            crashReporting false")
                if (!config.anrReporting) appendLine("            anrReporting false")
                if (!config.nativeCrashReporting) appendLine("            nativeCrashReporting false")
                if (config.hybridMonitoring) appendLine("            hybridMonitoring true")
                if (config.locationMonitoring) appendLine("            locationMonitoring true")
                if (config.rageTapDetection) {
                    appendLine("            behavioralEvents {")
                    appendLine("                detectRageTaps true")
                    appendLine("            }")
                }
                if (config.agentBehaviorLoadBalancing || config.agentBehaviorGrail) {
                    appendLine("            agentBehavior {")
                    if (config.agentBehaviorLoadBalancing) appendLine("                startupLoadBalancing true")
                    if (config.agentBehaviorGrail) appendLine("                startupWithGrailEnabled true")
                    appendLine("            }")
                }
                if (config.sessionReplayEnabled) appendLine("            sessionReplay.enabled true")
                if (config.agentLogging) {
                    appendLine("            debug {")
                    appendLine("                agentLogging true")
                    appendLine("            }")
                }
                val packages = config.excludePackages.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val classes = config.excludeClasses.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val methods = config.excludeMethods.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (packages.isNotEmpty() || classes.isNotEmpty() || methods.isNotEmpty()) {
                    appendLine("            exclude {")
                    if (packages.isNotEmpty()) appendLine("                packages ${packages.joinToString(", ") { "\"$it\"" }}")
                    if (classes.isNotEmpty()) appendLine("                classes ${classes.joinToString(", ") { "\"$it\"" }}")
                    if (methods.isNotEmpty()) appendLine("                methods ${methods.joinToString(", ") { "\"$it\"" }}")
                    appendLine("            }")
                }
                appendLine("        }")
                appendLine("    }")
                append("}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Single entry-point that applies the Dynatrace plugin + configuration to the
     * correct Gradle file(s), routing between Plugin DSL and buildscript classpath
     * approaches automatically.
     */
    fun configureGradleFiles(
        projectBuildFile: VirtualFile?,
        appBuildFile: VirtualFile?,
        isKotlinDsl: Boolean,
        config: DynatraceConfig
    ) {
        val projectContent = projectBuildFile
            ?.let { String(it.contentsToByteArray(), StandardCharsets.UTF_8) }
            ?: ""

        if (PLUGINS_BLOCK_REGEX.containsMatchIn(stripComments(projectContent)) && projectBuildFile != null) {
            // ── Plugin DSL path ──────────────────────────────────────────────
            // The Dynatrace plugin MUST be applied at the top level.
            // Both the plugin declaration and the dynatrace {} block go here.
            WriteCommandAction.runWriteCommandAction(project, "Configure Dynatrace Plugin", null, {
                val content = String(projectBuildFile.contentsToByteArray(), StandardCharsets.UTF_8)
                val modified = if (isKotlinDsl) {
                    applyPluginDslKts(content, config)
                } else {
                    applyPluginDslGroovy(content, config)
                }
                if (modified != content) {
                    projectBuildFile.setBinaryContent(modified.toByteArray(StandardCharsets.UTF_8))
                }
            })
        } else {
            // ── Buildscript classpath path ───────────────────────────────────
            // Classpath entry in project-level buildscript; apply + config in app module.
            projectBuildFile?.let { addClasspathToProjectBuild(it, isKotlinDsl) }
            appBuildFile?.let { addDynatraceToAppBuild(it, isKotlinDsl, config) }
        }
    }

    /**
     * Flow-aware overload: reads [ProjectInfo.setupFlow] and routes accordingly.
     *  - SINGLE_APP / SINGLE_BUILD_FILE / UNKNOWN → standard single-file logic above
     *  - FEATURE_MODULES → plugin at root + shared dynatrace-common file + apply from in each module
     *  - MULTI_APP → classpath at root + com.dynatrace.instrumentation.module in each app module
     */
    fun configureGradleFiles(projectInfo: ProjectDetectionService.ProjectInfo, config: DynatraceConfig) {
        when (projectInfo.setupFlow) {
            SetupFlow.FEATURE_MODULES -> configureFeatureModules(projectInfo, config)
            SetupFlow.MULTI_APP -> configureMultiAppModules(projectInfo, config)
            else -> configureGradleFiles(
                projectInfo.projectBuildFile,
                projectInfo.appBuildFile,
                projectInfo.isKotlinDsl,
                config
            )
        }
    }

    // ── Feature module flow ───────────────────────────────────────────────────

    /**
     * Feature-module setup per Dynatrace documentation:
     *  - Plugin applied at the **project root** (Plugin DSL or classpath path).
     *  - `dynatrace {}` config block goes into the root build file (Plugin DSL)
     *    or into the base app module (buildscript path).
     *  - Dynamic feature modules and library modules require **no changes** —
     *    they are instrumented automatically by the Gradle plugin.
     */
    private fun configureFeatureModules(
        projectInfo: ProjectDetectionService.ProjectInfo,
        config: DynatraceConfig
    ) {
        configureGradleFiles(
            projectInfo.projectBuildFile,
            projectInfo.appBuildFile,
            projectInfo.isKotlinDsl,
            config
        )
    }

    /** Adds only the plugin declaration (no dynatrace block) to the project build file. */
    private fun addPluginDeclarationOnly(file: VirtualFile, isKts: Boolean) {
        WriteCommandAction.runWriteCommandAction(project, "Add Dynatrace Plugin Declaration", null, {
            val content = String(file.contentsToByteArray(), StandardCharsets.UTF_8)
            if (content.contains(DYNATRACE_PLUGIN_ID) || content.contains(DYNATRACE_MAVEN_ARTIFACT)) return@runWriteCommandAction
            val modified = if (PLUGINS_BLOCK_REGEX.containsMatchIn(content)) {
                val pluginLine = if (isKts)
                    """    id("$DYNATRACE_PLUGIN_ID") version "$DYNATRACE_PLUGIN_VERSION""""
                else
                    """    id '$DYNATRACE_PLUGIN_ID' version '$DYNATRACE_PLUGIN_VERSION'"""
                PLUGINS_BLOCK_REGEX.replaceFirst(content, "$1\n$pluginLine")
            } else {
                if (isKts) addClasspathKts(content) else addClasspathGroovy(content)
            }
            if (modified != content) file.setBinaryContent(modified.toByteArray(StandardCharsets.UTF_8))
        })
    }

    /**
     * Creates `dynatrace-common.gradle[.kts]` in [dir] containing only the dynatrace {} block.
     * Returns the created (or existing) VirtualFile.
     */
    fun createSharedDynatraceFile(
        dir: VirtualFile,
        fileName: String,
        isKts: Boolean,
        config: DynatraceConfig
    ): VirtualFile {
        val existing = dir.findChild(fileName)
        val block = if (isKts) buildDynatraceBlockKts(config) else buildDynatraceBlockGroovy(config)
        val header = if (isKts)
            "// Shared Dynatrace configuration — applied by base and feature modules\n\n"
        else
            "// Shared Dynatrace configuration — applied by base and feature modules\n\n"
        val content = (header + block).toByteArray(StandardCharsets.UTF_8)

        var result: VirtualFile? = existing
        WriteCommandAction.runWriteCommandAction(project, "Create Shared Dynatrace Config", null, {
            result = existing ?: dir.createChildData(this, fileName)
            result!!.setBinaryContent(content)
        })
        return result!!
    }

    /** Adds `apply from: 'fileName'` (or `apply(from = "...")`) to a module build file. */
    fun applyFromSharedFile(moduleFile: VirtualFile, sharedFileName: String, isKts: Boolean) {
        WriteCommandAction.runWriteCommandAction(project, "Apply Shared Dynatrace Config", null, {
            val content = String(moduleFile.contentsToByteArray(), StandardCharsets.UTF_8)
            if (content.contains(sharedFileName)) return@runWriteCommandAction
            val applyLine = if (isKts) "\napply(from = \"../$sharedFileName\")\n"
            else "\napply from: '../$sharedFileName'\n"
            moduleFile.setBinaryContent((content.trimEnd() + applyLine).toByteArray(StandardCharsets.UTF_8))
        })
    }

    // ── Multi-app flow ────────────────────────────────────────────────────────

    /**
     * Removes `com.dynatrace.instrumentation` (coordinator) plugin declarations from [content]
     * without touching `com.dynatrace.instrumentation.module` lines.
     *
     * Handles all common forms:
     *   - `id("com.dynatrace.instrumentation") version "..." [apply false]` in plugins {} block
     *   - `id 'com.dynatrace.instrumentation' version '...' [apply false]` in plugins {} block
     *   - `apply(plugin = "com.dynatrace.instrumentation")`
     *   - `apply plugin: 'com.dynatrace.instrumentation'`
     */
    private fun removeCoordinatorLine(content: String): String {
        var result = content
        // plugins {} block form: id("com.dynatrace.instrumentation") ...
        // Negative lookahead (?!\.module) ensures we keep the module plugin line untouched
        result = result.replace(
            Regex("""[ \t]*id\s*[("']+com\.dynatrace\.instrumentation(?!\.module)[^"'\n]*["')][^\n]*\n?"""),
            ""
        )
        // apply(plugin = "com.dynatrace.instrumentation") form
        result = result.replace(
            Regex("""[ \t]*apply\s*\(\s*plugin\s*=\s*["']com\.dynatrace\.instrumentation(?!\.module)[^"'\n]*["'][^)]*\)[^\n]*\n?"""),
            ""
        )
        // apply plugin: 'com.dynatrace.instrumentation' form
        result = result.replace(
            Regex("""[ \t]*apply\s+plugin\s*:\s*["']com\.dynatrace\.instrumentation(?!\.module)[^"'\n]*["'][^\n]*\n?"""),
            ""
        )
        return result
    }

    /**
     * Multi-app-module setup per Dynatrace documentation:
     *
     * **Plugin DSL path** (`plugins {}` block at root):
     *   - Root: add `com.dynatrace.instrumentation` (coordinator) with `apply true` + `dynatrace {}`.
     *     This is identical to the single-app Plugin DSL path.
     *   - App modules: **no changes needed** — the coordinator instruments all Android app
     *     submodules automatically. Any previously-added module plugin lines are cleaned up.
     *
     * **Buildscript classpath path** (no `plugins {}` block at root):
     *   - Root: add `classpath 'com.dynatrace.tools.android:gradle-plugin:8.+'` to buildscript.
     *   - Each app module: apply `com.dynatrace.instrumentation.module` + `dynatrace {}` block.
     */
    private fun configureMultiAppModules(
        projectInfo: ProjectDetectionService.ProjectInfo,
        config: DynatraceConfig
    ) {
        val projectBuildFile = projectInfo.projectBuildFile
        val isKts = projectInfo.isKotlinDsl

        // Use the approach the user explicitly chose (set on the Modules tab and threaded
        // through via getEffectiveProjectInfo() → projectInfo.usesPluginDsl).
        if (projectInfo.usesPluginDsl && projectBuildFile != null) {
            // ── Plugin DSL path ───────────────────────────────────────────────
            // Same as single-app: coordinator at root with dynatrace {} block.
            // The coordinator handles all app submodules — no per-module changes needed.
            WriteCommandAction.runWriteCommandAction(project, "Configure Dynatrace Plugin DSL", null, {
                var content = String(projectBuildFile.contentsToByteArray(), StandardCharsets.UTF_8)
                // Migrating FROM per-module (buildscript classpath) → Plugin DSL:
                // the old classpath entry must be removed BEFORE applyPluginDslKts/Groovy runs,
                // because those functions skip adding the coordinator when DYNATRACE_MAVEN_ARTIFACT
                // is still present in the file.
                if (stripComments(content).contains(DYNATRACE_MAVEN_ARTIFACT)) {
                    content = removeClasspathEntry(content)
                }
                val modified = if (isKts) applyPluginDslKts(content, config)
                else applyPluginDslGroovy(content, config)
                if (modified != content)
                    projectBuildFile.setBinaryContent(modified.toByteArray(StandardCharsets.UTF_8))
            })
            // Remove any module plugin declarations left by a previous (incorrect) wizard run.
            projectInfo.appModules.forEach { cleanupModulePlugin(it.buildFile) }
            // Remove any per-module dynatrace {} blocks (migrating from per-module to Plugin DSL).
            projectInfo.appModules.forEach { removeDynatraceBlockFromFile(it.buildFile) }

        } else {
            // ── Buildscript classpath path ────────────────────────────────────
            // If migrating FROM Plugin DSL: strip the coordinator plugin declaration and the
            // root dynatrace {} block — those now live in each app module's build file.
            projectBuildFile?.let { buildFile ->
                WriteCommandAction.runWriteCommandAction(project, "Migrate to Per-Module Classpath", null, {
                    val content = String(buildFile.contentsToByteArray(), StandardCharsets.UTF_8)
                    val stripped = stripComments(content)
                    val hasCoordinator = stripped.contains(DYNATRACE_PLUGIN_ID)
                            && !stripped.contains(DYNATRACE_MAVEN_ARTIFACT)
                    if (hasCoordinator) {
                        val modified = removeDynatraceBlock(removeCoordinatorLine(content))
                        if (modified != content)
                            buildFile.setBinaryContent(modified.toByteArray(StandardCharsets.UTF_8))
                    }
                })
            }
            // Classpath at root; each app module applies the module plugin individually.
            projectBuildFile?.let { addClasspathToProjectBuild(it, isKts) }
            projectInfo.appModules.forEach { module ->
                val moduleConfig = config.moduleCredentials[module.name]?.let {
                    config.copy(applicationId = it.appId, beaconUrl = it.beaconUrl)
                } ?: config
                // Version is intentionally omitted: the classpath entry in the root buildscript
                // already provides the artifact — specifying it again in plugins {} would cause
                // Gradle to resolve the plugin from a repository instead of the classpath.
                addModulePluginAndConfig(module.buildFile, isKts, moduleConfig, includeVersion = false)
            }
        }
    }

    /**
     * Removes all Dynatrace instrumentation from a module build file:
     *  - Strips the `com.dynatrace.instrumentation.module` plugin declaration
     *  - Removes the `dynatrace { ... }` block
     *
     * Used when a module is deselected in a per-module MULTI_APP project so that
     * previously-applied configuration is cleaned up on re-run.
     */
    fun removeModuleInstrumentation(file: VirtualFile) {
        cleanupModulePlugin(file)
        removeDynatraceBlockFromFile(file)
    }

    /**
     * Removes all forms of the `com.dynatrace.instrumentation.module` plugin declaration
     * from [file]. Used when the Plugin DSL coordinator at root makes per-module declarations
     * unnecessary (and conflicting).
     */
    private fun cleanupModulePlugin(file: VirtualFile) {
        WriteCommandAction.runWriteCommandAction(project, "Remove Module Plugin", null, {
            val content = String(file.contentsToByteArray(), StandardCharsets.UTF_8)
            if (!stripComments(content).contains(DYNATRACE_MODULE_PLUGIN_ID)) return@runWriteCommandAction
            var modified = content
                // plugins {} block form
                .replace(
                    Regex("""[ \t]*id\s*[("']+com\.dynatrace\.instrumentation\.module[^"'\n]*["')][^\n]*\n?"""),
                    ""
                )
                // apply(plugin = "...") form
                .replace(
                    Regex("""[ \t]*apply\s*\(\s*plugin\s*=\s*["']com\.dynatrace\.instrumentation\.module["'][^)]*\)[^\n]*\n?"""),
                    ""
                )
                // apply plugin: '...' form
                .replace(
                    Regex("""[ \t]*apply\s+plugin\s*:\s*["']com\.dynatrace\.instrumentation\.module["'][^\n]*\n?"""),
                    ""
                )
            if (modified != content)
                file.setBinaryContent(modified.toByteArray(StandardCharsets.UTF_8))
        })
    }

    /**
     * Removes the `dynatrace { ... }` block from [file].
     * Used when migrating module files away from per-module → Plugin DSL approach,
     * where the config block now lives at the project root instead.
     */
    private fun removeDynatraceBlockFromFile(file: VirtualFile) {
        WriteCommandAction.runWriteCommandAction(project, "Remove Dynatrace Block from Module", null, {
            val content = String(file.contentsToByteArray(), StandardCharsets.UTF_8)
            if (!stripComments(content).contains("dynatrace {") &&
                !stripComments(content).contains("DynatraceExtension")
            ) return@runWriteCommandAction
            val modified = removeDynatraceBlock(content)
            if (modified != content)
                file.setBinaryContent(modified.toByteArray(StandardCharsets.UTF_8))
        })
    }

    /**
     * Applies `com.dynatrace.instrumentation.module` + dynatrace {} block to a module build file.
     *
     * Plugin insertion strategy — ordering matters: Dynatrace must come **after** the Android plugin.
     *  - `plugins {}` block present → insert `id("...") version "..."` immediately after the
     *    Android application plugin line. The version is always explicit because the module plugin
     *    ID (`com.dynatrace.instrumentation.module`) differs from the coordinator plugin ID
     *    (`com.dynatrace.instrumentation`) declared at root; Gradle cannot inherit the version
     *    across different plugin IDs.
     *  - No `plugins {}` block → insert `apply(plugin = "...")` / `apply plugin: '...'` immediately
     *    after the Android application apply statement (version comes from buildscript classpath).
     */
    fun addModulePluginAndConfig(
        file: VirtualFile,
        isKts: Boolean,
        config: DynatraceConfig,
        /**
         * Whether to append `version "8.+"` to the `id(...)` line in a `plugins {}` block.
         * Should always be **false**:
         *  - Buildscript classpath path: the version is already supplied by the root
         *    `buildscript { dependencies { classpath(...) } }` entry; repeating it makes
         *    Gradle resolve the plugin from a remote repository instead of the classpath.
         *  - Plugin DSL path: no `addModulePluginAndConfig` call is made at all
         *    (coordinator at root handles all app modules automatically).
         */
        includeVersion: Boolean = false
    ) {
        WriteCommandAction.runWriteCommandAction(project, "Configure Dynatrace Module Plugin", null, {
            val content = String(file.contentsToByteArray(), StandardCharsets.UTF_8)
            var modified = content

            if (!stripComments(modified).contains(DYNATRACE_MODULE_PLUGIN_ID)) {
                // If the coordinator plugin (com.dynatrace.instrumentation, WITHOUT .module) is
                // present — e.g. from a previous single-app wizard run — strip it first.
                // Having both coordinator and module plugin in the same module is forbidden.
                if (stripComments(modified).contains(DYNATRACE_PLUGIN_ID)) {
                    modified = removeCoordinatorLine(modified)
                }

                val hasPluginsBlock = PLUGINS_BLOCK_REGEX.containsMatchIn(stripComments(modified))
                val lineToInsert = when {
                    hasPluginsBlock && isKts && includeVersion ->
                        """    id("$DYNATRACE_MODULE_PLUGIN_ID") version "$DYNATRACE_PLUGIN_VERSION""""

                    hasPluginsBlock && isKts ->
                        """    id("$DYNATRACE_MODULE_PLUGIN_ID")"""

                    hasPluginsBlock && includeVersion ->
                        """    id '$DYNATRACE_MODULE_PLUGIN_ID' version '$DYNATRACE_PLUGIN_VERSION'"""

                    hasPluginsBlock ->
                        """    id '$DYNATRACE_MODULE_PLUGIN_ID'"""

                    isKts ->
                        """apply(plugin = "$DYNATRACE_MODULE_PLUGIN_ID")"""

                    else ->
                        "apply plugin: '$DYNATRACE_MODULE_PLUGIN_ID'"
                }
                // insertAfterAndroidApplication handles placement AND fallback
                modified = insertAfterAndroidApplication(modified, lineToInsert)
            }

            val newBlock = if (isKts) buildDynatraceBlockKts(config) else buildDynatraceBlockGroovy(config)
            modified = replaceDynatraceBlock(modified, newBlock)

            if (modified != content) file.setBinaryContent(modified.toByteArray(StandardCharsets.UTF_8))
        })
    }

    /**
     * Inserts [lineToInsert] on the line immediately after the Android application plugin
     * declaration. Recognises all common forms:
     *   - `id("com.android.application")`        plugins {} block, explicit id
     *   - `id 'com.android.application'`         plugins {} block, Groovy
     *   - `androidApplication()`                 Kotlin type-safe accessor
     *   - `"android"`                            Groovy shorthand
     *   - `apply plugin: 'com.android.application'`  legacy apply
     *   - `alias(libs.plugins.android.application)`  version-catalog alias
     *
     * Fallback when the Android plugin line is not found:
     *   - `plugins {}` block exists → append inside that block (at least syntactically correct)
     *   - No `plugins {}` block → prepend as a standalone statement
     */
    private fun insertAfterAndroidApplication(content: String, lineToInsert: String): String {
        val lines = content.lines().toMutableList()
        val androidAppRegex = Regex(
            """com\.android\.application""" +
                    """|androidApplication\(\)""" +
                    """|"android"""" +
                    """|alias\s*\(\s*\S*android[._]application\S*\s*\)"""
        )
        val idx = lines.indexOfFirst { line ->
            !line.trimStart().startsWith("//") && androidAppRegex.containsMatchIn(line)
        }
        if (idx >= 0) {
            lines.add(idx + 1, lineToInsert)
            return lines.joinToString("\n")
        }
        // Android plugin line not found — best-effort fallbacks
        return if (PLUGINS_BLOCK_REGEX.containsMatchIn(content)) {
            // Insert at the start of the plugins {} block so at least the syntax is valid
            PLUGINS_BLOCK_REGEX.replaceFirst(content, "$1\n$lineToInsert")
        } else {
            // No plugins {} block at all — prepend as a standalone statement
            lineToInsert + "\n\n" + content
        }
    }

    /**
     * Ensures mavenCentral() is present in the given settings or build file.
     * The Dynatrace Gradle plugin is hosted on Maven Central.
     */
    fun ensureMavenCentral(file: VirtualFile, isKotlinDsl: Boolean) {
        WriteCommandAction.runWriteCommandAction(project, "Add mavenCentral() repository", null, {
            val content = String(file.contentsToByteArray(), StandardCharsets.UTF_8)
            if (stripComments(content).contains("mavenCentral()")) return@runWriteCommandAction
            val modified = if (isKotlinDsl) addMavenCentralKts(content) else addMavenCentralGroovy(content)
            if (modified != content) {
                file.setBinaryContent(modified.toByteArray(StandardCharsets.UTF_8))
            }
        })
    }

    /** Returns true if mavenCentral() is declared (and not commented out) in the file. */
    fun hasMavenCentral(file: VirtualFile): Boolean = try {
        stripComments(String(file.contentsToByteArray())).contains("mavenCentral()")
    } catch (_: Exception) {
        false
    }

    /**
     * Reads an existing Dynatrace configuration from a Gradle build file.
     * Handles both Kotlin DSL (`key("value")` / `key(true)`) and Groovy DSL (`key 'value'` / `key true`).
     * Returns a fully-populated [DynatraceConfig] with all detected values, or null if no config found.
     */
    /**
     * For MULTI_APP projects configured with the per-module approach, reads the
     * `applicationId` and `beaconUrl` from each app module's build file and returns
     * a map of module-name → [ModuleCredentials].
     *
     * Modules whose build file contains no `dynatrace {}` block are skipped.
     * Returns an empty map when all modules share a single root-level config.
     */
    fun readExistingModuleCredentials(
        appModules: List<ProjectDetectionService.ModuleInfo>
    ): Map<String, com.dynatrace.wizard.model.ModuleCredentials> =
        appModules.mapNotNull { module ->
            try {
                val raw = String(module.buildFile.contentsToByteArray(), StandardCharsets.UTF_8)
                val content = stripComments(raw)
                if (!content.contains("dynatrace {") && !content.contains("DynatraceExtension")) return@mapNotNull null
                fun readString(key: String): String =
                    Regex("""$key[\s(]*["']([^"']+)["']""").find(content)
                        ?.groupValues?.get(1)?.trim() ?: ""

                val appId = readString("applicationId")
                val beaconUrl = readString("beaconUrl")
                if (appId.isBlank() && beaconUrl.isBlank()) return@mapNotNull null
                module.name to com.dynatrace.wizard.model.ModuleCredentials(
                    appId = appId, beaconUrl = beaconUrl
                )
            } catch (_: Exception) {
                null
            }
        }.toMap()

    fun readExistingConfig(file: VirtualFile): DynatraceConfig? {
        val raw = String(file.contentsToByteArray(), StandardCharsets.UTF_8)
        return readExistingConfigFromString(raw)
    }

    /**
     * Parses a Dynatrace configuration from the raw [gradleContent] string.
     * Exposed for unit-testing without a real [VirtualFile].
     */
    fun readExistingConfigFromString(gradleContent: String): DynatraceConfig? {
        return try {
            val content = stripComments(gradleContent)   // ignore commented-out declarations
            // Recognize both `dynatrace {` and `configure<…DynatraceExtension…> {` as config blocks.
            if (!content.contains("dynatrace {") && !content.contains("DynatraceExtension")) return null

            // Extract content of a non-nested block: key { ... }
            fun extractBlock(key: String): String =
                Regex("""$key\s*\{([^}]*)}""", RegexOption.DOT_MATCHES_ALL)
                    .find(content)?.groupValues?.get(1) ?: ""

            // Read a string value: key("value"), key 'value', key "value"
            fun readString(key: String): String =
                Regex("""$key[\s(]*["']([^"']+)["']""").find(content)
                    ?.groupValues?.get(1)?.trim() ?: ""

            // Check if key=value pattern exists anywhere in the content (both DSL flavours)
            fun hasFlag(key: String, value: Boolean): Boolean =
                Regex("""$key[\s(]*$value""").containsMatchIn(content)

            // Check if key=value pattern exists within a specific extracted block
            fun hasFlagInBlock(block: String, key: String, value: Boolean): Boolean =
                Regex("""$key[\s(]*$value""").containsMatchIn(block)

            // Read comma-separated quoted values from a line starting with key inside a block
            fun readListValues(block: String, key: String): String {
                val line = block.lines().firstOrNull { it.trim().startsWith(key) } ?: return ""
                return Regex("""["']([^"']+)["']""").findAll(line)
                    .map { it.groupValues[1] }.joinToString(", ")
            }

            val appId = readString("applicationId")
            val beaconUrl = readString("beaconUrl")
            if (appId.isBlank() && beaconUrl.isBlank()) return null

            // --- Variant ---
            val variantFilter = readString("variantFilter")
            val buildVariant = if (variantFilter.isBlank() || variantFilter == ".*") "all" else variantFilter

            // --- Extract sub-blocks (all are non-nested) ---
            val autoStartBlock = extractBlock("autoStart")
            val userActionsBlock = extractBlock("userActions")
            val webRequestsBlock = extractBlock("webRequests")
            val lifecycleBlock = extractBlock("lifecycle")
            val agentBehaviorBlock = extractBlock("agentBehavior")
            val behavioralBlock = extractBlock("behavioralEvents")
            val excludeBlock = extractBlock("exclude")
            val debugBlock = extractBlock("debug")

            // Strip sub-blocks so the remaining content only has config-level flags.
            // This lets us isolate the config-level `enabled(false)` that controls autoInstrument.
            val stripped = content
                .replace(Regex("""autoStart\s*\{[^}]*}""", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("""userActions\s*\{[^}]*}""", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("""webRequests\s*\{[^}]*}""", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("""lifecycle\s*\{[^}]*}""", RegexOption.DOT_MATCHES_ALL), "")

            DynatraceConfig(
                applicationId = appId,
                beaconUrl = beaconUrl,
                // Global switches — emitted only when non-default, so absence = default (true)
                pluginEnabled = !hasFlag("pluginEnabled", false),
                // Config-level enabled(false) means autoInstrument = false; check stripped content
                autoInstrument = !Regex("""enabled[\s(]*false""").containsMatchIn(stripped),
                // autoStart sub-block
                autoStartEnabled = !hasFlagInBlock(autoStartBlock, "enabled", false),
                // userOptIn may be inside autoStart (new approach) OR at configuration level
                // (old snippet approach) — check both.
                userOptIn = hasFlagInBlock(autoStartBlock, "userOptIn", true)
                        || hasFlag("userOptIn", true),
                // Monitoring sections
                crashReporting = !hasFlag("crashReporting", false),
                anrReporting = !hasFlag("anrReporting", false),
                nativeCrashReporting = !hasFlag("nativeCrashReporting", false),
                hybridMonitoring = hasFlag("hybridMonitoring", true),
                userActionsEnabled = !hasFlagInBlock(userActionsBlock, "enabled", false),
                webRequestsEnabled = !hasFlagInBlock(webRequestsBlock, "enabled", false),
                lifecycleEnabled = !hasFlagInBlock(lifecycleBlock, "enabled", false),
                locationMonitoring = hasFlag("locationMonitoring", true),
                // userActions details
                namePrivacy = hasFlagInBlock(userActionsBlock, "namePrivacy", true),
                composeEnabled = !hasFlag("composeEnabled", false),
                // Behavioral events
                rageTapDetection = hasFlagInBlock(behavioralBlock, "detectRageTaps", true),
                // Agent behavior — handles both block form and dot-notation form
                agentBehaviorLoadBalancing = hasFlagInBlock(agentBehaviorBlock, "startupLoadBalancing", true)
                        || Regex("""agentBehavior\.startupLoadBalancing[\s(]*true""").containsMatchIn(content),
                agentBehaviorGrail = hasFlagInBlock(agentBehaviorBlock, "startupWithGrailEnabled", true)
                        || Regex("""agentBehavior\.startupWithGrailEnabled[\s(]*true""").containsMatchIn(content),
                // Session Replay — dot notation: sessionReplay.enabled(true) / sessionReplay.enabled true
                sessionReplayEnabled = Regex("""sessionReplay\.enabled[\s(]*true""").containsMatchIn(content),
                // Debug logging — debug { agentLogging true/agentLogging(true) }
                agentLogging = hasFlagInBlock(debugBlock, "agentLogging", true),
                // Exclusions
                excludePackages = readListValues(excludeBlock, "packages"),
                excludeClasses = readListValues(excludeBlock, "classes"),
                excludeMethods = readListValues(excludeBlock, "methods"),
                buildVariant = buildVariant,
                strictMode = hasFlag("strictMode", true)
            )
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Plugin DSL path — everything in the project-level build file
    // -------------------------------------------------------------------------

    /**
     * Removes the `dynatrace { ... }` block from [content] entirely.
     * Used when migrating away from a path that placed the config block in the current file.
     * Returns [content] unchanged if no block is found.
     * Commented-out `dynatrace {}` blocks are ignored.
     */
    private fun removeDynatraceBlock(content: String): String {
        val startMatch = findDynatraceBlockStart(content) ?: return content
        val openBraceIdx = content.indexOf('{', startMatch)
        if (openBraceIdx == -1) return content
        var depth = 0;
        var blockEnd = -1
        for (i in openBraceIdx until content.length) {
            when (content[i]) {
                '{' -> depth++; '}' -> {
                depth--; if (depth == 0) {
                    blockEnd = i; break
                }
            }
            }
        }
        if (blockEnd == -1) return content
        val before = content.substring(0, startMatch).trimEnd()
        val after = content.substring(blockEnd + 1).trimStart('\n', '\r')
        return if (after.isBlank()) before else "$before\n\n$after"
    }

    /**
     * Replaces an existing `dynatrace { ... }` block in [content] with [newBlock].
     * Uses brace-depth counting to correctly handle the deeply nested structure.
     * If no existing block is found, [newBlock] is appended at the end.
     * Commented-out `dynatrace {}` blocks are ignored.
     */
    private fun replaceDynatraceBlock(content: String, newBlock: String): String {
        val blockStart = findDynatraceBlockStart(content)
            ?: return content.trimEnd() + "\n\n" + newBlock

        val openBraceIdx = content.indexOf('{', blockStart)
        if (openBraceIdx == -1) return content.trimEnd() + "\n\n" + newBlock

        var depth = 0
        var blockEnd = -1
        for (i in openBraceIdx until content.length) {
            when (content[i]) {
                '{' -> depth++
                '}' -> {
                    depth--; if (depth == 0) {
                        blockEnd = i; break
                    }
                }
            }
        }
        if (blockEnd == -1) return content.trimEnd() + "\n\n" + newBlock

        val before = content.substring(0, blockStart).trimEnd()
        val after = content.substring(blockEnd + 1).trimStart('\n', '\r')
        return if (after.isBlank()) "$before\n\n$newBlock"
        else "$before\n\n$newBlock\n\n$after"
    }

    /**
     * Finds the character offset of the first non-commented `dynatrace {` or
     * `configure<…DynatraceExtension…> {` in [content].
     *
     * The `configure<>` form is required in Kotlin DSL when the plugin is applied via
     * `apply(plugin = "com.dynatrace.instrumentation")` at the project root (together
     * with a `buildscript { classpath }` entry), because the type-safe `dynatrace {}`
     * accessor is only generated when the plugin is declared inside a `plugins {}` block.
     *
     * A match is considered commented when the characters before it on the same line
     * (after trimming whitespace) start with `//`.
     * Returns `null` when no active block exists.
     */
    private fun findDynatraceBlockStart(content: String): Int? {
        val regex = Regex("""dynatrace\s*\{|configure\s*<[^>]*DynatraceExtension[^>]*>\s*\{""")
        return regex.findAll(content).firstOrNull { match ->
            val lineStart = content.lastIndexOf('\n', match.range.first).coerceAtLeast(-1) + 1
            val prefix = content.substring(lineStart, match.range.first)
            !prefix.trimStart().startsWith("//")
        }?.range?.first
    }

    /**
     * Removes the Dynatrace `classpath(...)` / `classpath '...'` dependency line from [content].
     * Used when migrating from per-module (buildscript classpath) to the Plugin DSL approach.
     *
     * If the `buildscript {}` block's `dependencies {}` section contained **only** the Dynatrace
     * classpath entry (i.e. no other `classpath` lines), the entire `buildscript {}` block is
     * removed — it would otherwise be left as an empty shell after the migration.
     * When other classpath entries are present only the Dynatrace line is removed.
     */
    private fun removeClasspathEntry(content: String): String {
        // Locate the buildscript block start in comment-stripped content, but operate on the
        // original content so that formatting / comments outside the block are preserved.
        val strippedContent = stripComments(content)
        val buildscriptMatch = Regex("""buildscript\s*\{""").find(strippedContent)

        if (buildscriptMatch != null) {
            // Find the opening brace offset in the *original* content at the same position.
            val openBraceIdx = content.indexOf('{', buildscriptMatch.range.first)
            if (openBraceIdx != -1) {
                // Brace-count to find the end of the buildscript block.
                var depth = 0
                var blockEnd = -1
                for (i in openBraceIdx until content.length) {
                    when (content[i]) {
                        '{' -> depth++
                        '}' -> {
                            depth--; if (depth == 0) {
                                blockEnd = i; break
                            }
                        }
                    }
                }
                if (blockEnd != -1) {
                    // Analyse the block body (stripped) for non-Dynatrace classpath entries.
                    val blockBody = stripComments(content.substring(openBraceIdx + 1, blockEnd))
                    val hasOtherClasspath = blockBody.lines().any { line ->
                        line.contains("classpath") && !line.contains("com.dynatrace")
                    }
                    if (!hasOtherClasspath) {
                        // Dynatrace was the only dependency — drop the entire buildscript block.
                        val lineStart = content.lastIndexOf('\n', buildscriptMatch.range.first)
                            .let { if (it == -1) 0 else it + 1 }
                        val before = content.substring(0, lineStart).trimEnd()
                        val after = content.substring(blockEnd + 1).trimStart('\n', '\r')
                        return when {
                            after.isBlank() -> before
                            before.isBlank() -> after
                            else -> "$before\n\n$after"
                        }
                    }
                }
            }
        }

        // Other classpath entries exist — remove only the Dynatrace classpath line.
        return content.replace(
            Regex("""[ \t]*classpath\s*[("']+com\.dynatrace[^"'\n]+["')]+[^\n]*\n?"""),
            ""
        )
    }

    /** Kotlin DSL: add plugin (no `apply false`) + dynatrace {} to project build file.
     *
     * Three sub-cases depending on what is already in [content]:
     *
     * 1. **Fresh / Plugin DSL only** — neither `DYNATRACE_PLUGIN_ID` nor `DYNATRACE_MAVEN_ARTIFACT`
     *    present → insert `id("com.dynatrace.instrumentation") version "8.+"` into the `plugins {}`
     *    block and emit a `dynatrace {}` block.  The type-safe `dynatrace {}` accessor is generated
     *    by Gradle because the plugin is declared inside `plugins {}`.
     *
     * 2. **Mixed: `plugins {}` + `buildscript classpath`** — `DYNATRACE_MAVEN_ARTIFACT` is already
     *    present in the file (project was configured with the old buildscript approach).  Do NOT add
     *    the plugin to the `plugins {}` block (that would conflict with the classpath entry).
     *    Instead ensure `apply(plugin = "com.dynatrace.instrumentation")` exists after the
     *    `plugins {}` block and emit `configure<com.dynatrace.tools.android.dsl.DynatraceExtension>`
     *    — required because the type-safe accessor is not generated for `apply(plugin = ...)`.
     *
     * 3. **Re-run / Plugin DSL already present** — `DYNATRACE_PLUGIN_ID` already in content, no
     *    classpath → just refresh the `dynatrace {}` block.
     */
    private fun applyPluginDslKts(content: String, config: DynatraceConfig): String {
        var result = content
        val stripped = stripComments(result)
        val hasMavenArtifact = stripped.contains(DYNATRACE_MAVEN_ARTIFACT)
        val hasConfigureBlock = stripped.contains("DynatraceExtension")

        when {
            !stripped.contains(DYNATRACE_PLUGIN_ID) && !hasMavenArtifact -> {
                // Case 1: fresh Plugin DSL setup → add to plugins{} block
                val pluginLine = """    id("$DYNATRACE_PLUGIN_ID") version "$DYNATRACE_PLUGIN_VERSION""""
                result = PLUGINS_BLOCK_REGEX.replaceFirst(result, "$1\n$pluginLine")
            }

            hasMavenArtifact -> {
                // Case 2: existing buildscript classpath approach → apply at root + configure<>
                if (!stripped.contains("""apply(plugin = "$DYNATRACE_PLUGIN_ID")""") &&
                    !stripped.contains("""apply(plugin = '$DYNATRACE_PLUGIN_ID')""")
                ) {
                    result = addApplyStatementAfterPluginsBlock(result, isKts = true)
                }
            }
            // Case 3: Plugin DSL already present → no declaration change needed
        }

        val block = if (hasMavenArtifact || hasConfigureBlock) {
            buildConfigureExtensionBlockKts(config)
        } else {
            buildDynatraceBlockKts(config)
        }
        result = replaceDynatraceBlock(result, block)
        return result
    }

    /** Groovy DSL: add plugin (no `apply false`) + dynatrace {} to project build file.
     *
     * Mirrors the three sub-cases of [applyPluginDslKts].  In Groovy DSL the `dynatrace {}`
     * extension closure is always accessible regardless of how the plugin is applied, so
     * `configure<>` is not needed — `dynatrace {}` is used in all cases.
     */
    private fun applyPluginDslGroovy(content: String, config: DynatraceConfig): String {
        var result = content
        val stripped = stripComments(result)
        val hasMavenArtifact = stripped.contains(DYNATRACE_MAVEN_ARTIFACT)

        when {
            !stripped.contains(DYNATRACE_PLUGIN_ID) && !hasMavenArtifact -> {
                // Case 1: fresh Plugin DSL setup → add to plugins{} block
                val pluginLine = """    id '$DYNATRACE_PLUGIN_ID' version '$DYNATRACE_PLUGIN_VERSION'"""
                result = PLUGINS_BLOCK_REGEX.replaceFirst(result, "$1\n$pluginLine")
            }

            hasMavenArtifact -> {
                // Case 2: existing buildscript classpath approach → apply at root
                if (!stripped.contains("apply plugin: '$DYNATRACE_PLUGIN_ID'") &&
                    !stripped.contains("apply plugin: \"$DYNATRACE_PLUGIN_ID\"")
                ) {
                    result = addApplyStatementAfterPluginsBlock(result, isKts = false)
                }
            }
            // Case 3: Plugin DSL already present → no declaration change needed
        }

        result = replaceDynatraceBlock(result, buildDynatraceBlockGroovy(config))
        return result
    }

    // -------------------------------------------------------------------------
    // Buildscript classpath path — classpath in project, apply+config in app
    // -------------------------------------------------------------------------

    /** Adds the Dynatrace classpath entry to the project-level buildscript block. */
    private fun addClasspathToProjectBuild(file: VirtualFile, isKotlinDsl: Boolean) {
        WriteCommandAction.runWriteCommandAction(project, "Add Dynatrace Plugin Dependency", null, {
            val content = String(file.contentsToByteArray(), StandardCharsets.UTF_8)
            // Skip only if the Maven classpath artifact is already present (uncommented).
            if (stripComments(content).contains(DYNATRACE_MAVEN_ARTIFACT)) return@runWriteCommandAction
            val modified = if (isKotlinDsl) addClasspathKts(content) else addClasspathGroovy(content)
            if (modified != content) {
                file.setBinaryContent(modified.toByteArray(StandardCharsets.UTF_8))
            }
        })
    }

    /** Adds `apply plugin` + dynatrace {} to the app-level build file (buildscript path only). */
    private fun addDynatraceToAppBuild(file: VirtualFile, isKotlinDsl: Boolean, config: DynatraceConfig) {
        WriteCommandAction.runWriteCommandAction(project, "Configure Dynatrace in App Module", null, {
            val content = String(file.contentsToByteArray(), StandardCharsets.UTF_8)
            val modified = if (isKotlinDsl) {
                addToAppBuildKts(content, config)
            } else {
                addToAppBuildGroovy(content, config)
            }
            if (modified != content) {
                file.setBinaryContent(modified.toByteArray(StandardCharsets.UTF_8))
            }
        })
    }

    internal fun addClasspathKts(content: String): String {
        val hasPluginsDsl = PLUGINS_BLOCK_REGEX.containsMatchIn(stripComments(content))
        val depsRegex = Regex("""(buildscript\s*\{[^}]*dependencies\s*\{)""", RegexOption.DOT_MATCHES_ALL)
        return if (depsRegex.containsMatchIn(content)) {
            val withClasspath = depsRegex.replaceFirst(content, "$1\n        classpath(\"$DYNATRACE_CLASSPATH\")")
            // Repositories are managed by pluginManagement in settings.gradle for Plugin DSL projects.
            if (hasPluginsDsl) withClasspath else ensureBuildscriptRepositories(withClasspath, isKts = true)
        } else {
            // Plugin DSL project: only a minimal buildscript {} with dependencies is needed.
            // Legacy template: include repositories so Gradle can resolve the artifact.
            val newBlock = if (hasPluginsDsl) {
                """
buildscript {
    dependencies {
        classpath("$DYNATRACE_CLASSPATH")
    }
}
"""
            } else {
                """
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("$DYNATRACE_CLASSPATH")
    }
}
"""
            }
            newBlock + "\n" + content
        }
    }

    internal fun addClasspathGroovy(content: String): String {
        val hasPluginsDsl = PLUGINS_BLOCK_REGEX.containsMatchIn(stripComments(content))
        val depsRegex = Regex("""(buildscript\s*\{[^}]*dependencies\s*\{)""", RegexOption.DOT_MATCHES_ALL)
        return if (depsRegex.containsMatchIn(content)) {
            val withClasspath = depsRegex.replaceFirst(content, "$1\n        classpath '$DYNATRACE_CLASSPATH'")
            if (hasPluginsDsl) withClasspath else ensureBuildscriptRepositories(withClasspath, isKts = false)
        } else {
            val newBlock = if (hasPluginsDsl) {
                """
buildscript {
    dependencies {
        classpath '$DYNATRACE_CLASSPATH'
    }
}
"""
            } else {
                """
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath '$DYNATRACE_CLASSPATH'
    }
}
"""
            }
            newBlock + "\n" + content
        }
    }

    /**
     * Ensures `buildscript { repositories { google(); mavenCentral() } }` exists.
     *
     * Called only on the buildscript-classpath path after adding the Dynatrace classpath line,
     * so multi-app/per-module setups match the full legacy guidance snippet.
     */
    private fun ensureBuildscriptRepositories(content: String, isKts: Boolean): String {
        val buildscriptMatch = Regex("""buildscript\s*\{""").find(content) ?: return content
        val buildscriptOpen = content.indexOf('{', buildscriptMatch.range.first)
        if (buildscriptOpen == -1) return content

        var depth = 0
        var buildscriptEnd = -1
        for (i in buildscriptOpen until content.length) {
            when (content[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        buildscriptEnd = i
                        break
                    }
                }
            }
        }
        if (buildscriptEnd == -1) return content

        val buildscriptBody = content.substring(buildscriptOpen + 1, buildscriptEnd)
        val repositoriesMatch = Regex("""repositories\s*\{""").find(buildscriptBody)
        if (repositoriesMatch == null) {
            val depsMatch = Regex("""dependencies\s*\{""").find(buildscriptBody)
            val repositoriesBlock = if (isKts) {
                """
    repositories {
        google()
        mavenCentral()
    }
"""
            } else {
                """
    repositories {
        google()
        mavenCentral()
    }
"""
            }

            if (depsMatch == null) {
                return content
            }

            val insertAt = buildscriptOpen + 1 + depsMatch.range.first
            return content.substring(0, insertAt) + repositoriesBlock + content.substring(insertAt)
        }

        val reposOpenInBody = buildscriptBody.indexOf('{', repositoriesMatch.range.first)
        if (reposOpenInBody == -1) return content

        depth = 0
        var reposEndInBody = -1
        for (i in reposOpenInBody until buildscriptBody.length) {
            when (buildscriptBody[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        reposEndInBody = i
                        break
                    }
                }
            }
        }
        if (reposEndInBody == -1) return content

        val reposBody = buildscriptBody.substring(reposOpenInBody + 1, reposEndInBody)
        val reposBodyStripped = stripComments(reposBody)
        val missingGoogle = !reposBodyStripped.contains("google()")
        val missingMavenCentral = !reposBodyStripped.contains("mavenCentral()")
        if (!missingGoogle && !missingMavenCentral) return content

        val insertAt = buildscriptOpen + 1 + reposOpenInBody + 1
        val additions = buildString {
            if (missingGoogle) append("\n        google()")
            if (missingMavenCentral) append("\n        mavenCentral()")
        }
        return content.substring(0, insertAt) + additions + content.substring(insertAt)
    }

    private fun addToAppBuildKts(content: String, config: DynatraceConfig): String {
        var result = content
        if (!result.contains(DYNATRACE_PLUGIN_ID)) {
            result = if (PLUGINS_BLOCK_REGEX.containsMatchIn(result)) {
                PLUGINS_BLOCK_REGEX.replaceFirst(result, "$1\n    id(\"$DYNATRACE_PLUGIN_ID\")")
            } else {
                """apply(plugin = "$DYNATRACE_PLUGIN_ID")""" + "\n\n" + result
            }
        }
        result = replaceDynatraceBlock(result, buildDynatraceBlockKts(config))
        return result
    }

    private fun addToAppBuildGroovy(content: String, config: DynatraceConfig): String {
        var result = content
        if (!result.contains(DYNATRACE_PLUGIN_ID)) {
            result = if (PLUGINS_BLOCK_REGEX.containsMatchIn(result)) {
                PLUGINS_BLOCK_REGEX.replaceFirst(result, "$1\n    id '$DYNATRACE_PLUGIN_ID'")
            } else {
                "apply plugin: '$DYNATRACE_PLUGIN_ID'\n\n" + result
            }
        }
        result = replaceDynatraceBlock(result, buildDynatraceBlockGroovy(config))
        return result
    }

    /**
     * Inserts an `apply(plugin = "com.dynatrace.instrumentation")` (Kotlin DSL) or
     * `apply plugin: 'com.dynatrace.instrumentation'` (Groovy DSL) statement on the line
     * immediately following the closing `}` of the `plugins {}` block.
     *
     * Used when the project root file has a `plugins {}` block for other plugins AND uses
     * the buildscript classpath approach for Dynatrace (old snippet pattern).  In this
     * scenario the Dynatrace plugin cannot go inside the `plugins {}` block because the
     * version is already supplied by the classpath entry; putting it in `plugins {}` as
     * well would cause Gradle to attempt a separate repository resolution.
     */
    private fun addApplyStatementAfterPluginsBlock(content: String, isKts: Boolean): String {
        val pluginsMatch = PLUGINS_BLOCK_REGEX.find(stripComments(content)) ?: return content
        // Find the same opening brace position in the original (un-stripped) content.
        // We search for it starting at the same offset as the match in stripped content.
        val openBraceIdx = content.indexOf('{', pluginsMatch.range.first)
        if (openBraceIdx == -1) return content
        var depth = 0
        var blockEnd = -1
        for (i in openBraceIdx until content.length) {
            when (content[i]) {
                '{' -> depth++
                '}' -> {
                    depth--; if (depth == 0) {
                        blockEnd = i; break
                    }
                }
            }
        }
        if (blockEnd == -1) return content
        val applyStatement = if (isKts) "\napply(plugin = \"$DYNATRACE_PLUGIN_ID\")\n"
        else "\napply plugin: '$DYNATRACE_PLUGIN_ID'\n"
        return content.substring(0, blockEnd + 1) + applyStatement + content.substring(blockEnd + 1)
    }

    /**
     * Builds a `configure<com.dynatrace.tools.android.dsl.DynatraceExtension> { … }` block
     * by reusing [buildDynatraceBlockKts] and replacing its `dynatrace {` opener.
     *
     * This form is required in Kotlin DSL when the plugin is applied via
     * `apply(plugin = "com.dynatrace.instrumentation")` — i.e. the buildscript classpath
     * approach on a root file that already has a `plugins {}` block for other plugins.
     * In that case Gradle does **not** generate the `dynatrace {}` type-safe accessor, so
     * `configure<DynatraceExtension> { }` must be used instead.
     */
    private fun buildConfigureExtensionBlockKts(config: DynatraceConfig): String =
        buildDynatraceBlockKts(config)
            .replaceFirst(
                "dynatrace {",
                "configure<com.dynatrace.tools.android.dsl.DynatraceExtension> {"
            )

    // -------------------------------------------------------------------------
    // Maven Central helpers
    // -------------------------------------------------------------------------

    private fun addMavenCentralKts(content: String): String {
        val reposRegex = Regex("""(repositories\s*\{)""")
        return if (reposRegex.containsMatchIn(content)) {
            reposRegex.replaceFirst(content, "$1\n        mavenCentral()")
        } else {
            "repositories {\n    mavenCentral()\n}\n\n$content"
        }
    }

    private fun addMavenCentralGroovy(content: String): String {
        val reposRegex = Regex("""(repositories\s*\{)""")
        return if (reposRegex.containsMatchIn(content)) {
            reposRegex.replaceFirst(content, "$1\n        mavenCentral()")
        } else {
            "repositories {\n    mavenCentral()\n}\n\n$content"
        }
    }

    // -------------------------------------------------------------------------
    // dynatrace {} block builders (shared by both paths) — delegate to companion
    // -------------------------------------------------------------------------

    private fun buildDynatraceBlockKts(config: DynatraceConfig): String =
        Companion.buildDynatraceBlockKts(config)

    private fun buildDynatraceBlockGroovy(config: DynatraceConfig): String =
        Companion.buildDynatraceBlockGroovy(config)

    /**
     * Adds a `subprojects {}` block to [projectFile] (the root build file) that injects
     * `com.dynatrace.tools.android.DynatracePlugin.agentDependency()` into every selected
     * Android library module via `pluginManager.withPlugin("com.android.library")`.
     *
     * Two variants are emitted depending on the selection:
     *  - **All library modules selected**: simple block with no name filter — all library
     *    modules in the project receive the SDK dependency automatically.
     *  - **Subset selected**: a name-based `if` guard is included so only the chosen modules
     *    get the dependency, matching the Dynatrace docs pattern:
     *    ```
     *    subprojects { project ->
     *        pluginManager.withPlugin("com.android.library") {
     *            if (project.name == 'lib1' || project.name == 'lib2') { … }
     *        }
     *    }
     *    ```
     *
     * The block is appended to the end of the root build file.  The call is idempotent:
     * if `agentDependency()` is already present the file is left unchanged.
     *
     * See: https://docs.dynatrace.com/docs/shortlink/android-multiple-modules
     */
    fun addOneAgentSdkToProjectBuild(
        projectFile: VirtualFile,
        isKts: Boolean,
        selectedModuleNames: List<String>,
        allLibraryModuleNames: List<String>
    ) {
        if (selectedModuleNames.isEmpty()) return
        WriteCommandAction.runWriteCommandAction(project, "Add Dynatrace OneAgent SDK to Libraries", null, {
            val content = String(projectFile.contentsToByteArray(), StandardCharsets.UTF_8)

            val filterAll = allLibraryModuleNames.size <= 1
            val newBlock = if (isKts) buildSdkSubprojectsBlockKts(selectedModuleNames, filterAll)
            else buildSdkSubprojectsBlockGroovy(selectedModuleNames, filterAll)

            val modified = replaceSdkSubprojectsBlock(content, newBlock)
            if (modified != content) projectFile.setBinaryContent(modified.toByteArray(StandardCharsets.UTF_8))
        })
    }

    /**
     * Finds an existing `subprojects { … agentDependency() … }` block in [content]
     * and replaces it with [newBlock].  If no such block exists the new block is
     * appended.  Uses brace-depth counting to locate the exact closing `}`.
     */
    private fun replaceSdkSubprojectsBlock(content: String, newBlock: String): String {
        val subprojectsRegex = Regex("""subprojects\s*\{""")
        for (match in subprojectsRegex.findAll(content)) {
            val openBrace = match.range.last
            var depth = 1
            var blockEnd = -1
            for (i in (openBrace + 1) until content.length) {
                when (content[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--; if (depth == 0) {
                            blockEnd = i; break
                        }
                    }
                }
            }
            if (blockEnd == -1) continue
            if (!content.substring(match.range.first, blockEnd + 1).contains("agentDependency()")) continue

            val before = content.substring(0, match.range.first).trimEnd()
            val after = content.substring(blockEnd + 1).trimStart('\n', '\r')
            return if (after.isBlank()) "$before\n\n$newBlock"
            else "$before\n\n$newBlock\n\n$after"
        }
        // No existing block — append
        return content.trimEnd() + "\n\n" + newBlock + "\n"
    }

    internal fun buildSdkSubprojectsBlockKts(moduleNames: List<String>, filterAll: Boolean): String =
        buildString {
            appendLine("subprojects {")
            appendLine("    pluginManager.withPlugin(\"com.android.library\") {")
            if (filterAll) {
                appendLine("        dependencies {")
                appendLine("            \"implementation\"(com.dynatrace.tools.android.DynatracePlugin.agentDependency())")
                appendLine("        }")
            } else {
                val condition = moduleNames.joinToString(" || ") { """project.name == "$it"""" }
                appendLine("        if ($condition) {")
                appendLine("            dependencies {")
                appendLine("                \"implementation\"(com.dynatrace.tools.android.DynatracePlugin.agentDependency())")
                appendLine("            }")
                appendLine("        }")
            }
            appendLine("    }")
            append("}")
        }

    internal fun buildSdkSubprojectsBlockGroovy(moduleNames: List<String>, filterAll: Boolean): String =
        buildString {
            appendLine("subprojects {")
            appendLine("    pluginManager.withPlugin('com.android.library') {")
            if (filterAll) {
                appendLine("        dependencies {")
                appendLine("            implementation com.dynatrace.tools.android.DynatracePlugin.agentDependency()")
                appendLine("        }")
            } else {
                val condition = moduleNames.joinToString(" || ") { "project.name == '$it'" }
                appendLine("        if ($condition) {")
                appendLine("            dependencies {")
                appendLine("                implementation com.dynatrace.tools.android.DynatracePlugin.agentDependency()")
                appendLine("            }")
                appendLine("        }")
            }
            appendLine("    }")
            append("}")
        }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------

    fun generateChangePreview(
        projectInfo: ProjectDetectionService.ProjectInfo,
        config: DynatraceConfig,
        deselectedModules: List<ProjectDetectionService.ModuleInfo> = emptyList()
    ): String {
        val projectContent = projectInfo.projectBuildFile
            ?.let { String(it.contentsToByteArray(), StandardCharsets.UTF_8) } ?: ""
        val strippedProjectContent = stripComments(projectContent)
        val usePluginDsl = PLUGINS_BLOCK_REGEX.containsMatchIn(strippedProjectContent)
        // "Mixed" = root file has both a `plugins {}` block (for other plugins) AND the
        // Dynatrace buildscript classpath entry → apply(plugin=…) + configure<> at root.
        val hasDynatraceClasspath = strippedProjectContent.contains(DYNATRACE_MAVEN_ARTIFACT)
        val isMixedApproach = usePluginDsl && hasDynatraceClasspath
        val isKts = projectInfo.isKotlinDsl
        // Choose the correct block form for the preview.
        val block = when {
            isMixedApproach && isKts -> buildConfigureExtensionBlockKts(config)
            isKts -> buildDynatraceBlockKts(config)
            else -> buildDynatraceBlockGroovy(config)
        }

        return buildString {
            appendLine("=== Changes to be applied ===")
            appendLine("Setup flow: ${projectInfo.setupFlow.title}\n")

            when (projectInfo.setupFlow) {
                SetupFlow.FEATURE_MODULES -> {
                    // Same changes as single-app; feature/library modules need nothing
                    when {
                        isMixedApproach && projectInfo.projectBuildFile != null -> {
                            appendLine("📄 ${projectInfo.projectBuildFile.path}")
                            appendLine("  → Add classpath(\"$DYNATRACE_CLASSPATH\") to buildscript dependencies")
                            appendLine("  → Add apply(plugin = \"$DYNATRACE_PLUGIN_ID\") after plugins {} block")
                            appendLine("  → Add configure<DynatraceExtension> {} configuration block:")
                            appendLine()
                            block.lines().forEach { appendLine("    $it") }
                        }

                        usePluginDsl && projectInfo.projectBuildFile != null -> {
                            appendLine("📄 ${projectInfo.projectBuildFile.path}")
                            appendLine("  → Add id(\"$DYNATRACE_PLUGIN_ID\") version \"$DYNATRACE_PLUGIN_VERSION\" to plugins {} block")
                            appendLine("  → Add dynatrace {} configuration block:")
                            appendLine()
                            block.lines().forEach { appendLine("    $it") }
                        }

                        else -> {
                            projectInfo.projectBuildFile?.let {
                                appendLine("📄 ${it.path}")
                                appendLine("  → Add classpath(\"$DYNATRACE_CLASSPATH\") to buildscript dependencies")
                                appendLine()
                            }
                            projectInfo.appBuildFile?.let {
                                appendLine("📄 ${it.path}")
                                appendLine("  → Apply Dynatrace plugin ($DYNATRACE_PLUGIN_ID)")
                                appendLine("  → Add dynatrace {} configuration block:")
                                appendLine()
                                block.lines().forEach { appendLine("    $it") }
                            }
                        }
                    }
                    val untouched = (projectInfo.featureModules + projectInfo.libraryModules)
                        .joinToString(", ") { it.name }
                    if (untouched.isNotEmpty()) {
                        appendLine()
                        appendLine("ℹ️  No changes to: $untouched")
                        appendLine("   Dynamic feature and library modules are instrumented automatically.")
                    }
                }

                SetupFlow.MULTI_APP -> {
                    // Respect projectInfo.usesPluginDsl — it was overridden by the user's
                    // approach choice on the Modules tab before the preview was requested.
                    if (projectInfo.usesPluginDsl && projectInfo.projectBuildFile != null) {
                        // Plugin DSL: coordinator at root + dynatrace block; app modules untouched
                        val rootContent = try {
                            stripComments(
                                String(
                                    projectInfo.projectBuildFile.contentsToByteArray(),
                                    StandardCharsets.UTF_8
                                )
                            )
                        } catch (_: Exception) {
                            ""
                        }
                        appendLine("📄 ${projectInfo.projectBuildFile.path}")
                        if (rootContent.contains(DYNATRACE_MAVEN_ARTIFACT)) {
                            appendLine("  🧹 Remove classpath(\"$DYNATRACE_CLASSPATH\") from buildscript dependencies")
                        }
                        appendLine("  → Add id(\"$DYNATRACE_PLUGIN_ID\") version \"$DYNATRACE_PLUGIN_VERSION\" to plugins {} block")
                        appendLine("  → Add dynatrace {} configuration block:")
                        appendLine()
                        block.lines().forEach { appendLine("    $it") }
                        appendLine()
                        appendLine("ℹ️  Plugin DSL coordinator at root instruments all app modules")
                        appendLine("   automatically — no changes to individual app module build files.")
                        if (projectInfo.appModules.any { m ->
                                try {
                                    stripComments(String(m.buildFile.contentsToByteArray(), StandardCharsets.UTF_8))
                                        .contains(DYNATRACE_MODULE_PLUGIN_ID)
                                } catch (_: Exception) {
                                    false
                                }
                            }) {
                            appendLine()
                            appendLine("🧹 Cleanup: com.dynatrace.instrumentation.module will be removed")
                            appendLine("   from app module build files (no longer needed with Plugin DSL).")
                        }
                    } else {
                        // Classpath: add classpath to root + module plugin to each app module
                        projectInfo.projectBuildFile?.let {
                            appendLine("📄 ${it.path}")
                            appendLine("  → Add classpath(\"$DYNATRACE_CLASSPATH\") to buildscript dependencies")
                            appendLine()
                        }
                        projectInfo.appModules.forEach { m ->
                            val moduleConfig = config.moduleCredentials[m.name]?.let {
                                config.copy(applicationId = it.appId, beaconUrl = it.beaconUrl)
                            } ?: config
                            val moduleBlock =
                                if (isKts) buildDynatraceBlockKts(moduleConfig) else buildDynatraceBlockGroovy(
                                    moduleConfig
                                )
                            val moduleContent = try {
                                stripComments(String(m.buildFile.contentsToByteArray(), StandardCharsets.UTF_8))
                            } catch (_: Exception) {
                                ""
                            }
                            val moduleHasPluginsBlock = PLUGINS_BLOCK_REGEX.containsMatchIn(moduleContent)
                            appendLine("📄 ${m.buildFile.path}")
                            val pluginLine = when {
                                moduleHasPluginsBlock && isKts ->
                                    "id(\"$DYNATRACE_MODULE_PLUGIN_ID\") inside plugins {} block"

                                moduleHasPluginsBlock ->
                                    "id '$DYNATRACE_MODULE_PLUGIN_ID' inside plugins {} block"

                                isKts -> "apply(plugin = \"$DYNATRACE_MODULE_PLUGIN_ID\")"
                                else -> "apply plugin: '$DYNATRACE_MODULE_PLUGIN_ID'"
                            }
                            appendLine("  → Add: $pluginLine")
                            if (config.moduleCredentials[m.name] != null) {
                                appendLine("  ℹ️  Module-specific credentials:")
                                appendLine("       App ID:     ${moduleConfig.applicationId}")
                                appendLine("       Beacon URL: ${moduleConfig.beaconUrl}")
                            }
                            appendLine("  → Add dynatrace {} configuration block:")
                            appendLine()
                            moduleBlock.lines().forEach { appendLine("    $it") }
                            appendLine()
                        }
                        // Show cleanup section for deselected modules that were previously instrumented
                        val modulesToClean = deselectedModules.filter { m ->
                            try {
                                stripComments(String(m.buildFile.contentsToByteArray(), StandardCharsets.UTF_8))
                                    .let { it.contains(DYNATRACE_MODULE_PLUGIN_ID) || it.contains("dynatrace {") }
                            } catch (_: Exception) {
                                false
                            }
                        }
                        if (modulesToClean.isNotEmpty()) {
                            appendLine("🧹 Cleanup — Dynatrace will be removed from deselected modules:")
                            modulesToClean.forEach { m ->
                                appendLine("   📄 ${m.buildFile.path}")
                                appendLine("      → Remove $DYNATRACE_MODULE_PLUGIN_ID plugin declaration")
                                appendLine("      → Remove dynatrace {} block")
                            }
                            appendLine()
                        }
                    }
                }

                else -> {
                    // SINGLE_APP / SINGLE_BUILD_FILE / UNKNOWN
                    when {
                        isMixedApproach && projectInfo.projectBuildFile != null -> {
                            appendLine("📄 ${projectInfo.projectBuildFile.path}")
                            appendLine("  → Add classpath(\"$DYNATRACE_CLASSPATH\") to buildscript dependencies")
                            appendLine("  → Add apply(plugin = \"$DYNATRACE_PLUGIN_ID\") after plugins {} block")
                            appendLine("  → Add configure<DynatraceExtension> {} configuration block:")
                            appendLine()
                            block.lines().forEach { appendLine("    $it") }
                        }

                        usePluginDsl && projectInfo.projectBuildFile != null -> {
                            appendLine("📄 ${projectInfo.projectBuildFile.path}")
                            appendLine("  → Add id(\"$DYNATRACE_PLUGIN_ID\") version \"$DYNATRACE_PLUGIN_VERSION\" to plugins {} block")
                            appendLine("  → Add dynatrace {} configuration block:")
                            appendLine()
                            block.lines().forEach { appendLine("    $it") }
                        }

                        else -> {
                            projectInfo.projectBuildFile?.let {
                                appendLine("📄 ${it.path}")
                                appendLine("  → Add classpath(\"$DYNATRACE_CLASSPATH\") to buildscript dependencies")
                                appendLine()
                            }
                            projectInfo.appBuildFile?.let {
                                appendLine("📄 ${it.path}")
                                appendLine("  → Apply Dynatrace plugin ($DYNATRACE_PLUGIN_ID)")
                                appendLine("  → Add dynatrace {} configuration block:")
                                appendLine()
                                block.lines().forEach { line -> appendLine("    $line") }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Legacy overload kept for any callers that pass individual files. */
    fun generateChangePreview(
        projectBuildFile: VirtualFile?,
        appBuildFile: VirtualFile?,
        isKotlinDsl: Boolean,
        config: DynatraceConfig
    ): String {
        val projectContent = projectBuildFile
            ?.let { String(it.contentsToByteArray(), StandardCharsets.UTF_8) } ?: ""
        val strippedContent = stripComments(projectContent)
        val usePluginDsl = PLUGINS_BLOCK_REGEX.containsMatchIn(strippedContent)
        val hasDynatraceClasspath = strippedContent.contains(DYNATRACE_MAVEN_ARTIFACT)
        val isMixedApproach = usePluginDsl && hasDynatraceClasspath
        return buildString {
            appendLine("=== Changes to be applied ===\n")
            when {
                isMixedApproach && projectBuildFile != null -> {
                    val block =
                        if (isKotlinDsl) buildConfigureExtensionBlockKts(config) else buildDynatraceBlockGroovy(config)
                    appendLine("📄 ${projectBuildFile.path}")
                    appendLine("  → Add classpath(\"$DYNATRACE_CLASSPATH\") to buildscript dependencies")
                    appendLine("  → Add apply(plugin = \"$DYNATRACE_PLUGIN_ID\") after plugins {} block")
                    appendLine("  → Add configure<DynatraceExtension> {} configuration block:")
                    appendLine()
                    block.lines().forEach { appendLine("    $it") }
                }

                usePluginDsl && projectBuildFile != null -> {
                    val block = if (isKotlinDsl) buildDynatraceBlockKts(config) else buildDynatraceBlockGroovy(config)
                    appendLine("📄 ${projectBuildFile.path}")
                    appendLine("  → Add id(\"$DYNATRACE_PLUGIN_ID\") version \"$DYNATRACE_PLUGIN_VERSION\" to plugins {} block")
                    appendLine("  → Add dynatrace {} configuration block:")
                    appendLine()
                    block.lines().forEach { appendLine("    $it") }
                }

                else -> {
                    val block = if (isKotlinDsl) buildDynatraceBlockKts(config) else buildDynatraceBlockGroovy(config)
                    projectBuildFile?.let {
                        appendLine("📄 ${it.path}")
                        appendLine("  → Add classpath(\"$DYNATRACE_CLASSPATH\") to buildscript dependencies")
                        appendLine()
                    }
                    appBuildFile?.let {
                        appendLine("📄 ${it.path}")
                        appendLine("  → Apply Dynatrace plugin ($DYNATRACE_PLUGIN_ID)")
                        appendLine("  → Add dynatrace {} configuration block:")
                        appendLine()
                        block.lines().forEach { appendLine("    $it") }
                    }
                }
            }
        }
    }
}

