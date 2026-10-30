package org.openui.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openui.contacts.data.ContactManager
import org.openui.contacts.model.CallLogItem
import org.openui.contacts.model.CallType
import org.openui.contacts.model.ContactItem
import org.openui.contacts.ui.SmsContactsScreen
import org.openui.contacts.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val currentIntentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setMaxRefreshRate(this)
        currentIntentState.value = intent

        setContent {
            val context = this
            val prefs = remember(context) { org.openui.contacts.data.ContactPreferences(context) }
            var currentThemeMode by remember { mutableStateOf(prefs.getThemeMode()) }

            MyApplicationTheme(themeMode = currentThemeMode) {
                var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
                val coroutineScope = rememberCoroutineScope()

                val activeIntent = currentIntentState.value

                var isPickerMode by remember { mutableStateOf(false) }
                var isInsertMode by remember { mutableStateOf(false) }
                var initialAddName by remember { mutableStateOf<String?>(null) }
                var initialAddNumber by remember { mutableStateOf<String?>(null) }
                var autoOpenAddDialog by remember { mutableStateOf(false) }
                var initialViewContact by remember { mutableStateOf<ContactItem?>(null) }

                var hasContactsPermission by remember {
                    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
                }

                val allPermissions = remember {
                    arrayOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.WRITE_CALL_LOG
                    )
                }

                val requestPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                    coroutineScope.launch {
                        contacts = withContext(Dispatchers.IO) { ContactManager.fetchContacts(context) }
                    }
                }

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                            hasContactsPermission = granted
                            if (granted) {
                                coroutineScope.launch {
                                    contacts = withContext(Dispatchers.IO) { ContactManager.fetchContacts(context) }
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(Unit) {
                    if (hasContactsPermission) {
                        contacts = withContext(Dispatchers.IO) { ContactManager.fetchContacts(context) }
                    } else {
                        requestPermissionLauncher.launch(allPermissions)
                    }
                }

                LaunchedEffect(activeIntent) {
                    val intentToProcess = activeIntent ?: return@LaunchedEffect
                    val action = intentToProcess.action

                    when (action) {
                        Intent.ACTION_PICK, Intent.ACTION_GET_CONTENT -> {
                            isPickerMode = true
                        }
                        Intent.ACTION_INSERT, "android.intent.action.INSERT_OR_EDIT" -> {
                            isInsertMode = true
                            val name = intentToProcess.getStringExtra(ContactsContract.Intents.Insert.NAME)
                                ?: intentToProcess.getStringExtra("name")
                            val phone = intentToProcess.getStringExtra(ContactsContract.Intents.Insert.PHONE)
                                ?: intentToProcess.getStringExtra("phone")
                                ?: intentToProcess.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                                ?: if (intentToProcess.data?.scheme == "tel") intentToProcess.data?.schemeSpecificPart else null

                            initialAddName = name
                            initialAddNumber = phone
                            autoOpenAddDialog = true
                        }
                        Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                            val uri = intentToProcess.data
                            if (uri != null) {
                                val fetched = withContext(Dispatchers.IO) {
                                    ContactManager.fetchContactByUri(context, uri)
                                }
                                if (fetched != null) {
                                    initialViewContact = fetched
                                }
                            }
                        }
                    }
                }

                SmsContactsScreen(
                    contacts = contacts,
                    hasContactsPermission = hasContactsPermission,
                    isPickerMode = isPickerMode,
                    initialAddName = initialAddName,
                    initialAddNumber = initialAddNumber,
                    autoOpenAddDialog = autoOpenAddDialog,
                    initialViewContact = initialViewContact,
                    onContactClick = { number, _, _ ->
                        try {
                            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
                            context.startActivity(smsIntent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "SMS: $number", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPickContact = { contact ->
                        val lookupUri = ContactManager.getContactLookupUri(contact.id, contact.lookupKey)
                        val type = activeIntent?.type
                        val isPhonePick = type?.contains("phone") == true ||
                                activeIntent?.data?.toString()?.contains("phone") == true

                        val resultData = Intent().apply {
                            if (isPhonePick) {
                                data = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, contact.id.toString())
                            } else {
                                data = lookupUri
                            }
                            putExtra(ContactsContract.Intents.Insert.PHONE, contact.number)
                            putExtra(ContactsContract.Intents.Insert.NAME, contact.name)
                            if (!contact.email.isNullOrBlank()) {
                                putExtra(ContactsContract.Intents.Insert.EMAIL, contact.email)
                            }
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        setResult(RESULT_OK, resultData)
                        finish()
                    },
                    onCancelPicker = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onRequestPermissions = {
                        requestPermissionLauncher.launch(allPermissions)
                    },
                    onRefresh = {
                        coroutineScope.launch {
                            contacts = withContext(Dispatchers.IO) { ContactManager.fetchContacts(context) }
                        }
                    },
                    onThemeModeChanged = { newMode ->
                        currentThemeMode = newMode
                    },
                    onAddContact = { name, number, extraNumbers, storageTarget ->
                        val success = ContactManager.saveContact(context, name, number, extraNumbers, storageTarget = storageTarget)
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.contacts_saved_success), Toast.LENGTH_SHORT).show()
                            contacts = withContext(Dispatchers.IO) { ContactManager.fetchContacts(context) }
                            if (isInsertMode) {
                                setResult(RESULT_OK)
                            }
                        } else {
                            Toast.makeText(context, context.getString(R.string.contacts_saved_error), Toast.LENGTH_SHORT).show()
                        }
                        success
                    },
                    onFetchCallLogs = { number ->
                        withContext(Dispatchers.IO) {
                            fetchCallLogsForNumber(context, number)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setMaxRefreshRate(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
    }
}

private fun setMaxRefreshRate(activity: ComponentActivity) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                (activity.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
            }
            val modes = display?.supportedModes ?: emptyArray()
            val maxMode = modes.maxByOrNull { it.refreshRate }
            if (maxMode != null && maxMode.refreshRate > 60f) {
                val lp = activity.window.attributes
                lp.preferredDisplayModeId = maxMode.modeId
                lp.preferredRefreshRate = maxMode.refreshRate
                activity.window.attributes = lp
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun fetchCallLogsForNumber(context: Context, phoneNumber: String): List<CallLogItem> {
    val list = mutableListOf<CallLogItem>()
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
        return list
    }

    val contentResolver = context.contentResolver
    val cleanTargetNumber = phoneNumber.replace("[^0-9+]".toRegex(), "")

    val projection = arrayOf(
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION
    )

    try {
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

            while (cursor.moveToNext()) {
                val num = if (numberIndex >= 0) cursor.getString(numberIndex) ?: "" else ""
                val cleanRowNumber = num.replace("[^0-9+]".toRegex(), "")

                if (cleanRowNumber.isNotEmpty() && cleanTargetNumber.isNotEmpty()) {
                    val matches = cleanRowNumber == cleanTargetNumber ||
                            cleanRowNumber.endsWith(cleanTargetNumber) ||
                            cleanTargetNumber.endsWith(cleanRowNumber)

                    if (matches) {
                        val id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L
                        val rawType = if (typeIndex >= 0) cursor.getInt(typeIndex) else 0
                        val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                        val duration = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L

                        val callType = when (rawType) {
                            CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                            CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                            CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                            CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                            else -> CallType.UNKNOWN
                        }

                        list.add(
                            CallLogItem(
                                id = id,
                                number = num,
                                type = callType,
                                timestamp = date,
                                durationSeconds = duration
                            )
                        )
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return list
}
