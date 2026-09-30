# Catálogo de vozes Piper

O aplicativo oferece Faber, Cadu e o mecanismo de fala do dispositivo.
A seleção é local ao dispositivo e não utiliza gênero do perfil do usuário.
Instalações novas não baixam modelos automaticamente. Instalações anteriores
preservam o Faber já presente quando seus arquivos estão completos.

Os pacotes vêm da release `tts-models` de k2-fsa/sherpa-onnx; o catálogo fixa
nome, tamanho e SHA-256. Não há credenciais nem interceptores da API nesses downloads.

- Faber e Cadu: 22.050 Hz, dados CC0, https://github.com/OHF-Voice/voice-datasets
- Modelos e fichas: https://huggingface.co/rhasspy/piper-voices/tree/main/pt/pt_BR
- Pacotes adaptados: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models

As prévias são os arquivos `samples/speaker_0.mp3` do respectivo modelo no
repositório rhasspy/piper-voices. Os arquivos são preservados; a interface toca
até 2,5 segundos. O download preserva a ficha MODEL_CARD de cada modelo.

## Instalação e recuperação

`filesDir/voices/staging` recebe o pacote e os arquivos extraídos. A extração
rejeita caminhos externos e links, limita tamanho e número de entradas e só
prossegue após validar SHA-256. O diretório `current` é renomeado para `previous`
e o staging é renomeado para `current`, sob exclusão mútua com a síntese JNI.
A preferência persistida só muda depois de inicializar a nova voz. Falhas antes
do commit restauram `previous`; o próximo início recupera transações interrompidas.

Após confirmação, `previous` é removido. Há um modelo instalado ao final, cerca
de 82 MB. Download, staging e cópia anterior coexistem temporariamente; a UI
pede 180 MB livres adicionais. O TTS do Android depende das vozes instaladas
no sistema e pode precisar de configuração externa para funcionar offline.

## Validação

Os testes JVM cobrem checksum, extração segura e recuperação após interrupções.
O CI compila o aplicativo e executa os testes. O workflow de modelos baixa os
três pacotes e sintetiza uma frase com Sherpa-ONNX 1.13.8 no Linux.
A validação no Android ainda deve conferir audição, cancelamento, rotação,
modo avião após instalação, pouco espaço e encerramento do processo durante troca.

## Preparação em segundo plano

O botão “Minimizar e continuar usando o app” fecha a apresentação sem cancelar
a preparação. Modelos neurais são preparados por um Worker único do WorkManager,
com serviço de primeiro plano do tipo dataSync e aviso discreto durante o trabalho.
O Android pode retomar uma tarefa interrompida recriando o processo; o download
reinicia se o pacote estava incompleto, e uma instalação já confirmada é reaproveitada.

A solicitação de aviso fica persistida até a conclusão. O aplicativo notifica
sucesso ou falha e não reproduz a saudação quando a tarefa foi minimizada.
No Android 13+, a ação pede POST_NOTIFICATIONS quando necessário. Se a permissão
for negada ou as notificações estiverem bloqueadas, o trabalho continua e a interface
informa que o resultado pode ser acompanhado nas configurações de voz.

Os testes do gerenciador incluem minimização sem cancelamento, notificação de
sucesso/falha, supressão da saudação, cancelamento sem notificação de sucesso e a
corrida entre conclusão e clique de minimização. Compilação e execução desses testes
ficam para o CI; validar no aparelho com o app em segundo plano e com notificações
permitidas e negadas.
