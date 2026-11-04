package com.lyricstage.lyrics

import android.content.Context
import androidx.core.content.edit
import com.lyricstage.core.lyrics.MusixmatchClient

/** Keeps the Musixmatch token between app starts (private app storage, not backed up). */
class MusixmatchTokenStore(context: Context) : MusixmatchClient.TokenStore {
    private val prefs = context.getSharedPreferences("musixmatch", Context.MODE_PRIVATE)

    override var token: String?
        get() = prefs.getString(K_TOKEN, null)
        set(v) = prefs.edit { putString(K_TOKEN, v) }

    override var restUntil: Long
        get() = prefs.getLong(K_REST, 0)
        set(v) = prefs.edit { putLong(K_REST, v) }

    private companion object {
        const val K_TOKEN = "token"
        const val K_REST = "rest_until"
    }
}
