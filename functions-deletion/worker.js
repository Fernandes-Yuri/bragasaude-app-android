"use strict";

const GATEWAY_URL = "https://api.bragasaude.online/api/admin/firebase-auth-deletion";
const STORAGE_PREFIXES = ["exams", "uploads"];

// Dependências injetadas permitem exercitar timeout/retry sem tocar em contas reais.
async function purgeAccount(userId, { assertDeleted, gateway, deletePrefix }) {
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(userId)) {
    throw new Error("invalid_user_id");
  }
  await assertDeleted(userId);
  const result = await gateway(userId);
  if (result?.success !== true || result?.deleted !== true || result?.storageCleanupPending !== false) {
    throw new Error("gateway_cleanup_pending");
  }
  for (const root of STORAGE_PREFIXES) {
    await deletePrefix(`${root}/${userId}/`);
  }
}

async function attemptJob(jobId, { load, purge, complete, fail }) {
  const job = await load(jobId);
  if (!job || job.state === "completed") return;
  try {
    await purge(job.userId);
    await complete(jobId);
  } catch (error) {
    await fail(jobId);
    // Sem sucesso fictício: o trigger deve falhar para ativar o retry nativo.
    throw error;
  }
}

function retryDelayMs(attempts) {
  return Math.min(6 * 60 * 60 * 1000, 60 * 1000 * 2 ** Math.min(attempts, 9));
}

module.exports = { GATEWAY_URL, STORAGE_PREFIXES, purgeAccount, attemptJob, retryDelayMs };
