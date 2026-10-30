package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Get the current user's [UserPreferences]
 */
@Singleton
class UserPreferencesService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val preferencesDataStore: DataStore<AppPreferences>,
    ) {
        val flow: Flow<UserPreferences> =
            preferencesDataStore.data.combine(serverRepository.currentUserFlow) { appPrefs, user ->
                UserPreferences(appPrefs, user?.appPreferences)
            }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Most recent value, cached so hot paths (eg every key press) don't re-query the database */
        val latest: StateFlow<UserPreferences?> = flow.stateIn(scope, SharingStarted.Eagerly, null)

        suspend fun getCurrent(): UserPreferences = flow.first()
    }
