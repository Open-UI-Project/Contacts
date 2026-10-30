package org.openui.contacts.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import org.openui.contacts.R
import org.openui.contacts.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.openui.contacts.data.ContactManager
import org.openui.contacts.data.ContactPreferences
import org.openui.contacts.data.SimAccountInfo
import org.openui.contacts.data.TrashContact
import org.openui.contacts.model.CallLogItem
import org.openui.contacts.model.ContactItem
import org.openui.contacts.model.getAvatarColor

private val StarBadgeColor = Color(0xFFFFB800)
private val ContactRowShape = RoundedCornerShape(22.dp)
private val SearchFieldShape = RoundedCornerShape(20.dp)
private val DialogFieldShape = RoundedCornerShape(16.dp)
private val ActionButtonShape = RoundedCornerShape(20.dp)
private val ContactCircleShape = CircleShape
private val SmsBlueIconColor = Color(0xFF2A7FF6)

@Composable
fun SmsContactsScreen(
    contacts: List<ContactItem>,
    hasContactsPermission: Boolean,
    onContactClick: (number: String, name: String?, photoUri: String?) -> Unit,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit,
    onFetchCallLogs: suspend (String) -> List<CallLogItem>,
    onAddContact: suspend (name: String, number: String, extraNumbers: List<String>, storageTarget: String) -> Boolean = { _, _, _, _ -> false },
    onThemeModeChanged: (org.openui.contacts.data.AppThemeMode) -> Unit = {},
    onDetailStateChanged: (Boolean) -> Unit = {},
    initialAddName: String? = null,
    initialAddNumber: String? = null,
    autoOpenAddDialog: Boolean = false,
    initialViewContact: ContactItem? = null,
    isPickerMode: Boolean = false,
    onPickContact: ((ContactItem) -> Unit)? = null,
    onCancelPicker: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val prefs = remember(context) { ContactPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var expandedContactId by remember { mutableStateOf<Long?>(null) }
    var selectedInfoContact by remember { mutableStateOf<ContactItem?>(null) }
    var showTrashDialog by remember { mutableStateOf(false) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var currentThemeMode by remember { mutableStateOf(prefs.getThemeMode()) }
    var trashVersion by remember { mutableStateOf(0) }

    var currentStorageTarget by remember { mutableStateOf(prefs.getStorageTarget()) }
    var simAccounts by remember { mutableStateOf<List<SimAccountInfo>>(emptyList()) }

    var showAddContactDialog by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var newContactNumber by remember { mutableStateOf("") }
    var newContactExtraNumbers by remember { mutableStateOf(listOf<String>()) }

    var pendingCallNumber by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(autoOpenAddDialog, initialAddName, initialAddNumber) {
        if (autoOpenAddDialog) {
            newContactName = initialAddName ?: ""
            newContactNumber = initialAddNumber ?: ""
            showAddContactDialog = true
        }
    }

    LaunchedEffect(initialViewContact) {
        if (initialViewContact != null) {
            selectedInfoContact = initialViewContact
        }
    }

    LaunchedEffect(Unit) {
        simAccounts = ContactManager.getAvailableSimAccounts(context)
    }

    val makeDirectCall: (String) -> Unit = remember(context) {
        { phoneNumber ->
            try {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
            } catch (e: Exception) {
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.contacts_call_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val numberToCall = pendingCallNumber
        pendingCallNumber = null
        if (isGranted && !numberToCall.isNullOrBlank()) {
            makeDirectCall(numberToCall)
        } else if (!numberToCall.isNullOrBlank()) {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numberToCall")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
            } catch (_: Exception) {}
        }
    }

    val initiateCall: (String) -> Unit = remember(context) {
        { phoneNumber ->
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                makeDirectCall(phoneNumber)
            } else {
                pendingCallNumber = phoneNumber
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }

    LaunchedEffect(selectedInfoContact) {
        onDetailStateChanged(selectedInfoContact != null)
    }

    val activeContacts = remember(contacts, prefs, trashVersion) {
        contacts.filter { !prefs.isDeleted(it.number) && !prefs.isContactInTrash(it.id, it.number) }
    }

    val filteredSortedContacts = remember(activeContacts, searchQuery, showFavoritesOnly) {
        var filtered = activeContacts
        if (showFavoritesOnly) {
            filtered = filtered.filter { prefs.isFavorite(it.number) }
        }
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.number.contains(searchQuery)
            }
        }
        filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    val groupedContacts = remember(filteredSortedContacts) {
        filteredSortedContacts.groupBy { contact ->
            val firstChar = contact.name.trim().firstOrNull()?.uppercaseChar()
            if (firstChar != null && (firstChar.isLetter() || firstChar in 'А'..'Я' || firstChar == 'Ё')) {
                firstChar.toString()
            } else {
                "#"
            }
        }
    }

    BackHandler(enabled = selectedInfoContact != null || isSearchVisible || showFavoritesOnly) {
        when {
            selectedInfoContact != null -> selectedInfoContact = null
            showFavoritesOnly -> showFavoritesOnly = false
            isSearchVisible -> {
                isSearchVisible = false
                searchQuery = ""
            }
        }
    }

    AnimatedContent(
        targetState = selectedInfoContact,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(320))).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth / 3 },
                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(200))
                )
            } else {
                (slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 3 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300))).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(200))
                )
            }
        },
        label = "ContactScreenTransition"
    ) { targetContact ->
        if (targetContact != null) {
            ContactCallHistoryScreen(
                contact = targetContact,
                onBackClick = { selectedInfoContact = null },
                onFetchCallLogs = onFetchCallLogs,
                onDeleteContact = { contact ->
                    prefs.moveToTrash(contact)
                    trashVersion++
                    selectedInfoContact = null
                    Toast.makeText(context, context.getString(R.string.contacts_trash_title), Toast.LENGTH_SHORT).show()
                    onRefresh()
                },
                onContactUpdated = {
                    onRefresh()
                }
            )
        } else {
            val isM3Mode = currentThemeMode == org.openui.contacts.data.AppThemeMode.MATERIAL_3_EXPRESSIVE

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(colors.background)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                ) {
                    if (isM3Mode) {
                        // Google Contacts Material 3 Expressive Top Search Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 12.dp)
                                .height(52.dp)
                                .clip(CircleShape)
                                .background(colors.cardElevated)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.contacts_search),
                                tint = colors.primaryBrand,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(colors.primaryBrand),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.contacts_search_placeholder),
                                            color = colors.textMuted,
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.contacts_clear),
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    simAccounts = ContactManager.getAvailableSimAccounts(context)
                                    showStorageDialog = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (currentStorageTarget.startsWith("SIM")) Icons.Rounded.SimCard else Icons.Rounded.Storage,
                                    contentDescription = stringResource(R.string.contacts_storage_location),
                                    tint = if (currentStorageTarget.startsWith("SIM")) colors.dialerGreen else colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { isMenuExpanded = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = stringResource(R.string.contacts_menu),
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = isMenuExpanded,
                                    onDismissRequest = { isMenuExpanded = false },
                                    modifier = Modifier.background(colors.surface)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.contacts_refresh), color = colors.textPrimary, fontSize = 14.sp) },
                                        onClick = {
                                            isMenuExpanded = false
                                            onRefresh()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.contacts_storage_location), color = colors.textPrimary, fontSize = 14.sp) },
                                        onClick = {
                                            isMenuExpanded = false
                                            simAccounts = ContactManager.getAvailableSimAccounts(context)
                                            showStorageDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.contacts_trash), color = colors.textPrimary, fontSize = 14.sp) },
                                        onClick = {
                                            isMenuExpanded = false
                                            showTrashDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.contacts_settings), color = colors.textPrimary, fontSize = 14.sp) },
                                        onClick = {
                                            isMenuExpanded = false
                                            showSettingsDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.contacts_about), color = colors.textPrimary, fontSize = 14.sp) },
                                        onClick = {
                                            isMenuExpanded = false
                                            showAboutDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // OpenUI One UI Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPickerMode) {
                                Text(
                                    text = stringResource(R.string.contacts_pick_contact),
                                    color = colors.textPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(onClick = { onCancelPicker?.invoke() }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.contacts_cancel),
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = if (showFavoritesOnly) stringResource(R.string.contacts_favorites_title) else stringResource(R.string.contacts_title),
                                    color = colors.textPrimary,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        simAccounts = ContactManager.getAvailableSimAccounts(context)
                                        showStorageDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentStorageTarget.startsWith("SIM")) Icons.Rounded.SimCard else Icons.Rounded.Storage,
                                        contentDescription = stringResource(R.string.contacts_storage_location),
                                        tint = if (currentStorageTarget.startsWith("SIM")) colors.dialerGreen else colors.textPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        newContactName = ""
                                        newContactNumber = ""
                                        newContactExtraNumbers = emptyList()
                                        showAddContactDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.contacts_add_contact),
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = stringResource(R.string.contacts_search),
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Box {
                                    IconButton(onClick = { isMenuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = stringResource(R.string.contacts_menu),
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = isMenuExpanded,
                                        onDismissRequest = { isMenuExpanded = false },
                                        modifier = Modifier.background(colors.surface)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.contacts_refresh), color = colors.textPrimary, fontSize = 14.sp) },
                                            onClick = {
                                                isMenuExpanded = false
                                                onRefresh()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.contacts_storage_location), color = colors.textPrimary, fontSize = 14.sp) },
                                            onClick = {
                                                isMenuExpanded = false
                                                simAccounts = ContactManager.getAvailableSimAccounts(context)
                                                showStorageDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.contacts_trash), color = colors.textPrimary, fontSize = 14.sp) },
                                            onClick = {
                                                isMenuExpanded = false
                                                showTrashDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.contacts_settings), color = colors.textPrimary, fontSize = 14.sp) },
                                            onClick = {
                                                isMenuExpanded = false
                                                showSettingsDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.contacts_about), color = colors.textPrimary, fontSize = 14.sp) },
                                            onClick = {
                                                isMenuExpanded = false
                                                showAboutDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // OpenUI Smooth Pill Search Field
                        AnimatedVisibility(
                            visible = isSearchVisible || searchQuery.isNotEmpty(),
                            enter = expandVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(animationSpec = tween(200)),
                            exit = shrinkVertically(
                                animationSpec = tween(180)
                            ) + fadeOut(animationSpec = tween(150))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .height(46.dp)
                                    .clip(CircleShape)
                                    .background(colors.cardElevated)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = stringResource(R.string.contacts_search),
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = colors.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        cursorBrush = SolidColor(colors.primaryBrand),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.contacts_search_placeholder),
                                                    color = colors.textMuted,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.contacts_clear),
                                                tint = colors.textMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    when {
                        !hasContactsPermission -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(colors.cardElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = colors.dialerGreen,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.contacts_need_access),
                                    color = colors.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.contacts_need_access_desc),
                                    color = colors.textSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = onRequestPermissions,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.dialerGreen)
                                ) {
                                    Text(
                                        text = stringResource(R.string.contacts_grant_access),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        filteredSortedContacts.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Contacts,
                                    contentDescription = null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (showFavoritesOnly) stringResource(R.string.contacts_no_favorites) else if (searchQuery.isBlank()) stringResource(R.string.contacts_no_contacts) else stringResource(R.string.contacts_nothing_found),
                                    color = colors.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (showFavoritesOnly) showFavoritesOnly = false else onRefresh()
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.cardElevated)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = if (showFavoritesOnly) stringResource(R.string.contacts_show_all) else stringResource(R.string.contacts_refresh), color = colors.textPrimary, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(if (isM3Mode) 2.dp else 6.dp)
                            ) {
                                if (searchQuery.isBlank() && !showFavoritesOnly) {
                                    item(key = "favorites_entry_button") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(if (isM3Mode) RoundedCornerShape(16.dp) else ContactRowShape)
                                                .background(if (isM3Mode) colors.cardElevated else colors.card)
                                                .clickable { showFavoritesOnly = true }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(StarBadgeColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Star,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Text(
                                                text = stringResource(R.string.contacts_favorite),
                                                color = colors.textPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                groupedContacts.forEach { (letter, contactsInGroup) ->
                                    item(key = "letter_header_$letter") {
                                        Text(
                                            text = letter,
                                            color = if (isM3Mode) colors.primaryBrand else colors.textMuted,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = if (isM3Mode) 16.dp else 8.dp, top = 14.dp, bottom = 4.dp)
                                        )
                                    }
                                    item(key = "group_$letter") {
                                        if (isM3Mode) {
                                            // Material 3 Expressive: Clean Flat Group
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .animateContentSize(
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                            ) {
                                                contactsInGroup.forEachIndexed { index, contact ->
                                                    val isExpanded = expandedContactId == contact.id
                                                    ContactRowItem(
                                                        contact = contact,
                                                        isExpanded = isExpanded,
                                                        onRowClick = {
                                                            if (isPickerMode) {
                                                                onPickContact?.invoke(contact)
                                                            } else {
                                                                expandedContactId = if (isExpanded) null else contact.id
                                                            }
                                                        },
                                                        onCallClick = {
                                                            if (isPickerMode) {
                                                                onPickContact?.invoke(contact)
                                                            } else {
                                                                initiateCall(contact.number)
                                                            }
                                                        },
                                                        onSmsClick = {
                                                            if (isPickerMode) {
                                                                onPickContact?.invoke(contact)
                                                            } else {
                                                                onContactClick(contact.number, contact.name, contact.photoUri)
                                                            }
                                                        },
                                                        onInfoClick = {
                                                            if (isPickerMode) {
                                                                onPickContact?.invoke(contact)
                                                            } else {
                                                                selectedInfoContact = contact
                                                            }
                                                        }
                                                    )
                                                    if (index < contactsInGroup.size - 1) {
                                                        HorizontalDivider(
                                                            color = colors.divider.copy(alpha = 0.5f),
                                                            thickness = 0.5.dp,
                                                            modifier = Modifier.padding(start = 74.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            // OpenUI: Floating Island One UI Group
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(ContactRowShape)
                                                    .background(colors.card)
                                                    .animateContentSize(
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                            ) {
                                                contactsInGroup.forEachIndexed { index, contact ->
                                                    val isExpanded = expandedContactId == contact.id
                                                    ContactRowItem(
                                                        contact = contact,
                                                        isExpanded = isExpanded,
                                                        onRowClick = {
                                                            if (isPickerMode) {
                                                                onPickContact?.invoke(contact)
                                                            } else {
                                                                expandedContactId = if (isExpanded) null else contact.id
                                                            }
                                                        },
                                                        onCallClick = {
                                                            if (isPickerMode) {
                                                                onPickContact?.invoke(contact)
                                                            } else {
                                                                initiateCall(contact.number)
                                                            }
                                                        },
                                                        onSmsClick = {
                                                            if (isPickerMode) {
                                                                onPickContact?.invoke(contact)
                                                            } else {
                                                                onContactClick(contact.number, contact.name, contact.photoUri)
                                                            }
                                                        },
                                                        onInfoClick = {
                                                            if (isPickerMode) {
                                                                onPickContact?.invoke(contact)
                                                            } else {
                                                                selectedInfoContact = contact
                                                            }
                                                        }
                                                    )
                                                    if (index < contactsInGroup.size - 1) {
                                                        HorizontalDivider(
                                                            color = colors.divider,
                                                            thickness = 0.5.dp,
                                                            modifier = Modifier.padding(start = 68.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item(key = "footer_list_spacer") {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        }
                    }
                }

                // Material 3 Expressive: Google Contacts Bottom FAB
                if (isM3Mode && !isPickerMode) {
                    FloatingActionButton(
                        onClick = {
                            newContactName = ""
                            newContactNumber = ""
                            newContactExtraNumbers = emptyList()
                            showAddContactDialog = true
                        },
                        containerColor = colors.primaryBrand,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 28.dp)
                            .testTag("add_contact_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.contacts_add_contact),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

                if (showAddContactDialog) {
                    AlertDialog(
                        onDismissRequest = { showAddContactDialog = false },
                        containerColor = colors.surface,
                        title = {
                            Text(
                                text = stringResource(R.string.contacts_new_contact),
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                val storageLabel = if (currentStorageTarget.startsWith("SIM")) currentStorageTarget else stringResource(R.string.contacts_storage_phone_title)
                                Text(
                                    text = stringResource(R.string.contacts_storage_saving_to, storageLabel),
                                    color = colors.dialerGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                OutlinedTextField(
                                    value = newContactName,
                                    onValueChange = { newContactName = it },
                                    label = { Text(stringResource(R.string.contacts_name_label), color = colors.textMuted) },
                                    singleLine = true,
                                    shape = DialogFieldShape,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.primaryBrand,
                                        unfocusedBorderColor = colors.divider,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = newContactNumber,
                                    onValueChange = { newContactNumber = it },
                                    label = { Text(stringResource(R.string.contacts_phone_label), color = colors.textMuted) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = DialogFieldShape,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.primaryBrand,
                                        unfocusedBorderColor = colors.divider,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                newContactExtraNumbers.forEachIndexed { index, extraNum ->
                                    OutlinedTextField(
                                        value = extraNum,
                                        onValueChange = { updatedText ->
                                            val list = newContactExtraNumbers.toMutableList()
                                            list[index] = updatedText
                                            newContactExtraNumbers = list
                                        },
                                        label = { Text(stringResource(R.string.contacts_extra_phone_label, index + 1), color = colors.textMuted) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                val list = newContactExtraNumbers.toMutableList()
                                                list.removeAt(index)
                                                newContactExtraNumbers = list
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = stringResource(R.string.contacts_remove_number),
                                                    tint = colors.actionRed,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        shape = DialogFieldShape,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.primaryBrand,
                                            unfocusedBorderColor = colors.divider,
                                            focusedTextColor = colors.textPrimary,
                                            unfocusedTextColor = colors.textPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                TextButton(
                                    onClick = { newContactExtraNumbers = newContactExtraNumbers + "" }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = colors.primaryBrand,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.contacts_add_another_number),
                                        color = colors.primaryBrand,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (newContactName.isNotBlank() && newContactNumber.isNotBlank()) {
                                        showAddContactDialog = false
                                        val validExtras = newContactExtraNumbers.map { it.trim() }.filter { it.isNotBlank() }
                                        coroutineScope.launch {
                                            onAddContact(newContactName.trim(), newContactNumber.trim(), validExtras, currentStorageTarget)
                                            onRefresh()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.dialerGreen),
                                shape = ActionButtonShape
                            ) {
                                Text(stringResource(R.string.contacts_save), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddContactDialog = false }) {
                                Text(stringResource(R.string.contacts_cancel), color = colors.textMuted)
                            }
                        }
                    )
                }

                if (showSettingsDialog) {
                    SettingsDialog(
                        currentThemeMode = currentThemeMode,
                        currentStorageTarget = currentStorageTarget,
                        onThemeModeChanged = { mode ->
                            currentThemeMode = mode
                            prefs.setThemeMode(mode)
                            onThemeModeChanged(mode)
                        },
                        onStorageTargetClick = {
                            showSettingsDialog = false
                            simAccounts = ContactManager.getAvailableSimAccounts(context)
                            showStorageDialog = true
                        },
                        onAboutClick = {
                            showSettingsDialog = false
                            showAboutDialog = true
                        },
                        onDismiss = { showSettingsDialog = false }
                    )
                }

                if (showStorageDialog) {
                    StorageLocationDialog(
                        currentTarget = currentStorageTarget,
                        simAccounts = simAccounts,
                        onDismiss = { showStorageDialog = false },
                        onSelectTarget = { selected ->
                            currentStorageTarget = selected
                            prefs.setStorageTarget(selected)
                            showStorageDialog = false
                            val label = if (selected.startsWith("SIM")) "SIM ($selected)" else context.getString(R.string.contacts_storage_phone_title)
                            Toast.makeText(context, context.getString(R.string.contacts_storage_selected_toast, label), Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (showAboutDialog) {
                    AboutAppDialog(
                        onDismiss = { showAboutDialog = false }
                    )
                }

                if (showTrashDialog) {
                    val trashItems = remember(prefs, trashVersion) { prefs.getTrashContacts() }
                    var contactToDeletePermanently by remember { mutableStateOf<TrashContact?>(null) }
                    var showClearTrashConfirm by remember { mutableStateOf(false) }

                    AlertDialog(
                        onDismissRequest = { showTrashDialog = false },
                        containerColor = colors.surface,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colors.actionRed.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = colors.actionRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.contacts_trash_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = colors.textPrimary
                                )
                            }
                        },
                        text = {
                            if (trashItems.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.contacts_trash_empty),
                                    color = colors.textMuted,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(trashItems.size, key = { trashItems[it].number }) { idx ->
                                        val item = trashItems[idx]
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(colors.card)
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.name.ifBlank { item.number },
                                                    color = colors.textPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp
                                                )
                                                Text(
                                                    text = item.number,
                                                    color = colors.textMuted,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        prefs.restoreFromTrash(item.number)
                                                        trashVersion++
                                                        Toast.makeText(context, context.getString(R.string.contacts_trash_restored_toast), Toast.LENGTH_SHORT).show()
                                                        onRefresh()
                                                    }
                                                ) {
                                                    Text(
                                                        stringResource(R.string.contacts_trash_restore),
                                                        color = colors.dialerGreen,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        contactToDeletePermanently = item
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Delete,
                                                        contentDescription = stringResource(R.string.contacts_trash_delete_permanently),
                                                        tint = colors.actionRed,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            if (trashItems.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        showClearTrashConfirm = true
                                    }
                                ) {
                                    Text(stringResource(R.string.contacts_trash_clear_all), color = colors.actionRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTrashDialog = false }) {
                                Text(stringResource(R.string.contacts_close), color = colors.textMuted)
                            }
                        }
                    )

                    if (contactToDeletePermanently != null) {
                        val toDelete = contactToDeletePermanently!!
                        AlertDialog(
                            onDismissRequest = { contactToDeletePermanently = null },
                            containerColor = colors.surface,
                            title = {
                                Text(
                                    text = stringResource(R.string.contacts_trash_confirm_delete_title),
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.contacts_trash_confirm_delete_desc),
                                    color = colors.textMuted,
                                    fontSize = 14.sp
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val item = toDelete
                                        contactToDeletePermanently = null
                                        coroutineScope.launch {
                                            ContactManager.deleteContactGlobally(context, item.id, item.name, item.number)
                                            prefs.removeFromTrash(item.number)
                                            trashVersion++
                                            Toast.makeText(context, context.getString(R.string.contacts_trash_deleted_permanently_toast), Toast.LENGTH_SHORT).show()
                                            onRefresh()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.actionRed),
                                    shape = ActionButtonShape
                                ) {
                                    Text(stringResource(R.string.contacts_trash_delete_permanently), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { contactToDeletePermanently = null }) {
                                    Text(stringResource(R.string.contacts_cancel), color = colors.textMuted)
                                }
                            }
                        )
                    }

                    if (showClearTrashConfirm) {
                        AlertDialog(
                            onDismissRequest = { showClearTrashConfirm = false },
                            containerColor = colors.surface,
                            title = {
                                Text(
                                    text = stringResource(R.string.contacts_trash_clear_all),
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.contacts_trash_confirm_delete_desc),
                                    color = colors.textMuted,
                                    fontSize = 14.sp
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showClearTrashConfirm = false
                                        showTrashDialog = false
                                        coroutineScope.launch {
                                            val currentItems = prefs.getTrashContacts()
                                            for (item in currentItems) {
                                                ContactManager.deleteContactGlobally(context, item.id, item.name, item.number)
                                            }
                                            prefs.clearTrash()
                                            trashVersion++
                                            Toast.makeText(context, context.getString(R.string.contacts_trash_cleared_toast), Toast.LENGTH_SHORT).show()
                                            onRefresh()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.actionRed),
                                    shape = ActionButtonShape
                                ) {
                                    Text(stringResource(R.string.contacts_trash_clear_all), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearTrashConfirm = false }) {
                                    Text(stringResource(R.string.contacts_cancel), color = colors.textMuted)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

@Composable
private fun ContactRowItem(
    contact: ContactItem,
    isExpanded: Boolean,
    onRowClick: () -> Unit,
    onCallClick: () -> Unit,
    onSmsClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val avatarColor = remember(contact.name) { getAvatarColor(contact.name) }
    val initial = contact.initials.ifBlank { "#" }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = colors.textPrimary.copy(alpha = 0.08f)),
                onClick = onRowClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("contact_item_${contact.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(ContactCircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                if (contact.photoUri != null) {
                    AsyncImage(
                        model = contact.photoUri,
                        contentDescription = contact.name,
                        modifier = Modifier.size(42.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val isSimContact = contact.accountType.contains("sim", ignoreCase = true) || contact.accountType.startsWith("SIM")
                    if (isSimContact) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.SimCard,
                            contentDescription = "SIM",
                            tint = colors.dialerGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.number,
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(
                animationSpec = tween(180)
            ) + fadeOut(animationSpec = tween(120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
            ) {
                if (contact.additionalNumbers.isNotEmpty()) {
                    contact.additionalNumbers.forEach { extraNum ->
                        Text(
                            text = extraNum,
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 56.dp, bottom = 4.dp)
                        )
                    }
                }
                if (contact.email != null) {
                    Text(
                        text = contact.email,
                        color = colors.primaryBrand,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 56.dp, bottom = 6.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(ContactCircleShape)
                            .background(colors.cardElevated)
                            .clickable(onClick = onCallClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Phone,
                            contentDescription = stringResource(R.string.contacts_call),
                            tint = colors.dialerGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(ContactCircleShape)
                            .background(colors.cardElevated)
                            .clickable(onClick = onSmsClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubble,
                            contentDescription = stringResource(R.string.contacts_message),
                            tint = SmsBlueIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(ContactCircleShape)
                            .background(colors.cardElevated)
                            .clickable(onClick = onInfoClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                            contentDescription = stringResource(R.string.contacts_info),
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
