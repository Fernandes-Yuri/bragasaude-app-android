
@file:Suppress(
  "KotlinRedundantDiagnosticSuppress",
  "PropertyName",
  "MayBeConstant",
  "RedundantVisibilityModifier",
  "RedundantCompanionReference",
  "RemoveEmptyClassBody",
  "SpellCheckingInspection",
  "unused",
)

package br.com.bragasaude.data.dataconnect

import com.google.firebase.dataconnect.getInstance as _fdcGetInstance
import kotlin.time.Duration.Companion.milliseconds as _milliseconds

public interface DefaultConnector : com.google.firebase.dataconnect.generated.GeneratedConnector<DefaultConnector> {
  override val dataConnect: com.google.firebase.dataconnect.FirebaseDataConnect

  
    public val acceptFamilyBinding: AcceptFamilyBindingMutation
  
    public val closeLeagueCycle: CloseLeagueCycleMutation
  
    public val createExam: CreateExamMutation
  
    public val createExamItem: CreateExamItemMutation
  
    public val createFamilyBinding: CreateFamilyBindingMutation
  
    public val createFamilyMessage: CreateFamilyMessageMutation
  
    public val createFeedback: CreateFeedbackMutation
  
    public val createLeagueCycle: CreateLeagueCycleMutation
  
    public val createMedication: CreateMedicationMutation
  
    public val createSocialPost: CreateSocialPostMutation
  
    public val createVitalSign: CreateVitalSignMutation
  
    public val deleteFamilyBindingsByUserId: DeleteFamilyBindingsByUserIdMutation
  
    public val deleteProfile: DeleteProfileMutation
  
    public val deleteUserFeedbacks: DeleteUserFeedbacksMutation
  
    public val findFamilyBindingByCode: FindFamilyBindingByCodeQuery
  
    public val getActiveCyclesGroupedByLevel: GetActiveCyclesGroupedByLevelQuery
  
    public val getActiveLeagueCycle: GetActiveLeagueCycleQuery
  
    public val getActiveLeagueCycles: GetActiveLeagueCyclesQuery
  
    public val getClinicalReferences: GetClinicalReferencesQuery
  
    public val getDailyMetrics: GetDailyMetricsQuery
  
    public val getExamItemsByUser: GetExamItemsByUserQuery
  
    public val getExams: GetExamsQuery
  
    public val getExamsByUser: GetExamsByUserQuery
  
    public val getFamilyBindingsForCaregiver: GetFamilyBindingsForCaregiverQuery
  
    public val getFamilyBindingsForPatient: GetFamilyBindingsForPatientQuery
  
    public val getFamilyMessages: GetFamilyMessagesQuery
  
    public val getFeedbacks: GetFeedbacksQuery
  
    public val getGlobalFeed: GetGlobalFeedQuery
  
    public val getLeagueMembershipsByCycle: GetLeagueMembershipsByCycleQuery
  
    public val getMedicationLogsToday: GetMedicationLogsTodayQuery
  
    public val getMedications: GetMedicationsQuery
  
    public val getMyLeagueMembership: GetMyLeagueMembershipQuery
  
    public val getMyLeagueRanking: GetMyLeagueRankingQuery
  
    public val getPatientProfile: GetPatientProfileQuery
  
    public val getPatientVitalSigns: GetPatientVitalSignsQuery
  
    public val getPostReactions: GetPostReactionsQuery
  
    public val getProfile: GetProfileQuery
  
    public val getRankedMemberships: GetRankedMembershipsQuery
  
    public val getVitalSigns: GetVitalSignsQuery
  
    public val incrementUserXp: IncrementUserXpMutation
  
    public val joinLeagueCycle: JoinLeagueCycleMutation
  
    public val logMedicationTaken: LogMedicationTakenMutation
  
    public val logUserDeletion: LogUserDeletionMutation
  
    public val markCycleAsError: MarkCycleAsErrorMutation
  
    public val markFamilyMessageAsRead: MarkFamilyMessageAsReadMutation
  
    public val reactToPost: ReactToPostMutation
  
    public val removePostReaction: RemovePostReactionMutation
  
    public val revokeFamilyBinding: RevokeFamilyBindingMutation
  
    public val updateExamItem: UpdateExamItemMutation
  
    public val updateLeagueMembershipOutcome: UpdateLeagueMembershipOutcomeMutation
  
    public val updateLeagueMembershipXp: UpdateLeagueMembershipXpMutation
  
    public val updateProfileGamification: UpdateProfileGamificationMutation
  
    public val upsertDailyMetric: UpsertDailyMetricMutation
  
    public val upsertExamItem: UpsertExamItemMutation
  
    public val upsertProfile: UpsertProfileMutation
  

  public companion object {
    @Suppress("MemberVisibilityCanBePrivate")
    public val config: com.google.firebase.dataconnect.ConnectorConfig = com.google.firebase.dataconnect.ConnectorConfig(
      connector = "default",
      location = "us-east4",
      serviceId = "braga-saude-service",
    )

    public fun getInstance(
      dataConnect: com.google.firebase.dataconnect.FirebaseDataConnect
    ):DefaultConnector = synchronized(instances) {
      instances.getOrPut(dataConnect) {
        DefaultConnectorImpl(dataConnect)
      }
    }

    private val instances = java.util.WeakHashMap<com.google.firebase.dataconnect.FirebaseDataConnect, DefaultConnectorImpl>()

    
  }
}

public val DefaultConnector.Companion.instance:DefaultConnector
  get() = getInstance(com.google.firebase.dataconnect.FirebaseDataConnect._fdcGetInstance(
    config
  ))

public fun DefaultConnector.Companion.getInstance(
  settings: com.google.firebase.dataconnect.DataConnectSettings = com.google.firebase.dataconnect.DataConnectSettings()
):DefaultConnector =
  getInstance(com.google.firebase.dataconnect.FirebaseDataConnect._fdcGetInstance(config, settings))

public fun DefaultConnector.Companion.getInstance(
  app: com.google.firebase.FirebaseApp,
  settings: com.google.firebase.dataconnect.DataConnectSettings = com.google.firebase.dataconnect.DataConnectSettings()
):DefaultConnector =
  getInstance(com.google.firebase.dataconnect.FirebaseDataConnect._fdcGetInstance(app, config, settings))

private class DefaultConnectorImpl(
  override val dataConnect: com.google.firebase.dataconnect.FirebaseDataConnect
) : DefaultConnector {
  
    override val acceptFamilyBinding by lazy(LazyThreadSafetyMode.PUBLICATION) {
      AcceptFamilyBindingMutationImpl(this)
    }
  
    override val closeLeagueCycle by lazy(LazyThreadSafetyMode.PUBLICATION) {
      CloseLeagueCycleMutationImpl(this)
    }
  
    override val createExam by lazy(LazyThreadSafetyMode.PUBLICATION) {
      CreateExamMutationImpl(this)
    }
  
    override val createExamItem by lazy(LazyThreadSafetyMode.PUBLICATION) {
      CreateExamItemMutationImpl(this)
    }
  
    override val createFamilyBinding by lazy(LazyThreadSafetyMode.PUBLICATION) {
      CreateFamilyBindingMutationImpl(this)
    }
  
    override val createFamilyMessage by lazy(LazyThreadSafetyMode.PUBLICATION) {
      CreateFamilyMessageMutationImpl(this)
    }
  
    override val createFeedback by lazy(LazyThreadSafetyMode.PUBLICATION) {
      CreateFeedbackMutationImpl(this)
    }
  
    override val createLeagueCycle by lazy(LazyThreadSafetyMode.PUBLICATION) {
      CreateLeagueCycleMutationImpl(this)
    }
  
    override val createMedication by lazy(LazyThreadSafetyMode.PUBLICATION) {
      CreateMedicationMutationImpl(this)
    }
  
    override val createSocialPost by lazy(LazyThreadSafetyMode.PUBLICATION) {
      CreateSocialPostMutationImpl(this)
    }
  
    override val createVitalSign by lazy(LazyThreadSafetyMode.PUBLICATION) {
      CreateVitalSignMutationImpl(this)
    }
  
    override val deleteFamilyBindingsByUserId by lazy(LazyThreadSafetyMode.PUBLICATION) {
      DeleteFamilyBindingsByUserIdMutationImpl(this)
    }
  
    override val deleteProfile by lazy(LazyThreadSafetyMode.PUBLICATION) {
      DeleteProfileMutationImpl(this)
    }
  
    override val deleteUserFeedbacks by lazy(LazyThreadSafetyMode.PUBLICATION) {
      DeleteUserFeedbacksMutationImpl(this)
    }
  
    override val findFamilyBindingByCode by lazy(LazyThreadSafetyMode.PUBLICATION) {
      FindFamilyBindingByCodeQueryImpl(this)
    }
  
    override val getActiveCyclesGroupedByLevel by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetActiveCyclesGroupedByLevelQueryImpl(this)
    }
  
    override val getActiveLeagueCycle by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetActiveLeagueCycleQueryImpl(this)
    }
  
    override val getActiveLeagueCycles by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetActiveLeagueCyclesQueryImpl(this)
    }
  
    override val getClinicalReferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetClinicalReferencesQueryImpl(this)
    }
  
    override val getDailyMetrics by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetDailyMetricsQueryImpl(this)
    }
  
    override val getExamItemsByUser by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetExamItemsByUserQueryImpl(this)
    }
  
    override val getExams by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetExamsQueryImpl(this)
    }
  
    override val getExamsByUser by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetExamsByUserQueryImpl(this)
    }
  
    override val getFamilyBindingsForCaregiver by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetFamilyBindingsForCaregiverQueryImpl(this)
    }
  
    override val getFamilyBindingsForPatient by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetFamilyBindingsForPatientQueryImpl(this)
    }
  
    override val getFamilyMessages by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetFamilyMessagesQueryImpl(this)
    }
  
    override val getFeedbacks by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetFeedbacksQueryImpl(this)
    }
  
    override val getGlobalFeed by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetGlobalFeedQueryImpl(this)
    }
  
    override val getLeagueMembershipsByCycle by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetLeagueMembershipsByCycleQueryImpl(this)
    }
  
    override val getMedicationLogsToday by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetMedicationLogsTodayQueryImpl(this)
    }
  
    override val getMedications by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetMedicationsQueryImpl(this)
    }
  
    override val getMyLeagueMembership by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetMyLeagueMembershipQueryImpl(this)
    }
  
    override val getMyLeagueRanking by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetMyLeagueRankingQueryImpl(this)
    }
  
    override val getPatientProfile by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPatientProfileQueryImpl(this)
    }
  
    override val getPatientVitalSigns by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPatientVitalSignsQueryImpl(this)
    }
  
    override val getPostReactions by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetPostReactionsQueryImpl(this)
    }
  
    override val getProfile by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetProfileQueryImpl(this)
    }
  
    override val getRankedMemberships by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetRankedMembershipsQueryImpl(this)
    }
  
    override val getVitalSigns by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetVitalSignsQueryImpl(this)
    }
  
    override val incrementUserXp by lazy(LazyThreadSafetyMode.PUBLICATION) {
      IncrementUserXpMutationImpl(this)
    }
  
    override val joinLeagueCycle by lazy(LazyThreadSafetyMode.PUBLICATION) {
      JoinLeagueCycleMutationImpl(this)
    }
  
    override val logMedicationTaken by lazy(LazyThreadSafetyMode.PUBLICATION) {
      LogMedicationTakenMutationImpl(this)
    }
  
    override val logUserDeletion by lazy(LazyThreadSafetyMode.PUBLICATION) {
      LogUserDeletionMutationImpl(this)
    }
  
    override val markCycleAsError by lazy(LazyThreadSafetyMode.PUBLICATION) {
      MarkCycleAsErrorMutationImpl(this)
    }
  
    override val markFamilyMessageAsRead by lazy(LazyThreadSafetyMode.PUBLICATION) {
      MarkFamilyMessageAsReadMutationImpl(this)
    }
  
    override val reactToPost by lazy(LazyThreadSafetyMode.PUBLICATION) {
      ReactToPostMutationImpl(this)
    }
  
    override val removePostReaction by lazy(LazyThreadSafetyMode.PUBLICATION) {
      RemovePostReactionMutationImpl(this)
    }
  
    override val revokeFamilyBinding by lazy(LazyThreadSafetyMode.PUBLICATION) {
      RevokeFamilyBindingMutationImpl(this)
    }
  
    override val updateExamItem by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateExamItemMutationImpl(this)
    }
  
    override val updateLeagueMembershipOutcome by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateLeagueMembershipOutcomeMutationImpl(this)
    }
  
    override val updateLeagueMembershipXp by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateLeagueMembershipXpMutationImpl(this)
    }
  
    override val updateProfileGamification by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpdateProfileGamificationMutationImpl(this)
    }
  
    override val upsertDailyMetric by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertDailyMetricMutationImpl(this)
    }
  
    override val upsertExamItem by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertExamItemMutationImpl(this)
    }
  
    override val upsertProfile by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertProfileMutationImpl(this)
    }
  

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun operations(): List<com.google.firebase.dataconnect.generated.GeneratedOperation<DefaultConnector, *, *>> =
    queries() + mutations()

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun mutations(): List<com.google.firebase.dataconnect.generated.GeneratedMutation<DefaultConnector, *, *>> =
    listOf(
      acceptFamilyBinding,
        closeLeagueCycle,
        createExam,
        createExamItem,
        createFamilyBinding,
        createFamilyMessage,
        createFeedback,
        createLeagueCycle,
        createMedication,
        createSocialPost,
        createVitalSign,
        deleteFamilyBindingsByUserId,
        deleteProfile,
        deleteUserFeedbacks,
        incrementUserXp,
        joinLeagueCycle,
        logMedicationTaken,
        logUserDeletion,
        markCycleAsError,
        markFamilyMessageAsRead,
        reactToPost,
        removePostReaction,
        revokeFamilyBinding,
        updateExamItem,
        updateLeagueMembershipOutcome,
        updateLeagueMembershipXp,
        updateProfileGamification,
        upsertDailyMetric,
        upsertExamItem,
        upsertProfile,
        
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun queries(): List<com.google.firebase.dataconnect.generated.GeneratedQuery<DefaultConnector, *, *>> =
    listOf(
      findFamilyBindingByCode,
        getActiveCyclesGroupedByLevel,
        getActiveLeagueCycle,
        getActiveLeagueCycles,
        getClinicalReferences,
        getDailyMetrics,
        getExamItemsByUser,
        getExams,
        getExamsByUser,
        getFamilyBindingsForCaregiver,
        getFamilyBindingsForPatient,
        getFamilyMessages,
        getFeedbacks,
        getGlobalFeed,
        getLeagueMembershipsByCycle,
        getMedicationLogsToday,
        getMedications,
        getMyLeagueMembership,
        getMyLeagueRanking,
        getPatientProfile,
        getPatientVitalSigns,
        getPostReactions,
        getProfile,
        getRankedMemberships,
        getVitalSigns,
        
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun copy(dataConnect: com.google.firebase.dataconnect.FirebaseDataConnect) =
    DefaultConnectorImpl(dataConnect)

  override fun equals(other: Any?): Boolean =
    other is DefaultConnectorImpl &&
    other.dataConnect == dataConnect

  override fun hashCode(): Int =
    java.util.Objects.hash(
      "DefaultConnectorImpl",
      dataConnect,
    )

  override fun toString(): String =
    "DefaultConnectorImpl(dataConnect=$dataConnect)"
}



private open class DefaultConnectorGeneratedQueryImpl<Data, Variables>(
  override val connector: DefaultConnector,
  override val operationName: String,
  override val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data>,
  override val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables>,
) : com.google.firebase.dataconnect.generated.GeneratedQuery<DefaultConnector, Data, Variables> {

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun copy(
    connector: DefaultConnector,
    operationName: String,
    dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data>,
    variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables>,
  ) =
    DefaultConnectorGeneratedQueryImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun <NewVariables> withVariablesSerializer(
    variablesSerializer: kotlinx.serialization.SerializationStrategy<NewVariables>
  ) =
    DefaultConnectorGeneratedQueryImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun <NewData> withDataDeserializer(
    dataDeserializer: kotlinx.serialization.DeserializationStrategy<NewData>
  ) =
    DefaultConnectorGeneratedQueryImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  override fun equals(other: Any?): Boolean =
    other is DefaultConnectorGeneratedQueryImpl<*,*> &&
    other.connector == connector &&
    other.operationName == operationName &&
    other.dataDeserializer == dataDeserializer &&
    other.variablesSerializer == variablesSerializer

  override fun hashCode(): Int =
    java.util.Objects.hash(
      "DefaultConnectorGeneratedQueryImpl",
      connector, operationName, dataDeserializer, variablesSerializer
    )

  override fun toString(): String =
    "DefaultConnectorGeneratedQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

private open class DefaultConnectorGeneratedMutationImpl<Data, Variables>(
  override val connector: DefaultConnector,
  override val operationName: String,
  override val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data>,
  override val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables>,
) : com.google.firebase.dataconnect.generated.GeneratedMutation<DefaultConnector, Data, Variables> {

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun copy(
    connector: DefaultConnector,
    operationName: String,
    dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data>,
    variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables>,
  ) =
    DefaultConnectorGeneratedMutationImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun <NewVariables> withVariablesSerializer(
    variablesSerializer: kotlinx.serialization.SerializationStrategy<NewVariables>
  ) =
    DefaultConnectorGeneratedMutationImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun <NewData> withDataDeserializer(
    dataDeserializer: kotlinx.serialization.DeserializationStrategy<NewData>
  ) =
    DefaultConnectorGeneratedMutationImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  override fun equals(other: Any?): Boolean =
    other is DefaultConnectorGeneratedMutationImpl<*,*> &&
    other.connector == connector &&
    other.operationName == operationName &&
    other.dataDeserializer == dataDeserializer &&
    other.variablesSerializer == variablesSerializer

  override fun hashCode(): Int =
    java.util.Objects.hash(
      "DefaultConnectorGeneratedMutationImpl",
      connector, operationName, dataDeserializer, variablesSerializer
    )

  override fun toString(): String =
    "DefaultConnectorGeneratedMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}



private class AcceptFamilyBindingMutationImpl(
  connector: DefaultConnector
):
  AcceptFamilyBindingMutation,
  DefaultConnectorGeneratedMutationImpl<
      AcceptFamilyBindingMutation.Data,
      AcceptFamilyBindingMutation.Variables
  >(
    connector,
    AcceptFamilyBindingMutation.Companion.operationName,
    AcceptFamilyBindingMutation.Companion.dataDeserializer,
    AcceptFamilyBindingMutation.Companion.variablesSerializer,
  )


private class CloseLeagueCycleMutationImpl(
  connector: DefaultConnector
):
  CloseLeagueCycleMutation,
  DefaultConnectorGeneratedMutationImpl<
      CloseLeagueCycleMutation.Data,
      CloseLeagueCycleMutation.Variables
  >(
    connector,
    CloseLeagueCycleMutation.Companion.operationName,
    CloseLeagueCycleMutation.Companion.dataDeserializer,
    CloseLeagueCycleMutation.Companion.variablesSerializer,
  )


private class CreateExamMutationImpl(
  connector: DefaultConnector
):
  CreateExamMutation,
  DefaultConnectorGeneratedMutationImpl<
      CreateExamMutation.Data,
      CreateExamMutation.Variables
  >(
    connector,
    CreateExamMutation.Companion.operationName,
    CreateExamMutation.Companion.dataDeserializer,
    CreateExamMutation.Companion.variablesSerializer,
  )


private class CreateExamItemMutationImpl(
  connector: DefaultConnector
):
  CreateExamItemMutation,
  DefaultConnectorGeneratedMutationImpl<
      CreateExamItemMutation.Data,
      CreateExamItemMutation.Variables
  >(
    connector,
    CreateExamItemMutation.Companion.operationName,
    CreateExamItemMutation.Companion.dataDeserializer,
    CreateExamItemMutation.Companion.variablesSerializer,
  )


private class CreateFamilyBindingMutationImpl(
  connector: DefaultConnector
):
  CreateFamilyBindingMutation,
  DefaultConnectorGeneratedMutationImpl<
      CreateFamilyBindingMutation.Data,
      CreateFamilyBindingMutation.Variables
  >(
    connector,
    CreateFamilyBindingMutation.Companion.operationName,
    CreateFamilyBindingMutation.Companion.dataDeserializer,
    CreateFamilyBindingMutation.Companion.variablesSerializer,
  )


private class CreateFamilyMessageMutationImpl(
  connector: DefaultConnector
):
  CreateFamilyMessageMutation,
  DefaultConnectorGeneratedMutationImpl<
      CreateFamilyMessageMutation.Data,
      CreateFamilyMessageMutation.Variables
  >(
    connector,
    CreateFamilyMessageMutation.Companion.operationName,
    CreateFamilyMessageMutation.Companion.dataDeserializer,
    CreateFamilyMessageMutation.Companion.variablesSerializer,
  )


private class CreateFeedbackMutationImpl(
  connector: DefaultConnector
):
  CreateFeedbackMutation,
  DefaultConnectorGeneratedMutationImpl<
      CreateFeedbackMutation.Data,
      CreateFeedbackMutation.Variables
  >(
    connector,
    CreateFeedbackMutation.Companion.operationName,
    CreateFeedbackMutation.Companion.dataDeserializer,
    CreateFeedbackMutation.Companion.variablesSerializer,
  )


private class CreateLeagueCycleMutationImpl(
  connector: DefaultConnector
):
  CreateLeagueCycleMutation,
  DefaultConnectorGeneratedMutationImpl<
      CreateLeagueCycleMutation.Data,
      CreateLeagueCycleMutation.Variables
  >(
    connector,
    CreateLeagueCycleMutation.Companion.operationName,
    CreateLeagueCycleMutation.Companion.dataDeserializer,
    CreateLeagueCycleMutation.Companion.variablesSerializer,
  )


private class CreateMedicationMutationImpl(
  connector: DefaultConnector
):
  CreateMedicationMutation,
  DefaultConnectorGeneratedMutationImpl<
      CreateMedicationMutation.Data,
      CreateMedicationMutation.Variables
  >(
    connector,
    CreateMedicationMutation.Companion.operationName,
    CreateMedicationMutation.Companion.dataDeserializer,
    CreateMedicationMutation.Companion.variablesSerializer,
  )


private class CreateSocialPostMutationImpl(
  connector: DefaultConnector
):
  CreateSocialPostMutation,
  DefaultConnectorGeneratedMutationImpl<
      CreateSocialPostMutation.Data,
      CreateSocialPostMutation.Variables
  >(
    connector,
    CreateSocialPostMutation.Companion.operationName,
    CreateSocialPostMutation.Companion.dataDeserializer,
    CreateSocialPostMutation.Companion.variablesSerializer,
  )


private class CreateVitalSignMutationImpl(
  connector: DefaultConnector
):
  CreateVitalSignMutation,
  DefaultConnectorGeneratedMutationImpl<
      CreateVitalSignMutation.Data,
      CreateVitalSignMutation.Variables
  >(
    connector,
    CreateVitalSignMutation.Companion.operationName,
    CreateVitalSignMutation.Companion.dataDeserializer,
    CreateVitalSignMutation.Companion.variablesSerializer,
  )


private class DeleteFamilyBindingsByUserIdMutationImpl(
  connector: DefaultConnector
):
  DeleteFamilyBindingsByUserIdMutation,
  DefaultConnectorGeneratedMutationImpl<
      DeleteFamilyBindingsByUserIdMutation.Data,
      DeleteFamilyBindingsByUserIdMutation.Variables
  >(
    connector,
    DeleteFamilyBindingsByUserIdMutation.Companion.operationName,
    DeleteFamilyBindingsByUserIdMutation.Companion.dataDeserializer,
    DeleteFamilyBindingsByUserIdMutation.Companion.variablesSerializer,
  )


private class DeleteProfileMutationImpl(
  connector: DefaultConnector
):
  DeleteProfileMutation,
  DefaultConnectorGeneratedMutationImpl<
      DeleteProfileMutation.Data,
      DeleteProfileMutation.Variables
  >(
    connector,
    DeleteProfileMutation.Companion.operationName,
    DeleteProfileMutation.Companion.dataDeserializer,
    DeleteProfileMutation.Companion.variablesSerializer,
  )


private class DeleteUserFeedbacksMutationImpl(
  connector: DefaultConnector
):
  DeleteUserFeedbacksMutation,
  DefaultConnectorGeneratedMutationImpl<
      DeleteUserFeedbacksMutation.Data,
      DeleteUserFeedbacksMutation.Variables
  >(
    connector,
    DeleteUserFeedbacksMutation.Companion.operationName,
    DeleteUserFeedbacksMutation.Companion.dataDeserializer,
    DeleteUserFeedbacksMutation.Companion.variablesSerializer,
  )


private class FindFamilyBindingByCodeQueryImpl(
  connector: DefaultConnector
):
  FindFamilyBindingByCodeQuery,
  DefaultConnectorGeneratedQueryImpl<
      FindFamilyBindingByCodeQuery.Data,
      FindFamilyBindingByCodeQuery.Variables
  >(
    connector,
    FindFamilyBindingByCodeQuery.Companion.operationName,
    FindFamilyBindingByCodeQuery.Companion.dataDeserializer,
    FindFamilyBindingByCodeQuery.Companion.variablesSerializer,
  )


private class GetActiveCyclesGroupedByLevelQueryImpl(
  connector: DefaultConnector
):
  GetActiveCyclesGroupedByLevelQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetActiveCyclesGroupedByLevelQuery.Data,
      Unit
  >(
    connector,
    GetActiveCyclesGroupedByLevelQuery.Companion.operationName,
    GetActiveCyclesGroupedByLevelQuery.Companion.dataDeserializer,
    GetActiveCyclesGroupedByLevelQuery.Companion.variablesSerializer,
  )


private class GetActiveLeagueCycleQueryImpl(
  connector: DefaultConnector
):
  GetActiveLeagueCycleQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetActiveLeagueCycleQuery.Data,
      GetActiveLeagueCycleQuery.Variables
  >(
    connector,
    GetActiveLeagueCycleQuery.Companion.operationName,
    GetActiveLeagueCycleQuery.Companion.dataDeserializer,
    GetActiveLeagueCycleQuery.Companion.variablesSerializer,
  )


private class GetActiveLeagueCyclesQueryImpl(
  connector: DefaultConnector
):
  GetActiveLeagueCyclesQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetActiveLeagueCyclesQuery.Data,
      Unit
  >(
    connector,
    GetActiveLeagueCyclesQuery.Companion.operationName,
    GetActiveLeagueCyclesQuery.Companion.dataDeserializer,
    GetActiveLeagueCyclesQuery.Companion.variablesSerializer,
  )


private class GetClinicalReferencesQueryImpl(
  connector: DefaultConnector
):
  GetClinicalReferencesQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetClinicalReferencesQuery.Data,
      Unit
  >(
    connector,
    GetClinicalReferencesQuery.Companion.operationName,
    GetClinicalReferencesQuery.Companion.dataDeserializer,
    GetClinicalReferencesQuery.Companion.variablesSerializer,
  )


private class GetDailyMetricsQueryImpl(
  connector: DefaultConnector
):
  GetDailyMetricsQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetDailyMetricsQuery.Data,
      GetDailyMetricsQuery.Variables
  >(
    connector,
    GetDailyMetricsQuery.Companion.operationName,
    GetDailyMetricsQuery.Companion.dataDeserializer,
    GetDailyMetricsQuery.Companion.variablesSerializer,
  )


private class GetExamItemsByUserQueryImpl(
  connector: DefaultConnector
):
  GetExamItemsByUserQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetExamItemsByUserQuery.Data,
      GetExamItemsByUserQuery.Variables
  >(
    connector,
    GetExamItemsByUserQuery.Companion.operationName,
    GetExamItemsByUserQuery.Companion.dataDeserializer,
    GetExamItemsByUserQuery.Companion.variablesSerializer,
  )


private class GetExamsQueryImpl(
  connector: DefaultConnector
):
  GetExamsQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetExamsQuery.Data,
      GetExamsQuery.Variables
  >(
    connector,
    GetExamsQuery.Companion.operationName,
    GetExamsQuery.Companion.dataDeserializer,
    GetExamsQuery.Companion.variablesSerializer,
  )


private class GetExamsByUserQueryImpl(
  connector: DefaultConnector
):
  GetExamsByUserQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetExamsByUserQuery.Data,
      GetExamsByUserQuery.Variables
  >(
    connector,
    GetExamsByUserQuery.Companion.operationName,
    GetExamsByUserQuery.Companion.dataDeserializer,
    GetExamsByUserQuery.Companion.variablesSerializer,
  )


private class GetFamilyBindingsForCaregiverQueryImpl(
  connector: DefaultConnector
):
  GetFamilyBindingsForCaregiverQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetFamilyBindingsForCaregiverQuery.Data,
      GetFamilyBindingsForCaregiverQuery.Variables
  >(
    connector,
    GetFamilyBindingsForCaregiverQuery.Companion.operationName,
    GetFamilyBindingsForCaregiverQuery.Companion.dataDeserializer,
    GetFamilyBindingsForCaregiverQuery.Companion.variablesSerializer,
  )


private class GetFamilyBindingsForPatientQueryImpl(
  connector: DefaultConnector
):
  GetFamilyBindingsForPatientQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetFamilyBindingsForPatientQuery.Data,
      GetFamilyBindingsForPatientQuery.Variables
  >(
    connector,
    GetFamilyBindingsForPatientQuery.Companion.operationName,
    GetFamilyBindingsForPatientQuery.Companion.dataDeserializer,
    GetFamilyBindingsForPatientQuery.Companion.variablesSerializer,
  )


private class GetFamilyMessagesQueryImpl(
  connector: DefaultConnector
):
  GetFamilyMessagesQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetFamilyMessagesQuery.Data,
      GetFamilyMessagesQuery.Variables
  >(
    connector,
    GetFamilyMessagesQuery.Companion.operationName,
    GetFamilyMessagesQuery.Companion.dataDeserializer,
    GetFamilyMessagesQuery.Companion.variablesSerializer,
  )


private class GetFeedbacksQueryImpl(
  connector: DefaultConnector
):
  GetFeedbacksQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetFeedbacksQuery.Data,
      GetFeedbacksQuery.Variables
  >(
    connector,
    GetFeedbacksQuery.Companion.operationName,
    GetFeedbacksQuery.Companion.dataDeserializer,
    GetFeedbacksQuery.Companion.variablesSerializer,
  )


private class GetGlobalFeedQueryImpl(
  connector: DefaultConnector
):
  GetGlobalFeedQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetGlobalFeedQuery.Data,
      GetGlobalFeedQuery.Variables
  >(
    connector,
    GetGlobalFeedQuery.Companion.operationName,
    GetGlobalFeedQuery.Companion.dataDeserializer,
    GetGlobalFeedQuery.Companion.variablesSerializer,
  )


private class GetLeagueMembershipsByCycleQueryImpl(
  connector: DefaultConnector
):
  GetLeagueMembershipsByCycleQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetLeagueMembershipsByCycleQuery.Data,
      GetLeagueMembershipsByCycleQuery.Variables
  >(
    connector,
    GetLeagueMembershipsByCycleQuery.Companion.operationName,
    GetLeagueMembershipsByCycleQuery.Companion.dataDeserializer,
    GetLeagueMembershipsByCycleQuery.Companion.variablesSerializer,
  )


private class GetMedicationLogsTodayQueryImpl(
  connector: DefaultConnector
):
  GetMedicationLogsTodayQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetMedicationLogsTodayQuery.Data,
      GetMedicationLogsTodayQuery.Variables
  >(
    connector,
    GetMedicationLogsTodayQuery.Companion.operationName,
    GetMedicationLogsTodayQuery.Companion.dataDeserializer,
    GetMedicationLogsTodayQuery.Companion.variablesSerializer,
  )


private class GetMedicationsQueryImpl(
  connector: DefaultConnector
):
  GetMedicationsQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetMedicationsQuery.Data,
      GetMedicationsQuery.Variables
  >(
    connector,
    GetMedicationsQuery.Companion.operationName,
    GetMedicationsQuery.Companion.dataDeserializer,
    GetMedicationsQuery.Companion.variablesSerializer,
  )


private class GetMyLeagueMembershipQueryImpl(
  connector: DefaultConnector
):
  GetMyLeagueMembershipQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetMyLeagueMembershipQuery.Data,
      GetMyLeagueMembershipQuery.Variables
  >(
    connector,
    GetMyLeagueMembershipQuery.Companion.operationName,
    GetMyLeagueMembershipQuery.Companion.dataDeserializer,
    GetMyLeagueMembershipQuery.Companion.variablesSerializer,
  )


private class GetMyLeagueRankingQueryImpl(
  connector: DefaultConnector
):
  GetMyLeagueRankingQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetMyLeagueRankingQuery.Data,
      GetMyLeagueRankingQuery.Variables
  >(
    connector,
    GetMyLeagueRankingQuery.Companion.operationName,
    GetMyLeagueRankingQuery.Companion.dataDeserializer,
    GetMyLeagueRankingQuery.Companion.variablesSerializer,
  )


private class GetPatientProfileQueryImpl(
  connector: DefaultConnector
):
  GetPatientProfileQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetPatientProfileQuery.Data,
      GetPatientProfileQuery.Variables
  >(
    connector,
    GetPatientProfileQuery.Companion.operationName,
    GetPatientProfileQuery.Companion.dataDeserializer,
    GetPatientProfileQuery.Companion.variablesSerializer,
  )


private class GetPatientVitalSignsQueryImpl(
  connector: DefaultConnector
):
  GetPatientVitalSignsQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetPatientVitalSignsQuery.Data,
      GetPatientVitalSignsQuery.Variables
  >(
    connector,
    GetPatientVitalSignsQuery.Companion.operationName,
    GetPatientVitalSignsQuery.Companion.dataDeserializer,
    GetPatientVitalSignsQuery.Companion.variablesSerializer,
  )


private class GetPostReactionsQueryImpl(
  connector: DefaultConnector
):
  GetPostReactionsQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetPostReactionsQuery.Data,
      GetPostReactionsQuery.Variables
  >(
    connector,
    GetPostReactionsQuery.Companion.operationName,
    GetPostReactionsQuery.Companion.dataDeserializer,
    GetPostReactionsQuery.Companion.variablesSerializer,
  )


private class GetProfileQueryImpl(
  connector: DefaultConnector
):
  GetProfileQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetProfileQuery.Data,
      GetProfileQuery.Variables
  >(
    connector,
    GetProfileQuery.Companion.operationName,
    GetProfileQuery.Companion.dataDeserializer,
    GetProfileQuery.Companion.variablesSerializer,
  )


private class GetRankedMembershipsQueryImpl(
  connector: DefaultConnector
):
  GetRankedMembershipsQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetRankedMembershipsQuery.Data,
      GetRankedMembershipsQuery.Variables
  >(
    connector,
    GetRankedMembershipsQuery.Companion.operationName,
    GetRankedMembershipsQuery.Companion.dataDeserializer,
    GetRankedMembershipsQuery.Companion.variablesSerializer,
  )


private class GetVitalSignsQueryImpl(
  connector: DefaultConnector
):
  GetVitalSignsQuery,
  DefaultConnectorGeneratedQueryImpl<
      GetVitalSignsQuery.Data,
      GetVitalSignsQuery.Variables
  >(
    connector,
    GetVitalSignsQuery.Companion.operationName,
    GetVitalSignsQuery.Companion.dataDeserializer,
    GetVitalSignsQuery.Companion.variablesSerializer,
  )


private class IncrementUserXpMutationImpl(
  connector: DefaultConnector
):
  IncrementUserXpMutation,
  DefaultConnectorGeneratedMutationImpl<
      IncrementUserXpMutation.Data,
      IncrementUserXpMutation.Variables
  >(
    connector,
    IncrementUserXpMutation.Companion.operationName,
    IncrementUserXpMutation.Companion.dataDeserializer,
    IncrementUserXpMutation.Companion.variablesSerializer,
  )


private class JoinLeagueCycleMutationImpl(
  connector: DefaultConnector
):
  JoinLeagueCycleMutation,
  DefaultConnectorGeneratedMutationImpl<
      JoinLeagueCycleMutation.Data,
      JoinLeagueCycleMutation.Variables
  >(
    connector,
    JoinLeagueCycleMutation.Companion.operationName,
    JoinLeagueCycleMutation.Companion.dataDeserializer,
    JoinLeagueCycleMutation.Companion.variablesSerializer,
  )


private class LogMedicationTakenMutationImpl(
  connector: DefaultConnector
):
  LogMedicationTakenMutation,
  DefaultConnectorGeneratedMutationImpl<
      LogMedicationTakenMutation.Data,
      LogMedicationTakenMutation.Variables
  >(
    connector,
    LogMedicationTakenMutation.Companion.operationName,
    LogMedicationTakenMutation.Companion.dataDeserializer,
    LogMedicationTakenMutation.Companion.variablesSerializer,
  )


private class LogUserDeletionMutationImpl(
  connector: DefaultConnector
):
  LogUserDeletionMutation,
  DefaultConnectorGeneratedMutationImpl<
      LogUserDeletionMutation.Data,
      LogUserDeletionMutation.Variables
  >(
    connector,
    LogUserDeletionMutation.Companion.operationName,
    LogUserDeletionMutation.Companion.dataDeserializer,
    LogUserDeletionMutation.Companion.variablesSerializer,
  )


private class MarkCycleAsErrorMutationImpl(
  connector: DefaultConnector
):
  MarkCycleAsErrorMutation,
  DefaultConnectorGeneratedMutationImpl<
      MarkCycleAsErrorMutation.Data,
      MarkCycleAsErrorMutation.Variables
  >(
    connector,
    MarkCycleAsErrorMutation.Companion.operationName,
    MarkCycleAsErrorMutation.Companion.dataDeserializer,
    MarkCycleAsErrorMutation.Companion.variablesSerializer,
  )


private class MarkFamilyMessageAsReadMutationImpl(
  connector: DefaultConnector
):
  MarkFamilyMessageAsReadMutation,
  DefaultConnectorGeneratedMutationImpl<
      MarkFamilyMessageAsReadMutation.Data,
      MarkFamilyMessageAsReadMutation.Variables
  >(
    connector,
    MarkFamilyMessageAsReadMutation.Companion.operationName,
    MarkFamilyMessageAsReadMutation.Companion.dataDeserializer,
    MarkFamilyMessageAsReadMutation.Companion.variablesSerializer,
  )


private class ReactToPostMutationImpl(
  connector: DefaultConnector
):
  ReactToPostMutation,
  DefaultConnectorGeneratedMutationImpl<
      ReactToPostMutation.Data,
      ReactToPostMutation.Variables
  >(
    connector,
    ReactToPostMutation.Companion.operationName,
    ReactToPostMutation.Companion.dataDeserializer,
    ReactToPostMutation.Companion.variablesSerializer,
  )


private class RemovePostReactionMutationImpl(
  connector: DefaultConnector
):
  RemovePostReactionMutation,
  DefaultConnectorGeneratedMutationImpl<
      RemovePostReactionMutation.Data,
      RemovePostReactionMutation.Variables
  >(
    connector,
    RemovePostReactionMutation.Companion.operationName,
    RemovePostReactionMutation.Companion.dataDeserializer,
    RemovePostReactionMutation.Companion.variablesSerializer,
  )


private class RevokeFamilyBindingMutationImpl(
  connector: DefaultConnector
):
  RevokeFamilyBindingMutation,
  DefaultConnectorGeneratedMutationImpl<
      RevokeFamilyBindingMutation.Data,
      RevokeFamilyBindingMutation.Variables
  >(
    connector,
    RevokeFamilyBindingMutation.Companion.operationName,
    RevokeFamilyBindingMutation.Companion.dataDeserializer,
    RevokeFamilyBindingMutation.Companion.variablesSerializer,
  )


private class UpdateExamItemMutationImpl(
  connector: DefaultConnector
):
  UpdateExamItemMutation,
  DefaultConnectorGeneratedMutationImpl<
      UpdateExamItemMutation.Data,
      UpdateExamItemMutation.Variables
  >(
    connector,
    UpdateExamItemMutation.Companion.operationName,
    UpdateExamItemMutation.Companion.dataDeserializer,
    UpdateExamItemMutation.Companion.variablesSerializer,
  )


private class UpdateLeagueMembershipOutcomeMutationImpl(
  connector: DefaultConnector
):
  UpdateLeagueMembershipOutcomeMutation,
  DefaultConnectorGeneratedMutationImpl<
      UpdateLeagueMembershipOutcomeMutation.Data,
      UpdateLeagueMembershipOutcomeMutation.Variables
  >(
    connector,
    UpdateLeagueMembershipOutcomeMutation.Companion.operationName,
    UpdateLeagueMembershipOutcomeMutation.Companion.dataDeserializer,
    UpdateLeagueMembershipOutcomeMutation.Companion.variablesSerializer,
  )


private class UpdateLeagueMembershipXpMutationImpl(
  connector: DefaultConnector
):
  UpdateLeagueMembershipXpMutation,
  DefaultConnectorGeneratedMutationImpl<
      UpdateLeagueMembershipXpMutation.Data,
      UpdateLeagueMembershipXpMutation.Variables
  >(
    connector,
    UpdateLeagueMembershipXpMutation.Companion.operationName,
    UpdateLeagueMembershipXpMutation.Companion.dataDeserializer,
    UpdateLeagueMembershipXpMutation.Companion.variablesSerializer,
  )


private class UpdateProfileGamificationMutationImpl(
  connector: DefaultConnector
):
  UpdateProfileGamificationMutation,
  DefaultConnectorGeneratedMutationImpl<
      UpdateProfileGamificationMutation.Data,
      UpdateProfileGamificationMutation.Variables
  >(
    connector,
    UpdateProfileGamificationMutation.Companion.operationName,
    UpdateProfileGamificationMutation.Companion.dataDeserializer,
    UpdateProfileGamificationMutation.Companion.variablesSerializer,
  )


private class UpsertDailyMetricMutationImpl(
  connector: DefaultConnector
):
  UpsertDailyMetricMutation,
  DefaultConnectorGeneratedMutationImpl<
      UpsertDailyMetricMutation.Data,
      UpsertDailyMetricMutation.Variables
  >(
    connector,
    UpsertDailyMetricMutation.Companion.operationName,
    UpsertDailyMetricMutation.Companion.dataDeserializer,
    UpsertDailyMetricMutation.Companion.variablesSerializer,
  )


private class UpsertExamItemMutationImpl(
  connector: DefaultConnector
):
  UpsertExamItemMutation,
  DefaultConnectorGeneratedMutationImpl<
      UpsertExamItemMutation.Data,
      UpsertExamItemMutation.Variables
  >(
    connector,
    UpsertExamItemMutation.Companion.operationName,
    UpsertExamItemMutation.Companion.dataDeserializer,
    UpsertExamItemMutation.Companion.variablesSerializer,
  )


private class UpsertProfileMutationImpl(
  connector: DefaultConnector
):
  UpsertProfileMutation,
  DefaultConnectorGeneratedMutationImpl<
      UpsertProfileMutation.Data,
      UpsertProfileMutation.Variables
  >(
    connector,
    UpsertProfileMutation.Companion.operationName,
    UpsertProfileMutation.Companion.dataDeserializer,
    UpsertProfileMutation.Companion.variablesSerializer,
  )


