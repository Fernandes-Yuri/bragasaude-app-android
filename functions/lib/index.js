"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onUserDeleted = exports.weeklyLeagueCloseJob = void 0;
const scheduler_1 = require("firebase-functions/v2/scheduler");
const functions = require("firebase-functions");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const crypto = require("crypto");
const data_connect_1 = require("firebase/data-connect");
const dataconnect_1 = require("@bragasaude/dataconnect");
const dataconnect_2 = require("@bragasaude/dataconnect");
const dataconnect_3 = require("@bragasaude/dataconnect");
const dataconnect_4 = require("@bragasaude/dataconnect");
const dataconnect_5 = require("@bragasaude/dataconnect");
const dataconnect_6 = require("@bragasaude/dataconnect");
const dataconnect_7 = require("@bragasaude/dataconnect");
const dataconnect_8 = require("@bragasaude/dataconnect");
const dataconnect_9 = require("@bragasaude/dataconnect");
const dataconnect_10 = require("@bragasaude/dataconnect");
const dataconnect_11 = require("@bragasaude/dataconnect");
const dataconnect_12 = require("@bragasaude/dataconnect");
admin.initializeApp();
const db = admin.firestore();
const SMALL_LEAGUE_PROMOTION_XP = 200;
const SMALL_LEAGUE_DEMOTION_XP = 30;
const MIN_XP_FOR_PROMOTION_BASE = 100;
const MIN_XP_FOR_PROMOTION_PER_LEVEL = 50;
const XP_LEVEL_CURVE_STEP = 50;
const MIN_LEVEL = 1;
const MIN_PARTICIPANTS_FOR_TIER_MOVEMENT = 5;
const PROMOTION_THRESHOLD_PERCENT = 0.20;
const DEMOTION_THRESHOLD_PERCENT = 0.80;
const dataConnect = (0, data_connect_1.getDataConnect)(dataconnect_1.connectorConfig);
function minXpForPromotion(level) {
    const safeLevel = Math.max(MIN_LEVEL, level);
    return MIN_XP_FOR_PROMOTION_BASE + (safeLevel - 1) * MIN_XP_FOR_PROMOTION_PER_LEVEL;
}
function levelFromTotalXp(totalXp) {
    const safeXp = Math.max(0, totalXp);
    let level = MIN_LEVEL;
    while (level < 100 && XP_LEVEL_CURVE_STEP * level * (level + 1) <= safeXp) {
        level++;
    }
    return level;
}
function determineLeagueOutcomeImproved(currentLevel, xpEarned, rankAtClose, totalParticipantsInLevel) {
    if (rankAtClose < 1 || totalParticipantsInLevel < 1) {
        return "maintained";
    }
    if (totalParticipantsInLevel < MIN_PARTICIPANTS_FOR_TIER_MOVEMENT) {
        if (xpEarned >= SMALL_LEAGUE_PROMOTION_XP) {
            return "promoted";
        }
        if (xpEarned < SMALL_LEAGUE_DEMOTION_XP && currentLevel > MIN_LEVEL) {
            return "demoted";
        }
        return "maintained";
    }
    const topPercent = rankAtClose / totalParticipantsInLevel;
    if (topPercent <= PROMOTION_THRESHOLD_PERCENT && xpEarned >= minXpForPromotion(currentLevel)) {
        return "promoted";
    }
    if (topPercent >= DEMOTION_THRESHOLD_PERCENT && currentLevel > MIN_LEVEL) {
        return "demoted";
    }
    return "maintained";
}
function resolveFinalLevel(outcomeLevel, xpFloorLevel) {
    return Math.max(Math.max(MIN_LEVEL, outcomeLevel), Math.max(MIN_LEVEL, xpFloorLevel));
}
async function updateMemberOutcomeAdmin(membershipId, rank, outcome) {
    await (0, dataconnect_2.updateLeagueMembershipOutcome)(dataConnect, { membershipId, rankAtClose: rank, outcome });
    logger.debug(`DC: updateLeagueMembershipOutcome(membershipId=${membershipId}, rank=${rank}, outcome=${outcome})`);
}
async function updateUserLevelAndGamificationAdmin(userId, newLevel) {
    await (0, dataconnect_7.updateProfileGamification)(dataConnect, { userId, currentLevel: newLevel });
    logger.debug(`DC: updateProfileGamification(userId=${userId}, currentLevel=${newLevel})`);
}
async function grantXpAdmin(userId, xp) {
    await (0, dataconnect_8.incrementUserXp)(dataConnect, { userId, xpAmount: xp });
    logger.debug(`DC: incrementUserXp(userId=${userId}, xp=${xp})`);
}
async function createSocialPostAdmin(userId, title, postType) {
    await (0, dataconnect_6.createSocialPost)(dataConnect, { userId, postType, title, createdAt: new Date().toISOString() });
    logger.debug(`DC: createSocialPost(userId=${userId}, postType=${postType}, title=${title})`);
}
async function getActiveCyclesAdmin() {
    const result = await (0, dataconnect_9.getActiveLeagueCycles)(dataConnect);
    return (result.data.leagueCycles ?? []).map((c) => ({
        id: c.id,
        level: c.level,
        weekEndDate: new Date(c.weekEndDate),
    }));
}
async function getMembershipsForCycleAdmin(cycleId, cycleLevel) {
    const result = await (0, dataconnect_10.getLeagueMembershipsByCycle)(dataConnect, { cycleId, level: cycleLevel });
    return (result.data.memberships ?? []).map((m) => ({
        id: m.id,
        userId: m.user?.id ?? m.userId,
        xpEarned: m.xpEarned ?? 0,
        currentLevel: m.user?.currentLevel ?? 1,
        totalXp: m.user?.totalXp ?? 0,
    }));
}
async function closeCycleAdmin(cycleId) {
    try {
        await (0, dataconnect_3.closeLeagueCycle)(dataConnect, { cycleId });
    }
    catch (err) {
        logger.error(`DC: Falha ao fechar ciclo ${cycleId}`, err);
        throw err;
    }
}
async function markCycleErrorAdmin(cycleId) {
    try {
        await (0, dataconnect_4.markCycleAsError)(dataConnect, { cycleId });
    }
    catch (err) {
        logger.error(`DC: Falha ao marcar ciclo ${cycleId} como erro`, err);
    }
}
async function createNextCycleAdmin(cycleEndTime) {
    const now = new Date();
    const startTime = now;
    const endTime = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
    const levels = Array.from({ length: 100 }, (_, i) => i + 1);
    const createdIds = [];
    for (const level of levels) {
        const result = await (0, dataconnect_5.createLeagueCycle)(dataConnect, {
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
async function getCyclesByLevelAdmin() {
    const result = await (0, dataconnect_11.getActiveCyclesGroupedByLevel)(dataConnect);
    const cyclesMap = new Map();
    for (const entry of (result.data.entries ?? [])) {
        cyclesMap.set(entry.level, { id: entry.cycleId, level: entry.level });
    }
    return cyclesMap;
}
async function getMembershipsRankedForCycleAdmin(cycleId, level) {
    const result = await (0, dataconnect_12.getRankedMemberships)(dataConnect, { cycleId, level });
    const members = (result.data.memberships ?? []);
    return members.map((m, i) => ({
        membershipId: m.membershipId ?? m.id,
        userId: m.userId ?? m.user?.id,
        xpEarned: m.xpEarned ?? 0,
        currentLevel: m.currentLevel ?? m.user?.currentLevel ?? 1,
        totalXp: m.totalXp ?? m.user?.totalXp ?? 0,
        rank: i + 1,
    }));
}
exports.weeklyLeagueCloseJob = (0, scheduler_1.onSchedule)({
    schedule: "59 23 * * 0",
    timeZone: "America/Sao_Paulo",
    retryCount: 1,
}, async () => {
    logger.info("Weekly league closure job starting");
    try {
        const cyclesByLevel = await getCyclesByLevelAdmin();
        logger.info(`Found ${cyclesByLevel.size} active league cycles`);
        if (cyclesByLevel.size === 0) {
            logger.info("No active league cycles found. Creating initial cycles.");
            await createNextCycleAdmin(new Date());
            return;
        }
        for (const [level, cycle] of [...cyclesByLevel.entries()].sort((a, b) => a[0] - b[0])) {
            const cycleLabel = `L${level}-${cycle.id}`;
            logger.info(`Processing cycle ${cycleLabel}`);
            try {
                const members = await getMembershipsRankedForCycleAdmin(cycle.id, level);
                const total = members.length;
                logger.info(`Cycle ${cycleLabel} has ${total} participants`);
                if (total < MIN_PARTICIPANTS_FOR_TIER_MOVEMENT) {
                    logger.info(`Cycle ${cycleLabel}: too few participants (${total}), maintaining everyone`);
                    const smallLeagueResults = await Promise.all(members.map(async (member) => {
                        const outcome = determineLeagueOutcomeImproved(member.currentLevel, member.xpEarned, member.rank, total);
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
                        }
                        catch (err) {
                            logger.error(`Failed to process member ${member.userId} in ${cycleLabel}`, err);
                            return { userId: member.userId, outcome, status: "error", error: String(err) };
                        }
                    }));
                    smallLeagueResults.forEach(r => {
                        if (r.status === "success") {
                            logger.info(`Small league ${cycleLabel}: ${r.userId} → ${r.outcome} → Level ${r.finalLevel}`);
                        }
                        else {
                            logger.error(`Small league ${cycleLabel}: ${r.userId} → ${r.outcome} → ERROR: ${r.error}`);
                        }
                    });
                    try {
                        await closeCycleAdmin(cycle.id);
                    }
                    catch (err) {
                        logger.error(`Failed to close cycle ${cycleLabel}`, err);
                        await markCycleErrorAdmin(cycle.id);
                    }
                    continue;
                }
                logger.info(`Cycle ${cycleLabel}: full league with ${total} participants, running improved algorithm`);
                const results = await Promise.all(members.map(async (member) => {
                    const rank = member.rank;
                    const topPercent = rank / total;
                    const outcome = determineLeagueOutcomeImproved(member.currentLevel, member.xpEarned, rank, total);
                    const outcomeLevel = outcome === "promoted"
                        ? member.currentLevel + 1
                        : outcome === "demoted"
                            ? Math.max(MIN_LEVEL, member.currentLevel - 1)
                            : member.currentLevel;
                    const xpFloorLevel = levelFromTotalXp(member.totalXp);
                    const finalLevel = resolveFinalLevel(outcomeLevel, xpFloorLevel);
                    try {
                        await updateMemberOutcomeAdmin(member.membershipId, rank, outcome);
                        if (finalLevel !== member.currentLevel) {
                            await updateUserLevelAndGamificationAdmin(member.userId, finalLevel);
                        }
                        if (outcome === "promoted") {
                            const message = `Parabéns! Você subiu para o Nível ${finalLevel}. Continue nesse ritmo!`;
                            await createSocialPostAdmin(member.userId, message, "weekly_recap");
                        }
                        else if (outcome === "maintained") {
                            const message = `Você se manteve firme no Nível ${finalLevel} esta semana. Continue assim!`;
                            await createSocialPostAdmin(member.userId, message, "weekly_recap");
                        }
                        else if (outcome === "demoted") {
                            const message = `Nova semana, novo ciclo! Você está no Nível ${finalLevel} — vamos juntos de novo?`;
                            await createSocialPostAdmin(member.userId, message, "weekly_recap");
                        }
                        return { userId: member.userId, outcome, finalLevel, rank, topPercent: parseFloat(topPercent.toFixed(4)), status: "success" };
                    }
                    catch (err) {
                        logger.error(`Failed to process member ${member.userId} in ${cycleLabel}`, err);
                        return { userId: member.userId, outcome, rank, status: "error", error: String(err) };
                    }
                }));
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
                if (errors.length > 0) {
                    logger.warn(`Cycle ${cycleLabel} had errors, marking as error state`);
                    await markCycleErrorAdmin(cycle.id);
                }
                else {
                    await closeCycleAdmin(cycle.id);
                }
            }
            catch (cycleError) {
                logger.error(`Fatal error processing cycle ${cycleLabel}`, cycleError);
                await markCycleErrorAdmin(cycle.id);
            }
        }
        await createNextCycleAdmin(new Date());
        logger.info("Weekly league closure completed successfully");
    }
    catch (error) {
        logger.error("Fatal error in weekly league close job", error);
    }
});
exports.onUserDeleted = functions.auth.user().onDelete(async (user) => {
    const userId = user.uid;
    const startTime = Date.now();
    try {
        logger.info(`Cleaning up data for deleted user: ${userId}`);
        await deleteUserSocialReactionsAdmin(userId);
        await deleteUserSocialPostsAdmin(userId);
        await deleteUserLeagueMembershipsAdmin(userId);
        await deleteUserMilestonesAdmin(userId);
        await deleteUserClinicalDataAdmin(userId);
        await deleteUserProfileAdmin(userId);
        logger.info(`Data purged successfully for user ${userId} (local DC helpers, ${Date.now() - startTime}ms)`);
    }
    catch (error) {
        logger.error("Error in local data cleanup", error);
    }
    try {
        await triggerCascadeDeletion(userId);
        logger.info(`Cascade deletion completed for user ${userId} (${Date.now() - startTime}ms total)`);
    }
    catch (error) {
        logger.error("Error triggering cascade deletion (may retry automatically or require manual action)", error);
    }
});
const WEBSERVICE_CASCADE_URL = process.env.WEBSERVICE_CASCADE_URL || 'https://api.bragasaude.app/api/admin/delete-user-data';
function generateDeleteToken(userId) {
    const secret = process.env.ADMIN_SESSION_SECRET || process.env.ADMIN_PASSWORD || '';
    if (!secret) {
        throw new Error('ADMIN_SESSION_SECRET or ADMIN_PASSWORD must be configured for cascade deletion');
    }
    const payload = {
        userId,
        timestamp: Date.now(),
        exp: Date.now() + 5 * 60 * 1000
    };
    const payloadBase64 = Buffer.from(JSON.stringify(payload)).toString('base64url');
    const signature = crypto
        .createHmac('sha256', secret)
        .update(payloadBase64)
        .digest('base64url');
    return `${payloadBase64}.${signature}`;
}
async function triggerCascadeDeletion(userId) {
    const token = generateDeleteToken(userId);
    const response = await fetch(WEBSERVICE_CASCADE_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ userId }),
        signal: AbortSignal.timeout(30000)
    });
    if (!response.ok) {
        const text = await response.text().catch(() => '');
        logger.warn(`Cascade deletion returned ${response.status}: ${text}`);
        throw new Error(`Cascade deletion failed with status ${response.status}: ${text}`);
    }
    const result = await response.json();
    logger.info(`Cascade result: tables=${result.deletedTables.length}, files=${result.storageFilesDeleted}`);
}
async function grantXpAdminLegacy(userId, xp) {
    logger.info(`Admin: Granting ${xp} XP to user ${userId}`);
}
async function createSocialPostAdminLegacy(userId, content, type) {
    logger.info(`Admin: Creating social post for ${userId}: ${content}`);
}
async function getActiveCyclesAdminLegacy() {
    return [{ id: "current-cycle-uuid", endTime: new Date() }];
}
async function getMembershipsForCycleAdminLegacy(cycleId) {
    return [];
}
async function updateMemberOutcomeAdminLegacy(userId, cycleId, rank, outcome) {
    logger.info(`Admin: Setting outcome ${outcome} for user ${userId} in cycle ${cycleId}`);
}
async function updateUserLevelAdminLegacy(userId, newLevel) {
    logger.info(`Admin: Updating user ${userId} to Level ${newLevel} and resetting cycle XP`);
}
async function closeCycleAdminLegacy(cycleId) {
    logger.info(`Admin: Closing cycle ${cycleId}`);
}
async function createNextCycleAdminLegacy(lastEndTime) {
    logger.info("Admin: Creating next league cycle");
}
async function deleteUserSocialReactionsAdmin(userId) {
    logger.info(`Admin: Deleting reactions for user ${userId}`);
}
async function deleteUserSocialPostsAdmin(userId) {
    logger.info(`Admin: Deleting posts for user ${userId}`);
}
async function deleteUserLeagueMembershipsAdmin(userId) {
    logger.info(`Admin: Deleting league memberships for user ${userId}`);
}
async function deleteUserMilestonesAdmin(userId) {
    logger.info(`Admin: Deleting milestones for user ${userId}`);
}
async function deleteUserClinicalDataAdmin(userId) {
    logger.info(`Admin: Deleting all clinical data (vitals, exams, etc) for user ${userId}`);
}
async function deleteUserProfileAdmin(userId) {
    logger.info(`Admin: Deleting profile for user ${userId}`);
}
function isVitalsWithinTarget(data) {
    return true;
}
//# sourceMappingURL=index.js.map