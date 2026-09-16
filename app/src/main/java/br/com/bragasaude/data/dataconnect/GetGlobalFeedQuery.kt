
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


public interface GetGlobalFeedQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetGlobalFeedQuery.Data,
      GetGlobalFeedQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val limit: Int,
  
    val offset: Int,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val socialPosts: List<SocialPostsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class SocialPostsItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val postType: String,
  
    val title: String,
  
    val description: String?,
  
    val createdAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
    val isVisible: Boolean,
  
    val user: User,
  
    val relatedMilestone: RelatedMilestone?,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class User(
  
    val id: String,
  
    val fullName: String,
  
    val currentLevel: Int?,
  
  ) {
    
    
  }
      
        @kotlinx.serialization.Serializable
  public data class RelatedMilestone(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val badgeType: String,
  
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetGlobalFeed"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetGlobalFeedQuery.ref(
  
    limit: Int,offset: Int,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetGlobalFeedQuery.Data,
    GetGlobalFeedQuery.Variables
  > =
  ref(
    
      GetGlobalFeedQuery.Variables(
        limit=limit,offset=offset,
  
      )
    
  )

public suspend fun GetGlobalFeedQuery.execute(

  
    
      limit: Int,offset: Int,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetGlobalFeedQuery.Data,
    GetGlobalFeedQuery.Variables
  > =
  ref(
    
      limit=limit,offset=offset,
  
    
  ).execute()


  public fun GetGlobalFeedQuery.flow(
    
      limit: Int,offset: Int,

  
    
    ): kotlinx.coroutines.flow.Flow<GetGlobalFeedQuery.Data> =
    ref(
        
          limit=limit,offset=offset,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

