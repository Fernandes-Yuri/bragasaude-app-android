
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



public interface ReactToPostMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      ReactToPostMutation.Data,
      ReactToPostMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val postId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val userId: String,
  
    val reactionType: String,
  
    val createdAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val postReaction_insert: PostReactionKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "ReactToPost"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun ReactToPostMutation.ref(
  
    postId: java.util.UUID,userId: String,reactionType: String,createdAt: com.google.firebase.Timestamp,

  
  
): com.google.firebase.dataconnect.MutationRef<
    ReactToPostMutation.Data,
    ReactToPostMutation.Variables
  > =
  ref(
    
      ReactToPostMutation.Variables(
        postId=postId,userId=userId,reactionType=reactionType,createdAt=createdAt,
  
      )
    
  )

public suspend fun ReactToPostMutation.execute(

  
    
      postId: java.util.UUID,userId: String,reactionType: String,createdAt: com.google.firebase.Timestamp,

  

  ): com.google.firebase.dataconnect.MutationResult<
    ReactToPostMutation.Data,
    ReactToPostMutation.Variables
  > =
  ref(
    
      postId=postId,userId=userId,reactionType=reactionType,createdAt=createdAt,
  
    
  ).execute()


