package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import com.example.data.PartScanResponse
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// --- Gemini Request / Response Models ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    @Json(name = "inline_data") val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mime_type") val mimeType: String,
    val data: String // Base64 encoded image
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "response_mime_type") val responseMimeType: String? = "application/json",
    val temperature: Float? = 0.2f
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

// --- Retrofit API Service Interface ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- Retrofit Client & Helper Methods ---

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }

    // Helper to compress and convert Bitmap to base64
    fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Compress with reasonable quality to limit payload size
        compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // Direct Gemini Vision API Call
    suspend fun analyzePart(
        apiKey: String,
        bitmap: Bitmap,
        userContext: String?,
        vehicleSessionContext: String? = null
    ): PartScanResponse {
        val base64Image = bitmap.toBase64()

        // Construct context text
        val contextPrompt = buildString {
            if (!vehicleSessionContext.isNullOrBlank()) {
                append("Target Vehicle/Part-Out Session: $vehicleSessionContext\n(IMPORTANT: Anchor your identification and compatibility fitment strictly to this target vehicle. Identify this part as it relates to this vehicle, and list compatible/interchangeable years/makes/models for this part).\n")
            }
            if (!userContext.isNullOrBlank()) {
                append("Additional User Context: $userContext\n(IMPORTANT: Weight this context heavily for fitment/compatibility. Do not ignore it).\n")
            }
        }

        val prompt = """
            Analyze the provided photo of an automotive part. 
            $contextPrompt
            
            Identify the part name, its category, guess unverified part numbers, evaluate conditions from the image, estimate Private Party price in USD (low, typical, high), determine the removal effort (grade and note), and generate a high-quality ready-to-post marketplace listing.
            
            Respond strictly in valid JSON matching this exact schema:
            {
              "identified": true,
              "part_name": "string",
              "category": "Engine | Electrical | Suspension | Brakes | Body | Interior | Drivetrain | Cooling | Exhaust | Other",
              "confidence": "high | medium | low",
              "confidence_reason": "one sentence",
              "possible_part_numbers": ["string"],
              "compatibility": [
                { "make": "string", "models": ["string"], "year_range": "string" }
              ],
              "condition_observations": ["string", "string"],
              "condition_grade": "Like New | Good | Fair | Poor | For Parts",
              "price_estimate_usd": { "low": 0, "typical": 0, "high": 0 },
              "pricing_rationale": "one sentence",
              "removal_effort": {
                "grade": "easy | moderate | hard",
                "note": "short phrase like '4 bolts + one connector'"
              },
              "listing": {
                "title": "max 80 chars, keyword-rich for marketplace search",
                "description": "3-5 sentences: what it is, fitment, condition, buyer guidance",
                "condition_disclosure": "honest 1-2 sentence disclosure of flaws"
              }
            }
        """.trimIndent()

        val systemInstructions = """
            You are an expert automotive parts identifier and used-parts pricing analyst with junkyard, dealership, and eBay Motors experience.

            Analyze the provided photo of an automotive part. Respond ONLY with valid JSON matching the specified schema. Do not output markdown code blocks or wrapper text.

            Rules:
            - If the image is not an automotive part, set identified to false and include a "message" field politely saying what you see instead.
            - Never invent certainty. If unsure of exact fitment, give broader year ranges and set confidence to medium or low.
            - Part numbers are best guesses — the UI labels them as unverified.
            - Price estimates reflect USED private-party sale values in the US market.
            - If target vehicle/part-out session context is included, strictly anchor predictions to compatibility/fitment with that vehicle.
            - Provide a removal effort estimate representing how difficult it is to remove this part from the specified or typical vehicle.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = systemInstructions))
            )
        )

        val response = service.generateContent(apiKey, request)
        val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini API")

        return parseDefensive(rawText)
    }

    // Defensive parsing of the JSON response, stripping markdown fences if any
    private fun parseDefensive(rawText: String): PartScanResponse {
        var cleanText = rawText.trim()
        
        // Strip leading markdown block
        if (cleanText.startsWith("```json")) {
            cleanText = cleanText.substring(7)
        } else if (cleanText.startsWith("```")) {
            cleanText = cleanText.substring(3)
        }
        
        // Strip trailing markdown block
        if (cleanText.endsWith("```")) {
            cleanText = cleanText.substring(0, cleanText.length - 3)
        }
        cleanText = cleanText.trim()

        return try {
            val adapter = moshi.adapter(PartScanResponse::class.java)
            adapter.fromJson(cleanText) ?: throw Exception("JSON was null after parsing")
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to parse response: ${e.localizedMessage}")
        }
    }

    suspend fun getPullGuide(
        apiKey: String,
        bitmap: Bitmap,
        partName: String,
        vehicleContext: String?
    ): com.example.data.PullGuideResponse {
        val base64Image = bitmap.toBase64()

        val prompt = """
            You are assisting a salvage yard puller with removing the following part:
            Part Name: $partName
            ${vehicleContext?.let { "Vehicle/Context: $it" } ?: ""}

            Examine the provided photo carefully. Locate any visible fasteners (screws, bolts, clips, connectors, pins, tabs, pry points).
            If you can confidently locate them in the photo, provide their locations in the 'annotations' array. Use normalized coordinates from 0 to 1000 (where x: 0 is left, 1000 is right, y: 0 is top, 1000 is bottom).
            If you cannot confidently locate specific fasteners or connectors on this actual photo (e.g., they are hidden behind, under the part, or out of frame), return an empty 'annotations' array.

            Respond strictly in valid JSON matching this exact schema:
            {
              "removal_steps": [
                {
                  "step_number": 1,
                  "instruction": "Unscrew the two 10mm retaining bolts holding the bracket.",
                  "tools": ["10mm Socket", "Ratchet"]
                }
              ],
              "tools_needed": ["10mm Socket", "Ratchet", "Trim Pry Tool"],
              "time_estimate_minutes": 15,
              "preserve_value_tips": [
                "The plastic locking tab on the electrical harness is highly fragile; press gently with a flathead screwdriver to unlock without snapping."
              ],
              "safety_warnings": [
                "Disconnect the negative battery cable before touching electrical connections."
              ],
              "annotations": [
                {
                  "label": "1",
                  "point": { "x": 450, "y": 620 },
                  "description": "10mm Bracket Bolt"
                }
              ]
            }
        """.trimIndent()

        val systemInstructions = """
            You are a master mechanic and expert salvage yard advisor. You provide highly accurate, safe, and professional guides for removing automotive parts.
            You must analyze the user's photo of the part to locate fasteners/clips.
            Provide:
            1. An ordered list of steps to remove the part (each instruction should be strictly 1-2 sentences).
            2. A comprehensive list of tools required.
            3. A time estimate in minutes.
            4. Critical advice to preserve the resale value of the part (such as fragile plastic clips, clips that break easily, delicate surfaces).
            5. Clear safety warnings.
            6. Annotations marking visible fasteners/connectors/tabs/pry points directly on the image using 0-1000 normalized coordinates. If they are not clearly visible in this specific photo, return an empty array for annotations.

            Respond ONLY with valid JSON matching the specified schema. Do not output markdown code blocks or wrapper text.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = systemInstructions))
            )
        )

        val response = service.generateContent(apiKey, request)
        val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini API for Pull Guide")

        return parsePullGuideDefensive(rawText)
    }

    private fun parsePullGuideDefensive(rawText: String): com.example.data.PullGuideResponse {
        var cleanText = rawText.trim()
        if (cleanText.startsWith("```json")) {
            cleanText = cleanText.substring(7)
        } else if (cleanText.startsWith("```")) {
            cleanText = cleanText.substring(3)
        }
        if (cleanText.endsWith("```")) {
            cleanText = cleanText.substring(0, cleanText.length - 3)
        }
        cleanText = cleanText.trim()

        return try {
            val adapter = moshi.adapter(com.example.data.PullGuideResponse::class.java)
            adapter.fromJson(cleanText) ?: throw Exception("JSON was null after parsing Pull Guide")
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to parse Pull Guide: ${e.localizedMessage}")
        }
    }
}
