
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


public interface GetLeagueMembershipsByCycleQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetLeagueMembershipsByCycleQuery.Data,
      GetLeagueMembershipsByCycleQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val cycleId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val level: Int,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val memberships: List<MembershipsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class MembershipsItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val xpEarned: Int,
  
    val user: User,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class User(
  
    val id: String,
  
    val currentLevel: Int?,
  
    val totalXp: Int?,
  
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetLeagueMembershipsByCycle"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetLeagueMembershipsByCycleQuery.ref(
  
    cycleId: java.util.UUID,level: Int,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetLeagueMembershipsByCycleQuery.Data,
    GetLeagueMembershipsByCycleQuery.Variables
  > =
  ref(
    
      GetLeagueMembershipsByCycleQuery.Variables(
        cycleId=cycleId,level=level,
  
      )
    
  )

public suspend fun GetLeagueMembershipsByCycleQuery.execute(

  
    
      cycleId: java.util.UUID,level: Int,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetLeagueMembershipsByCycleQuery.Data,
    GetLeagueMembershipsByCycleQuery.Variables
  > =
  ref(
    
      cycleId=cycleId,level=level,
  
    
  ).execute()


  public fun GetLeagueMembershipsByCycleQuery.flow(
    
      cycleId: java.util.UUID,level: Int,

  
    
    ): kotlinx.coroutines.flow.Flow<GetLeagueMembershipsByCycleQuery.Data> =
    ref(
        
          cycleId=cycleId,level=level,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

