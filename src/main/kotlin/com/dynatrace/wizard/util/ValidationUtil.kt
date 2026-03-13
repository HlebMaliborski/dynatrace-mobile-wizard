package com.dynatrace.wizard.util

import java.net.MalformedURLException
import java.net.URL

/**
 * Utility functions for validating wizard input fields.
 */
object ValidationUtil {

    /**
     * Validates a Dynatrace Application ID.
     * Must be non-empty and match the expected format (alphanumeric with possible hyphens).
     */
    fun validateApplicationId(appId: String): ValidationResult {
        if (appId.isBlank()) {
            return ValidationResult.Error("Application ID must not be empty.")
        }
        if (!appId.matches(Regex("[A-Za-z0-9._\\-]+"))) {
            return ValidationResult.Error("Application ID may only contain letters, digits, dots, hyphens, and underscores.")
        }
        return ValidationResult.Success
    }

    /**
     * Validates a Dynatrace Beacon URL.
     * Must be a non-empty, well-formed HTTPS URL.
     */
    fun validateBeaconUrl(url: String): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult.Error("Beacon URL must not be empty.")
        }
        return try {
            val parsed = URL(url)
            if (parsed.protocol != "https") {
                ValidationResult.Error("Beacon URL must use HTTPS.")
            } else {
                ValidationResult.Success
            }
        } catch (e: MalformedURLException) {
            ValidationResult.Error("Beacon URL is not a valid URL.")
        }
    }

    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()

        val isSuccess: Boolean get() = this is Success
        val errorMessage: String? get() = (this as? Error)?.message
    }
}
