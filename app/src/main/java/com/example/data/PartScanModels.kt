package com.example.data

import android.graphics.Bitmap
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PartScanResponse(
    val identified: Boolean,
    @Json(name = "part_name") val partName: String? = null,
    val category: String? = null,
    val confidence: String? = null, // high | medium | low
    @Json(name = "confidence_reason") val confidenceReason: String? = null,
    @Json(name = "possible_part_numbers") val possiblePartNumbers: List<String>? = null,
    val compatibility: List<CompatibilityItem>? = null,
    @Json(name = "condition_observations") val conditionObservations: List<String>? = null,
    @Json(name = "condition_grade") val conditionGrade: String? = null, // Like New | Good | Fair | Poor | For Parts
    @Json(name = "price_estimate_usd") val priceEstimateUsd: PriceEstimate? = null,
    @Json(name = "pricing_rationale") val pricingRationale: String? = null,
    val listing: Listing? = null,
    val message: String? = null, // For when identified = false
    @Json(name = "removal_effort") val removalEffort: RemovalEffort? = null
)

@JsonClass(generateAdapter = true)
data class RemovalEffort(
    val grade: String, // easy | moderate | hard
    val note: String
)

@JsonClass(generateAdapter = true)
data class CompatibilityItem(
    val make: String,
    val models: List<String>,
    @Json(name = "year_range") val yearRange: String
)

@JsonClass(generateAdapter = true)
data class PriceEstimate(
    val low: Double,
    val typical: Double,
    val high: Double
)

@JsonClass(generateAdapter = true)
data class Listing(
    val title: String,
    val description: String,
    @Json(name = "condition_disclosure") val conditionDisclosure: String
)

// Session history item (persisted to disk; bitmap cached in memory)
data class ScanHistoryItem(
    val id: String,
    val timestamp: Long,
    val bitmap: Bitmap?,
    val imagePath: String? = null,
    val response: PartScanResponse,
    val userContext: String?,
    
    // Editable local overrides for screen 4 listing generator
    val editedPrice: Double,
    val editedTitle: String,
    val editedDescription: String,
    val editedConditionNotes: String,

    // Part-Out tracking checkboxes
    val pulled: Boolean = false,
    val listed: Boolean = false,
    
    // Associated vehicle session
    val sessionId: String? = null
)

// Part-Out Vehicle Session model
@JsonClass(generateAdapter = true)
data class PartOutSession(
    val id: String,
    val vehicleName: String, // Year Make Model Trim, e.g. "2011 Honda Accord EX-L V6"
    val mileage: String?,
    val notes: String?,
    val timestamp: Long
)

// A single turn in the AI Mechanic repair chat
data class MechanicMessage(
    val id: String,
    val role: String, // "user" | "model"
    val text: String,
    val bitmap: Bitmap? = null,
    val imagePath: String? = null,
    val isError: Boolean = false
)

// --- Persistence DTOs (bitmaps are stored as JPEG files on disk, referenced by path) ---

@JsonClass(generateAdapter = true)
data class PersistedScan(
    val id: String,
    val timestamp: Long,
    val imagePath: String?,
    val response: PartScanResponse,
    val userContext: String?,
    val editedPrice: Double,
    val editedTitle: String,
    val editedDescription: String,
    val editedConditionNotes: String,
    val pulled: Boolean = false,
    val listed: Boolean = false,
    val sessionId: String? = null
)

@JsonClass(generateAdapter = true)
data class PersistedChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val imagePath: String? = null
)

@JsonClass(generateAdapter = true)
data class PersistedState(
    val scans: List<PersistedScan> = emptyList(),
    val sessions: List<PartOutSession> = emptyList(),
    val activeSessionId: String? = null,
    val chat: List<PersistedChatMessage> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PullGuideResponse(
    @Json(name = "removal_steps") val removalSteps: List<RemovalStep>,
    @Json(name = "tools_needed") val toolsNeeded: List<String>,
    @Json(name = "time_estimate_minutes") val timeEstimateMinutes: Int,
    @Json(name = "preserve_value_tips") val preserveValueTips: List<String>,
    @Json(name = "safety_warnings") val safetyWarnings: List<String>,
    val annotations: List<PullAnnotation> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RemovalStep(
    @Json(name = "step_number") val stepNumber: Int,
    val instruction: String,
    val tools: List<String>
)

@JsonClass(generateAdapter = true)
data class PullAnnotation(
    val label: String,
    val point: AnnotationPoint,
    val description: String
)

@JsonClass(generateAdapter = true)
data class AnnotationPoint(
    val x: Int,
    val y: Int
)

