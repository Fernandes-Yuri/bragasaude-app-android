
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


public interface GetMyLeagueMembershipQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetMyLeagueMembershipQuery.Data,
      GetMyLeagueMembershipQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
    val cycleId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val leagueMemberships: List<LeagueMembershipsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class LeagueMembershipsItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val xpEarned: Int,
  
    val rankAtClose: Int?,
  
    val outcome: String?,
  
    val leagueCycle: LeagueCycle,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class LeagueCycle(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val level: Int,
  
    val weekStartDate: com.google.firebase.dataconnect.LocalDate,
  
    val weekEndDate: com.google.firebase.dataconnect.LocalDate,
  
    val status: String,
  
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetMyLeagueMembership"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetMyLeagueMembershipQuery.ref(
  
    userId: String,cycleId: java.util.UUID,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetMyLeagueMembershipQuery.Data,
    GetMyLeagueMembershipQuery.Variables
  > =
  ref(
    
      GetMyLeagueMembershipQuery.Variables(
        userId=userId,cycleId=cycleId,
  
      )
    
  )

public suspend fun GetMyLeagueMembershipQuery.execute(

  
    
      userId: String,cycleId: java.util.UUID,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetMyLeagueMembershipQuery.Data,
    GetMyLeagueMembershipQuery.Variables
  > =
  ref(
    
      userId=userId,cycleId=cycleId,
  
    
  ).execute()


  public fun GetMyLeagueMembershipQuery.flow(
    
      userId: String,cycleId: java.util.UUID,

  
    
    ): kotlinx.coroutines.flow.Flow<GetMyLeagueMembershipQuery.Data> =
    ref(
        
          userId=userId,cycleId=cycleId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

