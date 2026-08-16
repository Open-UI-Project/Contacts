package org.openui.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
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
import org.openui.contacts.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openui.contacts.data.ContactManager
import org.openui.contacts.model.CallLogItem
import org.openui.contacts.model.CallType
import org.openui.contacts.model.ContactItem
import org.openui.contacts.ui.SmsContactsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
                val context = this
                val coroutineScope = rememberCoroutineScope()

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
                        Manifest.permission.WRITE_CALL_LOG,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
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

                SmsContactsScreen(
                    contacts = contacts,
                    hasContactsPermission = hasContactsPermission,
                    onContactClick = { number, _, _ ->
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "SMS: $number", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRequestPermissions = {
                        requestPermissionLauncher.launch(allPermissions)
                    },
                    onRefresh = {
                        coroutineScope.launch {
                            contacts = withContext(Dispatchers.IO) { ContactManager.fetchContacts(context) }
                        }
                    },
                    onAddContact = { name, number, storageTarget ->
                        val success = ContactManager.saveContact(context, name, number, storageTarget = storageTarget)
                        if (success) {
                            Toast.makeText(context, "Контакт сохранён", Toast.LENGTH_SHORT).show()
                            contacts = withContext(Dispatchers.IO) { ContactManager.fetchContacts(context) }
                        } else {
                            Toast.makeText(context, "Ошибка сохранения контакта", Toast.LENGTH_SHORT).show()
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
