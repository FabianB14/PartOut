package com.example.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MechanicMessage
import com.example.ui.theme.NeutralDark
import com.example.ui.theme.PartOutBackground
import com.example.ui.theme.PartOutBorder
import com.example.ui.theme.PartOutPrimary
import com.example.ui.theme.PartOutSurface
import com.example.ui.theme.PartOutTextSecondary

// ==========================================
// AI MECHANIC — REPAIR CHAT TAB
// ==========================================

@Composable
fun MechanicScreen(viewModel: PartOutViewModel) {
    val context = LocalContext.current
    val messages by viewModel.mechanicMessages.collectAsState()
    val input by viewModel.mechanicInput.collectAsState()
    val sending by viewModel.mechanicSending.collectAsState()
    val attachment by viewModel.mechanicAttachment.collectAsState()
    val vehicle by viewModel.mechanicVehicle.collectAsState()

    val listState = rememberLazyListState()

    // Keep the newest message in view
    LaunchedEffect(messages.size, sending) {
        val count = messages.size + if (sending) 1 else 0
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                if (bitmap != null) {
                    viewModel.setMechanicAttachment(bitmap)
                } else {
                    Toast.makeText(context, "Could not load image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PartOutBackground)
            .imePadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AI MECHANIC",
                    color = PartOutPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Repair Assistant",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Row {
                IconButton(onClick = { viewModel.openApiKeyDialog() }) {
                    Icon(Icons.Default.Key, contentDescription = "API Key Settings", tint = PartOutTextSecondary)
                }
                if (messages.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearMechanicChat() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat", tint = PartOutTextSecondary)
                    }
                }
            }
        }

        // Vehicle context field — every answer is anchored to this vehicle
        OutlinedTextField(
            value = vehicle,
            onValueChange = { viewModel.setMechanicVehicle(it) },
            label = { Text("Your vehicle", color = PartOutTextSecondary, fontSize = 12.sp) },
            placeholder = {
                Text("e.g. 1997 Jeep Wrangler TJ 4.0L", color = PartOutTextSecondary.copy(alpha = 0.5f), fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(Icons.Default.DirectionsCar, contentDescription = "Vehicle", tint = PartOutPrimary, modifier = Modifier.size(20.dp))
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = PartOutPrimary,
                unfocusedBorderColor = PartOutBorder,
                focusedContainerColor = NeutralDark,
                unfocusedContainerColor = NeutralDark
            ),
            shape = RoundedCornerShape(10.dp),
            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("mechanic_vehicle_input")
        )

        // Conversation
        if (messages.isEmpty() && !sending) {
            MechanicEmptyState(
                onSuggestionClick = { suggestion -> viewModel.setMechanicInput(suggestion) },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MechanicBubble(message)
                }
                if (sending) {
                    item(key = "thinking") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.border(1.dp, PartOutBorder, RoundedCornerShape(14.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = PartOutPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Wrenching on it…",
                                        color = PartOutTextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Attachment preview
        if (attachment != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    Image(
                        bitmap = attachment!!.asImageBitmap(),
                        contentDescription = "Attached photo",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, PartOutPrimary, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .clickable { viewModel.setMechanicAttachment(null) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
                Text(
                    text = "Photo attached — the mechanic will look at it.",
                    color = PartOutTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PartOutSurface)
                .border(BorderStroke(1.dp, PartOutBorder))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = "Attach photo",
                    tint = PartOutPrimary
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = { viewModel.setMechanicInput(it) },
                placeholder = {
                    Text(
                        "Describe the problem or ask a question…",
                        color = PartOutTextSecondary,
                        fontSize = 13.sp
                    )
                },
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = PartOutPrimary,
                    unfocusedBorderColor = PartOutBorder,
                    focusedContainerColor = NeutralDark,
                    unfocusedContainerColor = NeutralDark
                ),
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("mechanic_input_field")
            )

            val canSend = !sending && (input.isNotBlank() || attachment != null)
            IconButton(
                onClick = { viewModel.sendMechanicMessage() },
                enabled = canSend,
                modifier = Modifier
                    .background(
                        if (canSend) PartOutPrimary else PartOutBorder,
                        CircleShape
                    )
                    .testTag("mechanic_send_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun MechanicBubble(message: MechanicMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) PartOutPrimary.copy(alpha = 0.18f) else PartOutSurface
            ),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            ),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .border(
                    1.dp,
                    when {
                        message.isError -> Color.Red.copy(alpha = 0.7f)
                        isUser -> PartOutPrimary.copy(alpha = 0.5f)
                        else -> PartOutBorder
                    },
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 14.dp
                    )
                )
        ) {
            Column {
                message.bitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Attached photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = if (message.isError) Color.Red.copy(alpha = 0.9f) else Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MechanicEmptyState(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        "My engine cranks but won't start. Where do I begin?",
        "I hear a grinding noise when I brake. What should I check?",
        "The check engine light just came on. What do I do first?",
        "It shakes badly at highway speed. How do I diagnose it?"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(NeutralDark, CircleShape)
                .border(1.dp, PartOutBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Handyman,
                contentDescription = "AI Mechanic",
                tint = PartOutPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What are we fixing today?",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Describe the symptom, attach a photo of the part, and get a diagnosis with step-by-step repair help for your vehicle.",
            color = PartOutTextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        suggestions.forEach { suggestion ->
            Card(
                colors = CardDefaults.cardColors(containerColor = PartOutSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, PartOutBorder, RoundedCornerShape(12.dp))
                    .clickable { onSuggestionClick(suggestion) }
            ) {
                Text(
                    text = suggestion,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "AI guidance — verify torque specs and anything safety-critical against a repair manual.",
            color = PartOutTextSecondary.copy(alpha = 0.7f),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
