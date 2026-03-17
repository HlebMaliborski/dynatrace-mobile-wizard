package com.dynatrace.wizard.util

import com.dynatrace.wizard.util.ValidationUtil.validateApplicationId
import com.dynatrace.wizard.util.ValidationUtil.validateBeaconUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationUtilTest {

    @Test
    fun `blank appId returns error`() {
        assertFalse(validateApplicationId("").isSuccess)
    }

    @Test
    fun `valid appId alphanumeric passes`() {
        assertTrue(validateApplicationId("ABC123").isSuccess)
    }

    @Test
    fun `valid appId with hyphens underscores dots passes`() {
        assertTrue(validateApplicationId("my-App_ID.v2").isSuccess)
    }

    @Test
    fun `appId with spaces fails`() {
        assertFalse(validateApplicationId("my app id").isSuccess)
    }

    @Test
    fun `appId with special chars fails`() {
        assertFalse(validateApplicationId("app@id!").isSuccess)
    }

    @Test
    fun `blank beacon URL returns error`() {
        assertFalse(validateBeaconUrl("").isSuccess)
    }

    @Test
    fun `valid https URL returns success`() {
        assertTrue(validateBeaconUrl("https://example.live.dynatrace.com/mbeacon").isSuccess)
    }

    @Test
    fun `http URL fails`() {
        assertFalse(validateBeaconUrl("http://example.live.dynatrace.com/mbeacon").isSuccess)
    }

    @Test
    fun `malformed URL fails`() {
        assertFalse(validateBeaconUrl("not-a-url").isSuccess)
    }

    @Test
    fun `error message is populated on failure`() {
        val result = validateApplicationId("")
        assertTrue(result.errorMessage != null)
    }

    @Test
    fun `success has no error message`() {
        val result = validateApplicationId("ValidApp123")
        assertTrue(result.errorMessage == null)
    }
}
