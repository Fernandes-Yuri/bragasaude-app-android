
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


public interface GetVitalSignsQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetVitalSignsQuery.Data,
      GetVitalSignsQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val vitalSigns: List<VitalSignsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class VitalSignsItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val systolicPressure: Int?,
  
    val diastolicPressure: Int?,
  
    val heartRate: Int?,
  
    val glucoseLevel: Int?,
  
    val glucoseType: String?,
  
    val hydrationMl: Int?,
  
    val measuredAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetVitalSigns"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetVitalSignsQuery.ref(
  
    userId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetVitalSignsQuery.Data,
    GetVitalSignsQuery.Variables
  > =
  ref(
    
      GetVitalSignsQuery.Variables(
        userId=userId,
  
      )
    
  )

public suspend fun GetVitalSignsQuery.execute(

  
    
      userId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetVitalSignsQuery.Data,
    GetVitalSignsQuery.Variables
  > =
  ref(
    
      userId=userId,
  
    
  ).execute()


  public fun GetVitalSignsQuery.flow(
    
      userId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetVitalSignsQuery.Data> =
    ref(
        
          userId=userId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

