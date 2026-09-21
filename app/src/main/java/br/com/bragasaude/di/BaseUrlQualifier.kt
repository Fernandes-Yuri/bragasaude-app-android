package br.com.bragasaude.di

import javax.inject.Qualifier

// AUD-AN40: qualificador para a URL base da API. BragaApiClient agora recebe
// baseUrl no construtor (imutavel) para os testes injetarem o MockWebServer;
// sem qualificador, o Dagger nao sabe como construir um String solto.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseUrl
