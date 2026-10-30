package org.openui.contacts.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openui.contacts.model.ContactItem
import java.io.ByteArrayOutputStream

@Immutable
data class SimAccountInfo(
    val simId: Int,
    val displayName: String,
    val carrierName: String,
    val slotIndex: Int,
    val accountName: String?,
    val accountType: String?
)

object ContactManager {

    fun getAvailableSimAccounts(context: Context): List<SimAccountInfo> {
        val simList = mutableListOf<SimAccountInfo>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return simList
        }
        try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (subManager != null) {
                val activeSubs: List<SubscriptionInfo>? = subManager.activeSubscriptionInfoList
                if (!activeSubs.isNullOrEmpty()) {
                    for (sub in activeSubs) {
                        val carrier = sub.carrierName?.toString()?.trim() ?: ""
                        val display = sub.displayName?.toString()?.trim() ?: ""
                        
                        val isCarrierGeneric = carrier.equals("Android", ignoreCase = true) || carrier.equals("T-Mobile", ignoreCase = true)
                        val isDisplayGeneric = display.equals("Android", ignoreCase = true) || display.equals("T-Mobile", ignoreCase = true)
                        
                        val cleanCarrier = if (isCarrierGeneric) "" else carrier
                        val cleanDisplay = if (isDisplayGeneric) "" else display

                        val realName = when {
                            cleanDisplay.isNotBlank() && cleanCarrier.isNotBlank() && cleanDisplay != cleanCarrier -> "$cleanDisplay ($cleanCarrier)"
                            cleanCarrier.isNotBlank() -> cleanCarrier
                            cleanDisplay.isNotBlank() -> cleanDisplay
                            else -> "SIM ${sub.simSlotIndex + 1}"
                        }

                        simList.add(
                            SimAccountInfo(
                                simId = sub.subscriptionId,
                                displayName = realName,
                                carrierName = cleanCarrier,
                                slotIndex = sub.simSlotIndex,
                                accountName = "SIM_${sub.subscriptionId}",
                                accountType = "com.android.sim"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return simList
    }

    private fun saveToPhysicalSim(
        context: Context,
        name: String,
        phone: String,
        storageTarget: String
    ): Boolean {
        val simAccounts = getAvailableSimAccounts(context)
        val targetSim = if (storageTarget == "SIM 2") {
            simAccounts.find { it.slotIndex == 1 }
        } else if (storageTarget == "SIM 1") {
            simAccounts.find { it.slotIndex == 0 }
        } else {
            simAccounts.firstOrNull()
        }

        val targetSubId = targetSim?.simId

        val simValues = android.content.ContentValues().apply {
            put("tag", name)
            put("number", phone)
        }

        val targetUris = mutableListOf<Uri>()
        if (targetSubId != null && targetSubId >= 0) {
            targetUris.add(Uri.parse("content://icc/adn/subId/$targetSubId"))
        }
        if (storageTarget == "SIM 2") {
            targetUris.add(Uri.parse("content://icc/adn/subId/2"))
        } else if (storageTarget == "SIM 1") {
            targetUris.add(Uri.parse("content://icc/adn/subId/1"))
        }
        targetUris.add(Uri.parse("content://icc/adn"))
        targetUris.add(Uri.parse("content://sim/adn"))

        for (uri in targetUris) {
            try {
                val inserted = context.contentResolver.insert(uri, simValues)
                if (inserted != null) {
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun updateToPhysicalSim(
        context: Context,
        oldName: String,
        oldPhone: String,
        newName: String,
        newPhone: String
    ): Boolean {
        val simValues = android.content.ContentValues().apply {
            put("tag", oldName)
            put("number", oldPhone)
            put("newTag", newName)
            put("newNumber", newPhone)
        }

        val simUris = listOf(
            Uri.parse("content://icc/adn"),
            Uri.parse("content://sim/adn"),
            Uri.parse("content://icc/adn/subId/1"),
            Uri.parse("content://icc/adn/subId/2")
        )

        val cleanOldName = oldName.replace("'", "''")
        val cleanOldPhone = oldPhone.replace("[^0-9+]".toRegex(), "").replace("'", "''")
        val where = "tag='$cleanOldName' AND number='$cleanOldPhone'"

        for (uri in simUris) {
            try {
                val updated = context.contentResolver.update(uri, simValues, where, null)
                if (updated > 0) return true
            } catch (_: Exception) {}
        }
        return false
    }

    suspend fun saveContact(
        context: Context,
        name: String,
        phone: String,
        additionalPhones: List<String> = emptyList(),
        email: String? = null,
        photoUri: Uri? = null,
        storageTarget: String = "DEVICE"
    ): Boolean = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext false
        }
        return@withContext try {
            if (storageTarget.startsWith("SIM")) {
                saveToPhysicalSim(context, name, phone, storageTarget)
            }

            val ops = ArrayList<ContentProviderOperation>()

            var accountType: String? = null
            var accountName: String? = null

            if (storageTarget.startsWith("SIM")) {
                accountType = "com.android.sim"
                val simAccounts = getAvailableSimAccounts(context)
                val targetSim = if (storageTarget == "SIM 2") {
                    simAccounts.find { it.slotIndex == 1 }
                } else {
                    simAccounts.find { it.slotIndex == 0 }
                }
                accountName = targetSim?.accountName ?: storageTarget
            }

            val rawContactInsertIndex = ops.size
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                    .build()
            )

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            val allPhones = (listOf(phone) + additionalPhones).filter { it.isNotBlank() }
            allPhones.forEachIndexed { idx, pNum ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, pNum)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, if (idx == 0) ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER)
                        .build()
                )
            }

            if (!email.isNullOrBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                        .build()
                )
            }

            if (photoUri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(photoUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bitmap != null) {
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        val photoBytes = stream.toByteArray()
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                                .build()
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateContact(
        context: Context,
        contactId: Long,
        name: String,
        phone: String,
        additionalPhones: List<String> = emptyList(),
        email: String? = null,
        photoUri: Uri? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext false
        }
        return@withContext try {
            val contactUri = android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            val oldContact = fetchContactByUri(context, contactUri)
            if (oldContact != null && oldContact.accountType.contains("sim", ignoreCase = true)) {
                updateToPhysicalSim(context, oldContact.name, oldContact.number, name, phone)
            }

            val ops = ArrayList<ContentProviderOperation>()

            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    )
                    .build()
            )

            val rawContactId = getRawContactId(context, contactId)
            val allPhones = (listOf(phone) + additionalPhones).filter { it.isNotBlank() }
            if (rawContactId != null) {
                allPhones.forEachIndexed { idx, pNum ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, pNum)
                            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, if (idx == 0) ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER)
                            .build()
                    )
                }
            }

            if (!email.isNullOrBlank()) {
                val emailUri = ContactsContract.Data.CONTENT_URI
                val emailCursor = context.contentResolver.query(
                    emailUri,
                    arrayOf(ContactsContract.Data._ID),
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),
                    null
                )
                val hasEmail = (emailCursor?.count ?: 0) > 0
                emailCursor?.close()

                if (hasEmail) {
                    ops.add(
                        ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection(
                                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                                arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                            )
                            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                            .build()
                    )
                } else {
                    if (rawContactId != null) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                                .build()
                        )
                    }
                }
            }

            if (photoUri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(photoUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bitmap != null) {
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        val photoBytes = stream.toByteArray()

                        if (rawContactId != null) {
                            ops.add(
                                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                                    .withSelection(
                                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                                        arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                    )
                                    .build()
                            )
                            ops.add(
                                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                                    .build()
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getRawContactId(context: Context, contactId: Long): Long? {
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    private class ContactBuilder(
        val id: Long,
        val name: String,
        val primaryNumber: String,
        val photoUri: String?,
        val phoneType: String,
        val accountType: String,
        val lookupKey: String?,
        val additionalNumbers: MutableList<String> = mutableListOf()
    )

    fun fetchDirectSimContacts(context: Context): List<ContactItem> {
        val simContacts = mutableListOf<ContactItem>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return simContacts
        }

        val simAccounts = getAvailableSimAccounts(context)
        val urisToQuery = mutableListOf<Pair<Uri, String>>()

        for (sim in simAccounts) {
            urisToQuery.add(Pair(Uri.parse("content://icc/adn/subId/${sim.simId}"), sim.displayName))
        }
        if (urisToQuery.isEmpty()) {
            urisToQuery.add(Pair(Uri.parse("content://icc/adn"), "SIM 1"))
            urisToQuery.add(Pair(Uri.parse("content://icc/adn/subId/1"), "SIM 1"))
            urisToQuery.add(Pair(Uri.parse("content://icc/adn/subId/2"), "SIM 2"))
            urisToQuery.add(Pair(Uri.parse("content://sim/adn"), "SIM"))
        }

        val seenInSim = mutableSetOf<String>()

        for ((uri, label) in urisToQuery) {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    val tagIdx = cursor.getColumnIndex("tag")
                    val numberIdx = cursor.getColumnIndex("number")
                    val idIdx = cursor.getColumnIndex("_id")

                    var rowIndex = 0
                    while (cursor.moveToNext()) {
                        rowIndex++
                        val number = if (numberIdx >= 0) cursor.getString(numberIdx) ?: "" else ""
                        if (number.isBlank()) continue

                        val clean = number.replace("[^0-9+]".toRegex(), "")
                        if (clean.isBlank() || !seenInSim.add(clean)) continue

                        val name = when {
                            tagIdx >= 0 -> cursor.getString(tagIdx)
                            nameIdx >= 0 -> cursor.getString(nameIdx)
                            else -> null
                        } ?: number

                        val rowId = if (idIdx >= 0) cursor.getLong(idIdx) else rowIndex.toLong()
                        val pseudoId = -200000L - (rowIndex * 100L + kotlin.math.abs(rowId % 99))

                        simContacts.add(
                            ContactItem(
                                id = pseudoId,
                                name = name.ifBlank { number },
                                number = number,
                                email = null,
                                photoUri = null,
                                phoneType = "SIM",
                                accountType = "SIM ($label)",
                                lookupKey = null
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        return simContacts
    }

    fun fetchContacts(context: Context): List<ContactItem> {
        val list = mutableListOf<ContactItem>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return list
        }
        val contentResolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.ACCOUNT_TYPE_AND_DATA_SET,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        )

        val seenNumbers = mutableSetOf<String>()
        val contactsMap = linkedMapOf<Long, ContactBuilder>()

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val accountTypeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.ACCOUNT_TYPE_AND_DATA_SET)
                val lookupIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

                while (cursor.moveToNext()) {
                    val id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "" else ""
                    val rawNumber = if (numberIndex >= 0) cursor.getString(numberIndex) ?: "" else ""
                    val photoUri = if (photoIndex >= 0) cursor.getString(photoIndex) else null
                    val type = if (typeIndex >= 0) cursor.getInt(typeIndex).toString() else ""
                    val accountType = if (accountTypeIndex >= 0) cursor.getString(accountTypeIndex) ?: "" else ""
                    val lookupKey = if (lookupIndex >= 0) cursor.getString(lookupIndex) else null

                    val cleanNum = rawNumber.replace("[^0-9+]".toRegex(), "")
                    if (cleanNum.isNotBlank()) {
                        seenNumbers.add(cleanNum)
                    }

                    val existing = contactsMap[id]
                    if (existing == null) {
                        contactsMap[id] = ContactBuilder(
                            id = id,
                            name = name.ifBlank { rawNumber },
                            primaryNumber = rawNumber,
                            photoUri = photoUri,
                            phoneType = type,
                            accountType = accountType,
                            lookupKey = lookupKey
                        )
                    } else {
                        if (rawNumber.isNotBlank() && rawNumber != existing.primaryNumber && !existing.additionalNumbers.contains(rawNumber)) {
                            existing.additionalNumbers.add(rawNumber)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        for (builder in contactsMap.values) {
            val email = fetchEmailForContact(context, builder.id)
            list.add(
                ContactItem(
                    id = builder.id,
                    name = builder.name,
                    number = builder.primaryNumber,
                    additionalNumbers = builder.additionalNumbers.toList(),
                    email = email,
                    photoUri = builder.photoUri,
                    phoneType = builder.phoneType,
                    accountType = builder.accountType,
                    lookupKey = builder.lookupKey
                )
            )
        }

        val directSimContacts = fetchDirectSimContacts(context)
        for (simContact in directSimContacts) {
            val clean = simContact.number.replace("[^0-9+]".toRegex(), "")
            if (clean.isNotBlank() && seenNumbers.add(clean)) {
                list.add(simContact)
            }
        }

        return list.sortedBy { it.name.lowercase() }
    }

    fun getContactLookupUri(contactId: Long, lookupKey: String?): Uri {
        return if (!lookupKey.isNullOrBlank()) {
            ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
                ?: android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        } else {
            android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        }
    }

    fun fetchContactByUri(context: Context, contactUri: Uri): ContactItem? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.LOOKUP_KEY
            )
            var contactId: Long? = null
            var displayName: String? = null
            var photoUri: String? = null
            var lookupKey: String? = null

            context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                    val lookupIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)

                    if (idIdx >= 0) contactId = cursor.getLong(idIdx)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                    if (photoIdx >= 0) photoUri = cursor.getString(photoIdx)
                    if (lookupIdx >= 0) lookupKey = cursor.getString(lookupIdx)
                }
            }

            val finalId = contactId ?: return null
            var phoneNumber = ""
            var phoneType = ""
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(finalId.toString()),
                null
            )?.use { phoneCursor ->
                if (phoneCursor.moveToFirst()) {
                    phoneNumber = phoneCursor.getString(0) ?: ""
                    phoneType = phoneCursor.getInt(1).toString()
                }
            }

            val email = fetchEmailForContact(context, finalId)
            ContactItem(
                id = finalId,
                name = displayName ?: phoneNumber,
                number = phoneNumber,
                email = email,
                photoUri = photoUri,
                phoneType = phoneType,
                lookupKey = lookupKey
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchEmailForContact(context: Context, contactId: Long): String? {
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteContactGlobally(
        context: Context,
        contactId: Long,
        name: String,
        phone: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext false
        }
        var success = false
        val cleanPhone = phone.replace("[^0-9+]".toRegex(), "")

        try {
            if (contactId > 0) {
                val contactUri = android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
                val deletedRows = context.contentResolver.delete(contactUri, null, null)
                if (deletedRows > 0) success = true

                val rawOps = ArrayList<ContentProviderOperation>()
                rawOps.add(
                    ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                        .withSelection("${ContactsContract.RawContacts.CONTACT_ID} = ?", arrayOf(contactId.toString()))
                        .build()
                )
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, rawOps)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (cleanPhone.isNotEmpty()) {
                val dataUri = ContactsContract.Data.CONTENT_URI
                val cursor = context.contentResolver.query(
                    dataUri,
                    arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
                    "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                    arrayOf("%$cleanPhone%"),
                    null
                )
                cursor?.use {
                    while (it.moveToNext()) {
                        val rawId = it.getLong(0)
                        val rawUri = android.content.ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId)
                        val rows = context.contentResolver.delete(rawUri, null, null)
                        if (rows > 0) success = true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val simUris = listOf(
                Uri.parse("content://icc/adn"),
                Uri.parse("content://sim/adn"),
                Uri.parse("content://icc/adn/subId/1"),
                Uri.parse("content://icc/adn/subId/2")
            )
            val cleanName = name.replace("'", "''")
            val cleanNum = cleanPhone.replace("'", "''")
            for (simUri in simUris) {
                try {
                    val simWhere = "tag='$cleanName' AND number='$cleanNum'"
                    val simDeleted = context.contentResolver.delete(simUri, simWhere, null)
                    if (simDeleted > 0) success = true
                } catch (_: Exception) {}

                try {
                    val simWhere2 = "number='$cleanNum'"
                    val simDeleted2 = context.contentResolver.delete(simUri, simWhere2, null)
                    if (simDeleted2 > 0) success = true
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        success
    }
}
