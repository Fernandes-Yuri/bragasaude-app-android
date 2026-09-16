
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


public interface GetPostReactionsQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetPostReactionsQuery.Data,
      GetPostReactionsQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val postId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val postReactions: List<PostReactionsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class PostReactionsItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val reactionType: String,
  
    val createdAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
    val user: User,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class User(
  
    val id: String,
  
    val fullName: String,
  
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetPostReactions"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetPostReactionsQuery.ref(
  
    postId: java.util.UUID,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetPostReactionsQuery.Data,
    GetPostReactionsQuery.Variables
  > =
  ref(
    
      GetPostReactionsQuery.Variables(
        postId=postId,
  
      )
    
  )

public suspend fun GetPostReactionsQuery.execute(

  
    
      postId: java.util.UUID,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetPostReactionsQuery.Data,
    GetPostReactionsQuery.Variables
  > =
  ref(
    
      postId=postId,
  
    
  ).execute()


  public fun GetPostReactionsQuery.flow(
    
      postId: java.util.UUID,

  
    
    ): kotlinx.coroutines.flow.Flow<GetPostReactionsQuery.Data> =
    ref(
        
          postId=postId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

