# Braga Saúde — Android App

> Aplicativo Android do assistente de saúde por voz Braga Saúde.  
> Kotlin + Jetpack Compose + Firebase + Orb WebSocket.

## 🏗️ Tech Stack

- **Kotlin** + Jetpack Compose (UI)
- **Firebase** (auth, FCM push, Firestore)
- **WebSocket** (chat de voz streaming via Orb)
- **Room** (SQLite local para cache offline)
- **Hilt** (dependency injection)
- **Retrofit/OkHttp** (REST API)

## 🚀 Quick Start

### Pré-requisitos
- Android Studio Hedgehog+
- JDK 17+
- Gradle 8+
- Firebase project configurado (`google-services.json`)

### Setup

1. Clone o repositório
2. Coloque `google-services.json` em `app/`
3. Abra no Android Studio, sincronize o Gradle
4. Rode no emulador ou dispositivo

### Build

```bash
./gradlew assembleDebug    # APK de debug
./gradlew assembleRelease  # APK de release (precisa de keystore)
```

## ⚙️ Configuração

### URL da API

Definida em `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "BASE_URL", "\"https://bragasaude.online\"")
```

Para desenvolvimento local, troque para `http://192.168.x.x:8080`.

### Firebase

- `google-services.json` na raiz de `app/`
- Serviços usados: Authentication, Firestore, FCM, Crashlytics

## 📁 Estrutura

```
app/src/main/java/br/com/bragasaude/
├── BragaApplication.kt       # Application + Hilt
├── di/                       # Dependency injection modules
├── data/
│   ├── local/                # Room DB, DAOs, Entities
│   ├── remote/
│   │   ├── api/              # BragaApiClient (REST)
│   │   ├── ai/               # OrbWebSocket, BragaLocalAiClient, NeuralAudioPlayer
│   │   ├── sync/             # SyncManager, FirebaseMessagingService
│   │   ├── service/          # NotificationClient
│   │   └── repository/       # Repositories
│   └── util/                 # MovementManager, Mappers
├── domain/                   # Domain models + VoiceHealthExecutor
├── ui/                       # Compose screens
│   ├── onboarding/
│   ├── profile/
│   ├── voice/                # Orb voice chat UI
│   └── home/
└── util/                     # Helpers (QrCode, Phonetic, AppPreferences)
```

## 🔌 Funcionalidades

- **Autenticação** (OTP via Firebase)
- **Chat de voz** (Orb WebSocket com streaming TTS)
- **Perfil de saúde** (dados biométricos, metas)
- **Sincronização offline-first** (Room → API)
- **Monitoramento de saúde** (vitals, métricas diárias)
- **Integração familiar** (bindings, mensagens)
- **Feed social** (posts, reações)
- **Notificações push** (FCM)
- **Relatórios PDF** (dados de saúde exportáveis)

## 🔒 Segurança

- `google-services.json` NUNCA versionado
- `keystore` e credenciais no `.gitignore`
- ProGuard/R8 em release
- Certificate pinning (em desenvolvimento)

## 📱 Releases (OTA)

O app consulta `/api/app/latest` no gateway para verificar updates.

As releases são gerenciadas via `app/build.gradle.kts` → `versionCode` / `versionName`.

## 📚 Documentação Relacionada

- `41_GUIA_MIGRACAO_AWS_CONCLUIDA.md` — Infraestrutura AWS
- `40_MIGRACAO_AWS_E_DIVISAO_DE_REPOSITORIOS.md` — Divisão de repositórios