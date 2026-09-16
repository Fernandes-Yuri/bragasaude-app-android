package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.remote.model.RemoteDetectedCondition
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConditionRepository @Inject constructor() {
    private val inMemoryConditions = mutableListOf<RemoteDetectedCondition>()

    suspend fun getDetectedConditions(userId: String): List<RemoteDetectedCondition> {
        return inMemoryConditions.filter { it.userId == userId }
    }

    suspend fun saveDetectedCondition(condition: RemoteDetectedCondition) {
        inMemoryConditions.add(condition)
    }
}
