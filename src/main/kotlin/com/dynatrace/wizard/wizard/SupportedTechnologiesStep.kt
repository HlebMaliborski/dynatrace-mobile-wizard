package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.service.ProjectDetectionService
import com.dynatrace.wizard.util.DocumentationLinks
import com.dynatrace.wizard.util.WizardColors
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Step 4 of the wizard — Supported Technologies (simplified).
 *
 * Shows only the version-gated technologies relevant to Dynatrace Android instrumentation:
 *  - Android SDK (API 21+, compileSdk, minSdk — ANR/native-crash note when minSdk < 30)
 *  - Gradle Wrapper (min 7.0.2)
 *  - Android Gradle Plugin (min 7.0)
 *  - Java (min 11)
 *  - Kotlin (1.8 – 2.2 — ~2 versions back/forward from 2.0.21 baseline)
 *  - Jetpack Compose (1.4 – 1.10)
 *  - OkHttp (v3+)
 *
 * Additionally scans for known competing instrumentation / monitoring plugins and
 * renders a warning banner with links to the Dynatrace compatibility documentation.
 */
class SupportedTechnologiesStep {

    // ── Domain model ──────────────────────────────────────────────────────────

    private enum class SupportType { BUILT_IN, AUTO }

    private data class TechItem(
        val name: String,
        val versionLabel: String,
        val type: SupportType,
        val artifacts: List<String> = emptyList(),
        val minVersion: String? = null,
        val maxVersion: String? = null,
        val detectionId: String? = null,
        val note: String = ""
    )

    /** @param detectedVersion null = absent / unknown · "BOM …" = BOM-managed */
    private data class DetectionResult(val detectedVersion: String?, val inRange: Boolean)

    /** A competing monitoring / instrumentation plugin found in the project. */
    private data class ConflictingPlugin(val name: String, val identifier: String)

    // ── Catalog ───────────────────────────────────────────────────────────────

    private val CATALOG = listOf(
        TechItem(
            name = "Android SDK (compileSdk / minSdk)",
            versionLabel = "API 21+ (minSdk)",
            type = SupportType.BUILT_IN,
            detectionId = "API_LEVEL",
            note = "Minimum supported minSdk: API 21. ANR & native-crash reporting require API 30+ (Android 11)."
        ),
        TechItem(
            name = "Gradle Wrapper",
            versionLabel = "7.0.2+",
            type = SupportType.AUTO,
            detectionId = "GRADLE_WRAPPER",
            minVersion = "7.0.2",
            note = "Detected from gradle-wrapper.properties"
        ),
        TechItem(
            name = "Android Gradle Plugin",
            versionLabel = "7.0+",
            type = SupportType.AUTO,
            detectionId = "AGP",
            minVersion = "7.0.0",
            note = "Required for Dynatrace plugin 8.x compatibility"
        ),
        TechItem(
            name = "Java",
            versionLabel = "11+",
            type = SupportType.AUTO,
            detectionId = "JAVA",
            minVersion = "11",
            note = "Source/target compatibility must be Java 11 or higher"
        ),
        TechItem(
            name = "Kotlin",
            // ~2 versions back (1.8, 1.9) and ~2 forward (2.1, 2.2) from baseline 2.0.21
            versionLabel = "1.8 – 2.2",
            type = SupportType.AUTO,
            detectionId = "KOTLIN",
            minVersion = "1.8.0",
            maxVersion = "2.2.99",
            note = "Required for Coroutines and Compose instrumentation"
        ),
        TechItem(
            name = "Jetpack Compose",
            versionLabel = "1.4 – 1.10",
            type = SupportType.AUTO,
            artifacts = listOf("androidx.compose.ui", "compose-ui", "compose.ui"),
            detectionId = "COMPOSE",
            minVersion = "1.4.0",
            maxVersion = "1.10.99",
            note = "Clickable, swipeable, slider, pager and pull-refresh auto-instrumented"
        ),
        TechItem(
            name = "OkHttp",
            versionLabel = "3, 4 or 5",
            type = SupportType.AUTO,
            artifacts = listOf("okhttp3:okhttp", "squareup.okhttp3", "com.squareup.okhttp3"),
            minVersion = "3.0.0",
            maxVersion = "5.99.99",
            note = "HTTP requests auto-instrumented (includes Retrofit 2)"
        )
    )

    // ── Competing-plugin registry ─────────────────────────────────────────────

    /**
     * Known performance-monitoring / bytecode-instrumentation plugins that may conflict
     * with Dynatrace auto-instrumentation.
     * Each entry: (display name, substring searched in the full project content).
     */
    private val CONFLICTING_PLUGIN_SIGNATURES = listOf(
        ConflictingPlugin("Firebase Performance Monitoring",  "com.google.firebase.firebase-perf"),
        ConflictingPlugin("Firebase Performance Monitoring",  "firebase-performance"),
        ConflictingPlugin("New Relic",                        "com.newrelic.agent.android"),
        ConflictingPlugin("New Relic",                        "newrelic-android"),
        ConflictingPlugin("Datadog",                          "com.datadoghq:dd-sdk-android"),
        ConflictingPlugin("Datadog",                          "com.datadog.android"),
        ConflictingPlugin("AppDynamics",                      "com.appdynamics"),
        ConflictingPlugin("AppDynamics",                      "appdynamics"),
        ConflictingPlugin("Sentry",                           "io.sentry.android"),
        ConflictingPlugin("Sentry",                           "io.sentry:sentry-android"),
        ConflictingPlugin("Bugsnag",                          "com.bugsnag.android"),
        ConflictingPlugin("Instabug",                         "com.instabug"),
        ConflictingPlugin("AppCenter",                        "com.microsoft.appcenter"),
        ConflictingPlugin("Embrace",                          "io.embrace"),
        ConflictingPlugin("Adjust",                           "com.adjust.sdk"),
    )

    // ── Pure detection utilities — companion so they are directly unit-testable ─

    companion object Detection {

        internal fun parseVersion(v: String): IntArray? {
            val clean = v.trimStart('v').trimEnd('+')
            val parts = clean.split(".").mapNotNull { it.toIntOrNull() }
            return if (parts.isEmpty()) null else parts.toIntArray()
        }

        internal fun compareVersions(a: IntArray, b: IntArray): Int {
            val len = maxOf(a.size, b.size)
            for (i in 0 until len) {
                val diff = (if (i < a.size) a[i] else 0) - (if (i < b.size) b[i] else 0)
                if (diff != 0) return diff
            }
            return 0
        }

        internal fun isVersionInRange(version: String, min: String?, max: String?): Boolean {
            val v = parseVersion(version) ?: return true
            if (min != null) { val m = parseVersion(min) ?: return true; if (compareVersions(v, m) < 0) return false }
            if (max != null) { val m = parseVersion(max) ?: return true; if (compareVersions(v, m) > 0) return false }
            return true
        }

        internal fun extractVersion(artifacts: List<String>, content: String): String? {
            val vp = """([\d]+\.[\d]+(?:\.[\d]+)?)"""
            for (f in artifacts) {
                Regex("""${Regex.escape(f)}[^"'\n\r]*:$vp""").find(content)?.groupValues?.get(1)?.let { return it }
            }
            for (f in artifacts) {
                val ref = Regex(
                    """${Regex.escape(f)}[^}]{0,300}?version\.ref\s*=\s*["'](\w[\w.-]*)["']""",
                    RegexOption.DOT_MATCHES_ALL
                ).find(content)?.groupValues?.get(1) ?: continue
                Regex("""(?:^|\n)\s*${Regex.escape(ref)}\s*=\s*["']$vp["']""")
                    .find(content)?.groupValues?.get(1)?.let { return it }
            }
            for (f in artifacts) {
                Regex("""${Regex.escape(f)}["')\s]+version\s+["']$vp["']""")
                    .find(content)?.groupValues?.get(1)?.let { return it }
            }
            return null
        }

        internal fun extractVersionByDetectionId(id: String, content: String): String? = when (id) {
            "AGP"            -> extractAgpVersion(content)
            "KOTLIN"         -> extractKotlinVersion(content)
            "COMPOSE"        -> extractComposeVersion(content)
            "API_LEVEL"      -> extractApiLevel(content)
            "GRADLE_WRAPPER" -> extractGradleWrapperVersion(content)
            "JAVA"           -> extractJavaVersion(content)
            else             -> null
        }

        internal fun extractAgpVersion(content: String): String? {
            val v = """([\d]+\.[\d]+(?:\.[\d]+)?)"""
            Regex("""com\.android\.tools\.build:gradle[^:\n"']*:$v""").find(content)?.groupValues?.get(1)?.let { return it }
            Regex("""com\.android\.application["')\s]+version\s+["']$v""").find(content)?.groupValues?.get(1)?.let { return it }
            val ref = Regex("""com\.android\.application[^}]{0,200}?version\.ref\s*=\s*["'](\w[\w.-]*)["']""", RegexOption.DOT_MATCHES_ALL)
                .find(content)?.groupValues?.get(1)
            if (ref != null) Regex("""(?:^|\n)\s*${Regex.escape(ref)}\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
            return null
        }

        internal fun extractKotlinVersion(content: String): String? {
            val v = """([\d]+\.[\d]+(?:\.[\d]+)?)"""
            Regex("""kotlin-gradle-plugin[^:\n"']*:$v""").find(content)?.groupValues?.get(1)?.let { return it }
            Regex("""org\.jetbrains\.kotlin\.(?:android|jvm|multiplatform)[^\n"']*version\s+["']$v""").find(content)?.groupValues?.get(1)?.let { return it }
            Regex("""kotlin\s*\(\s*["']\w+["']\s*\)\s*version\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
            Regex("""(?:^|\n)\s*kotlin\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
            val refByPlugin = Regex(
                """id\s*=\s*["']org\.jetbrains\.kotlin[^"']*["'][^}]{0,200}?version\.ref\s*=\s*["'](\w[\w.-]*)["']""",
                RegexOption.DOT_MATCHES_ALL
            ).find(content)?.groupValues?.get(1)
            if (refByPlugin != null)
                Regex("""(?:^|\n)\s*${Regex.escape(refByPlugin)}\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
            if (Regex("""version\.ref\s*=\s*["']kotlin["']""").containsMatchIn(content))
                Regex("""(?:^|\n)\s*kotlin\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
            Regex("""(?:^|\n)\s*kotlin[-_]?[Vv]ersion\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
            return null
        }

        internal fun extractComposeVersion(content: String): String? {
            val v = """([\d]+\.[\d]+(?:\.[\d]+)?)"""
            for (f in listOf("androidx.compose.ui", "compose-ui", "compose.ui")) {
                Regex("""${Regex.escape(f)}[^"'\n\r]*:$v""").find(content)?.groupValues?.get(1)?.let { return it }
            }
            val uiRef = Regex("""androidx\.compose\.ui[^}]{0,300}?version\.ref\s*=\s*["'](\w[\w.-]*)["']""", RegexOption.DOT_MATCHES_ALL)
                .find(content)?.groupValues?.get(1)
            if (uiRef != null)
                Regex("""(?:^|\n)\s*${Regex.escape(uiRef)}\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
            if (!content.contains("androidx.compose") && !content.contains("compose-ui") && !content.contains("compose.ui")) return null
            Regex("""compose-bom[^:\n"']*:([^"'\n\r\s]+)""").find(content)?.groupValues?.get(1)?.let { return "BOM $it" }
            val bomRef = Regex("""compose-bom[^}\n]{0,200}?version\.ref\s*=\s*["'](\w[\w.-]*)["']""")
                .find(content)?.groupValues?.get(1)
            if (bomRef != null)
                Regex("""(?:^|\n)\s*${Regex.escape(bomRef)}\s*=\s*["']([^"'\n]+)["']""").find(content)?.groupValues?.get(1)?.let { return "BOM $it" }
            if (Regex("""version\.ref\s*=\s*["']compose["']""").containsMatchIn(content))
                Regex("""(?:^|\n)\s*compose\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
            return null
        }

        internal fun extractApiLevel(content: String): String? {
            fun findInt(key: String) =
                Regex("""(?<!\w)$key\s*[=()\s]*(\d{1,3})(?!\.)""").find(content)?.groupValues?.get(1)?.toIntOrNull()
            val compile = findInt("compileSdk")
            val min     = findInt("minSdk")
            val target  = findInt("targetSdk")
            return listOfNotNull(
                compile?.let { "compileSdk $it" },
                min?.let     { "minSdk $it" },
                target?.let  { "targetSdk $it" }
            ).joinToString(" · ").takeIf { it.isNotEmpty() }
        }

        internal fun extractGradleWrapperVersion(content: String): String? =
            Regex("""distributionUrl[^\n]*?gradle-([\d.]+)-""").find(content)?.groupValues?.get(1)

        internal fun extractJavaVersion(content: String): String? {
            Regex("""JavaVersion\.VERSION_(\d+)""").find(content)?.groupValues?.get(1)?.let { return it }
            Regex("""jvmTarget\s*[=(]\s*["'](\d+)["']""").find(content)?.groupValues?.get(1)?.let { return it }
            Regex("""JavaLanguageVersion\.of\s*\(\s*(\d+)\s*\)""").find(content)?.groupValues?.get(1)?.let { return it }
            Regex("""sourceCompatibility\s*[=()\s]*(\d+)""").find(content)?.groupValues?.get(1)?.let { return it }
            return null
        }

        /** Returns true when the detected minSdk is below API 30 (Android 11). */
        internal fun hasAnrWarning(content: String): Boolean {
            val minSdk = Regex("""(?<!\w)minSdk[Vv]?ersion?\s*[=()\s]*(\d{1,3})(?!\.)""")
                .find(content)?.groupValues?.get(1)?.toIntOrNull() ?: return false
            return minSdk < 30
        }

        /**
         * Scans [content] for known competing instrumentation plugins.
         * Returns distinct matches (by name).
         */
        internal fun detectConflictingPluginNames(
            signatures: List<Pair<String, String>>,
            content: String
        ): List<String> =
            signatures.filter { (_, id) -> content.contains(id) }
                      .map { (name, _) -> name }
                      .distinct()

    } // end companion object Detection

    // ── Project content ───────────────────────────────────────────────────────

    private fun buildProjectContent(info: ProjectDetectionService.ProjectInfo): String =
        buildString {
            info.allModules.forEach { m ->
                try { append(String(m.buildFile.contentsToByteArray())) } catch (_: Exception) { }
            }
            info.projectBuildFile?.let { f ->
                try { append(String(f.contentsToByteArray())) } catch (_: Exception) { }
                val root = f.parent ?: return@let
                root.findChild("gradle")?.findChild("libs.versions.toml")?.let { toml ->
                    try { append(String(toml.contentsToByteArray())) } catch (_: Exception) { }
                }
                root.findChild("gradle")?.findChild("wrapper")
                    ?.findChild("gradle-wrapper.properties")?.let { props ->
                        try { append(String(props.contentsToByteArray())) } catch (_: Exception) { }
                    }
            }
        }

    // ── Instance detection ────────────────────────────────────────────────────

    private fun detectItem(item: TechItem, content: String): DetectionResult {
        return when (item.type) {
            SupportType.BUILT_IN -> {
                val detected = item.detectionId?.let { extractVersionByDetectionId(it, content) }
                DetectionResult(detected, true)
            }
            SupportType.AUTO -> {
                val version = if (item.detectionId != null) {
                    extractVersionByDetectionId(item.detectionId, content)
                        ?: if (item.artifacts.isNotEmpty() && item.artifacts.any { content.contains(it) })
                            extractVersion(item.artifacts, content) ?: "BOM"
                           else return DetectionResult(null, false)
                } else {
                    if (item.artifacts.isEmpty() || item.artifacts.none { content.contains(it) })
                        return DetectionResult(null, false)
                    extractVersion(item.artifacts, content) ?: "BOM"
                }
                if (version == "BOM" || version.startsWith("BOM ")) return DetectionResult(version, true)
                DetectionResult(version, isVersionInRange(version, item.minVersion, item.maxVersion))
            }
        }
    }

    private fun detectConflictingPlugins(content: String): List<String> =
        detectConflictingPluginNames(
            CONFLICTING_PLUGIN_SIGNATURES.map { it.name to it.identifier },
            content
        )

    // ── Panel ─────────────────────────────────────────────────────────────────

    fun createPanel(info: ProjectDetectionService.ProjectInfo): JComponent {
        val content   = buildProjectContent(info)
        val builder   = FormBuilder.createFormBuilder()
        val conflicts = detectConflictingPlugins(content)
        val anrWarn   = hasAnrWarning(content)

        builder.addComponent(
            JBLabel("Supported Technologies").apply {
                font       = JBUI.Fonts.label(16f).asBold()
                foreground = WizardColors.accent
                border     = JBUI.Borders.emptyBottom(2)
            }
        )
        builder.addComponent(
            JBLabel(
                "<html>Versions detected in your project vs. the Dynatrace-supported range.<br>" +
                "<b style='color:green'>Green</b> = compatible · " +
                "<b style='color:red'>Red</b> = unsupported version · " +
                "Grey = not detected in project.</html>"
            ).apply {
                foreground = UIUtil.getContextHelpForeground()
                border     = JBUI.Borders.emptyBottom(8)
            }
        )

        builder.addComponent(TitledSeparator("Version Requirements"))
        builder.addComponent(techGrid(CATALOG, content))

        if (anrWarn) {
            builder.addVerticalGap(6)
            builder.addComponent(noticePanel(
                "⚠  ANR and native-crash reporting require Android 11 (API 30+). " +
                "Devices running an older OS will not generate these events.",
                WizardColors.warning
            ))
        }

        if (conflicts.isNotEmpty()) {
            builder.addVerticalGap(10)
            builder.addComponent(TitledSeparator("⚠  Other Instrumentation Plugins Detected"))
            val names = conflicts.joinToString(", ")
            builder.addComponent(noticePanel(
                "The following performance-monitoring or instrumentation plugins were found in this project: " +
                "<b>$names</b>.<br><br>" +
                "Using multiple instrumentation plugins simultaneously may cause compatibility issues, " +
                "incorrect data, or build failures. " +
                "Test thoroughly or use only one plugin at a time.",
                WizardColors.warning
            ))
            builder.addVerticalGap(4)
            builder.addComponent(DocumentationLinks.createLinkLabel(
                "Build-specific limitations →", DocumentationLinks.BUILD_SPECIFIC_LIMITATIONS))
            builder.addComponent(DocumentationLinks.createLinkLabel(
                "Compatibility with other monitoring tools →", DocumentationLinks.COMPATIBILITY_MONITORING_TOOLS))
        }

        builder.addVerticalGap(8)
        builder.addComponent(TitledSeparator("Documentation"))
        builder.addComponent(DocumentationLinks.createLinkLabel(
            "Supported versions & limitations →", DocumentationLinks.SUPPORT_LIMITATIONS))
        builder.addComponent(DocumentationLinks.createLinkLabel(
            "Build-specific limitations →", DocumentationLinks.BUILD_SPECIFIC_LIMITATIONS))
        builder.addComponent(DocumentationLinks.createLinkLabel(
            "Compatibility with other monitoring tools →", DocumentationLinks.COMPATIBILITY_MONITORING_TOOLS))
        builder.addVerticalGap(8)

        return builder.panel.also { it.border = JBUI.Borders.empty(12, 16, 12, 16) }
    }

    // ── Grid builder ──────────────────────────────────────────────────────────

    private fun techGrid(items: List<TechItem>, content: String): JComponent {
        val grid = JPanel(GridLayout(0, 4, JBUI.scale(14), JBUI.scale(4)))
        grid.isOpaque = false
        grid.border   = JBUI.Borders.emptyLeft(4)

        listOf("Technology", "In Your Project", "Supported", "Status").forEach { h ->
            grid.add(JBLabel(h).apply {
                font       = JBUI.Fonts.label().asBold()
                foreground = UIUtil.getContextHelpForeground()
            })
        }

        items.forEach { item ->
            val result = detectItem(item, content)

            val (icon, statusText, statusColor, detectedText, isBold) = when {
                item.type == SupportType.BUILT_IN -> Row(
                    "✅", "Built-in", WizardColors.success,
                    result.detectedVersion ?: "—", result.detectedVersion != null
                )
                result.detectedVersion == null -> Row(
                    "💡", "Not in project", UIUtil.getContextHelpForeground(), "—", false
                )
                result.detectedVersion == "BOM" -> Row(
                    "✅", "Compatible (BOM)", WizardColors.success, "via BOM", true
                )
                result.detectedVersion.startsWith("BOM ") -> Row(
                    "✅", "Compatible (BOM)", WizardColors.success, result.detectedVersion, true
                )
                result.inRange -> Row(
                    "✅", "Compatible", WizardColors.success, result.detectedVersion, true
                )
                else -> Row(
                    "❌", "Unsupported version", WizardColors.error, result.detectedVersion, true
                )
            }

            grid.add(JBLabel("$icon  ${item.name}").apply {
                font = if (isBold) JBUI.Fonts.label().asBold() else JBUI.Fonts.label()
            })
            grid.add(JBLabel(detectedText).apply {
                font = if (isBold) JBUI.Fonts.label().asBold() else JBUI.Fonts.label()
                foreground = when {
                    item.type == SupportType.BUILT_IN && result.detectedVersion != null -> UIUtil.getLabelForeground()
                    result.detectedVersion == null -> UIUtil.getContextHelpForeground()
                    result.detectedVersion == "BOM" || result.detectedVersion.startsWith("BOM ") -> WizardColors.success
                    result.inRange -> WizardColors.success
                    else           -> WizardColors.error
                }
            })
            grid.add(JBLabel(item.versionLabel).apply {
                foreground = UIUtil.getContextHelpForeground()
            })
            grid.add(JBLabel(statusText).apply {
                foreground = statusColor
            })

            // Hint row: label in col 0, 3 blank fillers for cols 1–3
            if (!result.inRange && result.detectedVersion != null && item.type == SupportType.AUTO) {
                grid.add(JBLabel(
                    "<html><i>${item.note.ifBlank { "Check Dynatrace release notes for this version." }}</i></html>"
                ).apply {
                    font       = JBUI.Fonts.smallFont()
                    foreground = WizardColors.error
                    border     = JBUI.Borders.empty(0, JBUI.scale(24), 2, 0)
                })
                repeat(3) { grid.add(JPanel().apply { isOpaque = false }) }
            }
        }
        return grid
    }

    // ── Notice panel ──────────────────────────────────────────────────────────

    private fun noticePanel(html: String, borderColor: Color): JComponent {
        val label = JBLabel("<html>$html</html>").apply {
            border = JBUI.Borders.empty(6, 8, 6, 8)
        }
        return JPanel(BorderLayout()).apply {
            isOpaque   = true
            background = borderColor.let { Color(it.red, it.green, it.blue, 30) }
            border     = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1, true),
                JBUI.Borders.empty(2)
            )
            add(label, BorderLayout.CENTER)
        }
    }

    // ── Row helper ────────────────────────────────────────────────────────────

    private data class Row(
        val icon: String,
        val statusText: String,
        val statusColor: Color,
        val detectedText: String,
        val isBold: Boolean
    )
}
