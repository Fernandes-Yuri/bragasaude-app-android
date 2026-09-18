package br.com.bragasaude.data.util

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.model.*
import java.text.SimpleDateFormat
import java.util.*

fun RemoteVitalSign.toEntity() = VitalSignEntity(
    remoteId = id,
    userId = userId,
    systolicPressure = systolicPressure,
    diastolicPressure = diastolicPressure,
    heartRate = heartRate,
    oxygenSaturation = oxygenSaturation,
    glucoseLevel = glucoseLevel,
    glucoseType = glucoseType,
    hydrationMl = hydrationMl,
    steps = steps,
    distanceMeters = distanceMeters,
    measuredAt = measuredAt?.let { parseDate(it) } ?: Date(),
    status = status,
    confirmedVia = confirmedVia,
    confirmationCode = confirmationCode
)

fun VitalSignEntity.toRemote() = RemoteVitalSign(
    id = remoteId,
    userId = userId,
    systolicPressure = systolicPressure,
    diastolicPressure = diastolicPressure,
    heartRate = heartRate,
    oxygenSaturation = oxygenSaturation,
    glucoseLevel = glucoseLevel,
    glucoseType = glucoseType,
    hydrationMl = hydrationMl,
    steps = steps,
    distanceMeters = distanceMeters,
    measuredAt = formatDate(measuredAt),
    status = status,
    confirmedVia = confirmedVia,
    confirmationCode = confirmationCode
)

fun RemoteProfile.toEntity() = ProfileEntity(
    userId = id,
    fullName = fullName,
    birthDate = birthDate,
    weightGoal = weightGoal,
    dailyCalorieTarget = dailyCalorieTarget,
    hydrationTargetMl = hydrationTargetMl,
    stepGoal = stepGoal ?: 8000,
    hasDiabetes = hasDiabetes,
    hasHypertension = hasHypertension,
    hasThyroidIssue = hasThyroidIssue,
    hasRenalIssue = hasRenalIssue,
    hasBoneIssue = hasBoneIssue,
    hasMuscularIssue = hasMuscularIssue,
    pointsDiscipline = pointsDiscipline,
    currentXp = currentXp,
    totalXp = totalXp,
    currentLevel = currentLevel,
    currentStreak = currentStreak,
    lastXpAt = lastXpAt?.let { parseDate(it) },
    showInFeed = showInFeed,
    gender = gender,
    weight = weight,
    height = height,
    isSmoker = isSmoker,
    activityLevel = activityLevel,
    emergencyContactName = emergencyContactName,
    emergencyContactRelation = emergencyContactRelation,
    emergencyContactPhone = emergencyContactPhone,
    phone = phone,
    notificationsEnabled = notificationsEnabled,
    locationEnabled = locationEnabled,
    sleepStartTime = sleepStartTime,
    sleepEndTime = sleepEndTime,
    consentAcceptedAt = consentAcceptedAt?.let { parseDate(it) },
    updatedAt = updatedAt?.let { parseDate(it) },
    diabetesType = diabetesType,
    foodAllergies = foodAllergies ?: emptyList(),
    customFoodRestrictions = customFoodRestrictions,
    userRole = userRole,
    caregiverMode = caregiverMode,
    basicProfileComplete = basicProfileComplete,
    selfCareComplete = selfCareComplete,
    selfCareSetupPending = selfCareSetupPending,
    avatarIdentifier = avatarIdentifier,
    whatsappTotpSecret = whatsappTotpSecret,
    whatsappPhone = whatsappPhone
)

fun ProfileEntity.toRemote() = RemoteProfile(
    id = userId,
    fullName = fullName,
    birthDate = birthDate,
    weightGoal = weightGoal,
    dailyCalorieTarget = dailyCalorieTarget,
    hydrationTargetMl = hydrationTargetMl,
    stepGoal = stepGoal ?: 8000,
    hasDiabetes = hasDiabetes,
    hasHypertension = hasHypertension,
    hasThyroidIssue = hasThyroidIssue,
    hasRenalIssue = hasRenalIssue,
    hasBoneIssue = hasBoneIssue,
    hasMuscularIssue = hasMuscularIssue,
    pointsDiscipline = pointsDiscipline,
    currentXp = currentXp,
    totalXp = totalXp,
    currentLevel = currentLevel,
    currentStreak = currentStreak,
    lastXpAt = lastXpAt?.let { formatDate(it) },
    showInFeed = showInFeed,
    gender = gender,
    weight = weight,
    height = height,
    isSmoker = isSmoker,
    activityLevel = activityLevel,
    emergencyContactName = emergencyContactName,
    emergencyContactRelation = emergencyContactRelation,
    emergencyContactPhone = emergencyContactPhone,
    phone = phone,
    notificationsEnabled = notificationsEnabled,
    locationEnabled = locationEnabled,
    sleepStartTime = sleepStartTime,
    sleepEndTime = sleepEndTime,
    consentAcceptedAt = consentAcceptedAt?.let { formatDate(it) },
    updatedAt = updatedAt?.let { formatDate(it) },
    diabetesType = diabetesType,
    foodAllergies = foodAllergies,
    customFoodRestrictions = customFoodRestrictions,
    userRole = userRole,
    caregiverMode = caregiverMode,
    basicProfileComplete = basicProfileComplete,
    selfCareComplete = selfCareComplete,
    selfCareSetupPending = selfCareSetupPending,
    avatarIdentifier = avatarIdentifier,
    whatsappTotpSecret = whatsappTotpSecret,
    whatsappPhone = whatsappPhone
)

fun RemoteBiometry.toEntity() = BiometryEntity(
    remoteId = id,
    userId = userId,
    weight = weight,
    height = height,
    imc = imc,
    measuredAt = measuredAt?.let { parseDate(it) } ?: Date()
)

fun BiometryEntity.toRemote() = RemoteBiometry(
    id = remoteId,
    userId = userId,
    weight = weight,
    height = height,
    imc = imc,
    measuredAt = formatDate(measuredAt)
)

fun RemoteExam.toEntity() = ExamEntity(
    remoteId = id,
    userId = userId,
    title = title,
    category = category,
    examDate = parseDate(examDate) ?: Date(),
    resultSummary = resultSummary,
    fileUrl = fileUrl,
    status = status,
    aiExtractedData = aiExtractedData,
    validatedBy = validatedBy,
    validationNotes = validationNotes,
    createdAt = createdAt?.let { parseDate(it) },
    pendingSync = false
)

fun ExamEntity.toRemote() = RemoteExam(
    id = remoteId,
    userId = userId,
    title = title,
    category = category,
    examDate = formatDate(examDate),
    resultSummary = resultSummary,
    fileUrl = fileUrl,
    status = status,
    aiExtractedData = aiExtractedData,
    validatedBy = validatedBy,
    validationNotes = validationNotes,
    createdAt = createdAt?.let { formatDate(it) }
)

fun RemoteMilestone.toEntity() = MilestoneEntity(
    remoteId = id,
    userId = userId,
    title = title,
    description = description,
    badgeType = badgeType,
    achievedAt = achievedAt?.let { parseDate(it) } ?: Date()
)

fun MilestoneEntity.toRemote() = RemoteMilestone(
    id = remoteId,
    userId = userId,
    title = title,
    description = description,
    badgeType = badgeType,
    achievedAt = formatDate(achievedAt)
)

fun RemoteMedication.toEntity() = MedicationEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    userId = userId,
    name = name,
    dosage = dosage,
    dosageMg = dosageMg,
    pillQuantity = pillQuantity,
    scheduleTime = scheduleTime,
    scheduleTimes = scheduleTimes,
    notes = notes
)

fun MedicationEntity.toRemote() = RemoteMedication(
    id = id,
    userId = userId,
    name = name,
    dosage = dosage,
    dosageMg = dosageMg,
    pillQuantity = pillQuantity,
    scheduleTime = scheduleTime,
    scheduleTimes = scheduleTimes,
    notes = notes
)

fun RemoteMedicationLog.toEntity() = MedicationLogEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    userId = userId,
    medicationId = medicationId,
    scheduledFor = scheduledFor,
    takenAt = parseDate(takenAt) ?: Date()
)

fun MedicationLogEntity.toRemote() = RemoteMedicationLog(
    id = id,
    userId = userId,
    medicationId = medicationId,
    scheduledFor = scheduledFor,
    takenAt = formatDate(takenAt)
)

fun RemoteClinicalReference.toEntity() = ClinicalReferenceEntity(
    itemKey = itemKey,
    itemName = itemName,
    category = category,
    gender = gender,
    minTarget = minTarget,
    maxTarget = maxTarget,
    minCritical = minCritical,
    maxCritical = maxCritical,
    unit = unit,
    interpretationHint = interpretationHint
)

fun ClinicalReferenceEntity.toRemote() = RemoteClinicalReference(
    itemKey = itemKey,
    itemName = itemName,
    category = category,
    gender = gender,
    minTarget = minTarget,
    maxTarget = maxTarget,
    minCritical = minCritical,
    maxCritical = maxCritical,
    unit = unit,
    interpretationHint = interpretationHint
)

fun RemoteExamItem.toEntity() = ExamItemEntity(
    remoteId = id,
    examId = examId,
    userId = userId,
    itemKey = itemKey,
    itemName = itemName,
    valueNumeric = valueNumeric,
    valueText = valueText,
    unit = unit,
    referenceText = referenceText,
    status = status,
    measuredAt = measuredAt?.let { parseDate(it) },
    createdAt = Date(),
    pendingSync = false
)

fun ExamItemEntity.toRemote() = RemoteExamItem(
    id = remoteId,
    examId = examId,
    userId = userId,
    itemKey = itemKey,
    itemName = itemName,
    valueNumeric = valueNumeric,
    valueText = valueText,
    unit = unit,
    referenceText = referenceText,
    status = status,
    measuredAt = measuredAt?.let { formatDate(it) }
)

fun DailyMetricsEntity.toRemote() = RemoteDailyMetrics(
    userId = userId,
    date = date,
    steps = steps,
    distanceMeters = distanceMeters,
    caloriesBurned = caloriesBurned,
    activeMinutes = activeMinutes
)

fun RemoteDailyMetrics.toEntity() = DailyMetricsEntity(
    date = date,
    userId = userId,
    steps = steps,
    distanceMeters = distanceMeters,
    caloriesBurned = caloriesBurned,
    activeMinutes = activeMinutes
)

fun RemoteLeagueCycle.toEntity() = LeagueCycleEntity(
    id = id,
    level = level,
    weekStartDate = weekStartDate,
    weekEndDate = weekEndDate,
    status = status
)

fun LeagueCycleEntity.toRemote() = RemoteLeagueCycle(
    id = id,
    level = level,
    weekStartDate = weekStartDate,
    weekEndDate = weekEndDate,
    status = status
)

fun RemoteLeagueMembership.toEntity() = LeagueMembershipEntity(
    id = id,
    userId = userId,
    userName = userName,
    userLevel = userLevel,
    userStreak = userStreak,
    leagueCycleId = leagueCycleId,
    xpEarned = xpEarned,
    rankAtClose = rankAtClose,
    outcome = outcome
)

fun LeagueMembershipEntity.toRemote() = RemoteLeagueMembership(
    id = id,
    userId = userId,
    userName = userName,
    userLevel = userLevel,
    userStreak = userStreak,
    leagueCycleId = leagueCycleId,
    xpEarned = xpEarned,
    rankAtClose = rankAtClose,
    outcome = outcome
)

fun RemoteSocialPost.toEntity() = SocialPostEntity(
    id = id,
    userId = userId,
    userName = userName,
    userLevel = userLevel,
    postType = postType,
    title = title,
    description = description,
    relatedMilestoneId = relatedMilestoneId,
    createdAt = createdAt?.let { parseDate(it) } ?: Date(),
    isVisible = isVisible,
    reactionCount = reactionCount,
    hasUserReacted = hasUserReacted
)

fun SocialPostEntity.toRemote() = RemoteSocialPost(
    id = id,
    userId = userId,
    userName = userName,
    userLevel = userLevel,
    postType = postType,
    title = title,
    description = description,
    relatedMilestoneId = relatedMilestoneId,
    createdAt = formatDate(createdAt),
    isVisible = isVisible,
    reactionCount = reactionCount,
    hasUserReacted = hasUserReacted
)

fun RemotePostReaction.toEntity() = PostReactionEntity(
    id = id,
    postId = postId,
    userId = userId,
    userName = userName,
    reactionType = reactionType,
    createdAt = createdAt?.let { parseDate(it) } ?: Date()
)

fun PostReactionEntity.toRemote() = RemotePostReaction(
    id = id,
    postId = postId,
    userId = userId,
    userName = userName,
    reactionType = reactionType,
    createdAt = formatDate(createdAt)
)


internal fun parseDate(dateStr: String): Date? {
    try {
        return Date.from(java.time.OffsetDateTime.parse(dateStr).toInstant())
    } catch (_: java.time.format.DateTimeParseException) { /* Legacy timestamps below. */ }
    return try {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd"
        )
        var date: Date? = null
        for (format in formats) {
            try {
                date = SimpleDateFormat(format, Locale.getDefault()).parse(dateStr)
                if (date != null) break
            } catch (e: Exception) { continue }
        }
        date
    } catch (e: Exception) {
        null
    }
}

internal fun formatDate(date: Date): String {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(date)
}
