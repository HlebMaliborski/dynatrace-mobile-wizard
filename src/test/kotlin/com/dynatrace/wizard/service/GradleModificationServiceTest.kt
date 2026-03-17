package com.dynatrace.wizard.service

import com.dynatrace.wizard.model.DynatraceConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GradleModificationService] block-builder helpers and config parsing.
 * These run without a live IDE — the service is instantiated with a null Project.
 */
class GradleModificationServiceTest {

    // ── agentLogging — Kotlin DSL ──────────────────────────────────────────

    @Test
    fun `buildDynatraceBlockKts does not emit debug block when agentLogging is false`() {
        val block = GradleModificationService.buildDynatraceBlockKts(DynatraceConfig())
        assertFalse(block.contains("agentLogging"))
        assertFalse(block.contains("debug {"))
    }

    @Test
    fun `buildDynatraceBlockKts emits debug block when agentLogging is true`() {
        val block = GradleModificationService.buildDynatraceBlockKts(
            DynatraceConfig(agentLogging = true)
        )
        assertTrue(block.contains("debug {"))
        assertTrue(block.contains("agentLogging(true)"))
    }

    @Test
    fun `buildDynatraceBlockKts places debug block inside configurations block`() {
        val block = GradleModificationService.buildDynatraceBlockKts(
            DynatraceConfig(agentLogging = true)
        )
        val configurationsIdx = block.indexOf("configurations {")
        val debugIdx          = block.indexOf("debug {")
        val closingBrace      = block.lastIndexOf("}")
        assertTrue("debug block must appear after configurations {", debugIdx > configurationsIdx)
        assertTrue("debug block must appear before the final closing brace", debugIdx < closingBrace)
    }

    // ── agentLogging — Groovy DSL ──────────────────────────────────────────

    @Test
    fun `buildDynatraceBlockGroovy does not emit debug block when agentLogging is false`() {
        val block = GradleModificationService.buildDynatraceBlockGroovy(DynatraceConfig())
        assertFalse(block.contains("agentLogging"))
        assertFalse(block.contains("debug {"))
    }

    @Test
    fun `buildDynatraceBlockGroovy emits debug block when agentLogging is true`() {
        val block = GradleModificationService.buildDynatraceBlockGroovy(
            DynatraceConfig(agentLogging = true)
        )
        assertTrue(block.contains("debug {"))
        assertTrue(block.contains("agentLogging true"))
    }

    // ── Other flags unaffected ─────────────────────────────────────────────

    @Test
    fun `agentLogging does not interfere with agentBehavior block in Kotlin DSL`() {
        val block = GradleModificationService.buildDynatraceBlockKts(
            DynatraceConfig(agentLogging = true, agentBehaviorGrail = true)
        )
        assertTrue(block.contains("agentBehavior {"))
        assertTrue(block.contains("startupWithGrailEnabled(true)"))
        assertTrue(block.contains("debug {"))
        assertTrue(block.contains("agentLogging(true)"))
    }

    @Test
    fun `agentLogging does not interfere with agentBehavior block in Groovy DSL`() {
        val block = GradleModificationService.buildDynatraceBlockGroovy(
            DynatraceConfig(agentLogging = true, agentBehaviorLoadBalancing = true)
        )
        assertTrue(block.contains("agentBehavior {"))
        assertTrue(block.contains("startupLoadBalancing true"))
        assertTrue(block.contains("debug {"))
        assertTrue(block.contains("agentLogging true"))
    }

    @Test
    fun `agentLogging and sessionReplay can coexist in Kotlin DSL`() {
        val block = GradleModificationService.buildDynatraceBlockKts(
            DynatraceConfig(agentLogging = true, sessionReplayEnabled = true)
        )
        assertTrue(block.contains("sessionReplay.enabled(true)"))
        assertTrue(block.contains("debug {"))
        assertTrue(block.contains("agentLogging(true)"))
    }

    @Test
    fun `agentLogging and sessionReplay can coexist in Groovy DSL`() {
        val block = GradleModificationService.buildDynatraceBlockGroovy(
            DynatraceConfig(agentLogging = true, sessionReplayEnabled = true)
        )
        assertTrue(block.contains("sessionReplay.enabled true"))
        assertTrue(block.contains("debug {"))
        assertTrue(block.contains("agentLogging true"))
    }

    // ── readExistingConfigFromString round-trip ────────────────────────────

    private val service = GradleModificationService(null)

    @Test
    fun `readExistingConfigFromString detects agentLogging true in Kotlin DSL`() {
        val gradleContent = """
            dynatrace {
                configurations {
                    create("sampleConfig") {
                        variantFilter(".*")
                        autoStart {
                            applicationId("my-app-id")
                            beaconUrl("https://tenant.example/mbeacon")
                        }
                        debug {
                            agentLogging(true)
                        }
                    }
                }
            }
        """.trimIndent()

        val config = service.readExistingConfigFromString(gradleContent)
        assertTrue("agentLogging should be detected", config?.agentLogging == true)
    }

    @Test
    fun `readExistingConfigFromString detects agentLogging true in Groovy DSL`() {
        val gradleContent = """
            dynatrace {
                configurations {
                    sampleConfig {
                        variantFilter '.*'
                        autoStart {
                            applicationId 'my-app-id'
                            beaconUrl 'https://tenant.example/mbeacon'
                        }
                        debug {
                            agentLogging true
                        }
                    }
                }
            }
        """.trimIndent()

        val config = service.readExistingConfigFromString(gradleContent)
        assertTrue("agentLogging should be detected", config?.agentLogging == true)
    }

    @Test
    fun `readExistingConfigFromString defaults agentLogging to false when absent`() {
        val gradleContent = """
            dynatrace {
                configurations {
                    sampleConfig {
                        variantFilter '.*'
                        autoStart {
                            applicationId 'my-app-id'
                            beaconUrl 'https://tenant.example/mbeacon'
                        }
                    }
                }
            }
        """.trimIndent()

        val config = service.readExistingConfigFromString(gradleContent)
        assertFalse("agentLogging should default to false", config?.agentLogging == true)
    }

    // ── OneAgent SDK subprojects block — single-module (filterAll) round-trip ──

    /**
     * Regression test for: "When there is only one library module and I set add OneAgent SDK
     * dependency, next time when I run plugin I see that it is unset."
     *
     * Root cause: with a single library module [filterAll] = true, so the generated
     * subprojects block contains NO `project.name` guard.  The old proximity-based
     * detection required the module name to appear near `agentDependency()`, which it
     * doesn't in the filterAll block → checkbox appeared unchecked on re-run.
     */
    @Test
    fun `buildSdkSubprojectsBlockKts with filterAll omits project name guard`() {
        val service = GradleModificationService(null)
        val block = service.buildSdkSubprojectsBlockKts(listOf("mylib"), filterAll = true)
        assertTrue("block must contain agentDependency()", block.contains("agentDependency()"))
        assertFalse("filterAll block must NOT contain project.name guard", block.contains("project.name"))
    }

    @Test
    fun `buildSdkSubprojectsBlockGroovy with filterAll omits project name guard`() {
        val service = GradleModificationService(null)
        val block = service.buildSdkSubprojectsBlockGroovy(listOf("mylib"), filterAll = true)
        assertTrue("block must contain agentDependency()", block.contains("agentDependency()"))
        assertFalse("filterAll block must NOT contain project.name guard", block.contains("project.name"))
    }

    @Test
    fun `buildSdkSubprojectsBlockKts with multiple modules emits project name guards`() {
        val service = GradleModificationService(null)
        val block = service.buildSdkSubprojectsBlockKts(listOf("lib1", "lib2"), filterAll = false)
        assertTrue(block.contains("project.name == \"lib1\""))
        assertTrue(block.contains("project.name == \"lib2\""))
    }

    /**
     * Simulates the detection logic that [ModuleSelectionStep.hasAgentSdk] applies on re-run.
     * When a single-module (filterAll) block was written, the module name is absent from the
     * block, so the proximity check alone returns false.  The fallback — "agentDependency()
     * with no project.name guard nearby" — must detect the block as already configured.
     */
    @Test
    fun `hasAgentSdk detection succeeds for filterAll block without project name guard`() {
        val service = GradleModificationService(null)
        val rootBuildContent = """
            plugins { id("com.android.application") }
        """.trimIndent() + "\n\n" +
            service.buildSdkSubprojectsBlockKts(listOf("mylib"), filterAll = true)

        assertTrue("agentDependency() must be present", rootBuildContent.contains("agentDependency()"))
        assertFalse("filterAll block must not contain project.name", rootBuildContent.contains("project.name"))

        // Replicate the fixed detection logic from hasAgentSdk
        val agentPositions     = Regex("""agentDependency\(\)""").findAll(rootBuildContent).map { it.range.first }.toList()
        val nameGuardPositions = Regex("""project\.name\s*==\s*["'][^"']+["']""").findAll(rootBuildContent).map { it.range.first }.toList()
        val detectedByFallback = agentPositions.any { ap -> nameGuardPositions.none { ng -> kotlin.math.abs(ng - ap) < 500 } }

        assertTrue("filterAll block must be detected as already configured by fallback check", detectedByFallback)
    }

    // ── Classpath injection — Plugin DSL vs legacy template ────────────────

    /**
     * Regression test:
     * When the root build file already uses Plugin DSL (`plugins {}` block), adding the
     * Dynatrace classpath should produce a **minimal** `buildscript { dependencies { ... } }`
     * block WITHOUT a `repositories {}` section (those live in pluginManagement in settings.gradle).
     */
    @Test
    fun `addClasspathGroovy emits minimal buildscript when plugins block is present`() {
        val service = GradleModificationService(null)
        val input = """
            plugins {
                id 'com.android.application' version '8.1.0' apply false
            }
        """.trimIndent()

        val result = service.addClasspathGroovy(input)

        assertTrue("classpath entry must be present", result.contains("classpath 'com.dynatrace.tools.android:gradle-plugin"))
        assertFalse("repositories block must NOT be emitted for Plugin DSL projects", result.contains("repositories {"))
        assertTrue("buildscript block must be present", result.contains("buildscript {"))
        assertTrue("original plugins block must be preserved", result.contains("id 'com.android.application'"))
    }

    @Test
    fun `addClasspathKts emits minimal buildscript when plugins block is present`() {
        val service = GradleModificationService(null)
        val input = """
            plugins {
                id("com.android.application") version "8.1.0" apply false
            }
        """.trimIndent()

        val result = service.addClasspathKts(input)

        assertTrue("classpath entry must be present", result.contains("classpath(\"com.dynatrace.tools.android:gradle-plugin"))
        assertFalse("repositories block must NOT be emitted for Plugin DSL projects", result.contains("repositories {"))
        assertTrue("buildscript block must be present", result.contains("buildscript {"))
    }

    @Test
    fun `addClasspathGroovy emits full buildscript with repositories for legacy template`() {
        val service = GradleModificationService(null)
        // Legacy project: no plugins {} block, just a buildscript section stub
        val input = """
            // legacy root build.gradle
            allprojects {
                repositories { google(); mavenCentral() }
            }
        """.trimIndent()

        val result = service.addClasspathGroovy(input)

        assertTrue("classpath entry must be present", result.contains("classpath 'com.dynatrace.tools.android:gradle-plugin"))
        assertTrue("repositories block must be present for legacy projects", result.contains("repositories {"))
        assertTrue("mavenCentral must be listed", result.contains("mavenCentral()"))
    }

    @Test
    fun `addClasspathGroovy inserts into existing buildscript dependencies without adding repositories for Plugin DSL`() {
        val service = GradleModificationService(null)
        // Plugin DSL project that already has a minimal buildscript {} (e.g. for another classpath)
        val input = """
            plugins {
                id 'com.android.application' version '8.1.0' apply false
            }
            buildscript {
                dependencies {
                    classpath 'com.android.tools.build:gradle:8.1.0'
                }
            }
        """.trimIndent()

        val result = service.addClasspathGroovy(input)

        assertTrue("Dynatrace classpath must be inserted", result.contains("classpath 'com.dynatrace.tools.android:gradle-plugin"))
        assertFalse("no repositories block should be injected for Plugin DSL", result.contains("mavenCentral()"))
    }
}
