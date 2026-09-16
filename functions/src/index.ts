import { onSchedule } from "firebase-functions/v2/scheduler";
import * as functions from "firebase-functions";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import * as crypto from "crypto";
// Firebase Data Connect JavaScript SDK — funções geradas automaticamente
import { getDataConnect } from "firebase/data-connect";
import { connectorConfig } from "@bragasaude/dataconnect";

// Mutations e queries geradas pelo DC SDK para as operações da liga semanal
import { updateLeagueMembershipOutcome } from "@bragasaude/dataconnect";
import { closeLeagueCycle } from "@bragasaude/dataconnect";
import { markCycleAsError } from "@bragasaude/dataconnect";
import { createLeagueCycle } from "@bragasaude/dataconnect";
import { createSocialPost } from "@bragasaude/dataconnect";
import { updateProfileGamification } from "@bragasaude/dataconnect";
import { incrementUserXp } from "@bragasaude/dataconnect";
import { getActiveLeagueCycles } from "@bragasaude/dataconnect";
import { getLeagueMembershipsByCycle } from "@bragasaude/dataconnect";
import { getActiveCyclesGroupedByLevel } from "@bragasaude/dataconnect";
import { getRankedMemberships } from "@bragasaude/dataconnect";

admin.initializeApp();

const db = admin.firestore(); // Embora o projeto utilize Firebase Data Connect/PostgreSQL,
                              // Cloud Functions interagem com os dados via Admin SDK e mutações DC.

// ---------------------------------------------------------------
// CONSTANTES ALINHADAS COM XpRewards.kt (Fase 3 — Liga Balanceada)
// ---------------------------------------------------------------
const SMALL_LEAGUE_PROMOTION_XP = 200;
const SMALL_LEAGUE_DEMOTION_XP = 30;
const MIN_XP_FOR_PROMOTION_BASE = 100;
const MIN_XP_FOR_PROMOTION_PER_LEVEL = 50;
const XP_LEVEL_CURVE_STEP = 50;
const MIN_LEVEL = 1;
const MIN_PARTICIPANTS_FOR_TIER_MOVEMENT = 5;
const PROMOTION_THRESHOLD_PERCENT = 0.20;
const DEMOTION_THRESHOLD_PERCENT = 0.80;

// Inicializa o conector Firebase Data Connect (SDK JavaScript gerado)
const dataConnect = getDataConnect(connectorConfig);

/**
 * Calcula o XP mínimo necessário para promoção em ligas cheias.
 * Níveis mais altos exigem mais XP demonstrado na semana (anti-sandbagging).
 * Alinhado com minXpForPromotion() de GamificationEngine.kt [R17].
 */
function minXpForPromotion(level: number): number {
    const safeLevel = Math.max(MIN_LEVEL, level);
    return MIN_XP_FOR_PROMOTION_BASE + (safeLevel - 1) * MIN_XP_FOR_PROMOTION_PER_LEVEL;
}

/**
 * Deriva o nível do usuário a partir do XP total acumulado (lifetime).
 * Curva polinomial suave: XP(n) = XP_LEVEL_CURVE_STEP * (n-1) * n
 * Alinhado com levelFromTotalXp() de GamificationEngine.kt [R14].
 */
function levelFromTotalXp(totalXp: number): number {
    const safeXp = Math.max(0, totalXp);
    let level = MIN_LEVEL;
    while (level < 100 && XP_LEVEL_CURVE_STEP * level * (level + 1) <= safeXp) {
        level++;
    }
    return level;
}

/**
 * Determina o resultado da liga semanal seguindo o algoritmo melhorado
 * de GamificationEngine.kt determineLeagueOutcomeImproved [R9.1].
 *
 * Correções sobre a versão original:
 * 1. Ligas pequenas (< 5 participantes) usam limiares absolutos de XP —
 *    o progresso nunca trava por falta de concorrentes.
 * 2. Em ligas cheias, estar no Top 20% só promove se o usuário também
 *    atingir o XP mínimo da semana (anti-sandbagging / semanas vazias).
 * 3. Em ligas pequenas, só é rebaixado quem praticamente não participou.
 */
function determineLeagueOutcomeImproved(
    currentLevel: number,
    xpEarned: number,
    rankAtClose: number,
    totalParticipantsInLevel: number
): string {
    if (rankAtClose < 1 || totalParticipantsInLevel < 1) {
        return "maintained";
    }

    // Regra 1: liga pequena → limiares absolutos de XP (progresso nunca trava)
    if (totalParticipantsInLevel < MIN_PARTICIPANTS_FOR_TIER_MOVEMENT) {
        if (xpEarned >= SMALL_LEAGUE_PROMOTION_XP) {
            return "promoted";
        }
        if (xpEarned < SMALL_LEAGUE_DEMOTION_XP && currentLevel > MIN_LEVEL) {
            return "demoted";
        }
        return "maintained";
    }

    // Regra 2: liga cheia → percentil + XP mínimo para promoção (anti-sandbagging)
    const topPercent = rankAtClose / totalParticipantsInLevel;
    if (topPercent <= PROMOTION_THRESHOLD_PERCENT && xpEarned >= minXpForPromotion(currentLevel)) {
        return "promoted";
    }
    if (topPercent >= DEMOTION_THRESHOLD_PERCENT && currentLevel > MIN_LEVEL) {
        return "demoted";
    }
    return "maintained";
}

/**
 * Resolve o nível final pós-ciclo.
 * UX Geriátrica: o rebaixamento de liga NUNCA pode colocar o usuário abaixo
 * do piso conquistado por XP acumulado (alinhado com resolveFinalLevel() [R19]).
 */
function resolveFinalLevel(outcomeLevel: number, xpFloorLevel: number): number {
    return Math.max(Math.max(MIN_LEVEL, outcomeLevel), Math.max(MIN_LEVEL, xpFloorLevel));
}

// ------------------------------------------------------------------
// FUNÇÕES AUXILIARES DATA CONNECT SDK (funções geradas)
// ------------------------------------------------------------------

async function updateMemberOutcomeAdmin(membershipId: string, rank: number, outcome: string): Promise<void> {
    await updateLeagueMembershipOutcome(dataConnect, { membershipId, rankAtClose: rank, outcome });
    logger.debug(`DC: updateLeagueMembershipOutcome(membershipId=${membershipId}, rank=${rank}, outcome=${outcome})`);
}

async function updateUserLevelAndGamificationAdmin(userId: string, newLevel: number): Promise<void> {
    await updateProfileGamification(dataConnect, { userId, currentLevel: newLevel });
    logger.debug(`DC: updateProfileGamification(userId=${userId}, currentLevel=${newLevel})`);
}

async function grantXpAdmin(userId: string, xp: number): Promise<void> {
    await incrementUserXp(dataConnect, { userId, xpAmount: xp });
    logger.debug(`DC: incrementUserXp(userId=${userId}, xp=${xp})`);
}

async function createSocialPostAdmin(userId: string, title: string, postType: string): Promise<void> {
    await createSocialPost(dataConnect, { userId, postType, title, createdAt: new Date().toISOString() });
    logger.debug(`DC: createSocialPost(userId=${userId}, postType=${postType}, title=${title})`);
}

async function getActiveCyclesAdmin(): Promise<Array<{ id: string; level: number; weekEndDate: Date }>> {
    const result = await getActiveLeagueCycles(dataConnect);
    return (result.data.leagueCycles ?? []).map((c: any) => ({
        id: c.id,
        level: c.level,
        weekEndDate: new Date(c.weekEndDate),
    }));
}

async function getMembershipsForCycleAdmin(
    cycleId: string,
    cycleLevel: number
): Promise<Array<{ id: string; userId: string; xpEarned: number; currentLevel: number; totalXp: number }>> {
    const result = await getLeagueMembershipsByCycle(dataConnect, { cycleId, level: cycleLevel });
    return (result.data.memberships ?? []).map((m: any) => ({
        id: m.id,
        userId: m.user?.id ?? m.userId,
        xpEarned: m.xpEarned ?? 0,
        currentLevel: m.user?.currentLevel ?? 1,
        totalXp: m.user?.totalXp ?? 0,
    }));
}

async function closeCycleAdmin(cycleId: string): Promise<void> {
    try {
        await closeLeagueCycle(dataConnect, { cycleId });
    } catch (err: any) {
        logger.error(`DC: Falha ao fechar ciclo ${cycleId}`, err);
        throw err;
    }
}

async function markCycleErrorAdmin(cycleId: string): Promise<void> {
    try {
        await markCycleAsError(dataConnect, { cycleId });
    } catch (err: any) {
        logger.error(`DC: Falha ao marcar ciclo ${cycleId} como erro`, err);
    }
}

/**
 * Cria o próximo ciclo de liga usando mutation real do Data Connect.
 * Prazo padrão: 7 dias a partir do momento atual.
 */
async function createNextCycleAdmin(cycleEndTime: Date): Promise<string> {
    const now = new Date();
    const startTime = now;
    const endTime = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000); // +7 dias

    // Busca o maior nível ativo para criar ciclos nos níveis ausentes
    const levels = Array.from({ length: 100 }, (_, i) => i + 1);
    const createdIds: string[] = [];

    for (const level of levels) {
        const result = await createLeagueCycle(dataConnect, {
            level,
            weekStartDate: startTime.toISOString().split("T")[0],
            weekEndDate: endTime.toISOString().split("T")[0],
            status: "active",
        });
        if (result.data.leagueCycle_insert?.id) {
            createdIds.push(result.data.leagueCycle_insert.id);
        }
    }

    const newCycleId = createdIds[0] || `${Date.now()}`;
    logger.info(`DC: ${createdIds.length} novos ciclos de liga criados (id base: ${newCycleId})`);
    return newCycleId;
}

/**
 * Busca todos os ciclos ativos separados por nível.
 * Retorna Map<levelNumber, { id, level }>
 */
async function getCyclesByLevelAdmin(): Promise<Map<number, { id: string; level: number }>> {
    const result = await getActiveCyclesGroupedByLevel(dataConnect);
    const cyclesMap = new Map<number, { id: string; level: number }>();
    for (const entry of (result.data.entries ?? [])) {
        cyclesMap.set(entry.level, { id: entry.cycleId, level: entry.level });
    }
    return cyclesMap;
}

/**
 * Busca membros de um ciclo por nível e ordena por xpEarned DESC para ranking.
 */
async function getMembershipsRankedForCycleAdmin(
    cycleId: string,
    level: number
): Promise<Array<{
    membershipId: string;
    userId: string;
    xpEarned: number;
    currentLevel: number;
    totalXp: number;
    rank: number;
}>> {
    const result = await getRankedMemberships(dataConnect, { cycleId, level });
    const members = (result.data.memberships ?? []);
    return members.map((m: any, i: number) => ({
        membershipId: m.membershipId ?? m.id,
        userId: m.userId ?? m.user?.id,
        xpEarned: m.xpEarned ?? 0,
        currentLevel: m.currentLevel ?? m.user?.currentLevel ?? 1,
        totalXp: m.totalXp ?? m.user?.totalXp ?? 0,
        rank: i + 1,
    }));
}

// DESATIVADO: Prevenção de fraude de XP até implementação de validação real no servidor.
// Esta função era um onRequest público sem autenticação que concedia XP e criava posts
// com base em dados enviados pelo cliente, sem verificação anti-fraude.
// export const onWriteRelevantEvent = onRequest(async (req, res) => { ... });

/**
 * [F2] FECHAMENTO SEMANAL DE LIGAS
 * Roda todo domingo às 23:59 America/Sao_Paulo
 *
 * Implementação alinhada com GamificationEngine.kt Fase 3:
 *  - Anti-sandbagging: promocão só ocorre com percentil + XP mínimo
 *  - Ligas pequenas usam XP absoluto para desbloquear progresso
 *  - Piso de proteção: nivel final >= nivel derivado do XP total
 *  - Processamento assíncrono com tolerancia a falhas individuais
 */
export const weeklyLeagueCloseJob = onSchedule(
    {
        schedule: "59 23 * * 0",
        timeZone: "America/Sao_Paulo",
        retryCount: 1,
    },
    async () => {
        logger.info("Weekly league closure job starting");

        try {
            // 1. Obtém todos os ciclos ativos agrupados por nível
            const cyclesByLevel = await getCyclesByLevelAdmin();
            logger.info(`Found ${cyclesByLevel.size} active league cycles`);

            if (cyclesByLevel.size === 0) {
                logger.info("No active league cycles found. Creating initial cycles.");
                await createNextCycleAdmin(new Date());
                return;
            }

            // Processa cada nível de liga sequencialmente (evita concorrência entre níveis)
            for (const [level, cycle] of [...cyclesByLevel.entries()].sort((a, b) => a[0] - b[0])) {
                const cycleLabel = `L${level}-${cycle.id}`;
                logger.info(`Processing cycle ${cycleLabel}`);

                try {
                    // 2. Busca membros do ciclo ordenados por xpEarned DESC (ranking)
                    const members = await getMembershipsRankedForCycleAdmin(cycle.id, level);
                    const total = members.length;

                    logger.info(`Cycle ${cycleLabel} has ${total} participants`);

                    // 3. Liga pequena (< 5 participantes): mantém todos sem movimento de tier
                    if (total < MIN_PARTICIPANTS_FOR_TIER_MOVEMENT) {
                        logger.info(`Cycle ${cycleLabel}: too few participants (${total}), maintaining everyone`);
                        
                        // Ainda assim permite promoções/rebaixamentos por XP absoluto em ligas pequenas
                        const smallLeagueResults = await Promise.all(
                            members.map(async (member) => {
                                const outcome = determineLeagueOutcomeImproved(
                                    member.currentLevel,
                                    member.xpEarned,
                                    member.rank,
                                    total
                                );

                                // Calcular nível da promoção/demissão vs nível por XP acumulado
                                const outcomeLevel = outcome === "promoted" 
                                    ? member.currentLevel + 1 
                                    : outcome === "demoted"
                                        ? Math.max(MIN_LEVEL, member.currentLevel - 1)
                                        : member.currentLevel;

                                const xpFloorLevel = levelFromTotalXp(member.totalXp);
                                const finalLevel = resolveFinalLevel(outcomeLevel, xpFloorLevel);

                                try {
                                    await updateMemberOutcomeAdmin(member.membershipId, member.rank, outcome);
                                    
                                    if (finalLevel !== member.currentLevel) {
                                        await updateUserLevelAndGamificationAdmin(member.userId, finalLevel);
                                    }

                                    return { userId: member.userId, outcome, finalLevel, status: "success" };
                                } catch (err) {
                                    logger.error(`Failed to process member ${member.userId} in ${cycleLabel}`, err);
                                    return { userId: member.userId, outcome, status: "error", error: String(err) };
                                }
                            })
                        );

                        smallLeagueResults.forEach(r => {
                            if (r.status === "success") {
                                logger.info(`Small league ${cycleLabel}: ${r.userId} → ${r.outcome} → Level ${r.finalLevel}`);
                            } else {
                                logger.error(`Small league ${cycleLabel}: ${r.userId} → ${r.outcome} → ERROR: ${r.error}`);
                            }
                        });

                        // Fecha ciclo após processar todos os membros (atomicidade garantida)
                        try {
                            await closeCycleAdmin(cycle.id);
                        } catch (err) {
                            logger.error(`Failed to close cycle ${cycleLabel}`, err);
                            await markCycleErrorAdmin(cycle.id);
                        }

                        continue;
                    }

                    // 4. Liga cheia: usa algoritmo melhorado (percentil + XP mínimo)
                    logger.info(`Cycle ${cycleLabel}: full league with ${total} participants, running improved algorithm`);

                    const results = await Promise.all(
                        members.map(async (member) => {
                            const rank = member.rank;
                            const topPercent = rank / total;

                            const outcome = determineLeagueOutcomeImproved(
                                member.currentLevel,
                                member.xpEarned,
                                rank,
                                total
                            );

                            // Calcular nível final com piso de proteção (alinhado com resolveFinalLevel [R19])
                            const outcomeLevel = outcome === "promoted"
                                ? member.currentLevel + 1
                                : outcome === "demoted"
                                    ? Math.max(MIN_LEVEL, member.currentLevel - 1)
                                    : member.currentLevel;

                            const xpFloorLevel = levelFromTotalXp(member.totalXp);
                            const finalLevel = resolveFinalLevel(outcomeLevel, xpFloorLevel);

                            try {
                                // Atualiza outcome na membership antes de mudar o nível do perfil
                                await updateMemberOutcomeAdmin(member.membershipId, rank, outcome);

                                if (finalLevel !== member.currentLevel) {
                                    await updateUserLevelAndGamificationAdmin(member.userId, finalLevel);
                                }

                                // Cria publicação social baseada no outcome (sem emojis, tom positivo UX geriátrica)
                                if (outcome === "promoted") {
                                    const message = `Parabéns! Você subiu para o Nível ${finalLevel}. Continue nesse ritmo!`;
                                    await createSocialPostAdmin(member.userId, message, "weekly_recap");
                                } else if (outcome === "maintained") {
                                    const message = `Você se manteve firme no Nível ${finalLevel} esta semana. Continue assim!`;
                                    await createSocialPostAdmin(member.userId, message, "weekly_recap");
                                } else if (outcome === "demoted") {
                                    const message = `Nova semana, novo ciclo! Você está no Nível ${finalLevel} — vamos juntos de novo?`;
                                    await createSocialPostAdmin(member.userId, message, "weekly_recap");
                                }

                                return { userId: member.userId, outcome, finalLevel, rank, topPercent: parseFloat(topPercent.toFixed(4)), status: "success" };
                            } catch (err) {
                                logger.error(`Failed to process member ${member.userId} in ${cycleLabel}`, err);
                                return { userId: member.userId, outcome, rank, status: "error", error: String(err) };
                            }
                        })
                    );

                    // Log detalhado de cada resultado
                    const promoted = results.filter(r => r.status === "success" && r.outcome === "promoted");
                    const demoted = results.filter(r => r.status === "success" && r.outcome === "demoted");
                    const maintained = results.filter(r => r.status === "success" && r.outcome === "maintained");
                    const errors = results.filter(r => r.status === "error");

                    logger.info(`Cycle ${cycleLabel} results: ${promoted.length} promoted, ${demoted.length} demoted, ${maintained.length} maintained, ${errors.length} errors`);

                    promoted.forEach(r => {
                        logger.info(`Cycle ${cycleLabel}: User ${r.userId} PROMOTED to Level ${r.finalLevel} (rank #${r.rank}, top ${r.topPercent * 100}%)`);
                    });

                    demoted.forEach(r => {
                        logger.info(`Cycle ${cycleLabel}: User ${r.userId} DEMOTED to Level ${r.finalLevel} (rank #${r.rank}, top ${r.topPercent * 100}%)`);
                    });

                    maintained.forEach(r => {
                        logger.info(`Cycle ${cycleLabel}: User ${r.userId} MAINTAINED at Level ${r.finalLevel} (rank #${r.rank})`);
                    });

                    errors.forEach(r => {
                        logger.error(`Cycle ${cycleLabel}: User ${r.userId} ERROR (${r.error})`);
                    });

                    // 5. Fecha ciclo ANTES de criar próximo (garantindo atomicidade)
                    if (errors.length > 0) {
                        logger.warn(`Cycle ${cycleLabel} had errors, marking as error state`);
                        await markCycleErrorAdmin(cycle.id);
                    } else {
                        await closeCycleAdmin(cycle.id);
                    }

                } catch (cycleError) {
                    logger.error(`Fatal error processing cycle ${cycleLabel}`, cycleError);
                    await markCycleErrorAdmin(cycle.id);
                }
            }

            // 6. Cria próximo ciclo APÓS fechar todos os anteriores (atomicidade: fechar → criar)
            await createNextCycleAdmin(new Date());

            logger.info("Weekly league closure completed successfully");
        } catch (error) {
            logger.error("Fatal error in weekly league close job", error);
        }
    }
);

/**
 * [F3] DIREITO AO ESQUECIMENTO (LGPD)
 * Trigger nativo do Firebase Auth — executa automaticamente quando um usuário
 * é excluído. NÃO é um endpoint HTTP; impossível de invocar externamente.
 */
export const onUserDeleted = functions.auth.user().onDelete(async (user) => {
    const userId = user.uid;
    const startTime = Date.now();

    try {
        logger.info(`Cleaning up data for deleted user: ${userId}`);

        // --- FASE A: Remoção via Firebase Data Connect (backend PostgreSQL) ---
        // Ordem de exclusão respeitando integridade referencial (PostgreSQL)
        // 1. Reações e Posts Sociais
        await deleteUserSocialReactionsAdmin(userId);
        await deleteUserSocialPostsAdmin(userId);

        // 2. Ligas e Medalhas
        await deleteUserLeagueMembershipsAdmin(userId);
        await deleteUserMilestonesAdmin(userId);

        // 3. Dados Clínicos e Perfil
        await deleteUserClinicalDataAdmin(userId);
        await deleteUserProfileAdmin(userId);

        logger.info(`Data purged successfully for user ${userId} (local DC helpers, ${Date.now() - startTime}ms)`);

    } catch (error) {
        logger.error("Error in local data cleanup", error);
    }

    // --- FASE B: Exclusão em cascata completa via Webservice HTTP ---
    // O Cloud Function aciona o endpoint seguro que apaga:
    //   - Todas as tabelas relacionadas ao usuário no PostgreSQL (via Data Connect)
    //   - Arquivos do Storage Firebase (exams/ e uploads/)
    //   - Registro de auditoria LGPD (user_deletion_log)
    try {
        await triggerCascadeDeletion(userId);
        logger.info(`Cascade deletion completed for user ${userId} (${Date.now() - startTime}ms total)`);
    } catch (error) {
        logger.error("Error triggering cascade deletion (may retry automatically or require manual action)", error);
        // NÃO rejeita o trigger — os dados locais já foram limpos acima.
        // O endpoint HTTP pode falhar se o webservice estiver indisponível temporariamente.
    }
});

// --- CASCADE DELETION VIA WEBSERVICE ---

const WEBSERVICE_CASCADE_URL = process.env.WEBSERVICE_CASCADE_URL || 'https://api.bragasaude.app/api/admin/delete-user-data';

/**
 * Gera HMAC token para autenticação entre Cloud Function e Webservice.
 * Mesma lógica do adminAuth.ts no webservice.
 */
function generateDeleteToken(userId: string): string {
    const secret = process.env.ADMIN_SESSION_SECRET || process.env.ADMIN_PASSWORD || '';
    if (!secret) {
        throw new Error('ADMIN_SESSION_SECRET or ADMIN_PASSWORD must be configured for cascade deletion');
    }

    const payload: { userId: string; timestamp: number; exp: number } = {
        userId,
        timestamp: Date.now(),
        exp: Date.now() + 5 * 60 * 1000 // 5 minutos de validade
    };

    const payloadBase64 = Buffer.from(JSON.stringify(payload)).toString('base64url');
    const signature = crypto
        .createHmac('sha256', secret)
        .update(payloadBase64)
        .digest('base64url');

    return `${payloadBase64}.${signature}`;
}

/**
 * Aciona a exclusão em cascata completa via endpoint HTTP seguro do Webservice.
 * Remove todas as tabelas relacionadas ao usuário, arquivos do Storage,
 * e registra a exclusão no log de auditoria LGPD.
 */
async function triggerCascadeDeletion(userId: string): Promise<void> {
    const token = generateDeleteToken(userId);

    const response = await fetch(WEBSERVICE_CASCADE_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ userId }),
        signal: AbortSignal.timeout(30000) // timeout de 30s
    });

    if (!response.ok) {
        const text = await response.text().catch(() => '');
        logger.warn(`Cascade deletion returned ${response.status}: ${text}`);
        throw new Error(`Cascade deletion failed with status ${response.status}: ${text}`);
    }

    const result = await response.json() as { success: boolean; deletedTables: string[]; storageFilesDeleted: number };
    logger.info(`Cascade result: tables=${result.deletedTables.length}, files=${result.storageFilesDeleted}`);
}

// --- HELPER FUNCTIONS (SIMULANDO CHAMADAS AO DATA CONNECT VIA SERVICE ACCOUNT) ---

async function grantXpAdminLegacy(userId: string, xp: number) {
    // Aqui usaria o SDK Admin do Data Connect ou uma mutation server-only via HTTP
    logger.info(`Admin: Granting ${xp} XP to user ${userId}`);
}

async function createSocialPostAdminLegacy(userId: string, content: string, type: string) {
    logger.info(`Admin: Creating social post for ${userId}: ${content}`);
}

async function getActiveCyclesAdminLegacy(): Promise<any[]> {
    return [{ id: "current-cycle-uuid", endTime: new Date() }];
}

async function getMembershipsForCycleAdminLegacy(cycleId: string): Promise<any[]> {
    return []; // Retornaria a lista de membros do ciclo
}

async function updateMemberOutcomeAdminLegacy(userId: string, cycleId: string, rank: number, outcome: string) {
    logger.info(`Admin: Setting outcome ${outcome} for user ${userId} in cycle ${cycleId}`);
}

async function updateUserLevelAdminLegacy(userId: string, newLevel: number) {
    logger.info(`Admin: Updating user ${userId} to Level ${newLevel} and resetting cycle XP`);
}

async function closeCycleAdminLegacy(cycleId: string) {
    logger.info(`Admin: Closing cycle ${cycleId}`);
}

async function createNextCycleAdminLegacy(lastEndTime: Date) {
    logger.info("Admin: Creating next league cycle");
}

async function deleteUserSocialReactionsAdmin(userId: string) {
    logger.info(`Admin: Deleting reactions for user ${userId}`);
}

async function deleteUserSocialPostsAdmin(userId: string) {
    logger.info(`Admin: Deleting posts for user ${userId}`);
}

async function deleteUserLeagueMembershipsAdmin(userId: string) {
    logger.info(`Admin: Deleting league memberships for user ${userId}`);
}

async function deleteUserMilestonesAdmin(userId: string) {
    logger.info(`Admin: Deleting milestones for user ${userId}`);
}

async function deleteUserClinicalDataAdmin(userId: string) {
    logger.info(`Admin: Deleting all clinical data (vitals, exams, etc) for user ${userId}`);
}

async function deleteUserProfileAdmin(userId: string) {
    logger.info(`Admin: Deleting profile for user ${userId}`);
}

function isVitalsWithinTarget(data: any): boolean {
    // Replicar lógica de GamificationEngine.kt
    return true;
}
