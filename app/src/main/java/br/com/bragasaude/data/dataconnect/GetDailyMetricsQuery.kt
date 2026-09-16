
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


public interface GetDailyMetricsQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetDailyMetricsQuery.Data,
      GetDailyMetricsQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val dailyMetrics: List<DailyMetricsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class DailyMetricsItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val date: com.google.firebase.dataconnect.LocalDate,
  
    val steps: Int,
  
    val distanceMeters: Double,
  
    val distanceGpsMeters: Double?,
  
    val distanceStepsMeters: Double?,
  
    val distanceFinalMeters: Double?,
  
    val reliabilityScore: Double?,
  
    val caloriesBurned: Double,
  
    val activeMinutes: Int,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetDailyMetrics"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetDailyMetricsQuery.ref(
  
    userId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetDailyMetricsQuery.Data,
    GetDailyMetricsQuery.Variables
  > =
  ref(
    
      GetDailyMetricsQuery.Variables(
        userId=userId,
  
      )
    
  )

public suspend fun GetDailyMetricsQuery.execute(

  
    
      userId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetDailyMetricsQuery.Data,
    GetDailyMetricsQuery.Variables
  > =
  ref(
    
      userId=userId,
  
    
  ).execute()


  public fun GetDailyMetricsQuery.flow(
    
      userId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetDailyMetricsQuery.Data> =
    ref(
        
          userId=userId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

