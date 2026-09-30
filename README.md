# Braga Saúde — Android App

> Aplicativo móvel nativo do assistente de saúde por voz Braga Saúde.  
> Kotlin + Jetpack Compose + Firebase + On-Device Neural Voice (Piper/Sherpa-ONNX) + Offline-First Room.

## 🏗️ Tech Stack

- **Linguagem & UI:** Kotlin 2.1+, Jetpack Compose, Material 3 (Design System acessível e de alto contraste)
- **Voz Neural On-Device (0% nuvem):** Piper TTS local via C++ JNI (`sherpa-onnx`) com síntese streaming PCM via `AudioTrack`
- **Fallback de Síntese:** Android `TextToSpeech` nativo (Google Speech Engine) com chaveamento automático e zero latência
- **Autenticação & Push:** Firebase Auth (OTP/Phone), Firebase Cloud Messaging (FCM)
- **Bancos Locais & Criptografia:** Room Database com SQLCipher 4.6+ e Android Security Crypto (Hardware Keystore)
- **Injeção de Dependências:** Dagger Hilt
- **Rede & Sincronização:** BragaApiClient com Certificate Pinning em Release (Let's Encrypt leaf e intermediate CA)
- **OCR & Processamento de Documentos:** Google ML Kit Text Recognition + Apache PDFBox Android

## 🚀 Arquitetura de Síntese Vocal On-Device

A partir da versão **1.3.0+**, a síntese de voz foi migrada 100% para o dispositivo do usuário (on-device), eliminando chamadas remotas à EC2:

1. **Áudios Clínicos Pré-gravados:** Respostas e interjeições curtas de alta frequência executam direto da memória (`res/raw`, 0ms de latência).
2. **Modelo Neural Faber (ONNX):** Download inteligente e seguro em segundo plano (`PiperModelDownloader`) no primeiro uso via HTTPS puro (sem acoplamento com headers de autenticação), com integridade validada e regras fonéticas `espeak-ng-data`.
3. **Inferência C++ Sherpa-ONNX:** Execução multithread local na CPU do celular gerando fluxo PCM de ponto flutuante diretamente para o `AudioTrack`.
4. **Fallback Universal:** Em caso de download pendente ou ausência de modelo, o `AndroidSystemTtsFallback` assume a fala imediatamente sem degradar a experiência do usuário.

## ⚙️ Diretrizes de Compilação & CI/CD

> ⛔ **REGRA INVIOLÁVEL:** Toda compilação de APK e testes unitários é 100% delegada para o **GitHub Actions CI**. É proibido rodar `./gradlew assemble` ou builds pesadas localmente.

### Fluxo de Trabalho (Branching & Releases)

1. Crie uma branch isolada para a tarefa (`feat/...` ou `fix/...`).
2. Valide as regras de UI antes do commit:
   ```bash
   python tools/check_emojis_ui.py
   ```
3. Realize commits seguindo o padrão **Conventional Commits estritamente em Português (pt-BR)**.
4. Faça o `git push` e aguarde a validação da pipeline `build.yml` no GitHub Actions.
5. Após o merge na `master`, dispare a release oficial via GitHub CLI:
   ```bash
   gh workflow run android-apk.yml -f destino=lancar-release -f variante=release
   ```

### Configuração de Ambiente

Definida em `app/build.gradle.kts`:
- `BASE_URL`: `"https://api.bragasaude.online"` (Gateway de APIs)
- `WEBSERVICE_BASE_URL`: `"https://portal.bragasaude.online"` (Portal administrativo e suporte)
- Versão Atual: **v1.3.2 (build 16)**

## 📁 Estrutura de Diretórios

```
app/src/main/java/br/com/bragasaude/
├── BragaApplication.kt       # Application, Dagger Hilt & inicialização segura
├── di/                       # Módulos de injeção de dependências (Hilt)
├── data/
│   ├── local/
│   │   ├── db/               # Room DB, DAOs, Entidades criptografadas (SQLCipher)
│   │   └── voice/            # Motor On-Device: PiperOnDeviceEngine, PiperModelDownloader,
│   │                         #   AndroidSystemTtsFallback e PcmStreamAudioPlayer
│   └── remote/
│       ├── api/              # BragaApiClient (REST, Cert Pinning, AuthInterceptor)
│       ├── ai/               # NeuralAudioPlayer, OrbWebSocket, BragaLocalAiClient
│       ├── auth/             # AuthService, SessionManager
│       └── sync/             # SyncManager, SyncScheduler, BragaFirebaseMessagingService
├── domain/                   # Regras de negócio, Parsers clínicos, Executores de voz
├── ui/                       # Telas e componentes Jetpack Compose
│   ├── voice/                # Interface do Assistente Conversacional (Orb)
│   ├── home/                 # Dashboard principal de saúde
│   ├── profile/              # Perfil clínico, cuidadores e histórico
│   └── theme/                # Tipografia, Cores e Design System
└── util/                     # Utilitários (QR Code, Formatação fonética, Preferências)
```

## 🔌 Funcionalidades Principais

- **Assistente de Voz Clínico On-Device:** Reconhecimento e síntese contínua com streaming de áudio PCM.
- **Triagem e Registro de Vitais:** Pressão arterial, glicemia, peso, hidratação e sintomas.
- **Modo Cuidador e Ponte Familiar:** Vínculo seguro entre familiares, histórico compartilhado e alertas.
- **Mural Social & Gamificação:** Registro de hábitos saudáveis, reações e streaks de cuidado.
- **Sincronização Offline-First:** Armazenamento local seguro e sincronização bidirecional idempotente.
- **Preservação R8/ProGuard:** Regras estritas de ofuscação com proteção total de assinaturas JNI C++ nativas.


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

## 👨‍👩‍👧 Ponte familiar resiliente

O chat da família sincronizava de forma cega: um `while(isActive)` com 3
chamadas de rede a cada 15s, rodando até com o app em background, sem
backoff em falha e sem detectar se algo havia mudado — ruído constante no
log do gateway e consumo de bateria/dados sem necessidade.

Agora o polling é **lifecycle-aware** e resiliente:

- **Gate de tela:** só sincroniza com a `FamilyChatScreen` em primeiro plano
  (`FamilyViewModel.setChatScreenVisible`, acionado pelo ciclo de vida).
- **Backoff exponencial em falha:** 15s → 30s → 60s (teto) após falhas
  consecutivas; retorna a 15s no primeiro sucesso.
- **Skip de ciclo idêntico:** o fingerprint do payload de mensagens
  (`FamilyBridgeRepository.messagesFingerprint`) é comparado entre ciclos;
  conversa parada não gera mais tráfego repetido.
- **Erro visível:** `FamilyViewModel.chatSyncState` mostra um aviso discreto
  na UI ("Tentando atualizar…" / "Não foi possível atualizar…") em vez do
  `Log.e` silencioso. O histórico local segue legível.
- **Isolamento de falhas:** `chatCycleSnapshot` executa vínculos e mensagens
  de forma independente — um erro numa parte não cancela a outra.
