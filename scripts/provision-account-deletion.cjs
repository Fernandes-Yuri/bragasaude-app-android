/* Configuração reproduzível DT-001. Usa a sessão do Firebase CLI, sem exportar chaves. */
"use strict";
const tools = "firebase-tools/lib/";
const project = "braga-saude";
const accountId = "account-deletion";
const bucket = "braga-saude.firebasestorage.app";

async function main() {
  const session = require(tools + "auth").getGlobalDefaultAccount();
  if (!session) throw new Error("Execute firebase login antes da configuração.");
  await require(tools + "requireAuth").requireAuth({ ...session, project });
  const { Client } = require(tools + "apiv2");
  const iam = require(tools + "gcp/iam");
  const manager = require(tools + "gcp/resourceManager");
  const rules = require(tools + "gcp/rules");

  // O banco default já bloqueia clientes. Não publicar regras que sobrescrevam
  // outro produto: abortar se a proteção da fila deixar de ser verificável.
  const release = (await rules.listAllReleases(project)).find(r => r.name.endsWith("/cloud.firestore"));
  if (!release) throw new Error("Regras Firestore não encontradas; confira a proteção da fila.");
  const source = (await rules.getRulesetContent(release.rulesetName)).map(f => f.content).join("\n");
  if (!/match\s+\/\{document=\*\*\}/.test(source) || !/allow read, write: if false/.test(source)
      || /allow[^;]*if\s+(?!false\b)/.test(source)) {
    throw new Error("Regras Firestore precisam de revisão antes de ativar a fila.");
  }

  try {
    await iam.getServiceAccount(project, accountId);
  } catch (error) {
    if (error.status !== 404 && error.context?.response?.statusCode !== 404) throw error;
    await iam.createServiceAccount(project, accountId,
      "Worker de expurgo após exclusão Firebase Auth - DT-001", "Account deletion worker");
  }
  await manager.addServiceAccountToRoles(project, accountId,
    ["roles/datastore.user", "roles/firebaseauth.viewer", "roles/logging.logWriter"]);

  const iamClient = new Client({ urlPrefix: "https://iam.googleapis.com", apiVersion: "v1" });
  const roleName = `projects/${project}/roles/accountDeletionStorage`;
  const role = {
    title: "Account deletion storage", description: "Listar objetos e excluir prefixos de conta - DT-001",
    includedPermissions: ["storage.objects.list", "storage.objects.delete"], stage: "GA",
  };
  const existing = await iamClient.get(`/${roleName}`, { resolveOnHTTPError: true });
  if (existing.status === 404) {
    await iamClient.post(`/projects/${project}/roles`, { roleId: "accountDeletionStorage", role });
  } else if (existing.status !== 200) {
    throw new Error(`Consulta de papel IAM falhou: ${existing.status}`);
  } else {
    await iamClient.patch(`/${roleName}`, { ...role, etag: existing.body.etag },
      { queryParams: { updateMask: "title,description,includedPermissions,stage" } });
  }

  const storage = new Client({ urlPrefix: "https://storage.googleapis.com", apiVersion: "storage/v1" });
  const resource = `projects/_/buckets/${bucket}`;
  const response = await storage.get(`/b/${bucket}/iam`, { queryParams: { optionsRequestedPolicyVersion: 3 } });
  const policy = response.body;
  policy.version = 3;
  policy.bindings ||= [];
  const binding = {
    role: roleName, members: [`serviceAccount:${accountId}@${project}.iam.gserviceaccount.com`],
    condition: {
      title: "account-deletion-prefixes",
      expression: `resource.name == '${resource}' || resource.name.startsWith('${resource}/objects/exams/') || resource.name.startsWith('${resource}/objects/uploads/')`,
    },
  };
  const previous = policy.bindings.find(b => b.role === roleName && b.condition?.title === binding.condition.title);
  if (previous) Object.assign(previous, binding); else policy.bindings.push(binding);
  await storage.put(`/b/${bucket}/iam`, policy);
  console.log("Identidade configurada; fila protegida e exclusão Storage limitada a exams/ e uploads/.");
}

main().catch(error => {
  // Não imprimir objeto HTTP (pode conter Authorization).
  console.error(error.message || "Falha na configuração Firebase.");
  process.exitCode = 1;
});
