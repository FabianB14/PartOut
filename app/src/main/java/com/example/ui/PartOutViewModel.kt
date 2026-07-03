package com.example.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.RetrofitClient
import com.example.data.PartScanResponse
import com.example.data.ScanHistoryItem
import com.example.data.PartOutSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// Screens in the application
enum class PartOutScreen {
    CAPTURE,
    ANALYZING,
    RESULTS,
    LISTING_GENERATOR,
    PULL_GUIDE
}

// Bottom Navigation Tabs
enum class PartOutTab {
    SCAN,
    GARAGE_LOGS,
    PART_OUTS
}

class PartOutViewModel : ViewModel() {

    // --- State Declarations ---

    private val _currentTab = MutableStateFlow(PartOutTab.SCAN)
    val currentTab: StateFlow<PartOutTab> = _currentTab.asStateFlow()

    private val _currentScreen = MutableStateFlow(PartOutScreen.CAPTURE)
    val currentScreen: StateFlow<PartOutScreen> = _currentScreen.asStateFlow()

    // Scanned parts history (in-memory, lost on restart)
    private val _scanHistory = MutableStateFlow<List<ScanHistoryItem>>(emptyList())
    val scanHistory: StateFlow<List<ScanHistoryItem>> = _scanHistory.asStateFlow()

    // Part-Out Sessions (in-memory)
    private val _partOutSessions = MutableStateFlow<List<PartOutSession>>(emptyList())
    val partOutSessions: StateFlow<List<PartOutSession>> = _partOutSessions.asStateFlow()

    // Active session ID
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _activeSession = MutableStateFlow<PartOutSession?>(null)
    val activeSession: StateFlow<PartOutSession?> = _activeSession.asStateFlow()

    // Selected session for session details / pull list
    private val _selectedSession = MutableStateFlow<PartOutSession?>(null)
    val selectedSession: StateFlow<PartOutSession?> = _selectedSession.asStateFlow()

    // Current Image / Capture states
    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _userContext = MutableStateFlow("")
    val userContext: StateFlow<String> = _userContext.asStateFlow()

    private val _isContextExpanded = MutableStateFlow(false)
    val isContextExpanded: StateFlow<Boolean> = _isContextExpanded.asStateFlow()

    // Analyzing states
    private val _analysisStatus = MutableStateFlow("Identifying part…")
    val analysisStatus: StateFlow<String> = _analysisStatus.asStateFlow()

    // Active Results
    private val _activeResponse = MutableStateFlow<PartScanResponse?>(null)
    val activeResponse: StateFlow<PartScanResponse?> = _activeResponse.asStateFlow()

    // Reference to the active history item being viewed or edited
    private val _activeHistoryItem = MutableStateFlow<ScanHistoryItem?>(null)
    val activeHistoryItem: StateFlow<ScanHistoryItem?> = _activeHistoryItem.asStateFlow()

    // For not identified case
    private val _nonIdentifiedMessage = MutableStateFlow<String?>(null)
    val nonIdentifiedMessage: StateFlow<String?> = _nonIdentifiedMessage.asStateFlow()

    // Error states
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Pull Guide states
    private val _pullGuideLoading = MutableStateFlow(false)
    val pullGuideLoading: StateFlow<Boolean> = _pullGuideLoading.asStateFlow()

    private val _pullGuideError = MutableStateFlow<String?>(null)
    val pullGuideError: StateFlow<String?> = _pullGuideError.asStateFlow()

    private val _activePullGuide = MutableStateFlow<com.example.data.PullGuideResponse?>(null)
    val activePullGuide: StateFlow<com.example.data.PullGuideResponse?> = _activePullGuide.asStateFlow()

    // Editable text values for Screen 4 (Listing Generator)
    private val _editedPrice = MutableStateFlow("")
    val editedPrice: StateFlow<String> = _editedPrice.asStateFlow()

    private val _editedTitle = MutableStateFlow("")
    val editedTitle: StateFlow<String> = _editedTitle.asStateFlow()

    private val _editedDescription = MutableStateFlow("")
    val editedDescription: StateFlow<String> = _editedDescription.asStateFlow()

    private val _editedConditionNotes = MutableStateFlow("")
    val editedConditionNotes: StateFlow<String> = _editedConditionNotes.asStateFlow()

    private var statusRotationJob: Job? = null

    // --- Action Handlers ---

    fun selectTab(tab: PartOutTab) {
        _currentTab.value = tab
        if (tab == PartOutTab.PART_OUTS) {
            // Clear selected session when we go back to the list of sessions
            _selectedSession.value = null
        }
    }

    fun setCapturedBitmap(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
        _errorMessage.value = null
    }

    fun setUserContext(context: String) {
        _userContext.value = context
    }

    fun toggleContextExpanded() {
        _isContextExpanded.value = !_isContextExpanded.value
    }

    fun resetCapture() {
        _capturedBitmap.value = null
        _activeResponse.value = null
        _activeHistoryItem.value = null
        _nonIdentifiedMessage.value = null
        _errorMessage.value = null
        _userContext.value = ""
        _isContextExpanded.value = false
        _activePullGuide.value = null
        _pullGuideError.value = null
        _pullGuideLoading.value = false
        _currentScreen.value = PartOutScreen.CAPTURE
        _currentTab.value = PartOutTab.SCAN
    }

    // Opens a previous scan from history
    fun openHistoryItem(item: ScanHistoryItem) {
        _capturedBitmap.value = item.bitmap
        _activeResponse.value = item.response
        _activeHistoryItem.value = item
        _userContext.value = item.userContext ?: ""
        _nonIdentifiedMessage.value = if (!item.response.identified) item.response.message else null
        
        // Setup Screen 4 input values from history item state
        _editedPrice.value = item.editedPrice.toString()
        _editedTitle.value = item.editedTitle
        _editedDescription.value = item.editedDescription
        _editedConditionNotes.value = item.editedConditionNotes

        _currentScreen.value = PartOutScreen.RESULTS
        _currentTab.value = PartOutTab.SCAN
    }

    // Create session
    fun createPartOutSession(vehicleName: String, mileage: String?, notes: String?) {
        val newSession = PartOutSession(
            id = UUID.randomUUID().toString(),
            vehicleName = vehicleName,
            mileage = mileage,
            notes = notes,
            timestamp = System.currentTimeMillis()
        )
        _partOutSessions.value = listOf(newSession) + _partOutSessions.value
        _activeSessionId.value = newSession.id
        _activeSession.value = newSession
    }

    fun selectSession(session: PartOutSession?) {
        _selectedSession.value = session
    }

    fun toggleSessionActive(session: PartOutSession) {
        if (_activeSessionId.value == session.id) {
            _activeSessionId.value = null
            _activeSession.value = null
        } else {
            _activeSessionId.value = session.id
            _activeSession.value = session
        }
    }

    fun clearActiveSession() {
        _activeSessionId.value = null
        _activeSession.value = null
    }

    fun deleteSession(sessionId: String) {
        _partOutSessions.value = _partOutSessions.value.filter { it.id != sessionId }
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = null
            _activeSession.value = null
        }
        if (_selectedSession.value?.id == sessionId) {
            _selectedSession.value = null
        }
        // Remove association from items
        _scanHistory.value = _scanHistory.value.map { item ->
            if (item.sessionId == sessionId) item.copy(sessionId = null) else item
        }
    }

    fun togglePartPulled(itemId: String) {
        _scanHistory.value = _scanHistory.value.map { item ->
            if (item.id == itemId) item.copy(pulled = !item.pulled) else item
        }
        // Also update activeHistoryItem if needed
        if (_activeHistoryItem.value?.id == itemId) {
            _activeHistoryItem.value = _activeHistoryItem.value?.copy(pulled = !_activeHistoryItem.value!!.pulled)
        }
    }

    fun togglePartListed(itemId: String) {
        _scanHistory.value = _scanHistory.value.map { item ->
            if (item.id == itemId) item.copy(listed = !item.listed) else item
        }
        // Also update activeHistoryItem if needed
        if (_activeHistoryItem.value?.id == itemId) {
            _activeHistoryItem.value = _activeHistoryItem.value?.copy(listed = !_activeHistoryItem.value!!.listed)
        }
    }

    // Start analyzing process
    fun loadPullGuide() {
        val bitmap = _capturedBitmap.value ?: return
        val response = _activeResponse.value ?: return
        val partName = response.partName ?: "this part"
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _pullGuideError.value = "Gemini API Key is missing. Please configure your GEMINI_API_KEY in the Secrets panel in AI Studio."
            return
        }

        _pullGuideLoading.value = true
        _pullGuideError.value = null
        _activePullGuide.value = null
        _currentScreen.value = PartOutScreen.PULL_GUIDE

        viewModelScope.launch {
            try {
                val vehicleContextText = _activeSession.value?.let {
                    "Vehicle: ${it.vehicleName}${if (!it.mileage.isNullOrBlank()) ", Mileage: ${it.mileage}" else ""}"
                } ?: "Typical Vehicle"
                val pullGuide = RetrofitClient.getPullGuide(apiKey, bitmap, partName, vehicleContextText)
                _activePullGuide.value = pullGuide
            } catch (e: Exception) {
                e.printStackTrace()
                _pullGuideError.value = "Could not generate removal steps for this part. (Error: ${e.localizedMessage})"
            } finally {
                _pullGuideLoading.value = false
            }
        }
    }

    // Start analyzing process
    fun analyzeCapturedImage() {
        val bitmap = _capturedBitmap.value ?: return
        val apiKey = BuildConfig.GEMINI_API_KEY

        // Check for empty API key
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _errorMessage.value = "Gemini API Key is missing. Please configure your GEMINI_API_KEY in the Secrets panel in AI Studio."
            return
        }

        _errorMessage.value = null
        _currentScreen.value = PartOutScreen.ANALYZING

        // Start rotating messages
        startStatusRotation()

        viewModelScope.launch {
            try {
                // Determine session details to pass
                val sessionDetails = _activeSession.value?.let {
                    "Vehicle: ${it.vehicleName}${if (!it.mileage.isNullOrBlank()) ", Mileage: ${it.mileage}" else ""}${if (!it.notes.isNullOrBlank()) ", Notes: ${it.notes}" else ""}"
                }

                // Single multimodal vision call with 30-second timeout handled internally
                val result = RetrofitClient.analyzePart(apiKey, bitmap, _userContext.value, sessionDetails)

                stopStatusRotation()

                if (result.identified) {
                    _activeResponse.value = result
                    _nonIdentifiedMessage.value = null

                    // Prepare editable inputs for Screen 4
                    val defaultPrice = result.priceEstimateUsd?.typical ?: 0.0
                    _editedPrice.value = if (defaultPrice > 0) defaultPrice.toString() else ""
                    _editedTitle.value = result.listing?.title ?: ""
                    _editedDescription.value = result.listing?.description ?: ""
                    _editedConditionNotes.value = result.listing?.conditionDisclosure ?: ""

                    // Save to session history
                    val newItem = ScanHistoryItem(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        bitmap = bitmap,
                        response = result,
                        userContext = _userContext.value,
                        editedPrice = defaultPrice,
                        editedTitle = result.listing?.title ?: "",
                        editedDescription = result.listing?.description ?: "",
                        editedConditionNotes = result.listing?.conditionDisclosure ?: "",
                        sessionId = _activeSessionId.value,
                        pulled = false,
                        listed = false
                    )
                    _scanHistory.value = listOf(newItem) + _scanHistory.value
                    _activeHistoryItem.value = newItem

                    _currentScreen.value = PartOutScreen.RESULTS
                } else {
                    // Not an automotive part
                    _activeResponse.value = result
                    _nonIdentifiedMessage.value = result.message ?: "This image does not appear to be an automotive part."
                    _currentScreen.value = PartOutScreen.RESULTS
                }

            } catch (e: Exception) {
                stopStatusRotation()
                _errorMessage.value = "Couldn't read that one — try a clearer photo.\n(Error: ${e.localizedMessage})"
                _currentScreen.value = PartOutScreen.RESULTS // Show the result container which will trigger failure overlay
            }
        }
    }

    // Rotating status text coroutine
    private fun startStatusRotation() {
        statusRotationJob?.cancel()
        statusRotationJob = viewModelScope.launch {
            val statuses = listOf(
                "Identifying part…",
                "Checking compatibility…",
                "Estimating value…",
                "Synthesizing listing data…",
                "Formulating condition observations…"
            )
            var index = 0
            while (true) {
                _analysisStatus.value = statuses[index]
                delay(2000)
                index = (index + 1) % statuses.size
            }
        }
    }

    private fun stopStatusRotation() {
        statusRotationJob?.cancel()
        statusRotationJob = null
    }

    // Screen 4 listing changes
    fun updateEditedPrice(price: String) {
        _editedPrice.value = price
        updateActiveHistoryItem { it.copy(editedPrice = price.toDoubleOrNull() ?: 0.0) }
    }

    fun updateEditedTitle(title: String) {
        if (title.length <= 80) {
            _editedTitle.value = title
            updateActiveHistoryItem { it.copy(editedTitle = title) }
        }
    }

    fun updateEditedDescription(description: String) {
        _editedDescription.value = description
        updateActiveHistoryItem { it.copy(editedDescription = description) }
    }

    fun updateEditedConditionNotes(notes: String) {
        _editedConditionNotes.value = notes
        updateActiveHistoryItem { it.copy(editedConditionNotes = notes) }
    }

    private fun updateActiveHistoryItem(block: (ScanHistoryItem) -> ScanHistoryItem) {
        val currentItem = _activeHistoryItem.value ?: return
        val updatedItem = block(currentItem)
        _activeHistoryItem.value = updatedItem

        // Update in history list too
        _scanHistory.value = _scanHistory.value.map { item ->
            if (item.id == currentItem.id) updatedItem else item
        }
    }

    fun navigateToScreen(screen: PartOutScreen) {
        _currentScreen.value = screen
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusRotation()
    }
}
