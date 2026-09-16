
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



public interface DeleteUserFeedbacksMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      DeleteUserFeedbacksMutation.Data,
      DeleteUserFeedbacksMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val feedback_deleteMany: Int,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "DeleteUserFeedbacks"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun DeleteUserFeedbacksMutation.ref(
  
    userId: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    DeleteUserFeedbacksMutation.Data,
    DeleteUserFeedbacksMutation.Variables
  > =
  ref(
    
      DeleteUserFeedbacksMutation.Variables(
        userId=userId,
  
      )
    
  )

public suspend fun DeleteUserFeedbacksMutation.execute(

  
    
      userId: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    DeleteUserFeedbacksMutation.Data,
    DeleteUserFeedbacksMutation.Variables
  > =
  ref(
    
      userId=userId,
  
    
  ).execute()


