# Regras para agentes — Braga Saúde Android

## Escopo e autorização

- Trabalhe em uma branch isolada `feat/...` ou `fix/...`.
- Respeite a autorização atual do usuário para commits, push, PR e merge.
- Não misture outras frentes no mesmo PR. Confira o diff antes de commitar.
- Não faça deploy manual nem exponha credenciais em código, commits ou logs.

## O que pertence ao repositório

- Código do app, recursos utilizados, testes de produto, configurações de build
  e CI, schemas históricos do Room e dependências binárias necessárias à build.
- Documentação pública essencial: README, contribuição, segurança, licença,
  código de conduta, templates do GitHub e este AGENTS.md.
- Scripts executáveis necessários ao CI ficam em `.github/scripts/`. Não crie
  uma pasta `tools/` na raiz para guardar material de trabalho do agente.
- Não introduza arquivos sem uso demonstrável no produto, build, teste ou CI.

## O que fica fora do Git

- Planos, atas, auditorias, relatórios, decisões internas, catálogos técnicos e
  resumos de sessão devem ficar na área correspondente da base Contexto.
- Pendências novas ficam em `Contexto/_PENDENTE/`; material superado, em
  `Contexto/_HISTORICO/`. Nunca crie arquivos na raiz da Contexto nem faça
  `git init`, commits ou cópia de dados internos da Contexto para o repositório.
- APKs e outros artefatos de distribuição ficam no GitHub Actions ou Releases.
- Não versione saídas geradas, source maps, caches, logs, dependências instaladas,
  dumps, capturas de depuração, cópias de segurança ou arquivos temporários.
- Antes de excluir algo, confira referências de código e CI. Preserve documentos
  internos na Contexto e não remova dependências ativas ou schemas de migração.

## Validação

- Não execute Gradle, compilação ou testes Android pesados localmente.
  Use o GitHub Actions. Não crie atalhos que contornem essa restrição.
- Strings de UI não podem conter emojis literais; use ícones vetoriais.
- Modais devem usar os componentes compartilhados de BragaModals.
- Verificações leves, executadas a partir da raiz:
  ```bash
  python .github/scripts/check_emojis_ui.py
  python .github/scripts/check_repository_hygiene.py
  python .github/scripts/check_modal_consistency.py
  git diff --check
  ```
- O smoke de modelos de voz (`.github/scripts/check_voice_models.py`) roda no CI.
- Antes de mesclar em `master`, confirme sucesso dos checks pertinentes no CI.

## Commits

Use Conventional Commits. Descrição e corpo devem estar em português brasileiro,
com verbo direto no presente, descrição iniciada em minúscula e título sem ponto
final. Tipos: `feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`, `ci`, `perf`.
Exemplo: `chore(repositorio): remove artefatos antigos e atualiza regras dos agentes`.

O workflow `repository-hygiene.yml` verifica também commits apenas de documentação.
Não desative esse gate nem amplie permissões sem autorização explícita do usuário.
