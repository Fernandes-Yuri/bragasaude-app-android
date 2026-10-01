# Contribuição — Braga Saúde Android

1. Trabalhe em uma branch isolada `feat/...` ou `fix/...`.
2. Use Conventional Commits, com descrição e corpo em português brasileiro.
   Exemplo: `fix(mural): corrige data das postagens`.
3. Execute as verificações leves a partir da raiz do repositório:
   ```bash
   python .github/scripts/check_emojis_ui.py
   python .github/scripts/check_repository_hygiene.py
   python .github/scripts/check_modal_consistency.py
   git diff --check
   ```
4. Envie a branch quando autorizado e valide a compilação e os testes no
   GitHub Actions. Não execute builds, Gradle ou testes Android pesados localmente.
5. Abra o PR para `master`, descreva a alteração e a validação, e aguarde o CI
   antes de mesclar.

Mantenha no Git apenas código, recursos utilizados, testes de produto,
configurações necessárias e documentação pública essencial. Scripts necessários
à validação do projeto ficam em `.github/scripts/`.

Documentos operacionais, planos, auditorias, relatórios e histórico de trabalho
ficam na base Contexto. APKs ficam no GitHub Actions ou em Releases. Arquivos
compilados, caches, logs e dependências instaladas não devem ser versionados.
