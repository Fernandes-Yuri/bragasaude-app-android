
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


public interface GetActiveLeagueCyclesQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetActiveLeagueCyclesQuery.Data,
      Unit
    >
{
  

  
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
    public val operationName: String = "GetActiveLeagueCycles"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Unit> =
      kotlinx.serialization.serializer()
  }
}

public fun GetActiveLeagueCyclesQuery.ref(
  
): com.google.firebase.dataconnect.QueryRef<
    GetActiveLeagueCyclesQuery.Data,
    Unit
  > =
  ref(
    
      Unit
    
  )

public suspend fun GetActiveLeagueCyclesQuery.execute(

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetActiveLeagueCyclesQuery.Data,
    Unit
  > =
  ref(
    
  ).execute()


  public fun GetActiveLeagueCyclesQuery.flow(
    
    ): kotlinx.coroutines.flow.Flow<GetActiveLeagueCyclesQuery.Data> =
    ref(
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

