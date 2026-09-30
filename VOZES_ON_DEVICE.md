# Catálogo de vozes Piper

O aplicativo oferece Faber, Cadu, Edresson e o mecanismo de fala do dispositivo.
A seleção é local ao dispositivo e não utiliza gênero do perfil do usuário.
Instalações novas não baixam modelos automaticamente. Instalações anteriores
preservam o Faber já presente quando seus arquivos estão completos.

Os pacotes vêm da release `tts-models` de k2-fsa/sherpa-onnx; o catálogo fixa
nome, tamanho e SHA-256. Não há credenciais nem interceptores da API nesses downloads.

- Faber e Cadu: 22.050 Hz, dados CC0, https://github.com/OHF-Voice/voice-datasets
- Edresson: 16.000 Hz, dados CC BY 4.0, https://github.com/Edresson/TTS-Portuguese-Corpus
- Licença CC BY 4.0: https://creativecommons.org/licenses/by/4.0/
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
