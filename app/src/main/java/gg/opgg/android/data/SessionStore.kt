package gg.opgg.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("opgg_session")

data class Session(
    val userId: Long,
    val token: String,
    val nick: String?,
)

class SessionStore(private val context: Context) {

    private val kUser = longPreferencesKey("user_id")
    private val kToken = stringPreferencesKey("token")
    private val kNick = stringPreferencesKey("nick")
    private val kAvatar = stringPreferencesKey("avatar")

    @Volatile
    private var cached: Session? = null

    suspend fun load(): Session? {
        cached?.let { return it }
        val prefs = context.dataStore.data.first()
        val user = prefs[kUser]
        val token = prefs[kToken]
        val s = if (user != null && !token.isNullOrBlank()) {
            Session(user, token, prefs[kNick])
        } else null
        cached = s
        return s
    }

    /** Used synchronously by the API client to build the Dotgguserauth header. */
    fun peek(): Session? = cached

    suspend fun save(session: Session, avatar: String?) {
        cached = session
        context.dataStore.edit {
            it[kUser] = session.userId
            it[kToken] = session.token
            session.nick?.let { n -> it[kNick] = n }
            avatar?.let { a -> it[kAvatar] = a }
        }
    }

    suspend fun clear() {
        cached = null
        context.dataStore.edit { it.clear() }
    }
}
