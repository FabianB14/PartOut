package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.PartScanResponse
import com.example.data.ScanHistoryItem
import com.example.data.PartOutSession
import com.example.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Global Clipboard Helper
fun copyToClipboard(context: Context, text: String, label: String, onCopied: () -> Unit) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        onCopied()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to copy: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PartOutApp(
    viewModel: PartOutViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val historyList by viewModel.scanHistory.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    // Permissions State
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Clipboard notification
    var clipboardToastText by remember { mutableStateOf<String?>(null) }

    // Handle temporary custom toast on screen
    LaunchedEffect(clipboardToastText) {
        if (clipboardToastText != null) {
            delay(2000)
            clipboardToastText = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = PartOutSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.border(1.dp, PartOutBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = currentTab == PartOutTab.SCAN,
                    onClick = { viewModel.selectTab(PartOutTab.SCAN) },
                    icon = { Icon(Icons.Default.PhotoCamera, contentDescription = "Scan tab") },
                    label = { Text("Scan", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PartOutPrimary,
                        selectedTextColor = PartOutPrimary,
                        unselectedIconColor = PartOutTextSecondary,
                        unselectedTextColor = PartOutTextSecondary,
                        indicatorColor = PartOutSurface
                    )
                )
                NavigationBarItem(
                    selected = currentTab == PartOutTab.GARAGE_LOGS,
                    onClick = { viewModel.selectTab(PartOutTab.GARAGE_LOGS) },
                    icon = { Icon(Icons.Default.History, contentDescription = "Garage Logs tab") },
                    label = { Text("Garage Logs", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PartOutPrimary,
                        selectedTextColor = PartOutPrimary,
                        unselectedIconColor = PartOutTextSecondary,
                        unselectedTextColor = PartOutTextSecondary,
                        indicatorColor = PartOutSurface
                    )
                )
                NavigationBarItem(
                    selected = currentTab == PartOutTab.PART_OUTS,
                    onClick = { viewModel.selectTab(PartOutTab.PART_OUTS) },
                    icon = { Icon(Icons.Default.DirectionsCar, contentDescription = "Part-Outs tab") },
                    label = { Text("Part-Outs", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PartOutPrimary,
                        selectedTextColor = PartOutPrimary,
                        unselectedIconColor = PartOutTextSecondary,
                        unselectedTextColor = PartOutTextSecondary,
                        indicatorColor = PartOutSurface
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PartOutBackground)
                .padding(innerPadding)
        ) {
            when (currentTab) {
                PartOutTab.SCAN -> {
                    when (currentScreen) {
                        PartOutScreen.CAPTURE -> {
                            CaptureScreen(
                                viewModel = viewModel,
                                cameraPermissionGranted = cameraPermissionState.status.isGranted,
                                onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                            )
                        }
                        PartOutScreen.ANALYZING -> {
                            AnalyzingScreen(viewModel = viewModel)
                        }
                        PartOutScreen.RESULTS -> {
                            ResultsScreen(
                                viewModel = viewModel,
                                onToastTrigger = { msg -> clipboardToastText = msg }
                            )
                        }
                        PartOutScreen.LISTING_GENERATOR -> {
                            ListingGeneratorScreen(
                                viewModel = viewModel,
                                onToastTrigger = { msg -> clipboardToastText = msg }
                            )
                        }
                        PartOutScreen.PULL_GUIDE -> {
                            PullGuideScreen(viewModel = viewModel)
                        }
                    }
                }
                PartOutTab.GARAGE_LOGS -> {
                    GarageLogsTabScreen(
                        historyList = historyList,
                        onItemClick = { item -> viewModel.openHistoryItem(item) },
                        onStartCapture = { viewModel.resetCapture() }
                    )
                }
                PartOutTab.PART_OUTS -> {
                    val selectedSession by viewModel.selectedSession.collectAsState()
                    val partOutSessions by viewModel.partOutSessions.collectAsState()
                    val activeSessionId by viewModel.activeSessionId.collectAsState()

                    if (selectedSession != null) {
                        SessionDetailScreen(
                            session = selectedSession!!,
                            historyList = historyList,
                            onBackClick = { viewModel.selectSession(null) },
                            onTogglePulled = { itemId -> viewModel.togglePartPulled(itemId) },
                            onToggleListed = { itemId -> viewModel.togglePartListed(itemId) }
                        )
                    } else {
                        PartOutsTabScreen(
                            sessions = partOutSessions,
                            historyList = historyList,
                            activeSessionId = activeSessionId,
                            onCreateSession = { vehicle, mileage, notes -> viewModel.createPartOutSession(vehicle, mileage, notes) },
                            onSelectSession = { session -> viewModel.selectSession(session) },
                            onToggleActive = { session -> viewModel.toggleSessionActive(session) },
                            onDeleteSession = { sessionId -> viewModel.deleteSession(sessionId) }
                        )
                    }
                }
            }

            // Custom Floating Toast/Banner for copies
            AnimatedVisibility(
                visible = clipboardToastText != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .border(1.dp, PartOutPrimary, RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = PartOutPrimary)
                        Text(
                            text = clipboardToastText ?: "",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 1 — CAPTURE
// ==========================================

@Composable
fun CaptureScreen(
    viewModel: PartOutViewModel,
    cameraPermissionGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val userContext by viewModel.userContext.collectAsState()
    val isContextExpanded by viewModel.isContextExpanded.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    var cameraFallbackMode by remember { mutableStateOf(false) }

    // Launcher for gallery selection
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    viewModel.setCapturedBitmap(bitmap)
                } else {
                    Toast.makeText(context, "Could not load image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (capturedBitmap != null) {
        // IMAGE REVIEW MODE (Confirm capture/upload)
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = capturedBitmap!!.asImageBitmap(),
                contentDescription = "Captured Part image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Overlays at the bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                            startY = 0f
                        )
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Verify Photo Quality",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Ensure the automotive part is well-lit, centered, and fully visible for accurate Gemini vision analysis.",
                    color = PartOutTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resetCapture() },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("retake_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retake", modifier = Modifier.padding(end = 6.dp))
                        Text("Retake", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.analyzeCapturedImage() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("analyze_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PartOutPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Analyze", modifier = Modifier.padding(end = 6.dp))
                        Text("Analyze", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        // CAPTURE MODE (Live Camera Viewfinder or Gallery fallback)
        Box(modifier = Modifier.fillMaxSize()) {
            if (cameraPermissionGranted && !cameraFallbackMode) {
                // Live Viewfinder
                CameraXViewfinder(
                    onPhotoCaptured = { bitmap -> viewModel.setCapturedBitmap(bitmap) },
                    onFallback = { cameraFallbackMode = true }
                )
            } else {
                // Gallery / Fallback View (Camera Permission Denied or Unavailable)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PartOutBackground)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(PartOutSurface, CircleShape)
                                .border(2.dp, PartOutBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.NoPhotography,
                                contentDescription = "Camera Unavailable",
                                tint = PartOutPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Text(
                            text = if (cameraFallbackMode) "Camera Initialization Failed" else "Camera Permission Required",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (cameraFallbackMode)
                                "Your device camera could not be loaded. Please upload a high-quality photo from your library to analyze."
                            else
                                "Grant camera access to photograph parts instantly, or continue using standard file uploads.",
                            color = PartOutTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (!cameraFallbackMode && !cameraPermissionGranted) {
                            Button(
                                onClick = onRequestPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = PartOutPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Enable Camera Access", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = PartOutSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PartOutBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("upload_gallery_fallback_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Upload", modifier = Modifier.padding(end = 8.dp), tint = PartOutPrimary)
                            Text("Upload from Gallery", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Floating Active Session Chip Overlay
            if (activeSession != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .background(PartOutPrimary.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clickable { viewModel.clearActiveSession() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = "Active Session",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Adding to: ${activeSession!!.vehicleName}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Deactivate",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Viewfinder Shutter HUD (Only visible if camera is working)
            if (cameraPermissionGranted && !cameraFallbackMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp)
                ) {
                    // Shutter + gallery action row at bottom
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Gallery Button
                        Box(modifier = Modifier.size(56.dp)) {
                            IconButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .size(52.dp)
                                    .border(1.dp, PartOutBorder, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    contentDescription = "Upload from gallery",
                                    tint = Color.White
                                )
                            }
                        }

                        // Giant circular Shutter Button
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                                .padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(PartOutPrimary)
                                    .clickable {
                                        // Trigger custom capture handled by the camera view binding
                                        shutterClickTriggerFlow.tryEmit(Unit)
                                    }
                                    .testTag("shutter_button")
                            )
                        }

                        // Symmetry placeholder
                        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {}
                    }

                    // Collapsible "Add Context" Panel floating above Shutter
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 120.dp, start = 24.dp, end = 24.dp)
                            .border(1.dp, PartOutBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleContextExpanded() },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = "Context Icon", tint = PartOutPrimary, modifier = Modifier.size(18.dp))
                                    Text(
                                        "Improve Accuracy with Context",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    if (isContextExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle Context",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (isContextExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = userContext,
                                    onValueChange = { viewModel.setUserContext(it) },
                                    placeholder = {
                                        Text(
                                            "e.g., 'came off a 2014 Silverado' or 'OEM factory part'",
                                            fontSize = 12.sp,
                                            color = PartOutTextSecondary
                                        )
                                    },
                                    maxLines = 2,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = PartOutPrimary,
                                        unfocusedBorderColor = PartOutBorder,
                                        focusedContainerColor = PartOutBackground.copy(alpha = 0.8f),
                                        unfocusedContainerColor = PartOutBackground.copy(alpha = 0.8f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("context_text_field"),
                                    textStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Global flow channel to trigger capture inside Viewfinder
val shutterClickTriggerFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

@Composable
fun CameraXViewfinder(
    onPhotoCaptured: (Bitmap) -> Unit,
    onFallback: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
                onFallback()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Capture Trigger Listener
    LaunchedEffect(Unit) {
        shutterClickTriggerFlow.collect {
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = image.toBitmap()
                        image.close()
                        if (bitmap != null) {
                            onPhotoCaptured(bitmap)
                        } else {
                            onFallback()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        exception.printStackTrace()
                        onFallback()
                    }
                }
            )
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

// ==========================================
// SCREEN 2 — ANALYZING
// ==========================================

@Composable
fun AnalyzingScreen(viewModel: PartOutViewModel) {
    val statusText by viewModel.analysisStatus.collectAsState()
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()

    // Scanning animation offset
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Dimmed blurred capture photo in background
        capturedBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured part",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(16.dp),
                contentScale = ContentScale.Crop
            )
        }

        // Near-black dark overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
        )

        // Running scanning line animation
        Canvas(modifier = Modifier.fillMaxSize()) {
            val yPos = size.height * scanLineY
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, PartOutPrimary, Color.Transparent)
                ),
                start = androidx.compose.ui.geometry.Offset(0f, yPos),
                end = androidx.compose.ui.geometry.Offset(size.width, yPos),
                strokeWidth = 6.dp.toPx()
            )
        }

        // Processing indicators
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(72.dp),
                    color = PartOutPrimary,
                    strokeWidth = 4.dp
                )
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "AI computing",
                    tint = PartOutPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "PARTOUT ANALYSIS HUD",
                    color = PartOutPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )

                Text(
                    text = statusText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Gemini is estimating used-market values and compatibility fitment.",
                    color = PartOutTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 280.dp)
                )
            }
        }
    }
}

// ==========================================
// SCREEN 3 — RESULTS CARD
// ==========================================

@Composable
fun ResultsScreen(
    viewModel: PartOutViewModel,
    onToastTrigger: (String) -> Unit
) {
    val activeResult by viewModel.activeResponse.collectAsState()
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()

    // 1. Error / Failure Overlay Screen
    if (errorMsg != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PartOutBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(NeutralDark, CircleShape)
                        .border(1.dp, PartOutBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Error Icon",
                        tint = Color.Red,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Analysis Interrupted",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = errorMsg ?: "Unknown Error",
                    color = PartOutTextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Button(
                    onClick = { viewModel.resetCapture() },
                    colors = ButtonDefaults.buttonColors(containerColor = PartOutPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("error_retry_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.padding(end = 6.dp))
                    Text("Try Another Photo", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // 2. Loading Fallback
    if (activeResult == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = PartOutPrimary)
        }
        return
    }

    // 3. Not Identified Screen
    if (!activeResult!!.identified) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PartOutBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(NeutralDark, CircleShape)
                        .border(1.dp, PartOutBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.HelpOutline,
                        contentDescription = "Unknown Part",
                        tint = PartOutPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = "Part Not Identified",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = viewModel.nonIdentifiedMessage.collectAsState().value ?: "This image doesn't look like an automotive component.",
                    color = PartOutTextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Button(
                    onClick = { viewModel.resetCapture() },
                    colors = ButtonDefaults.buttonColors(containerColor = PartOutPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("not_identified_retake_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retake", modifier = Modifier.padding(end = 6.dp))
                    Text("Try Another Photo", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // 4. Main Results Card Screen
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PartOutBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back navigation header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.resetCapture() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PartOutPrimary)
                Text("Back to Capture", color = PartOutPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Thumbnail + Large Name Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PartOutBorder, RoundedCornerShape(16.dp))
            ) {
                Column {
                    capturedBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Scanned photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = activeResult!!.category?.uppercase() ?: "OTHER",
                                color = PartOutPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )

                            // Confidence chip
                            val confidenceColor = when (activeResult!!.confidence?.lowercase()) {
                                "high" -> HighConfidence
                                "medium" -> MediumConfidence
                                else -> LowConfidence
                            }

                            Box(
                                modifier = Modifier
                                    .background(confidenceColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(1.dp, confidenceColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${activeResult!!.confidence?.uppercase()} CONFIDENCE",
                                    color = confidenceColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Text(
                            text = activeResult!!.partName ?: "Unidentified Part",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 30.sp
                        )

                        Text(
                            text = activeResult!!.confidenceReason ?: "Identified via structural landmarks.",
                            color = PartOutTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Removal Effort Badge
        item {
            val effort = activeResult!!.removalEffort
            if (effort != null) {
                val effortColor = when (effort.grade.lowercase().trim()) {
                    "easy" -> Color(0xFF4CAF50) // Green
                    "moderate" -> Color(0xFFFFC107) // Yellow
                    "hard" -> Color(0xFFF44336) // Red
                    else -> PartOutPrimary
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PartOutBorder, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(effortColor.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, effortColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = "Removal effort",
                                tint = effortColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "REMOVAL EFFORT",
                                    color = PartOutTextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .background(effortColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, effortColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = effort.grade.uppercase(),
                                        color = effortColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = effort.note,
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }

        // Compatibility section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PartOutBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CarRepair, contentDescription = "Compatibility fitment", tint = PartOutPrimary, modifier = Modifier.size(20.dp))
                        Text(
                            text = "COMPATIBILITY FITMENT",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    val compatibilityList = activeResult!!.compatibility
                    if (compatibilityList.isNullOrEmpty()) {
                        Text("Broad universal compatibility or unverified custom fitment.", color = PartOutTextSecondary, fontSize = 13.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            compatibilityList.forEach { comp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NeutralDark, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = comp.make.uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = comp.models.joinToString(", "),
                                            color = PartOutTextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(OrangeAlpha10, RoundedCornerShape(6.dp))
                                            .border(1.dp, PartOutPrimary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = comp.yearRange,
                                            color = PartOutPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Possible part numbers section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PartOutBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = "Part numbers", tint = PartOutPrimary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "ESTIMATED PART NUMBERS",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Text(
                        text = "AI estimate — verify before listing",
                        color = PartOutPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    val parts = activeResult!!.possiblePartNumbers
                    if (parts.isNullOrEmpty()) {
                        Text("No specific part numbers identified.", color = PartOutTextSecondary, fontSize = 13.sp)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            parts.forEach { partNum ->
                                Box(
                                    modifier = Modifier
                                        .background(NeutralDark, RoundedCornerShape(6.dp))
                                        .border(1.dp, PartOutBorder, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = partNum,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Condition Assessment section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PartOutBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = "Observations", tint = PartOutPrimary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "CONDITION ASSESSMENT",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        // Grade badge
                        Box(
                            modifier = Modifier
                                .background(PartOutPrimary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = activeResult!!.conditionGrade?.uppercase() ?: "GOOD",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    val obs = activeResult!!.conditionObservations
                    if (obs.isNullOrEmpty()) {
                        Text("No critical visual flaws identified.", color = PartOutTextSecondary, fontSize = 13.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            obs.forEach { item ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("•", color = PartOutPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = item,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Used Market Price Range
        item {
            val price = activeResult!!.priceEstimateUsd ?: com.example.data.PriceEstimate(0.0, 0.0, 0.0)
            Card(
                colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PartOutBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.LocalOffer, contentDescription = "Market price", tint = PartOutPrimary, modifier = Modifier.size(20.dp))
                        Text(
                            text = "USED-MARKET PRICE RANGE",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    // Low, Typical, High columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LOW", color = PartOutTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("$${String.format(Locale.US, "%.0f", price.low)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TYPICAL", color = PartOutPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("$${String.format(Locale.US, "%.0f", price.typical)}", color = PartOutPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("HIGH", color = PartOutTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("$${String.format(Locale.US, "%.0f", price.high)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }

                    // Custom slider visualization showing Low/Typical/High
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Slider Line Track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(PartOutBorder, RoundedCornerShape(3.dp))
                        )

                        // Progress slider fill
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.weight(0.15f))
                            Box(
                                modifier = Modifier
                                    .weight(0.7f)
                                    .height(6.dp)
                                    .background(PartOutPrimary, RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.weight(0.15f))
                        }

                        // Marks
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Low Mark Dot
                            Box(modifier = Modifier.size(12.dp).background(Color.White, CircleShape))
                            // Typical Mark Dot
                            Box(modifier = Modifier.size(16.dp).background(PartOutPrimary, CircleShape))
                            // High Mark Dot
                            Box(modifier = Modifier.size(12.dp).background(Color.White, CircleShape))
                        }
                    }

                    Text(
                        text = activeResult!!.pricingRationale ?: "Private sale values reflecting local demand.",
                        color = PartOutTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // CTA Section (Generate Listing, Scan Another)
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Button(
                    onClick = { viewModel.loadPullGuide() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("pull_guide_button")
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Pull Guide icon", modifier = Modifier.padding(end = 8.dp), tint = Color.White)
                    Text("Pull Guide — how to remove this part", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }

                Button(
                    onClick = { viewModel.navigateToScreen(PartOutScreen.LISTING_GENERATOR) },
                    colors = ButtonDefaults.buttonColors(containerColor = PartOutPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("generate_listing_button")
                ) {
                    Icon(Icons.Default.Sell, contentDescription = "Generate Listing icon", modifier = Modifier.padding(end = 8.dp))
                    Text("Generate Marketplace Listing", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = { viewModel.resetCapture() },
                    border = BorderStroke(1.dp, PartOutBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("scan_another_button")
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = "Capture icon", modifier = Modifier.padding(end = 8.dp), tint = PartOutPrimary)
                    Text("Scan Another Part", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ==========================================
// SCREEN 4 — LISTING GENERATOR
// ==========================================

@Composable
fun ListingGeneratorScreen(
    viewModel: PartOutViewModel,
    onToastTrigger: (String) -> Unit
) {
    val context = LocalContext.current
    val editedPrice by viewModel.editedPrice.collectAsState()
    val editedTitle by viewModel.editedTitle.collectAsState()
    val editedDescription by viewModel.editedDescription.collectAsState()
    val editedConditionNotes by viewModel.editedConditionNotes.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PartOutBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.navigateToScreen(PartOutScreen.RESULTS) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PartOutPrimary)
                Text("Back to Specs", color = PartOutPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Top Heading
        item {
            Column {
                Text(
                    text = "AUTO-GENERATED LISTING",
                    color = PartOutPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Marketplace Draft",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Generated by PartOut's salvage expert model. Review and edit details before copying.",
                    color = PartOutTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Editable Details Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PartOutBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Asking Price
                    OutlinedTextField(
                        value = editedPrice,
                        onValueChange = { viewModel.updateEditedPrice(it) },
                        label = { Text("Asking Price (USD)", color = PartOutTextSecondary) },
                        prefix = { Text("$ ", color = PartOutPrimary, fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartOutPrimary,
                            unfocusedBorderColor = PartOutBorder,
                            focusedContainerColor = NeutralDark,
                            unfocusedContainerColor = NeutralDark
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("price_input_field"),
                        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    )

                    // Listing Title (with Counter)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = editedTitle,
                            onValueChange = { viewModel.updateEditedTitle(it) },
                            label = { Text("Listing Title", color = PartOutTextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PartOutPrimary,
                                unfocusedBorderColor = PartOutBorder,
                                focusedContainerColor = NeutralDark,
                                unfocusedContainerColor = NeutralDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("title_input_field"),
                            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${editedTitle.length}/80 characters max",
                            color = if (editedTitle.length > 75) Color.Red else PartOutTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    // Item Description
                    OutlinedTextField(
                        value = editedDescription,
                        onValueChange = { viewModel.updateEditedDescription(it) },
                        label = { Text("Description", color = PartOutTextSecondary) },
                        minLines = 4,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartOutPrimary,
                            unfocusedBorderColor = PartOutBorder,
                            focusedContainerColor = NeutralDark,
                            unfocusedContainerColor = NeutralDark
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("desc_input_field"),
                        textStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
                    )

                    // Condition Notes
                    OutlinedTextField(
                        value = editedConditionNotes,
                        onValueChange = { viewModel.updateEditedConditionNotes(it) },
                        label = { Text("Condition Flaws & Disclosures", color = PartOutTextSecondary) },
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartOutPrimary,
                            unfocusedBorderColor = PartOutBorder,
                            focusedContainerColor = NeutralDark,
                            unfocusedContainerColor = NeutralDark
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("condition_input_field"),
                        textStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
                    )
                }
            }
        }

        // Action Buttons Row (Copy All, Copy Title, Copy Description)
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Button(
                    onClick = {
                        val fullListingText = """
                            PRICE: $editedPrice
                            TITLE: $editedTitle
                            
                            DESCRIPTION:
                            $editedDescription
                            
                            CONDITION DISCLOSURE:
                            $editedConditionNotes
                            
                            (Listing generated via PartOut App)
                        """.trimIndent()
                        copyToClipboard(context, fullListingText, "Full Listing") {
                            onToastTrigger("Full listing copied to clipboard!")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PartOutPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("copy_full_listing_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy all", modifier = Modifier.padding(end = 8.dp))
                    Text("Copy Entire Listing Details", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, editedTitle, "Listing Title") {
                                onToastTrigger("Title copied to clipboard!")
                            }
                        },
                        border = BorderStroke(1.dp, PartOutBorder),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_title_button")
                    ) {
                        Text("Copy Title Only", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val descText = "$editedDescription\n\nCondition Notes: $editedConditionNotes"
                            copyToClipboard(context, descText, "Listing Description") {
                                onToastTrigger("Description copied to clipboard!")
                            }
                        },
                        border = BorderStroke(1.dp, PartOutBorder),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_desc_button")
                    ) {
                        Text("Copy Desc Only", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN — PULL GUIDE
// ==========================================

@Composable
fun PullGuideScreen(viewModel: PartOutViewModel) {
    val context = LocalContext.current
    val isLoading by viewModel.pullGuideLoading.collectAsState()
    val errorMsg by viewModel.pullGuideError.collectAsState()
    val pullGuide by viewModel.activePullGuide.collectAsState()
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val activeResult by viewModel.activeResponse.collectAsState()

    var selectedAnnotationLabel by remember { mutableStateOf<String?>(null) }
    var hasAcknowledgedSafety by remember { mutableStateOf(false) }

    // Check if safety critical
    val partName = activeResult?.partName ?: "this part"
    val category = activeResult?.category ?: ""
    val isCritical = remember(partName, category) {
        val nameLower = partName.lowercase()
        val catLower = category.lowercase()
        nameLower.contains("brake") || nameLower.contains("airbag") || nameLower.contains("fuel") || 
        nameLower.contains("voltage") || nameLower.contains("hybrid") || nameLower.contains("battery") ||
        catLower.contains("brake") || catLower.contains("electrical") && nameLower.contains("cable") ||
        nameLower.contains("srs") || nameLower.contains("inflator") || nameLower.contains("caliper") ||
        nameLower.contains("gas") || nameLower.contains("diesel") || nameLower.contains("injector")
    }

    // Rotator for fun status updates when loading
    var loadingStatusText by remember { mutableStateOf("Consulting master mechanic...") }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            val statuses = listOf(
                "Consulting master mechanic...",
                "Plotting mounting points...",
                "Calculating tool lists...",
                "Assessing safety warnings...",
                "Drafting step-by-step instructions..."
            )
            var index = 0
            while (true) {
                loadingStatusText = statuses[index]
                delay(2000)
                index = (index + 1) % statuses.size
            }
        }
    }

    // 1. Loading State Screen
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PartOutBackground)
        ) {
            // Blurred preview background
            capturedBitmap?.let { b ->
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(16.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = PartOutPrimary,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 5.dp
                )
                Text(
                    text = "GENERATING PULL GUIDE",
                    color = PartOutPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = loadingStatusText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "PartOut AI is analyzing fastener locations and safe salvage procedures.",
                    color = PartOutTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 280.dp)
                )
            }
        }
        return
    }

    // 2. Error State Screen
    if (errorMsg != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PartOutBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(NeutralDark, CircleShape)
                        .border(1.dp, PartOutBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color.Red,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Pull Guide Interrupted",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = errorMsg ?: "Unknown error occurred.",
                    color = PartOutTextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Button(
                    onClick = { viewModel.navigateToScreen(PartOutScreen.RESULTS) },
                    colors = ButtonDefaults.buttonColors(containerColor = PartOutPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Back to Specs", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val guide = pullGuide ?: return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PartOutBackground),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back Header Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.navigateToScreen(PartOutScreen.RESULTS) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PartOutPrimary)
                Text("Back to Specs", color = PartOutPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Title Row
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "SALVAGE PROCEDURES HUD",
                    color = PartOutPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Pull Guide: $partName",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Interactive Photo with Markers Overlay
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, PartOutBorder, RoundedCornerShape(16.dp))
            ) {
                Column {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        capturedBitmap?.let { b ->
                            Image(
                                bitmap = b.asImageBitmap(),
                                contentDescription = "Part Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Drawing markers scaled from 0-1000
                        guide.annotations.forEach { annotation ->
                            val xPosFraction = annotation.point.x / 1000f
                            val yPosFraction = annotation.point.y / 1000f

                            val sizeDp = 32.dp
                            val isSelected = selectedAnnotationLabel == annotation.label

                            Box(
                                modifier = Modifier
                                    .absoluteOffset(
                                        x = (maxWidth * xPosFraction) - (sizeDp / 2),
                                        y = (maxHeight * yPosFraction) - (sizeDp / 2)
                                    )
                                    .size(sizeDp)
                                    .background(
                                        if (isSelected) PartOutPrimary.copy(alpha = 0.9f) else PartOutPrimary,
                                        CircleShape
                                    )
                                    .border(2.dp, Color.White, CircleShape)
                                    .clickable {
                                        selectedAnnotationLabel = if (isSelected) null else annotation.label
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = annotation.label,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Tapped Marker Info HUD Overlay/Bar
                    val selectedAnnotation = guide.annotations.find { it.label == selectedAnnotationLabel }
                    if (selectedAnnotation != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PartOutPrimary.copy(alpha = 0.15f))
                                .border(BorderStroke(1.dp, PartOutPrimary), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(PartOutPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(selectedAnnotation.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = selectedAnnotation.description,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            IconButton(
                                onClick = { selectedAnnotationLabel = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Empty Annotations Warning/Hint
        if (guide.annotations.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NeutralDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(1.dp, PartOutBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Hint", tint = PartOutPrimary)
                        Text(
                            text = "Get closer to the part with mounting points visible for on-photo markers.",
                            color = PartOutTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // Tools Needed & Time Estimate Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, PartOutBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "Tools", tint = PartOutPrimary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "TOOLS & TIME ESTIMATE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        // Time estimate badge
                        Box(
                            modifier = Modifier
                                .background(PartOutPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, PartOutPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${guide.timeEstimateMinutes} MINS",
                                color = PartOutPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Tools bullet list
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        guide.toolsNeeded.forEach { tool ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(PartOutPrimary, CircleShape)
                                )
                                Text(text = tool, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Brakes / SRS / High-Voltage Block/Acknowledgment Guard Screen
        if (isCritical && !hasAcknowledgedSafety) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(1.5.dp, Color.Red, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.Red.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, Color.Red, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color.Red, modifier = Modifier.size(28.dp))
                        }

                        Text(
                            text = "Safety-Critical System",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "This part belongs to a safety-critical category (Brakes, Airbags, Fuel, or High-Voltage). Disassembling these systems poses severe injury or fire risks.",
                            color = PartOutTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { hasAcknowledgedSafety = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("I Understand, Show Steps", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        } else {
            // 3. Numbered Removal Steps
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "REMOVAL STEPS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    guide.removalSteps.forEach { step ->
                        val isHighlighted = step.stepNumber.toString() == selectedAnnotationLabel

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isHighlighted) PartOutPrimary.copy(alpha = 0.1f) else PartOutSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isHighlighted) PartOutPrimary else PartOutBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    // Mirror selection on photo
                                    selectedAnnotationLabel = if (isHighlighted) null else step.stepNumber.toString()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Step number badge
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(
                                            if (isHighlighted) PartOutPrimary else PartOutPrimary.copy(alpha = 0.15f),
                                            CircleShape
                                        )
                                        .border(
                                            1.dp,
                                            if (isHighlighted) Color.White else PartOutPrimary,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${step.stepNumber}",
                                        color = if (isHighlighted) Color.White else PartOutPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }

                                // Instruction and specific tools
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = step.instruction,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
                                    )

                                    if (step.tools.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Build,
                                                contentDescription = "Tool required",
                                                tint = PartOutPrimary,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = "Tools: ${step.tools.joinToString(", ")}",
                                                color = PartOutPrimary,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. "Protect the resale value" tips callout (Orange-bordered)
            if (guide.preserveValueTips.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .border(1.5.dp, PartOutPrimary, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.LocalOffer, contentDescription = "Value Preservation", tint = PartOutPrimary, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "PROTECT THE RESALE VALUE",
                                    color = PartOutPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            guide.preserveValueTips.forEach { tip ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("•", color = PartOutPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(text = tip, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Safety warnings callout (Red-bordered)
            if (guide.safetyWarnings.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .border(1.5.dp, Color.Red, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Safety Warnings", tint = Color.Red, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "SAFETY WARNINGS",
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            guide.safetyWarnings.forEach { warning ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("•", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(text = warning, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Fixed Disclaimer (Bottom)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeutralDark)
                    .border(BorderStroke(1.dp, PartOutBorder))
                    .padding(16.dp)
            ) {
                Text(
                    text = "AI-generated guidance — verify against a repair manual. Do not rely on this for brakes, airbags, fuel, or high-voltage systems.",
                    color = Color.Red.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ==========================================
// GARAGE LOGS TAB SCREEN
// ==========================================

@Composable
fun GarageLogsTabScreen(
    historyList: List<ScanHistoryItem>,
    onItemClick: (ScanHistoryItem) -> Unit,
    onStartCapture: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PartOutBackground)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = "GARAGE LOGS",
                color = PartOutPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Session History",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "In-memory scans recorded during this active session. Clears when the app is closed.",
                color = PartOutTextSecondary,
                fontSize = 13.sp,
                lineHeight = 17.sp
            )
        }

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(NeutralDark, CircleShape)
                            .border(1.dp, PartOutBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Empty History",
                            tint = PartOutPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        "No Parts Scanned Yet",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Capture a photo of any automotive part to estimate its price and write auto-listings instantly.",
                        color = PartOutTextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onStartCapture,
                        colors = ButtonDefaults.buttonColors(containerColor = PartOutPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("history_start_capture_button")
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Camera Icon", modifier = Modifier.padding(end = 8.dp))
                        Text("Capture Your First Part", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyList) { item ->
                    HistoryRowItem(
                        item = item,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryRowItem(
    item: ScanHistoryItem,
    onClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("h:mm a, MMM dd", Locale.US) }
    val formattedTime = formatter.format(Date(item.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = PartOutSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PartOutBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("history_item_row_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rounded Thumbnail Image
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeutralDark)
            ) {
                item.bitmap?.let { b ->
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Description info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.response.category?.uppercase() ?: "OTHER",
                        color = PartOutPrimary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formattedTime,
                        color = PartOutTextSecondary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.response.partName ?: "Unidentified Part",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.response.conditionGrade ?: "Good",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "$${String.format(Locale.US, "%.0f", item.editedPrice)} Typical",
                        color = PartOutPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Chevron trailing indicator
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Open item details",
                tint = PartOutTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==========================================
// PART-OUTS MODULE & SCREENS
// ==========================================

@Composable
fun PartOutsTabScreen(
    sessions: List<PartOutSession>,
    historyList: List<ScanHistoryItem>,
    activeSessionId: String?,
    onCreateSession: (String, String?, String?) -> Unit,
    onSelectSession: (PartOutSession) -> Unit,
    onToggleActive: (PartOutSession) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PartOutBackground)
            .padding(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PART-OUT MODULE",
                    color = PartOutPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Salvage Sessions",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PartOutPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("new_part_out_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Session", modifier = Modifier.padding(end = 4.dp))
                Text("New Session", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Text(
            text = "Track a salvage vehicle's pull list, list status, and total estimated payout. Scans are automatically attached to the active session.",
            color = PartOutTextSecondary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(NeutralDark, CircleShape)
                            .border(1.dp, PartOutBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = "Empty Part-Outs",
                            tint = PartOutPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        "No Active Part-Outs",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Create a new vehicle session to start cataloging salvaged parts with targeted Gemini fitment tracking.",
                        color = PartOutTextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions) { session ->
                    val scansForSession = historyList.filter { it.sessionId == session.id }
                    val partCount = scansForSession.size
                    val totalValue = scansForSession.sumOf { it.editedPrice }
                    val isActive = activeSessionId == session.id

                    Card(
                        colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isActive) PartOutPrimary else PartOutBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectSession(session) }
                            .testTag("session_card_${session.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Top Row: Title & Active Chip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.vehicleName,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!session.mileage.isNullOrBlank() || !session.notes.isNullOrBlank()) {
                                        Text(
                                            text = listOfNotNull(
                                                session.mileage?.let { "$it mi" },
                                                session.notes?.takeIf { it.isNotBlank() }
                                            ).joinToString(" • "),
                                            color = PartOutTextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .background(PartOutPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .border(1.dp, PartOutPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "ACTIVE TARGET",
                                            color = PartOutPrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Divider(color = PartOutBorder, thickness = 1.dp)

                            // Stats & Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Stats
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column {
                                        Text("PARTS", color = PartOutTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        Text("$partCount cataloged", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Column {
                                        Text("EST. PAYOUT", color = PartOutPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Text("$${String.format(Locale.US, "%.0f", totalValue)}", color = PartOutPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                    }
                                }

                                // Interactive action buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Target Toggle Button
                                    IconButton(
                                        onClick = { onToggleActive(session) },
                                        modifier = Modifier
                                            .background(if (isActive) PartOutPrimary.copy(alpha = 0.1f) else NeutralDark, CircleShape)
                                            .border(1.dp, if (isActive) PartOutPrimary else PartOutBorder, CircleShape)
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isActive) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Toggle Active Scanner",
                                            tint = if (isActive) PartOutPrimary else PartOutTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Delete Button
                                    IconButton(
                                        onClick = { onDeleteSession(session.id) },
                                        modifier = Modifier
                                            .background(NeutralDark, CircleShape)
                                            .border(1.dp, PartOutBorder, CircleShape)
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Session",
                                            tint = Color.Red.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // New Session Dialog
    if (showCreateDialog) {
        var vehicleText by remember { mutableStateOf("") }
        var mileageText by remember { mutableStateOf("") }
        var notesText by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PartOutBorder, RoundedCornerShape(16.dp))
                    .padding(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "NEW SALVAGE VEHICLE",
                        color = PartOutPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Text(
                        text = "Create Session",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Vehicle Text Field
                        OutlinedTextField(
                            value = vehicleText,
                            onValueChange = { vehicleText = it },
                            label = { Text("Year / Make / Model / Trim", color = PartOutTextSecondary) },
                            placeholder = { Text("e.g. 2011 Honda Accord EX-L V6", color = PartOutTextSecondary.copy(alpha = 0.5f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PartOutPrimary,
                                unfocusedBorderColor = PartOutBorder,
                                focusedContainerColor = NeutralDark,
                                unfocusedContainerColor = NeutralDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("vehicle_input")
                        )

                        // Mileage Text Field
                        OutlinedTextField(
                            value = mileageText,
                            onValueChange = { mileageText = it },
                            label = { Text("Mileage (Optional)", color = PartOutTextSecondary) },
                            placeholder = { Text("e.g. 145,000", color = PartOutTextSecondary.copy(alpha = 0.5f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PartOutPrimary,
                                unfocusedBorderColor = PartOutBorder,
                                focusedContainerColor = NeutralDark,
                                unfocusedContainerColor = NeutralDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("mileage_input")
                        )

                        // Notes Text Field
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            label = { Text("Notes (Optional)", color = PartOutTextSecondary) },
                            placeholder = { Text("e.g. Front collision damage. Engine intact.", color = PartOutTextSecondary.copy(alpha = 0.5f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PartOutPrimary,
                                unfocusedBorderColor = PartOutBorder,
                                focusedContainerColor = NeutralDark,
                                unfocusedContainerColor = NeutralDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("notes_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCreateDialog = false },
                            border = BorderStroke(1.dp, PartOutBorder),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (vehicleText.isNotBlank()) {
                                    onCreateSession(vehicleText, mileageText.takeIf { it.isNotBlank() }, notesText.takeIf { it.isNotBlank() })
                                    showCreateDialog = false
                                }
                            },
                            enabled = vehicleText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = PartOutPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(46.dp).testTag("save_session_button")
                        ) {
                            Text("Create", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionDetailScreen(
    session: PartOutSession,
    historyList: List<ScanHistoryItem>,
    onBackClick: () -> Unit,
    onTogglePulled: (String) -> Unit,
    onToggleListed: (String) -> Unit
) {
    val sessionScans = historyList.filter { it.sessionId == session.id }

    // Rank scans by value-to-effort (highest price and easiest removal first)
    val sortedScans = remember(sessionScans) {
        sessionScans.sortedWith(
            compareByDescending<ScanHistoryItem> { item -> item.editedPrice }
                .thenBy { item ->
                    when (item.response.removalEffort?.grade?.lowercase()?.trim()) {
                        "easy" -> 1
                        "moderate" -> 2
                        "hard" -> 3
                        else -> 4
                    }
                }
        )
    }

    val totalEstValue = sessionScans.sumOf { it.editedPrice }
    val listedValue = sessionScans.filter { it.listed }.sumOf { it.editedPrice }
    val progressFraction = if (totalEstValue > 0) (listedValue / totalEstValue).toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PartOutBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back Button Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBackClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PartOutPrimary)
            Text("Back to Sessions", color = PartOutPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        // Vehicle info panel
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "SALVAGE VEHICLE SESSION",
                color = PartOutPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Text(
                text = session.vehicleName,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (!session.mileage.isNullOrBlank() || !session.notes.isNullOrBlank()) {
                Text(
                    text = listOfNotNull(
                        session.mileage?.let { "Mileage: $it mi" },
                        session.notes?.takeIf { it.isNotBlank() }
                    ).joinToString(" | "),
                    color = PartOutTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Progress line panel
        Card(
            colors = CardDefaults.cardColors(containerColor = PartOutSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PartOutBorder, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LISTING PAYOUT PROGRESS",
                        color = PartOutTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$${String.format(Locale.US, "%.0f", listedValue)} of $${String.format(Locale.US, "%.0f", totalEstValue)} listed",
                        color = PartOutPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = PartOutPrimary,
                    trackColor = PartOutBorder
                )
            }
        }

        // Pull List Header
        Text(
            text = "PULL LIST (Ranked by Value-to-Effort)",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )

        if (sortedScans.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.BuildCircle,
                        contentDescription = "Empty list",
                        tint = PartOutTextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No Scanned Parts Associated",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Set this vehicle as your Active Scanner, then go to the Scan tab to photograph and add parts.",
                        color = PartOutTextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sortedScans) { item ->
                    PullListRow(
                        item = item,
                        onTogglePulled = { onTogglePulled(item.id) },
                        onToggleListed = { onToggleListed(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun PullListRow(
    item: ScanHistoryItem,
    onTogglePulled: () -> Unit,
    onToggleListed: () -> Unit
) {
    val effort = item.response.removalEffort
    val effortColor = when (effort?.grade?.lowercase()?.trim()) {
        "easy" -> Color(0xFF4CAF50)
        "moderate" -> Color(0xFFFFC107)
        "hard" -> Color(0xFFF44336)
        else -> PartOutPrimary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PartOutSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PartOutBorder, RoundedCornerShape(10.dp))
            .testTag("pull_row_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeutralDark)
            ) {
                item.bitmap?.let { b ->
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Part Info (Part Name, Price, Effort badge)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.response.partName ?: "Unidentified Part",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "$${String.format(Locale.US, "%.0f", item.editedPrice)}",
                        color = PartOutPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )

                    if (effort != null) {
                        Box(
                            modifier = Modifier
                                .background(effortColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, effortColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = effort.grade.uppercase(),
                                color = effortColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Checkboxes Section (Pulled & Listed)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pulled Checklist Column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.clickable { onTogglePulled() }
                ) {
                    Text(
                        "PULLED",
                        color = if (item.pulled) Color(0xFF4CAF50) else PartOutTextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Checkbox(
                        checked = item.pulled,
                        onCheckedChange = { onTogglePulled() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF4CAF50),
                            uncheckedColor = PartOutBorder
                        ),
                        modifier = Modifier.size(24.dp).testTag("pulled_check_${item.id}")
                    )
                }

                // Listed Checklist Column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.clickable { onToggleListed() }
                ) {
                    Text(
                        "LISTED",
                        color = if (item.listed) PartOutPrimary else PartOutTextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Checkbox(
                        checked = item.listed,
                        onCheckedChange = { onToggleListed() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = PartOutPrimary,
                            uncheckedColor = PartOutBorder
                        ),
                        modifier = Modifier.size(24.dp).testTag("listed_check_${item.id}")
                    )
                }
            }
        }
    }
}
