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
}
