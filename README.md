# Braga Saúde — Android

Aplicativo de autocuidado com assistente de voz, registros de saúde, lembretes,
ponte familiar e mural da comunidade.

Desenvolvido em Kotlin, Jetpack Compose e Room. A autenticação e as notificações
usam Firebase; a sincronização usa o gateway Braga Saúde.

## Compilação e distribuição

A compilação e os testes Android são executados pelo GitHub Actions no fluxo
[Build APK](.github/workflows/android-apk.yml). APKs de teste ficam nos artefatos
das execuções; versões publicadas ficam em
[Releases](https://github.com/Fernandes-Yuri/bragasaude-app-android/releases).

Não versionar APKs, resultados de compilação ou dependências instaladas.

## Contribuição

Consulte [CONTRIBUTING.md](CONTRIBUTING.md) para o fluxo de contribuição,
[AGENTS.md](AGENTS.md) para as regras dos agentes e [SECURITY.md](SECURITY.md)
para relatar problemas de segurança. A licença está em [LICENSE](LICENSE).

Planos, auditorias, decisões internas e histórico técnico ficam na base Contexto.
