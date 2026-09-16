
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


public interface GetPatientProfileQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetPatientProfileQuery.Data,
      GetPatientProfileQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val patientId: String,
  
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
  
    val hasDiabetes: Boolean?,
  
    val hasHypertension: Boolean?,
  
    val hydrationTargetMl: Int?,
  
    val stepGoal: Int?,
  
    val weight: Double?,
  
    val height: Double?,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetPatientProfile"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetPatientProfileQuery.ref(
  
    patientId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetPatientProfileQuery.Data,
    GetPatientProfileQuery.Variables
  > =
  ref(
    
      GetPatientProfileQuery.Variables(
        patientId=patientId,
  
      )
    
  )

public suspend fun GetPatientProfileQuery.execute(

  
    
      patientId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetPatientProfileQuery.Data,
    GetPatientProfileQuery.Variables
  > =
  ref(
    
      patientId=patientId,
  
    
  ).execute()


  public fun GetPatientProfileQuery.flow(
    
      patientId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetPatientProfileQuery.Data> =
    ref(
        
          patientId=patientId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

