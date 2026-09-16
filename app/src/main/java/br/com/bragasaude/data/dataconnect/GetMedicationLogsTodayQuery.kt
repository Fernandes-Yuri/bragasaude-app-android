
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


public interface GetMedicationLogsTodayQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetMedicationLogsTodayQuery.Data,
      GetMedicationLogsTodayQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val medicationLogs: List<MedicationLogsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class MedicationLogsItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val takenAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
    val medication: Medication,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class Medication(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val name: String,
  
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetMedicationLogsToday"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetMedicationLogsTodayQuery.ref(
  
    userId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetMedicationLogsTodayQuery.Data,
    GetMedicationLogsTodayQuery.Variables
  > =
  ref(
    
      GetMedicationLogsTodayQuery.Variables(
        userId=userId,
  
      )
    
  )

public suspend fun GetMedicationLogsTodayQuery.execute(

  
    
      userId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetMedicationLogsTodayQuery.Data,
    GetMedicationLogsTodayQuery.Variables
  > =
  ref(
    
      userId=userId,
  
    
  ).execute()


  public fun GetMedicationLogsTodayQuery.flow(
    
      userId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetMedicationLogsTodayQuery.Data> =
    ref(
        
          userId=userId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

