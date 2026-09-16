
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


public interface GetPatientVitalSignsQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetPatientVitalSignsQuery.Data,
      GetPatientVitalSignsQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val patientId: String,
  
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
    public val operationName: String = "GetPatientVitalSigns"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetPatientVitalSignsQuery.ref(
  
    patientId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetPatientVitalSignsQuery.Data,
    GetPatientVitalSignsQuery.Variables
  > =
  ref(
    
      GetPatientVitalSignsQuery.Variables(
        patientId=patientId,
  
      )
    
  )

public suspend fun GetPatientVitalSignsQuery.execute(

  
    
      patientId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetPatientVitalSignsQuery.Data,
    GetPatientVitalSignsQuery.Variables
  > =
  ref(
    
      patientId=patientId,
  
    
  ).execute()


  public fun GetPatientVitalSignsQuery.flow(
    
      patientId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetPatientVitalSignsQuery.Data> =
    ref(
        
          patientId=patientId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

