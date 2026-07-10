package com.mahjongqqu.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mahjongqqu.engine.GameMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mahjongTenDataStore by preferencesDataStore(name = "mahjong_ten")

class LocalBestStore(private val context: Context) {
    fun bestScoreFlow(mode: GameMode): Flow<Long> {
        val key = bestScoreKey(mode)
        return context.mahjongTenDataStore.data.map { preferences -> preferences[key] ?: 0L }
    }

    suspend fun saveBestScore(mode: GameMode, score: Long) {
        val key = bestScoreKey(mode)
        context.mahjongTenDataStore.edit { preferences ->
            val current = preferences[key] ?: 0L
            if (score > current) {
                preferences[key] = score
            }
        }
    }

    private fun bestScoreKey(mode: GameMode) = longPreferencesKey("best_score_${mode.name.lowercase()}")
}
