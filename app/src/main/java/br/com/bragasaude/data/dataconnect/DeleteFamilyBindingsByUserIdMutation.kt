
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



public interface DeleteFamilyBindingsByUserIdMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      DeleteFamilyBindingsByUserIdMutation.Data,
      DeleteFamilyBindingsByUserIdMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val familyBinding_deleteMany: Int,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "DeleteFamilyBindingsByUserId"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun DeleteFamilyBindingsByUserIdMutation.ref(
  
    userId: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    DeleteFamilyBindingsByUserIdMutation.Data,
    DeleteFamilyBindingsByUserIdMutation.Variables
  > =
  ref(
    
      DeleteFamilyBindingsByUserIdMutation.Variables(
        userId=userId,
  
      )
    
  )

public suspend fun DeleteFamilyBindingsByUserIdMutation.execute(

  
    
      userId: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    DeleteFamilyBindingsByUserIdMutation.Data,
    DeleteFamilyBindingsByUserIdMutation.Variables
  > =
  ref(
    
      userId=userId,
  
    
  ).execute()


