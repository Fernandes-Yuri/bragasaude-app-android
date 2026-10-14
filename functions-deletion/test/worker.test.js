"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { purgeAccount, attemptJob, retryDelayMs } = require("../worker");

function dependencies(overrides = {}) {
  const calls = [];
  return {
    calls,
    assertDeleted: async (uid) => calls.push(["auth", uid]),
    gateway: async (uid) => {
      calls.push(["gateway", uid]);
      return { success: true, deleted: true, storageCleanupPending: false };
    },
    deletePrefix: async (prefix) => calls.push(["storage", prefix]),
    ...overrides,
  };
}

test("expurga pelo contrato real e somente prefixos exatos da conta", async () => {
  const deps = dependencies();
  await purgeAccount("synthetic-uid", deps);
  assert.deepEqual(deps.calls, [
    ["auth", "synthetic-uid"], ["gateway", "synthetic-uid"],
    ["storage", "exams/synthetic-uid/"], ["storage", "uploads/synthetic-uid/"],
  ]);
});

test("UID inválido nunca chega à API ou ao armazenamento", async () => {
  for (const uid of ["../other", "", "uid/other", "x".repeat(129)]) {
    const deps = dependencies();
    await assert.rejects(purgeAccount(uid, deps));
    assert.deepEqual(deps.calls, []);
  }
});

test("conta ativa ou recriada impede todas as exclusões", async () => {
  const deps = dependencies({ assertDeleted: async () => { throw new Error("active"); } });
  await assert.rejects(purgeAccount("synthetic-uid", deps));
  assert.deepEqual(deps.calls, []);
});

test("timeout/503 ou resposta incompleta não anuncia expurgo concluído", async () => {
  for (const gateway of [
    async () => { throw new Error("timeout"); },
    async () => { throw new Error("503"); },
    async () => ({ success: false, deleted: true, storageCleanupPending: true }),
    async () => ({ success: true }),
  ]) {
    const deps = dependencies({ gateway });
    await assert.rejects(purgeAccount("synthetic-uid", deps));
    assert.equal(deps.calls.some(([stage]) => stage === "storage"), false);
  }
});

test("fila retém falha do gateway e conclui após recuperação; replay não executa", async () => {
  const job = { userId: "synthetic-uid", state: "pending" };
  let available = false;
  let failures = 0;
  let purges = 0;
  const deps = {
    load: async () => ({ ...job }),
    purge: async () => { purges++; if (!available) throw new Error("503"); },
    complete: async () => { job.state = "completed"; delete job.userId; },
    fail: async () => { failures++; },
  };
  await assert.rejects(attemptJob("job", deps));
  assert.equal(job.state, "pending");
  assert.equal(failures, 1);
  available = true;
  await attemptJob("job", deps);
  assert.equal(job.state, "completed");
  await attemptJob("job", deps);
  assert.equal(purges, 2);
});

test("falha parcial no Storage permanece pendente e repetir a limpeza é seguro", async () => {
  let available = false;
  const deps = dependencies({ deletePrefix: async () => { if (!available) throw new Error("storage unavailable"); } });
  await assert.rejects(purgeAccount("synthetic-uid", deps));
  available = true;
  await purgeAccount("synthetic-uid", deps);
  await purgeAccount("synthetic-uid", deps);
});

test("backoff cresce e mantém reconciliação depois da janela de retry do evento", () => {
  assert.ok(retryDelayMs(2) > retryDelayMs(1));
  assert.equal(retryDelayMs(100), 6 * 60 * 60 * 1000);
});
