package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.RetrofitClient
import com.example.data.MechanicMessage
import com.example.data.PartOutRepository
import com.example.data.PartOutSession
import com.example.data.PersistedChatMessage
import com.example.data.PersistedScan
import com.example.data.PersistedState
import com.example.data.ScanHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    MECHANIC,
    GARAGE_LOGS,
    PART_OUTS
}

class PartOutViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PartOutRepository(application)
    private val persistMutex = Mutex()

    // --- State Declarations ---

    private val _currentTab = MutableStateFlow(PartOutTab.SCAN)
    val currentTab: StateFlow<PartOutTab> = _currentTab.asStateFlow()

    private val _currentScreen = MutableStateFlow(PartOutScreen.CAPTURE)
    val currentScreen: StateFlow<PartOutScreen> = _currentScreen.asStateFlow()

    // Scanned parts history (persisted to disk)
    private val _scanHistory = MutableStateFlow<List<ScanHistoryItem>>(emptyList())
    val scanHistory: StateFlow<List<ScanHistoryItem>> = _scanHistory.asStateFlow()

    // Part-Out Sessions (persisted to disk)
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
    private val _activeResponse = MutableStateFlow<com.example.data.PartScanResponse?>(null)
    val activeResponse: StateFlow<com.example.data.PartScanResponse?> = _activeResponse.asStateFlow()

    // Reference to the active history item being viewed or edited
    private val _activeHistoryItem = MutableStateFlow<ScanHistoryItem?>(null)
    val activeHistoryItem: StateFlow<ScanHistoryItem?> = _activeHistoryItem.asStateFlow()

    // For not identified case
    private val _nonIdentifiedMessage = MutableStateFlow<String?>(null)
    val nonIdentifiedMessage: StateFlow<String?> = _nonIdentifiedMessage.asStateFlow()

    // Error states
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // API key management
    private val _userApiKey = MutableStateFlow("")
    val userApiKey: StateFlow<String> = _userApiKey.asStateFlow()

    private val _showApiKeyDialog = MutableStateFlow(false)
    val showApiKeyDialog: StateFlow<Boolean> = _showApiKeyDialog.asStateFlow()

    // AI Mechanic chat states
    private val _mechanicMessages = MutableStateFlow<List<MechanicMessage>>(emptyList())
    val mechanicMessages: StateFlow<List<MechanicMessage>> = _mechanicMessages.asStateFlow()

    private val _mechanicInput = MutableStateFlow("")
    val mechanicInput: StateFlow<String> = _mechanicInput.asStateFlow()

    private val _mechanicSending = MutableStateFlow(false)
    val mechanicSending: StateFlow<Boolean> = _mechanicSending.asStateFlow()

    private val _mechanicAttachment = MutableStateFlow<Bitmap?>(null)
    val mechanicAttachment: StateFlow<Bitmap?> = _mechanicAttachment.asStateFlow()

    private val _mechanicVehicle = MutableStateFlow("")
    val mechanicVehicle: StateFlow<String> = _mechanicVehicle.asStateFlow()

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

    init {
        _userApiKey.value = repository.apiKey
        _mechanicVehicle.value = repository.mechanicVehicle

        // Restore persisted scans, sessions, and chat from disk.
        viewModelScope.launch(Dispatchers.IO) {
            val state = repository.loadState()
            val restoredScans = state.scans.map { p ->
                ScanHistoryItem(
                    id = p.id,
                    timestamp = p.timestamp,
                    bitmap = repository.loadImage(p.imagePath),
                    imagePath = p.imagePath,
                    response = p.response,
                    userContext = p.userContext,
                    editedPrice = p.editedPrice,
                    editedTitle = p.editedTitle,
                    editedDescription = p.editedDescription,
                    editedConditionNotes = p.editedConditionNotes,
                    pulled = p.pulled,
                    listed = p.listed,
                    sessionId = p.sessionId
                )
            }
            val restoredChat = state.chat.map { c ->
                MechanicMessage(
                    id = c.id,
                    role = c.role,
                    text = c.text,
                    bitmap = repository.loadImage(c.imagePath),
                    imagePath = c.imagePath
                )
            }
            _scanHistory.update { current -> restoredScans + current.filter { c -> restoredScans.none { it.id == c.id } } }
            _partOutSessions.update { current -> state.sessions + current.filter { c -> state.sessions.none { it.id == c.id } } }
            _mechanicMessages.update { current -> restoredChat + current }
            state.activeSessionId?.let { id ->
                val session = state.sessions.find { it.id == id }
                if (session != null && _activeSessionId.value == null) {
                    _activeSessionId.value = session.id
                    _activeSession.value = session
                }
            }
        }
    }

    // --- API key handling ---

    // Resolves the key to use: one entered in-app wins, otherwise a key
    // baked in at build time via .env, otherwise null (prompts the dialog).
    private fun effectiveApiKey(): String? {
        val stored = _userApiKey.value
        if (stored.isNotBlank()) return stored
        val builtIn = BuildConfig.GEMINI_API_KEY
        if (builtIn.isNotBlank() && builtIn != "MY_GEMINI_API_KEY") return builtIn
        return null
    }

    fun hasUsableApiKey(): Boolean = effectiveApiKey() != null

    fun openApiKeyDialog() {
        _showApiKeyDialog.value = true
    }

    fun dismissApiKeyDialog() {
        _showApiKeyDialog.value = false
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        _userApiKey.value = trimmed
        repository.apiKey = trimmed
        _showApiKeyDialog.value = false
    }

    // --- Persistence ---

    private fun ScanHistoryItem.toPersisted() = PersistedScan(
        id = id,
        timestamp = timestamp,
        imagePath = imagePath,
        response = response,
        userContext = userContext,
        editedPrice = editedPrice,
        editedTitle = editedTitle,
        editedDescription = editedDescription,
        editedConditionNotes = editedConditionNotes,
        pulled = pulled,
        listed = listed,
        sessionId = sessionId
    )

    private fun persist() {
        viewModelScope.launch(Dispatchers.IO) {
            persistMutex.withLock {
                // Write any not-yet-saved photos to disk and record their paths.
                _scanHistory.update { list ->
                    list.map { item ->
                        val bitmap = item.bitmap
                        if (bitmap != null && item.imagePath == null) {
                            item.copy(imagePath = repository.saveImage("scan_${item.id}", bitmap))
                        } else item
                    }
                }
                _mechanicMessages.update { list ->
                    list.map { msg ->
                        val bitmap = msg.bitmap
                        if (bitmap != null && msg.imagePath == null) {
                            msg.copy(imagePath = repository.saveImage("chat_${msg.id}", bitmap))
                        } else msg
                    }
                }
                repository.saveState(
                    PersistedState(
                        scans = _scanHistory.value.map { it.toPersisted() },
                        sessions = _partOutSessions.value,
                        activeSessionId = _activeSessionId.value,
                        chat = _mechanicMessages.value
                            .filter { !it.isError }
                            .map { PersistedChatMessage(it.id, it.role, it.text, it.imagePath) }
                    )
                )
            }
        }
    }

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
        persist()
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
        persist()
    }

    fun clearActiveSession() {
        _activeSessionId.value = null
        _activeSession.value = null
        persist()
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
        persist()
    }

    fun togglePartPulled(itemId: String) {
        _scanHistory.value = _scanHistory.value.map { item ->
            if (item.id == itemId) item.copy(pulled = !item.pulled) else item
        }
        // Also update activeHistoryItem if needed
        if (_activeHistoryItem.value?.id == itemId) {
            _activeHistoryItem.value = _activeHistoryItem.value?.copy(pulled = !_activeHistoryItem.value!!.pulled)
        }
        persist()
    }

    fun togglePartListed(itemId: String) {
        _scanHistory.value = _scanHistory.value.map { item ->
            if (item.id == itemId) item.copy(listed = !item.listed) else item
        }
        // Also update activeHistoryItem if needed
        if (_activeHistoryItem.value?.id == itemId) {
            _activeHistoryItem.value = _activeHistoryItem.value?.copy(listed = !_activeHistoryItem.value!!.listed)
        }
        persist()
    }

    // Generate the pull guide for the active scan
    fun loadPullGuide() {
        val bitmap = _capturedBitmap.value ?: return
        val response = _activeResponse.value ?: return
        val partName = response.partName ?: "this part"
        val apiKey = effectiveApiKey()

        if (apiKey == null) {
            _showApiKeyDialog.value = true
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
                val pullGuide = withContext(Dispatchers.IO) {
                    RetrofitClient.getPullGuide(apiKey, bitmap, partName, vehicleContextText)
                }
                _activePullGuide.value = pullGuide
            } catch (e: Exception) {
                e.printStackTrace()
                _pullGuideError.value = e.localizedMessage ?: "Could not generate removal steps for this part."
            } finally {
                _pullGuideLoading.value = false
            }
        }
    }

    // Start analyzing process
    fun analyzeCapturedImage() {
        val bitmap = _capturedBitmap.value ?: return
        val apiKey = effectiveApiKey()

        if (apiKey == null) {
            _showApiKeyDialog.value = true
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

                // Single multimodal vision call
                val result = withContext(Dispatchers.IO) {
                    RetrofitClient.analyzePart(apiKey, bitmap, _userContext.value, sessionDetails)
                }

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
                    persist()

                    _currentScreen.value = PartOutScreen.RESULTS
                } else {
                    // Not an automotive part
                    _activeResponse.value = result
                    _nonIdentifiedMessage.value = result.message ?: "This image does not appear to be an automotive part."
                    _currentScreen.value = PartOutScreen.RESULTS
                }

            } catch (e: Exception) {
                stopStatusRotation()
                _errorMessage.value = e.localizedMessage ?: "Couldn't read that one — try a clearer photo."
                _currentScreen.value = PartOutScreen.RESULTS // Show the result container which will trigger failure overlay
            }
        }
    }

    // --- AI Mechanic chat ---

    fun setMechanicInput(text: String) {
        _mechanicInput.value = text
    }

    fun setMechanicVehicle(vehicle: String) {
        _mechanicVehicle.value = vehicle
        repository.mechanicVehicle = vehicle
    }

    fun setMechanicAttachment(bitmap: Bitmap?) {
        _mechanicAttachment.value = bitmap
    }

    fun clearMechanicChat() {
        _mechanicMessages.value = emptyList()
        persist()
    }

    // Jump from a scan result into the repair chat with the part pre-loaded
    fun askMechanicAboutActivePart() {
        val partName = _activeResponse.value?.partName
        _mechanicAttachment.value = _capturedBitmap.value
        _mechanicInput.value = if (partName != null) {
            "Help me with this $partName — how do I check if it's bad, and how do I replace it?"
        } else {
            "Help me figure out what's wrong with this part."
        }
        _currentTab.value = PartOutTab.MECHANIC
    }

    fun sendMechanicMessage() {
        if (_mechanicSending.value) return
        val apiKey = effectiveApiKey()
        if (apiKey == null) {
            _showApiKeyDialog.value = true
            return
        }
        val text = _mechanicInput.value.trim()
        val attachment = _mechanicAttachment.value
        if (text.isBlank() && attachment == null) return

        val userMessage = MechanicMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            text = text,
            bitmap = attachment
        )
        _mechanicMessages.value = _mechanicMessages.value + userMessage
        _mechanicInput.value = ""
        _mechanicAttachment.value = null
        _mechanicSending.value = true

        viewModelScope.launch {
            try {
                val history = _mechanicMessages.value.filter { !it.isError }
                val vehicle = _mechanicVehicle.value.takeIf { it.isNotBlank() }
                val reply = withContext(Dispatchers.IO) {
                    RetrofitClient.mechanicChat(apiKey, history, vehicle)
                }
                _mechanicMessages.value = _mechanicMessages.value + MechanicMessage(
                    id = UUID.randomUUID().toString(),
                    role = "model",
                    text = reply
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _mechanicMessages.value = _mechanicMessages.value + MechanicMessage(
                    id = UUID.randomUUID().toString(),
                    role = "model",
                    text = e.localizedMessage ?: "Something went wrong. Try sending that again.",
                    isError = true
                )
            } finally {
                _mechanicSending.value = false
                persist()
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
        persist()
    }

    fun navigateToScreen(screen: PartOutScreen) {
        _currentScreen.value = screen
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusRotation()
    }
}
