package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.MmqaUiState
import com.example.ui.MmqaViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun ChatScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean,
    onPickDocument: ((ChatAttachmentDraft) -> Unit) -> Unit,
    onPickPhoto: ((ChatAttachmentDraft) -> Unit) -> Unit,
    onSaveAttachment: (fileName: String, mimeType: String, dataUrl: String) -> Unit
) {
    val channels = db.chatChannels
    val channel = channels.firstOrNull { it.id == uiState.chatChannel } ?: channels.firstOrNull()
    val channelId = channel?.id ?: "CHN-UMUM"
    val q = uiState.chatSearch.trim().lowercase()
    val messages = db.chatMessages.filter { m ->
        m.channelId == channelId &&
            (q.isEmpty() ||
                m.text.lowercase().contains(q) ||
                m.senderName.lowercase().contains(q) ||
                m.attachmentName.lowercase().contains(q))
    }
    val pinnedMsg = messages.firstOrNull { it.pinned }
    val usersById = remember(db.users) { db.users.associateBy { it.id } }

    val canSend = viewModel.canAccessAnakSub("chat_send")
    val canAttach = viewModel.canAccessAnakSub("chat_attach")
    val canAddChannel = viewModel.canAccessAnakSub("chat_add_channel")
    val canPin = viewModel.canAccessAnakSub("chat_pin")
    val canDeleteAny = viewModel.canAccessAnakSub("chat_delete")
    val isDewan = uiState.currentUser?.role == "dewan_harian"

    var messageInput by remember { mutableStateOf("") }
    var pendingAttachment by remember { mutableStateOf<ChatAttachmentDraft?>(null) }
    var showAddChannelModal by remember { mutableStateOf(false) }

    val channelPanel: @Composable () -> Unit = {
        if (isWideScreen) {
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.padding(14.dp).fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💬 Ruang Diskusi",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate900
                        )
                        if (canAddChannel) {
                            Surface(
                                color = Emerald100,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .clickable { showAddChannelModal = true }
                                    .testTag("add_chat_channel_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Tambah Grup",
                                        tint = Emerald900,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Grup",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Emerald900
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        channels.forEach { ch ->
                            val active = ch.id == channelId
                            ChannelSelectCard(
                                channel = ch,
                                active = active,
                                onClick = {
                                    viewModel.updateState { it.copy(chatChannel = ch.id, chatReplyTo = null) }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                color = Emerald950,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    channels.forEach { ch ->
                        val active = ch.id == channelId
                        Surface(
                            color = if (active) Amber400 else Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, if (active) Amber300 else Color.White.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clickable {
                                    viewModel.updateState { it.copy(chatChannel = ch.id, chatReplyTo = null) }
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = ch.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (active) Emerald950 else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (canAddChannel) {
                        Surface(
                            color = Emerald700,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clickable { showAddChannelModal = true }
                                .testTag("add_chat_channel_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Tambah Grup",
                                    tint = Amber300,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Grup",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Amber300
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val chatBoxPanel: @Composable (Modifier) -> Unit = { panelModifier ->
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size, channelId) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        Surface(
            color = ChatWallpaper,
            shape = RoundedCornerShape(0.dp),
            modifier = panelModifier
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Chat Emerald950
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Emerald950)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel?.name ?: "Majelis Umum MMQA AR2",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Amber300,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = channel?.desc ?: "",
                                fontSize = 11.sp,
                                color = Emerald100,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        StatusPill("⚡ Auto-Sync", Emerald800, Emerald100)
                    }
                    OutlinedTextField(
                        value = uiState.chatSearch,
                        onValueChange = { s -> viewModel.updateState { it.copy(chatSearch = s) } },
                        placeholder = {
                            Text("🔍 Cari pesan atau file...", fontSize = 11.sp, color = Emerald100.copy(alpha = 0.8f))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.14f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Amber300.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = Amber300
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Pinned Message Banner
                if (pinnedMsg != null) {
                    Surface(
                        color = Amber100,
                        border = BorderStroke(1.dp, Amber200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            val pinPreview = pinnedMsg.text.ifBlank { "📎 ${pinnedMsg.attachmentName}" }
                            Text(
                                text = "📌 Sematkan (${pinnedMsg.senderName}): $pinPreview",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Amber950,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (canPin) {
                                TextButton(
                                    onClick = { viewModel.togglePinChatMessage(pinnedMsg.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Lepas", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Amber900)
                                }
                            }
                        }
                    }
                }

                // Messages List
                if (messages.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Text(
                                text = "Belum ada pesan di ruang diskusi ini. Mulai percakapan atau kirim file PDF/dokumen!",
                                fontSize = 12.sp,
                                color = Slate500,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(messages, key = { it.id }) { m ->
                            val isMine = m.senderId == uiState.currentUser?.id
                            val senderAccount = usersById[m.senderId]
                            val displayName = senderAccount?.name ?: m.senderName
                            val avatarB64 = senderAccount?.avatarBase64 ?: ""
                            ChatMessageBubble(
                                msg = m,
                                senderDisplayName = displayName,
                                senderAvatarBase64 = avatarB64,
                                isMine = isMine,
                                canPin = canPin,
                                canDelete = canDeleteAny && (isMine || isDewan),
                                onReply = { viewModel.setReplyChat(m) },
                                onReact = { emoji -> viewModel.reactChatMessage(m.id, emoji) },
                                onTogglePin = { viewModel.togglePinChatMessage(m.id) },
                                onDelete = { viewModel.deleteChatMessage(m.id) },
                                onDownloadAttachment = {
                                    if (m.attachmentDataUrl.isNotBlank()) {
                                        onSaveAttachment(m.attachmentName, m.attachmentMime, m.attachmentDataUrl)
                                    }
                                }
                            )
                        }
                    }
                }

                // Reply Preview Bar
                uiState.chatReplyTo?.let { rep ->
                    Surface(
                        color = Emerald50,
                        border = BorderStroke(1.dp, Emerald100),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Membalas ${rep.senderName}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Emerald900
                                )
                                Text(
                                    text = rep.text,
                                    fontSize = 11.sp,
                                    color = Slate700,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setReplyChat(null) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Batal Balas", tint = Slate700, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Pending Attachment Preview Bar
                pendingAttachment?.let { att ->
                    Surface(
                        color = Amber50,
                        border = BorderStroke(1.dp, Amber200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (att.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.Description,
                                contentDescription = "Lampiran terpilih",
                                tint = Emerald800,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = att.fileName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatFileSize(att.sizeBytes)} • Siap dikirim",
                                    fontSize = 10.sp,
                                    color = Amber900
                                )
                            }
                            IconButton(
                                onClick = { pendingAttachment = null },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus Lampiran", tint = Rose700, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Input Bar
                if (canSend || canAttach) {
                    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (canAttach) {
                                IconButton(
                                    onClick = { onPickDocument { draft -> pendingAttachment = draft } },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Slate100)
                                        .testTag("chat_attach_doc_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Lampirkan PDF atau Dokumen",
                                        tint = Emerald900,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onPickPhoto { draft -> pendingAttachment = draft } },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Emerald50)
                                        .testTag("chat_attach_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Lampirkan Foto",
                                        tint = Emerald800,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = messageInput,
                                onValueChange = { messageInput = it },
                                enabled = canSend,
                                placeholder = {
                                    Text(
                                        if (pendingAttachment != null) "Tulis keterangan file (opsional)..." else "Ketik pesan atau kirim PDF...",
                                        fontSize = 12.sp
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (messageInput.isNotBlank() || pendingAttachment != null) {
                                        viewModel.sendChatMessage(messageInput, pendingAttachment)
                                        messageInput = ""
                                        pendingAttachment = null
                                    }
                                }),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_message_input")
                            )
                            IconButton(
                                onClick = {
                                    if (messageInput.isNotBlank() || pendingAttachment != null) {
                                        viewModel.sendChatMessage(messageInput, pendingAttachment)
                                        messageInput = ""
                                        pendingAttachment = null
                                    }
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Emerald800)
                                    .testTag("chat_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Kirim Pesan",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    Surface(color = Slate100, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Mode baca saja — pengiriman pesan dibatasi oleh Admin.",
                            fontSize = 11.sp,
                            color = Slate500,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }

    if (isWideScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(290.dp).fillMaxHeight()) { channelPanel() }
            chatBoxPanel(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            channelPanel()
            chatBoxPanel(Modifier.weight(1f).fillMaxWidth())
        }
    }

    if (showAddChannelModal) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        MmqaModalDialog(title = "💬 Buat Ruang Diskusi / Channel Baru", onDismiss = { showAddChannelModal = false }) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama Grup / Channel") },
                placeholder = { Text("Misal: Panitia Ujian Semester") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Deskripsi Singkat") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showAddChannelModal = false }) { Text("Batal") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.addChatChannel(name, desc)
                            showAddChannelModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
                ) { Text("Buat Grup", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

@Composable
private fun ChannelSelectCard(
    channel: ChatChannelItem,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) Emerald900 else Slate50,
        border = BorderStroke(1.dp, if (active) Emerald900 else Slate200),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = channel.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (active) Amber300 else Slate900
            )
            Text(
                text = channel.desc,
                fontSize = 11.sp,
                color = if (active) Emerald100 else Slate500,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(
    msg: ChatMessageItem,
    senderDisplayName: String,
    senderAvatarBase64: String,
    isMine: Boolean,
    canPin: Boolean,
    canDelete: Boolean,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onDownloadAttachment: () -> Unit
) {
    val isImageAttachment = msg.attachmentMime.startsWith("image/") && msg.attachmentDataUrl.isNotBlank()
    val hasDocAttachment = msg.attachmentName.isNotBlank() && !isImageAttachment

    Row(
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!isMine) {
            UserAvatarImage(
                name = senderDisplayName,
                avatarBase64 = senderAvatarBase64,
                size = 34.dp,
                borderColor = Emerald700.copy(alpha = 0.45f)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Surface(
            color = if (isMine) ChatBubbleMine else Color.White,
            border = BorderStroke(1.dp, if (isMine) Emerald100 else Slate200),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = senderDisplayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Emerald900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = msg.senderRole, fontSize = 9.sp, color = Slate500, maxLines = 1)
                }

                msg.replyTo?.let { rep ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(rep.senderName, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Emerald800)
                            Text(rep.text, fontSize = 10.sp, color = Slate700, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // Inline Image Attachment Preview
                if (isImageAttachment) {
                    val imgBitmap = remember(msg.attachmentDataUrl) {
                        decodeBase64ImageBitmap(msg.attachmentDataUrl)
                    }
                    if (imgBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Image(
                                    bitmap = imgBitmap,
                                    contentDescription = msg.attachmentName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp, max = 210.dp)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Slate50)
                                        .clickable(onClick = onDownloadAttachment)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${msg.attachmentName} (${formatFileSize(msg.attachmentSize)})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate700,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Simpan Foto",
                                        tint = Emerald800,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // PDF / Word / Excel / Document Attachment Card
                if (hasDocAttachment) {
                    val extBadge = fileExtLabel(msg.attachmentName, msg.attachmentMime)
                    val isPdf = extBadge == "PDF"
                    Surface(
                        color = if (isPdf) Rose50 else Slate50,
                        border = BorderStroke(1.dp, if (isPdf) Rose200 else Slate200),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDownloadAttachment)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Surface(
                                color = if (isPdf) Rose600 else Emerald800,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = extBadge,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = msg.attachmentName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate900,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatFileSize(msg.attachmentSize)} • Ketuk untuk simpan",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Unduh Dokumen",
                                tint = Emerald800,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (msg.text.isNotBlank()) {
                    Text(text = msg.text, fontSize = 12.sp, lineHeight = 17.sp, color = Slate900)
                }

                if (msg.reactions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        msg.reactions.forEach { (emoji, count) ->
                            Surface(
                                color = Color.White.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, Slate200),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "$emoji $count",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Balas",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald800,
                            modifier = Modifier.clickable(onClick = onReply)
                        )
                        Text("👍", fontSize = 11.sp, modifier = Modifier.clickable { onReact("👍") })
                        Text("🤲", fontSize = 11.sp, modifier = Modifier.clickable { onReact("🤲") })
                        if (canPin) {
                            Text("📌", fontSize = 11.sp, modifier = Modifier.clickable(onClick = onTogglePin))
                        }
                        if (canDelete) {
                            Text(
                                text = "Hapus",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Rose700,
                                modifier = Modifier.clickable(onClick = onDelete)
                            )
                        }
                    }
                    Text(
                        text = "${msg.time} ✓✓",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500
                    )
                }
            }
        }

        if (isMine) {
            Spacer(modifier = Modifier.width(6.dp))
            UserAvatarImage(
                name = senderDisplayName,
                avatarBase64 = senderAvatarBase64,
                size = 34.dp,
                borderColor = Amber400
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> "File"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format("%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
}

private fun fileExtLabel(name: String, mime: String): String {
    val ext = name.substringAfterLast(".", "").uppercase()
    return when {
        ext.isNotBlank() && ext.length <= 4 -> ext
        mime.contains("pdf") -> "PDF"
        mime.contains("word") -> "DOC"
        mime.contains("excel") || mime.contains("spreadsheet") -> "XLS"
        mime.contains("presentation") -> "PPT"
        else -> "FILE"
    }
}
