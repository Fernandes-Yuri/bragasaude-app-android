package br.com.bragasaude.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiskManager @Inject constructor() {
    
    data class RiskEvent(val type: String, val message: String)

    private val _riskEvents = MutableSharedFlow<RiskEvent>(extraBufferCapacity = 1)
    val riskEvents = _riskEvents.asSharedFlow()

    fun emitRisk(type: String, message: String) {
        _riskEvents.tryEmit(RiskEvent(type, message))
    }
}
