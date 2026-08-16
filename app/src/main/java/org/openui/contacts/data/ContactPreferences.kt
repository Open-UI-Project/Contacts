package org.openui.contacts.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.openui.contacts.model.ContactItem

data class TrashContact(
    val id: Long,
    val name: String,
    val number: String,
    val email: String? = null,
    val photoUri: String? = null,
    val accountType: String? = null,
    val deletedTimestamp: Long = System.currentTimeMillis()
)

class ContactPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("open_sms_contact_prefs", Context.MODE_PRIVATE)

    fun getFavoriteNumbers(): Set<String> {
        return prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    }

    fun isFavorite(number: String): Boolean {
        if (number.isBlank()) return false
        val clean = cleanNumber(number)
        return getFavoriteNumbers().any { cleanNumber(it) == clean }
    }

    fun toggleFavorite(number: String): Boolean {
        if (number.isBlank()) return false
        val clean = cleanNumber(number)
        val current = getFavoriteNumbers().toMutableSet()
        val existing = current.find { cleanNumber(it) == clean }
        val nowFav: Boolean
        if (existing != null) {
            current.remove(existing)
            nowFav = false
        } else {
            current.add(clean)
            nowFav = true
        }
        prefs.edit().putStringSet("favorites", current).apply()
        return nowFav
    }

    fun getTrashContacts(): List<TrashContact> {
        val jsonStr = prefs.getString("trash_contacts_json", null) ?: return emptyList()
        val list = mutableListOf<TrashContact>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TrashContact(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", ""),
                        number = obj.optString("number", ""),
                        email = if (obj.isNull("email")) null else obj.optString("email"),
                        photoUri = if (obj.isNull("photoUri")) null else obj.optString("photoUri"),
                        accountType = if (obj.isNull("accountType")) null else obj.optString("accountType"),
                        deletedTimestamp = obj.optLong("deletedTimestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun moveToTrash(contact: ContactItem) {
        val current = getTrashContacts().toMutableList()
        val cleanNum = cleanNumber(contact.number)
        current.removeAll { cleanNumber(it.number) == cleanNum || (it.id != 0L && it.id == contact.id) }
        current.add(
            TrashContact(
                id = contact.id,
                name = contact.name,
                number = contact.number,
                email = contact.email,
                photoUri = contact.photoUri,
                accountType = contact.accountType
            )
        )
        saveTrashContacts(current)
    }

    fun moveToTrash(number: String) {
        if (number.isBlank()) return
        val current = getTrashContacts().toMutableList()
        val cleanNum = cleanNumber(number)
        if (current.none { cleanNumber(it.number) == cleanNum }) {
            current.add(
                TrashContact(
                    id = 0L,
                    name = number,
                    number = number
                )
            )
            saveTrashContacts(current)
        }
    }

    fun restoreFromTrash(number: String) {
        val current = getTrashContacts().toMutableList()
        val cleanNum = cleanNumber(number)
        current.removeAll { cleanNumber(it.number) == cleanNum }
        saveTrashContacts(current)
    }

    fun removeFromTrash(number: String) {
        val current = getTrashContacts().toMutableList()
        val cleanNum = cleanNumber(number)
        current.removeAll { cleanNumber(it.number) == cleanNum }
        saveTrashContacts(current)
    }

    fun isDeleted(number: String): Boolean {
        if (number.isBlank()) return false
        val clean = cleanNumber(number)
        return getTrashContacts().any { cleanNumber(it.number) == clean }
    }

    fun isContactInTrash(id: Long, number: String): Boolean {
        val clean = cleanNumber(number)
        return getTrashContacts().any {
            (clean.isNotBlank() && cleanNumber(it.number) == clean) || (id != 0L && it.id == id)
        }
    }

    fun clearTrash() {
        prefs.edit().remove("trash_contacts_json").apply()
    }

    private fun saveTrashContacts(list: List<TrashContact>) {
        try {
            val arr = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("name", item.name)
                obj.put("number", item.number)
                obj.put("email", item.email)
                obj.put("photoUri", item.photoUri)
                obj.put("accountType", item.accountType)
                obj.put("deletedTimestamp", item.deletedTimestamp)
                arr.put(obj)
            }
            prefs.edit().putString("trash_contacts_json", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getBlockedNumbers(): Set<String> {
        return prefs.getStringSet("blocked_numbers", emptySet()) ?: emptySet()
    }

    fun isBlocked(number: String): Boolean {
        if (number.isBlank()) return false
        val clean = cleanNumber(number)
        return getBlockedNumbers().any { cleanNumber(it) == clean }
    }

    fun toggleBlock(number: String): Boolean {
        if (number.isBlank()) return false
        val clean = cleanNumber(number)
        val current = getBlockedNumbers().toMutableSet()
        val existing = current.find { cleanNumber(it) == clean }
        val nowBlocked: Boolean
        if (existing != null) {
            current.remove(existing)
            nowBlocked = false
        } else {
            current.add(clean)
            nowBlocked = true
        }
        prefs.edit().putStringSet("blocked_numbers", current).apply()
        return nowBlocked
    }

    fun getStorageTarget(): String {
        return prefs.getString("storage_target", "DEVICE") ?: "DEVICE"
    }

    fun setStorageTarget(target: String) {
        prefs.edit().putString("storage_target", target).apply()
    }

    fun getCustomBackground(number: String): String? {
        return prefs.getString("custom_bg_${cleanNumber(number)}", null)
    }

    fun setCustomBackground(number: String, uriString: String?) {
        val clean = cleanNumber(number)
        if (uriString == null) {
            prefs.edit().remove("custom_bg_$clean").apply()
        } else {
            prefs.edit().putString("custom_bg_$clean", uriString).apply()
        }
    }

    fun getSocialMedia(number: String): String {
        return prefs.getString("social_${cleanNumber(number)}", "") ?: ""
    }

    fun setSocialMedia(number: String, social: String) {
        val clean = cleanNumber(number)
        if (social.isBlank()) {
            prefs.edit().remove("social_$clean").apply()
        } else {
            prefs.edit().putString("social_$clean", social).apply()
        }
    }

    private fun cleanNumber(number: String): String {
        return number.replace("[^0-9+]".toRegex(), "")
    }
}
