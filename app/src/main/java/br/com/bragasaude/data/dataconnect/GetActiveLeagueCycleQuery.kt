
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


public interface GetActiveLeagueCycleQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetActiveLeagueCycleQuery.Data,
      GetActiveLeagueCycleQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val level: Int,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val leagueCycles: List<LeagueCyclesItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class LeagueCyclesItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val level: Int,
  
    val weekStartDate: com.google.firebase.dataconnect.LocalDate,
  
    val weekEndDate: com.google.firebase.dataconnect.LocalDate,
  
    val status: String,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetActiveLeagueCycle"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetActiveLeagueCycleQuery.ref(
  
    level: Int,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetActiveLeagueCycleQuery.Data,
    GetActiveLeagueCycleQuery.Variables
  > =
  ref(
    
      GetActiveLeagueCycleQuery.Variables(
        level=level,
  
      )
    
  )

public suspend fun GetActiveLeagueCycleQuery.execute(

  
    
      level: Int,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetActiveLeagueCycleQuery.Data,
    GetActiveLeagueCycleQuery.Variables
  > =
  ref(
    
      level=level,
  
    
  ).execute()


  public fun GetActiveLeagueCycleQuery.flow(
    
      level: Int,

  
    
    ): kotlinx.coroutines.flow.Flow<GetActiveLeagueCycleQuery.Data> =
    ref(
        
          level=level,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

