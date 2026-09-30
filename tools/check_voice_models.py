"""Smoke de síntese real dos pacotes do catálogo; executar no CI."""
import hashlib
import pathlib
import re
import tarfile
import tempfile
import urllib.request
import sherpa_onnx

source = pathlib.Path('app/src/main/java/br/com/bragasaude/data/local/voice/VoiceCatalog.kt').read_text(encoding='utf-8-sig')
models = re.findall(r'VoiceOption\("(\w+)", "[^"]+", "([^"]+)", ([\d_]+),\s*"([a-f0-9]{64})"', source)
assert len(models) == 2
with tempfile.TemporaryDirectory() as temp:
    root = pathlib.Path(temp)
    for name, archive_name, size, checksum in models:
        archive = root / f'{name}.tar.bz2'
        urllib.request.urlretrieve(f'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/{archive_name}.tar.bz2', archive)
        assert archive.stat().st_size == int(size.replace('_', ''))
        assert hashlib.sha256(archive.read_bytes()).hexdigest() == checksum
        with tarfile.open(archive) as package:
            package.extractall(root, filter='data')
        model_dir = root / archive_name
        model = next(model_dir.glob('*.onnx'))
        config = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=str(model), tokens=str(model_dir / 'tokens.txt'),
                data_dir=str(model_dir / 'espeak-ng-data')), num_threads=2, provider='cpu'))
        assert config.validate()
        tts = sherpa_onnx.OfflineTts(config)
        audio = tts.generate('Olá! Estou pronto para ajudar.', sid=0, speed=1.0)
        assert len(audio.samples) > audio.sample_rate // 2
        assert audio.sample_rate == 22050
        assert max(abs(float(sample)) for sample in audio.samples) > 0.001
        print(f'{name}: síntese válida, {audio.sample_rate} Hz, {len(audio.samples)} amostras')
        del tts
