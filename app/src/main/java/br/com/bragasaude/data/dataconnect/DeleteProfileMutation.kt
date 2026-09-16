
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



public interface DeleteProfileMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      DeleteProfileMutation.Data,
      DeleteProfileMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val profile_delete: ProfileKey?,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "DeleteProfile"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun DeleteProfileMutation.ref(
  
    userId: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    DeleteProfileMutation.Data,
    DeleteProfileMutation.Variables
  > =
  ref(
    
      DeleteProfileMutation.Variables(
        userId=userId,
  
      )
    
  )

public suspend fun DeleteProfileMutation.execute(

  
    
      userId: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    DeleteProfileMutation.Data,
    DeleteProfileMutation.Variables
  > =
  ref(
    
      userId=userId,
  
    
  ).execute()


