
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


public interface GetMyLeagueRankingQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetMyLeagueRankingQuery.Data,
      GetMyLeagueRankingQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val level: Int,
  
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
  
    val user: User,
  
    val leagueCycle: LeagueCycle,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class User(
  
    val id: String,
  
    val fullName: String,
  
    val currentLevel: Int?,
  
    val currentStreak: Int?,
  
  ) {
    
    
  }
      
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
    public val operationName: String = "GetMyLeagueRanking"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetMyLeagueRankingQuery.ref(
  
    level: Int,cycleId: java.util.UUID,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetMyLeagueRankingQuery.Data,
    GetMyLeagueRankingQuery.Variables
  > =
  ref(
    
      GetMyLeagueRankingQuery.Variables(
        level=level,cycleId=cycleId,
  
      )
    
  )

public suspend fun GetMyLeagueRankingQuery.execute(

  
    
      level: Int,cycleId: java.util.UUID,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetMyLeagueRankingQuery.Data,
    GetMyLeagueRankingQuery.Variables
  > =
  ref(
    
      level=level,cycleId=cycleId,
  
    
  ).execute()


  public fun GetMyLeagueRankingQuery.flow(
    
      level: Int,cycleId: java.util.UUID,

  
    
    ): kotlinx.coroutines.flow.Flow<GetMyLeagueRankingQuery.Data> =
    ref(
        
          level=level,cycleId=cycleId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

