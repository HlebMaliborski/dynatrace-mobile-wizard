package com.dynatrace.wizard.wizard

import com.dynatrace.wizard.service.ProjectDetectionService
import com.dynatrace.wizard.util.DocumentationLinks
import com.dynatrace.wizard.util.WizardColors
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Step 4 of the wizard — Supported Technologies.
 *
 * For each catalog entry the wizard:
 *  1. Scans build files + libs.versions.toml + gradle-wrapper.properties.
 *  2. Extracts the concrete version used in the project via artifact/TOML/plugin-block patterns
 *     or custom extraction (AGP, Kotlin, API level, Gradle Wrapper).
 *  3. For AUTO items: compares detected version against [TechItem.minVersion]/[TechItem.maxVersion].
 *     ✅ compatible · ❌ unsupported version · 💡 not in project
 *  4. For BUILT_IN items: always green; detected version shown for information only.
 */
class SupportedTechnologiesStep {

    // ── Domain model ──────────────────────────────────────────────────────────

    private enum class SupportType { BUILT_IN, AUTO }

    private data class TechItem(
        val name: String,
        /** Human-readable supported version range — shown in the "Supported" column. */
        val versionLabel: String,
        val type: SupportType,
        /** Artifact substrings used for presence/version detection (standard path). */
        val artifacts: List<String> = emptyList(),
        /** Inclusive minimum supported version (null = no lower bound). */
        val minVersion: String? = null,
        /** Inclusive maximum supported version (null = no upper bound). */
        val maxVersion: String? = null,
        /**
         * When set, overrides the standard artifact-scan with a named extraction function.
         * Values: "AGP", "KOTLIN", "API_LEVEL", "GRADLE_WRAPPER".
         */
        val detectionId: String? = null,
        val note: String = ""
    )

    private data class TechCategory(val title: String, val items: List<TechItem>)

    /** @param detectedVersion null = absent / unknown · "BOM" = present but version via BOM */
    private data class DetectionResult(val detectedVersion: String?, val inRange: Boolean)

    // ── Catalog ───────────────────────────────────────────────────────────────

    private val SUPPORTED_CATALOG = listOf(

        TechCategory("Build & Toolchain", listOf(
            TechItem("Android Gradle Plugin", "8.0+", SupportType.AUTO,
                detectionId = "AGP",
                minVersion  = "8.0.0",
                note = "Required for Dynatrace plugin compatibility"),
            TechItem("Kotlin", "1.6+", SupportType.AUTO,
                detectionId = "KOTLIN",
                minVersion  = "1.6.0",
                note = "Required for Coroutines + Compose instrumentation"),
            TechItem("Gradle Wrapper", "Informational", SupportType.BUILT_IN,
                detectionId = "GRADLE_WRAPPER",
                note = "Detected from gradle-wrapper.properties"),
            TechItem("Android SDK (compileSdk / minSdk)", "API 21+ (minSdk)", SupportType.BUILT_IN,
                detectionId = "API_LEVEL",
                note = "Minimum supported minSdk: API 21 (Android 5.0)"),
        )),

        TechCategory("HTTP & Networking", listOf(
            TechItem("HttpURLConnection", "All", SupportType.BUILT_IN,
                note = "HTTPS supported — built into Android"),
            TechItem("OkHttp", "v3+", SupportType.AUTO,
                artifacts   = listOf("okhttp3:okhttp", "squareup.okhttp3"),
                minVersion  = "3.0.0",
                note = "HTTPS supported — includes Retrofit 2"),
            TechItem("Retrofit", "2.+", SupportType.AUTO,
                artifacts  = listOf("retrofit2:retrofit", "squareup.retrofit2"),
                minVersion = "2.0.0",
                note = "Auto-instrumented via OkHttp layer"),
            TechItem("Apache HTTP Client", "Android internal only", SupportType.BUILT_IN,
                note = "External Apache library is NOT auto-instrumented"),
        )),

        TechCategory("Jetpack & UI Frameworks", listOf(
            TechItem("Jetpack Compose", "1.4 – 1.10", SupportType.AUTO,
                artifacts  = listOf("androidx.compose.ui", "compose-ui", "compose.ui"),
                detectionId = "COMPOSE",
                minVersion = "1.4.0",
                maxVersion = "1.10.99",
                note = "Clickable, swipeable, sliders, pager, pull-to-refresh"),
            TechItem("Android Views / Activities", "All", SupportType.BUILT_IN,
                note = "App lifecycle, user action sensors, page changes"),
        )),

        TechCategory("Async Execution", listOf(
            TechItem("Kotlin Coroutines", "1.10.2 – 2.1", SupportType.AUTO,
                artifacts  = listOf("kotlinx-coroutines"),
                minVersion = "1.10.2",
                maxVersion = "2.1.99",
                note = "Coroutine-based async flows traced"),
        )),

        TechCategory("Crash & Exception Handling", listOf(
            TechItem("Java / Kotlin exceptions", "All", SupportType.BUILT_IN,
                note = "Uncaught exceptions, full stack traces, background threads"),
        )),

        TechCategory("Hybrid WebView Monitoring", listOf(
            TechItem("Android WebView", "All", SupportType.BUILT_IN,
                note = "Session merging via cookies, HTTPS domains, secure cookie injection"),
        )),

        TechCategory("Location Monitoring", listOf(
            TechItem("App-provided location", "All", SupportType.BUILT_IN,
                note = "Precision truncated to ~1 km — OneAgent does not request GPS permissions"),
        )),

        TechCategory("Behavioral Events", listOf(
            TechItem("Rage tap detection", "All", SupportType.BUILT_IN,
                note = "Repeated tapping on Activities"),
        )),

        TechCategory("Privacy", listOf(
            TechItem("Opt-in mode (userOptIn)", "All", SupportType.BUILT_IN,
                note = "Data sent only after user consent"),
            TechItem("Name privacy masking (namePrivacy)", "All", SupportType.BUILT_IN,
                note = "Auto-generated action names replaced with safe variants"),
        )),
    )

    // ── Version utilities ─────────────────────────────────────────────────────

    private fun parseVersion(v: String): IntArray? {
        val clean = v.trimStart('v').trimEnd('+')
        val parts = clean.split(".").mapNotNull { it.toIntOrNull() }
        return if (parts.isEmpty()) null else parts.toIntArray()
    }

    private fun compareVersions(a: IntArray, b: IntArray): Int {
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val diff = (if (i < a.size) a[i] else 0) - (if (i < b.size) b[i] else 0)
            if (diff != 0) return diff
        }
        return 0
    }

    private fun isVersionInRange(version: String, min: String?, max: String?): Boolean {
        val v = parseVersion(version) ?: return true
        if (min != null) { val minV = parseVersion(min) ?: return true; if (compareVersions(v, minV) < 0) return false }
        if (max != null) { val maxV = parseVersion(max) ?: return true; if (compareVersions(v, maxV) > 0) return false }
        return true
    }

    // ── Project content ───────────────────────────────────────────────────────

    /** Concatenates all Gradle files + version catalog + gradle-wrapper.properties. */
    private fun buildProjectContent(info: ProjectDetectionService.ProjectInfo): String =
        buildString {
            info.allModules.forEach { m ->
                try { append(String(m.buildFile.contentsToByteArray())) } catch (_: Exception) { }
            }
            info.projectBuildFile?.let { f ->
                try { append(String(f.contentsToByteArray())) } catch (_: Exception) { }
                val root = f.parent ?: return@let
                // libs.versions.toml
                root.findChild("gradle")?.findChild("libs.versions.toml")?.let { toml ->
                    try { append(String(toml.contentsToByteArray())) } catch (_: Exception) { }
                }
                // gradle-wrapper.properties (Gradle version)
                root.findChild("gradle")?.findChild("wrapper")
                    ?.findChild("gradle-wrapper.properties")?.let { props ->
                        try { append(String(props.contentsToByteArray())) } catch (_: Exception) { }
                    }
            }
        }

    // ── Standard version extraction ───────────────────────────────────────────

    /**
     * Tries to extract a version string for [artifacts] from [content].
     *
     * Strategy 1 — inline notation:         `"group:artifact:1.2.3"`
     * Strategy 2 — TOML version.ref:        `{ group = "…", version.ref = "alias" }` + `alias = "1.2.3"`
     * Strategy 3 — plugins {} version kw:   `id("plugin.id") version "1.2.3"`
     *
     * Returns null when the artifact is present but no explicit version is resolvable
     * (likely managed by a BOM/platform dependency or version catalog without matching alias).
     */
    private fun extractVersion(artifacts: List<String>, content: String): String? {
        val versionPattern = """([\d]+\.[\d]+(?:\.[\d]+)?)"""

        // Strategy 1: "group:artifact:1.2.3"
        for (f in artifacts) {
            Regex("""${Regex.escape(f)}[^"'\n\r]*:$versionPattern""")
                .find(content)?.groupValues?.get(1)?.let { return it }
        }
        // Strategy 2: TOML version.ref
        for (f in artifacts) {
            val ref = Regex(
                """${Regex.escape(f)}[^}]{0,300}?version\.ref\s*=\s*["'](\w[\w.-]*)["']""",
                RegexOption.DOT_MATCHES_ALL
            ).find(content)?.groupValues?.get(1) ?: continue
            Regex("""(?:^|\n)\s*${Regex.escape(ref)}\s*=\s*["']$versionPattern["']""")
                .find(content)?.groupValues?.get(1)?.let { return it }
        }
        // Strategy 3: id("plugin.id") version "1.2.3"
        for (f in artifacts) {
            Regex("""${Regex.escape(f)}["')\s]+version\s+["']$versionPattern["']""")
                .find(content)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    // ── Custom / named extraction functions ───────────────────────────────────

    private fun extractVersionByDetectionId(id: String, content: String): String? = when (id) {
        "AGP"            -> extractAgpVersion(content)
        "KOTLIN"         -> extractKotlinVersion(content)
        "COMPOSE"        -> extractComposeVersion(content)
        "API_LEVEL"      -> extractApiLevel(content)
        "GRADLE_WRAPPER" -> extractGradleWrapperVersion(content)
        else             -> null
    }

    private fun extractAgpVersion(content: String): String? {
        val v = """([\d]+\.[\d]+(?:\.[\d]+)?)"""
        // Classpath: classpath("com.android.tools.build:gradle:8.1.0")
        Regex("""com\.android\.tools\.build:gradle[^:\n"']*:$v""").find(content)?.groupValues?.get(1)?.let { return it }
        // Plugin DSL: id("com.android.application") version "8.1.0"
        Regex("""com\.android\.application["')\s]+version\s+["']$v""").find(content)?.groupValues?.get(1)?.let { return it }
        // TOML version.ref for com.android.application
        val ref = Regex("""com\.android\.application[^}]{0,200}?version\.ref\s*=\s*["'](\w[\w.-]*)["']""", RegexOption.DOT_MATCHES_ALL)
            .find(content)?.groupValues?.get(1)
        if (ref != null) Regex("""(?:^|\n)\s*${Regex.escape(ref)}\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
        return null
    }

    private fun extractKotlinVersion(content: String): String? {
        val v = """([\d]+\.[\d]+(?:\.[\d]+)?)"""
        // 1. Classpath: classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
        Regex("""kotlin-gradle-plugin[^:\n"']*:$v""").find(content)?.groupValues?.get(1)?.let { return it }
        // 2. Plugin DSL: id("org.jetbrains.kotlin.android") version "1.9.22"
        Regex("""org\.jetbrains\.kotlin\.(?:android|jvm|multiplatform)[^\n"']*version\s+["']$v""").find(content)?.groupValues?.get(1)?.let { return it }
        // 3. KTS shorthand: kotlin("android") version "1.9.22"
        Regex("""kotlin\s*\(\s*["']\w+["']\s*\)\s*version\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
        // 4. TOML [versions] entry literally named "kotlin": kotlin = "2.1.0"
        Regex("""(?:^|\n)\s*kotlin\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
        // 5. TOML: any entry with id = "org.jetbrains.kotlin.*" and version.ref → resolve that ref
        val refByPlugin = Regex(
            """id\s*=\s*["']org\.jetbrains\.kotlin[^"']*["'][^}]{0,200}?version\.ref\s*=\s*["'](\w[\w.-]*)["']""",
            RegexOption.DOT_MATCHES_ALL
        ).find(content)?.groupValues?.get(1)
        if (refByPlugin != null)
            Regex("""(?:^|\n)\s*${Regex.escape(refByPlugin)}\s*=\s*["']$v["']""")
                .find(content)?.groupValues?.get(1)?.let { return it }
        // 6. Broad TOML fallback: any version.ref whose value is literally "kotlin" → resolve it
        //    covers: jetbrains-kotlin-android = { ..., version.ref = "kotlin" }
        val refLiteralKotlin = Regex("""version\.ref\s*=\s*["']kotlin["']""").find(content)
        if (refLiteralKotlin != null)
            Regex("""(?:^|\n)\s*kotlin\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
        // 7. TOML [versions]: kotlinVersion or kotlin-version or kotlin_version
        Regex("""(?:^|\n)\s*kotlin[-_]?[Vv]ersion\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }
        return null
    }

    /**
     * Detects the Compose version used in the project.
     *
     * Priority order:
     *  1. Explicit `compose-ui:X.Y.Z` inline dependency
     *  2. TOML `version.ref` on the compose-ui library entry → resolve to concrete version
     *  3. Compose BOM version from inline `platform("androidx.compose:compose-bom:X")` → "BOM X"
     *  4. Compose BOM version from TOML `version.ref` on compose-bom entry → "BOM X"
     *  5. Any entry with `version.ref = "compose"` → resolve to concrete version
     */
    private fun extractComposeVersion(content: String): String? {
        val v = """([\d]+\.[\d]+(?:\.[\d]+)?)"""

        // 1. Explicit compose-ui version inline
        for (f in listOf("androidx.compose.ui", "compose-ui", "compose.ui")) {
            Regex("""${Regex.escape(f)}[^"'\n\r]*:$v""").find(content)?.groupValues?.get(1)?.let { return it }
        }
        // 2. TOML version.ref on compose-ui library entry
        val uiRef = Regex("""androidx\.compose\.ui[^}]{0,300}?version\.ref\s*=\s*["'](\w[\w.-]*)["']""", RegexOption.DOT_MATCHES_ALL)
            .find(content)?.groupValues?.get(1)
        if (uiRef != null)
            Regex("""(?:^|\n)\s*${Regex.escape(uiRef)}\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }

        // Compose is present at all?
        if (!content.contains("androidx.compose") && !content.contains("compose-ui") && !content.contains("compose.ui")) return null

        // 3. BOM inline: platform("androidx.compose:compose-bom:2024.06.00")
        Regex("""compose-bom[^:\n"']*:([^"'\n\r\s]+)""").find(content)?.groupValues?.get(1)?.let { return "BOM $it" }
        // 4. BOM from TOML version.ref: compose-bom = { ..., version.ref = "composeBom" }
        val bomRef = Regex("""compose-bom[^}\n]{0,200}?version\.ref\s*=\s*["'](\w[\w.-]*)["']""")
            .find(content)?.groupValues?.get(1)
        if (bomRef != null)
            Regex("""(?:^|\n)\s*${Regex.escape(bomRef)}\s*=\s*["']([^"'\n]+)["']""")
                .find(content)?.groupValues?.get(1)?.let { return "BOM $it" }
        // 5. Broad: any version.ref literally named "compose" → resolve it
        val composeRef = Regex("""version\.ref\s*=\s*["']compose["']""").find(content)
        if (composeRef != null)
            Regex("""(?:^|\n)\s*compose\s*=\s*["']$v["']""").find(content)?.groupValues?.get(1)?.let { return it }

        return null
    }

    /** Returns a summary string like "compileSdk 35 · minSdk 24 · targetSdk 35", or null. */
    private fun extractApiLevel(content: String): String? {
        fun findInt(key: String) =
            Regex("""(?<!\w)$key\s*[=()\s]*(\d{1,3})(?!\.)""").find(content)?.groupValues?.get(1)?.toIntOrNull()
        val compile = findInt("compileSdk")
        val min     = findInt("minSdk")
        val target  = findInt("targetSdk")
        return listOfNotNull(
            compile?.let { "compileSdk $it" },
            min?.let { "minSdk $it" },
            target?.let { "targetSdk $it" }
        ).joinToString(" · ").takeIf { it.isNotEmpty() }
    }

    /** Extracts the Gradle distribution version from `distributionUrl` in wrapper.properties. */
    private fun extractGradleWrapperVersion(content: String): String? =
        Regex("""distributionUrl[^\n]*?gradle-([\d.]+)-""").find(content)?.groupValues?.get(1)

    // ── Detection entry point ─────────────────────────────────────────────────

    private fun detectItem(item: TechItem, content: String): DetectionResult {
        return when (item.type) {

            SupportType.BUILT_IN -> {
                val detected = item.detectionId?.let { extractVersionByDetectionId(it, content) }
                DetectionResult(detected, true)
            }

            SupportType.AUTO -> {
                if (item.detectionId != null) {
                    val version = extractVersionByDetectionId(item.detectionId, content)
                        ?: return DetectionResult("BOM", true)
                    // "BOM X.Y.Z" prefix means BOM-managed — always compatible, don't range-check
                    if (version.startsWith("BOM ")) return DetectionResult(version, true)
                    DetectionResult(version, isVersionInRange(version, item.minVersion, item.maxVersion))
                } else {
                    if (item.artifacts.isEmpty()) return DetectionResult(null, false)
                    if (!item.artifacts.any { content.contains(it) }) return DetectionResult(null, false)
                    val version = extractVersion(item.artifacts, content)
                        ?: return DetectionResult("BOM", true)
                    if (version.startsWith("BOM ")) return DetectionResult(version, true)
                    DetectionResult(version, isVersionInRange(version, item.minVersion, item.maxVersion))
                }
            }
        }
    }

    // ── Panel ─────────────────────────────────────────────────────────────────

    fun createPanel(info: ProjectDetectionService.ProjectInfo): JComponent {
        val content = buildProjectContent(info)
        val builder = FormBuilder.createFormBuilder()

        builder.addComponent(
            JBLabel("Supported Technologies").apply {
                font       = JBUI.Fonts.label(16f).asBold()
                foreground = WizardColors.accent
                border     = JBUI.Borders.emptyBottom(2)
            }
        )
        builder.addComponent(
            JBLabel(
                "<html>Your project's detected versions are shown alongside the Dynatrace-supported range. " +
                "<b style='color:green'>Green</b> = compatible · " +
                "<b style='color:red'>Red</b> = unsupported version · " +
                "Grey = not detected.</html>"
            ).apply {
                foreground = UIUtil.getContextHelpForeground()
                border     = JBUI.Borders.emptyBottom(8)
            }
        )

        SUPPORTED_CATALOG.forEach { category ->
            builder.addComponent(TitledSeparator(category.title))
            builder.addComponent(techGrid(category.items, content))
        }

        builder.addVerticalGap(8)
        builder.addComponent(
            DocumentationLinks.createLinkLabel(
                "View full supported technologies list →",
                DocumentationLinks.SUPPORTED_TECHNOLOGIES
            )
        )
        builder.addVerticalGap(8)

        return builder.panel.also { it.border = JBUI.Borders.empty(12, 16, 12, 16) }
    }

    // ── Grid builder ──────────────────────────────────────────────────────────

    /** Four-column grid: **Technology** | **In Your Project** | **Supported** | **Status** */
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

            // ── Resolve display values ────────────────────────────────────────
            val icon: String
            val statusText: String
            val statusColor: Color
            val detectedText: String
            val isBold: Boolean

            when {
                item.type == SupportType.BUILT_IN -> {
                    val hasDetected = result.detectedVersion != null
                    icon        = "✅"
                    statusText  = "Built-in"
                    statusColor = WizardColors.success
                    detectedText = result.detectedVersion ?: "—"
                    isBold = hasDetected
                }
                result.detectedVersion == null -> {
                    icon = "💡"; statusText = "Not in project"
                    statusColor = UIUtil.getContextHelpForeground(); detectedText = "—"; isBold = false
                }
                // "BOM X.Y.Z" — Compose BOM or similar: show the BOM version number
                result.detectedVersion?.startsWith("BOM ") == true -> {
                    icon = "✅"; statusText = "Compatible (BOM)"
                    statusColor = WizardColors.success
                    detectedText = result.detectedVersion    // shows e.g. "BOM 2024.06.00"
                    isBold = true
                }
                // "BOM" sentinel — present but version completely unresolvable
                result.detectedVersion == "BOM" -> {
                    icon = "✅"; statusText = "Compatible (BOM)"
                    statusColor = WizardColors.success; detectedText = "via BOM"; isBold = true
                }
                result.inRange -> {
                    icon = "✅"; statusText = "Compatible"
                    statusColor = WizardColors.success; detectedText = result.detectedVersion; isBold = true
                }
                else -> {
                    icon = "❌"; statusText = "Unsupported version"
                    statusColor = WizardColors.error; detectedText = result.detectedVersion; isBold = true
                }
            }

            // ── Render cells ──────────────────────────────────────────────────
            grid.add(JBLabel("$icon  ${item.name}").apply {
                font = if (isBold) JBUI.Fonts.label().asBold() else JBUI.Fonts.label()
            })
            grid.add(JBLabel(detectedText).apply {
                font = if (isBold) JBUI.Fonts.label().asBold() else JBUI.Fonts.label()
                foreground = when {
                    item.type == SupportType.BUILT_IN && result.detectedVersion != null ->
                        UIUtil.getLabelForeground()
                    result.detectedVersion == null ->
                        UIUtil.getContextHelpForeground()
                    result.detectedVersion == "BOM" || result.detectedVersion?.startsWith("BOM ") == true ->
                        WizardColors.success
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
        }

        return grid
    }
}
