
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



public interface RemovePostReactionMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      RemovePostReactionMutation.Data,
      RemovePostReactionMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val postId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val postReaction_deleteMany: Int,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "RemovePostReaction"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun RemovePostReactionMutation.ref(
  
    postId: java.util.UUID,userId: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    RemovePostReactionMutation.Data,
    RemovePostReactionMutation.Variables
  > =
  ref(
    
      RemovePostReactionMutation.Variables(
        postId=postId,userId=userId,
  
      )
    
  )

public suspend fun RemovePostReactionMutation.execute(

  
    
      postId: java.util.UUID,userId: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    RemovePostReactionMutation.Data,
    RemovePostReactionMutation.Variables
  > =
  ref(
    
      postId=postId,userId=userId,
  
    
  ).execute()


