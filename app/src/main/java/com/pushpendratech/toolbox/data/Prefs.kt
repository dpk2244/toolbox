package com.pushpendratech.toolbox.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "toolbox_prefs")

/** Max tools shown in the home "Recently Used" section (Pushpendra, 2026-10-24: cap at 3 — he asked for 3 in the next update after seeing 6 rows on 1.2.3). */
const val MAX_RECENTS = 3

/** Theme choices stored in Prefs (Settings screen, v1.2.5). */
const val THEME_SYSTEM = "system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"

/** Stores ONLY tool ids (favorites + recents) and simple app settings. No files, no personal data. */
class Prefs(private val context: Context) {
    private val favKey = stringSetPreferencesKey("favorites")
    private val recentKey = stringSetPreferencesKey("recents_ordered") // encoded as comma-joined to keep order
    private val themeKey = stringPreferencesKey("theme_mode")
    private val showRecentsKey = booleanPreferencesKey("show_recents")

    val favorites: Flow<Set<String>> = context.dataStore.data.map { it[favKey] ?: emptySet() }
    val themeMode: Flow<String> = context.dataStore.data.map { it[themeKey] ?: THEME_SYSTEM }
    val showRecents: Flow<Boolean> = context.dataStore.data.map { it[showRecentsKey] ?: true }
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
    suspend fun clearRecents() {
        context.dataStore.edit { p -> p.remove(recentKey) }
    }
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { p -> p[themeKey] = mode }
    }
    suspend fun setShowRecents(show: Boolean) {
        context.dataStore.edit { p -> p[showRecentsKey] = show }
    }
}
