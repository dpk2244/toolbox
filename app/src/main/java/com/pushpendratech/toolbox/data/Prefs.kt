package com.pushpendratech.toolbox.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "toolbox_prefs")

/** Max tools shown in the home "Recently Used" section (Pushpendra, 2026-10-24: cap at 3 — he asked for 3 in the next update after seeing 6 rows on 1.2.3). */
const val MAX_RECENTS = 3

/** Stores ONLY tool ids (favorites + recents). No files, no personal data. */
class Prefs(private val context: Context) {
    private val favKey = stringSetPreferencesKey("favorites")
    private val recentKey = stringSetPreferencesKey("recents_ordered") // encoded as comma-joined to keep order

    val favorites: Flow<Set<String>> = context.dataStore.data.map { it[favKey] ?: emptySet() }
    // Trimmed on read too, so a list stored by an older build shrinks to MAX_RECENTS immediately.
    val recents: Flow<List<String>> = context.dataStore.data.map { prefs ->
        ((prefs[recentKey] ?: emptySet()).firstOrNull()?.split(",")?.filter { it.isNotBlank() } ?: emptyList()).take(MAX_RECENTS)
    }

    suspend fun toggleFavorite(id: String) {
        context.dataStore.edit { p -> val cur = p[favKey] ?: emptySet(); p[favKey] = if (id in cur) cur - id else cur + id }
    }
    suspend fun pushRecent(id: String) {
        context.dataStore.edit { p ->
            val cur = (p[recentKey] ?: emptySet()).firstOrNull()?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val next = (listOf(id) + cur.filter { it != id }).take(MAX_RECENTS)
            p[recentKey] = setOf(next.joinToString(","))
        }
    }
}
