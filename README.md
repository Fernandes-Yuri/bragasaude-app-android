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

## Identidade e autenticação

- Tokens Firebase usados pela API, IA, áudio e notificações passam pelo `AuthService`, com cache por usuário e renovação centralizada.
- Posts e reações do Mural usam o `fullName` do perfil local como nome público; o UID permanece como identificador técnico.
- O envio de reação registra `postId`, `userId` e o resultado da sincronização para facilitar o diagnóstico sem bloquear a atualização otimista.

## Publicação de conquistas

- O bottom sheet do Mural permanece aberto durante o envio e mostra progresso no botão.
- A publicação entra de forma otimista no banco local, mas é removida se `POST /api/sync/social-post` não devolver um identificador.
- Sucesso e falha são apresentados por snackbar; em falha, o texto preenchido permanece disponível para nova tentativa.

## 🧹 Higiene Android (T-12)

Refactors de baixo risco, sem toque no gateway, na branch `fase-higiene-android`:

- **Navegação type-safe (APP-6):** `MainScaffold.kt` deixou de detectar a aba
  ativa por `String.contains(simpleName)` — que casava `Profile` contra
  `ProfileEdit`. Agora a rota atual é comparada ao nome da tela da `sealed
  interface Screen` com match exato ou prefixo seguido de `?` para rotas com
  argumentos (`isCurrentRoute`).
- **Orb resiliente (APP-7):** em `OrbWebSocket.kt`, esgotar `maxFailures`
  não encerra mais a coroutine de conexão. O estado `FAILED` é anunciado à
  UI, mas o ciclo de reconexão com backoff continua — o Orb se recupera
  sozinho de uma falha transitória.
- **`ProfileInput` (APP-8):** `ProfileScreen.kt` chama o wrapper
  `ProfileInput` (18 campos) em vez do `saveProfile` monolítico de ~28
  parâmetros.

Testes de regressão: `MainScaffoldRouteMatchingTest` (colisão
Profile/ProfileEdit) e `OrbFailedNotTerminalTest` (FAILED seguido de
reconexão), além da suíte existente do `OrbWebSocketTest`.
