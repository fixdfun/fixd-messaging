package app.fixd.messaging.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.groupDataStore by preferencesDataStore(name = "fixd_groups")

/**
 * A named group conversation.
 *
 * Groups are stored locally  they map a stable [groupId] (UUID) to a list
 * of member phone numbers and a display name.  When a message is sent to a
 * group it is dispatched as MMS to all members simultaneously.
 *
 * The [threadId] is the Telephony MMS thread ID for the group's most recent
 * message; -1 until the first message has been sent.
 */
@Serializable
data class GroupConversation(
    val groupId: String,           // UUID
    val name: String,
    val members: List<String>,     // normalised phone numbers
    val threadId: Long = -1L,
    val createdAt: Long = System.currentTimeMillis()
)

class GroupRepository(private val ctx: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun groupKey(id: String) = stringPreferencesKey("group_$id")
    private val ALL_IDS_KEY = stringPreferencesKey("group_all_ids")

    //  CRUD 

    suspend fun createGroup(name: String, members: List<String>): GroupConversation {
        val group = GroupConversation(
            groupId = java.util.UUID.randomUUID().toString(),
            name = name,
            members = members.distinct()
        )
        save(group)
        return group
    }

    suspend fun save(group: GroupConversation) {
        ctx.groupDataStore.edit { prefs ->
            prefs[groupKey(group.groupId)] = json.encodeToString(group)
            val existing = prefs[ALL_IDS_KEY]?.split(",")?.filter { it.isNotBlank() }
                ?.toMutableSet() ?: mutableSetOf()
            existing.add(group.groupId)
            prefs[ALL_IDS_KEY] = existing.joinToString(",")
        }
    }

    suspend fun getGroup(groupId: String): GroupConversation? {
        val raw = ctx.groupDataStore.data.map { it[groupKey(groupId)] }.first()
        return raw?.let { runCatching { json.decodeFromString<GroupConversation>(it) }.getOrNull() }
    }

    suspend fun getAllGroups(): List<GroupConversation> {
        val prefs = ctx.groupDataStore.data.first()
        val ids = prefs[ALL_IDS_KEY]?.split(",")?.filter { it.isNotBlank() } ?: return emptyList()
        return ids.mapNotNull { id ->
            prefs[groupKey(id)]?.let {
                runCatching { json.decodeFromString<GroupConversation>(it) }.getOrNull()
            }
        }.sortedByDescending { it.createdAt }
    }

    suspend fun updateThreadId(groupId: String, threadId: Long) {
        val group = getGroup(groupId) ?: return
        save(group.copy(threadId = threadId))
    }

    suspend fun renameGroup(groupId: String, newName: String) {
        val group = getGroup(groupId) ?: return
        save(group.copy(name = newName))
    }

    suspend fun addMember(groupId: String, address: String) {
        val group = getGroup(groupId) ?: return
        if (address !in group.members) save(group.copy(members = group.members + address))
    }

    suspend fun removeMember(groupId: String, address: String) {
        val group = getGroup(groupId) ?: return
        save(group.copy(members = group.members - address))
    }

    suspend fun deleteGroup(groupId: String) {
        ctx.groupDataStore.edit { prefs ->
            prefs.remove(groupKey(groupId))
            val ids = prefs[ALL_IDS_KEY]?.split(",")?.filter { it.isNotBlank() && it != groupId }
                ?: emptyList()
            prefs[ALL_IDS_KEY] = ids.joinToString(",")
        }
    }

    /** Find a group by its MMS threadId (set after first send). */
    suspend fun getGroupByThreadId(threadId: Long): GroupConversation? =
        getAllGroups().firstOrNull { it.threadId == threadId }
}
