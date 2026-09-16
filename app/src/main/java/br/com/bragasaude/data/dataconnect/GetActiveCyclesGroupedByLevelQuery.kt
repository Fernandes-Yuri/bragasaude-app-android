
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


public interface GetActiveCyclesGroupedByLevelQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetActiveCyclesGroupedByLevelQuery.Data,
      Unit
    >
{
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val entries: List<EntriesItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class EntriesItem(
  
    val cycleId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val level: Int,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetActiveCyclesGroupedByLevel"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Unit> =
      kotlinx.serialization.serializer()
  }
}

public fun GetActiveCyclesGroupedByLevelQuery.ref(
  
): com.google.firebase.dataconnect.QueryRef<
    GetActiveCyclesGroupedByLevelQuery.Data,
    Unit
  > =
  ref(
    
      Unit
    
  )

public suspend fun GetActiveCyclesGroupedByLevelQuery.execute(

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetActiveCyclesGroupedByLevelQuery.Data,
    Unit
  > =
  ref(
    
  ).execute()


  public fun GetActiveCyclesGroupedByLevelQuery.flow(
    
    ): kotlinx.coroutines.flow.Flow<GetActiveCyclesGroupedByLevelQuery.Data> =
    ref(
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

