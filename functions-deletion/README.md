# Exclusão após Firebase Auth (DT-001)

Código operacional separado da árvore legada `functions/`. `firebase.json`
publica somente esta codebase. Não depende de Firebase Data Connect.

1. `onUserDeleted` (Auth, 1ª geração) persiste o evento em
   `account_deletion_jobs` antes de qualquer expurgo. Eventos repetidos usam
   o mesmo ID SHA256 e não reiniciam jobs concluídos.
2. Confirma ausência da conta no Firebase, chama o gateway com identidade
   Google OIDC e exige `success=true`, `deleted=true`,
   `storageCleanupPending=false`. O gateway também confirma ausência no Auth.
3. O gateway usa a mesma exclusão transacional do app: banco PostgreSQL,
   cascatas e fila persistente dos arquivos locais.
4. Limpa os prefixos legados `exams/{uid}/` e `uploads/{uid}/` no bucket
   `braga-saude.firebasestorage.app`. Não exclui objetos de outras contas.
5. Só então confirma o job e retira o UID do documento. Falha mantém o job
   pendente e reprova o trigger, sem registrar dados de saúde ou tokens.
6. `retryAccountDeletions`, a cada cinco minutos, reconcilia jobs com backoff
   até seis horas, inclusive após expirar a janela de retry do evento Auth.

## Entrega

Testes Node e carregamento dos exports: GitHub Actions `Validar exclusão Firebase`.
Não compilar Android localmente. Dependências reproduzíveis em package-lock.json.

Preparar identidade, usando o Firebase CLI instalado e sua sessão existente:

```bash
NODE_PATH="$(npm root -g)" node scripts/provision-account-deletion.cjs
cd functions-deletion && npm ci && cd ..
firebase deploy --only functions:account-deletion --project braga-saude
```

Publicar primeiro o contrato no gateway. Identidade permitida:
`account-deletion@braga-saude.iam.gserviceaccount.com`. Audience exata:
`https://api.bragasaude.online/api/admin/firebase-auth-deletion`.
Nenhuma chave de conta de serviço ou senha compartilhada é criada.

As regras atuais do Firestore negam todo acesso de clientes; o script confirma
isso sem sobrescrever regras. A conta de serviço usa Admin SDK e recebe papéis
de datastore, consulta de Auth e escrita de logs. No Storage, recebe apenas
listagem e exclusão com condição que limita exclusão a `exams/` e `uploads/`.

## Validação operacional

Usar conta sintética, com perfil e arquivos sintéticos nas duas raízes. Excluir
individualmente no Firebase Auth; confirmar job concluído, ausência do perfil,
cascatas e arquivos. Testar resposta 503/timeout e retry automático em cenário
controlado. Conferir rejeição de chamadas sem identidade e repetição idempotente.
Não consultar dados de pessoas reais ou imprimir conteúdo de jobs nos logs.

Exclusão em lote via Admin SDK `deleteUsers` não dispara eventos Auth. Esta
entrega cobre exclusões individuais futuras, não reprocessa contas históricas.
Se uma conta ainda existe ou foi recriada, o worker mantém o job pendente e
não autoriza o expurgo. Observar alerta de job pendente e revisar manualmente.
