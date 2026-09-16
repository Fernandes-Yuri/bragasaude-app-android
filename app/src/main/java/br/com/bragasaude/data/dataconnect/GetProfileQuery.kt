
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


import kotlinx.coroutines.flow.filterNotNull as _flow_filterNotNull
import kotlinx.coroutines.flow.map as _flow_map


public interface GetProfileQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetProfileQuery.Data,
      GetProfileQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val profile: Profile?,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class Profile(
  
    val id: String,
  
    val fullName: String,
  
    val birthDate: com.google.firebase.dataconnect.LocalDate?,
  
    val gender: String?,
  
    val height: Double?,
  
    val weight: Double?,
  
    val isSmoker: Boolean?,
  
    val hasDiabetes: Boolean?,
  
    val hasHypertension: Boolean?,
  
    val hasThyroidIssue: Boolean?,
  
    val hasRenalIssue: Boolean?,
  
    val hasBoneIssue: Boolean?,
  
    val hasMuscularIssue: Boolean?,
  
    val hydrationTargetMl: Int?,
  
    val dailyCalorieTarget: Int?,
  
    val stepGoal: Int?,
  
    val weightGoal: Double?,
  
    val sleepStartTime: String?,
  
    val sleepEndTime: String?,
  
    val emergencyContactName: String?,
  
    val emergencyContactRelation: String?,
  
    val emergencyContactPhone: String?,
  
    val consentAcceptedAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp?,
  
    val notificationsEnabled: Boolean?,
  
    val locationEnabled: Boolean?,
  
    val currentXp: Int?,
  
    val totalXp: Int?,
  
    val currentLevel: Int?,
  
    val currentStreak: Int?,
  
    val lastXpAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp?,
  
    val showInFeed: Boolean?,
  
    val userRole: String?,
  
    val caregiverMode: String?,
  
    val avatarIdentifier: String?,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetProfile"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetProfileQuery.ref(
  
    id: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetProfileQuery.Data,
    GetProfileQuery.Variables
  > =
  ref(
    
      GetProfileQuery.Variables(
        id=id,
  
      )
    
  )

public suspend fun GetProfileQuery.execute(

  
    
      id: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetProfileQuery.Data,
    GetProfileQuery.Variables
  > =
  ref(
    
      id=id,
  
    
  ).execute()


  public fun GetProfileQuery.flow(
    
      id: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetProfileQuery.Data> =
    ref(
        
          id=id,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

