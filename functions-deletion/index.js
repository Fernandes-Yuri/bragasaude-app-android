"use strict";

const functions = require("firebase-functions/v1");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const { getStorage } = require("firebase-admin/storage");
const { GoogleAuth } = require("google-auth-library");
const { createHash } = require("node:crypto");
const { GATEWAY_URL, purgeAccount, attemptJob, retryDelayMs } = require("./worker");

initializeApp({ storageBucket: "braga-saude.firebasestorage.app" });
const db = getFirestore();
const jobs = db.collection("account_deletion_jobs");
const identity = new GoogleAuth();
const account = "account-deletion@braga-saude.iam.gserviceaccount.com";

async function assertDeleted(userId) {
  try {
    await getAuth().getUser(userId);
  } catch (error) {
    if (error.code === "auth/user-not-found") return;
    throw new Error("firebase_verification_unavailable");
  }
  throw new Error("firebase_account_still_exists");
}

async function purge(userId) {
  await purgeAccount(userId, {
    assertDeleted,
    gateway: async (uid) => {
      const client = await identity.getIdTokenClient(GATEWAY_URL);
      const response = await client.request({
        url: GATEWAY_URL, method: "POST", data: { userId: uid },
        timeout: 30000, retry: false,
      });
      return response.data;
    },
    deletePrefix: (prefix) => getStorage().bucket().deleteFiles({ prefix }),
  });
}

async function attempt(jobId) {
  await attemptJob(jobId, {
    load: async (id) => (await jobs.doc(id).get()).data(),
    purge,
    complete: (id) => jobs.doc(id).update({
      state: "completed", completedAt: FieldValue.serverTimestamp(),
      nextAttemptAt: FieldValue.delete(),
      userId: FieldValue.delete(),
    }),
    fail: async (id) => {
      await db.runTransaction(async (tx) => {
        const ref = jobs.doc(id);
        const snapshot = await tx.get(ref);
        if (!snapshot.exists || snapshot.get("state") === "completed") return;
        const attempts = (snapshot.get("attempts") || 0) + 1;
        tx.update(ref, {
          attempts, state: "pending", lastFailureAt: FieldValue.serverTimestamp(),
          nextAttemptAt: Timestamp.fromMillis(Date.now() + retryDelayMs(attempts)),
        });
      });
      // Não registrar UID, resposta HTTP, Authorization ou objeto de erro.
      functions.logger.warn("Account deletion remains pending", { jobId: id });
    },
  });
}

exports.onUserDeleted = functions.region("us-central1").runWith({
  serviceAccount: account, failurePolicy: true, timeoutSeconds: 120, memory: "256MB",
}).auth.user().onDelete(async (user, context) => {
  const jobId = createHash("sha256").update(context.eventId).digest("hex");
  await db.runTransaction(async (tx) => {
    const ref = jobs.doc(jobId);
    if ((await tx.get(ref)).exists) return;
    tx.create(ref, {
      userId: user.uid, state: "pending", attempts: 0,
      createdAt: FieldValue.serverTimestamp(),
      nextAttemptAt: Timestamp.now(),
    });
  });
  try {
    await attempt(jobId);
  } catch {
    // Mantém o evento em retry sem vazar headers ou resposta do gateway nos logs.
    throw new Error("Account deletion pending; durable job retained");
  }
});

// Reconcilia inclusive após a janela finita de retry do evento Auth.
exports.retryAccountDeletions = functions.region("us-central1").runWith({
  serviceAccount: account, timeoutSeconds: 540, memory: "256MB",
}).pubsub.schedule("every 5 minutes").onRun(async () => {
  const pending = await jobs.where("nextAttemptAt", "<=", Timestamp.now()).limit(10).get();
  for (const job of pending.docs) {
    try { await attempt(job.id); } catch { /* job persistido; próxima rodada repete */ }
  }
});
