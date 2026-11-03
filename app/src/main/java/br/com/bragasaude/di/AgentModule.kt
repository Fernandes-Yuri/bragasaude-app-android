package br.com.bragasaude.di

import br.com.bragasaude.agent.extraction.MlKitOcrEngine
import br.com.bragasaude.agent.extraction.OcrEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de injeção de dependências do subsistema agêntico.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds
    @Singleton
    abstract fun bindOcrEngine(impl: MlKitOcrEngine): OcrEngine
}
