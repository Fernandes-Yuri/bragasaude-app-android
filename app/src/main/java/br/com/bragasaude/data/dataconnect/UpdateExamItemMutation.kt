
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



public interface UpdateExamItemMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      UpdateExamItemMutation.Data,
      UpdateExamItemMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val userId: String,
  
    val status: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val examItem_updateMany: Int,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpdateExamItem"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpdateExamItemMutation.ref(
  
    id: java.util.UUID,userId: String,status: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    UpdateExamItemMutation.Data,
    UpdateExamItemMutation.Variables
  > =
  ref(
    
      UpdateExamItemMutation.Variables(
        id=id,userId=userId,status=status,
  
      )
    
  )

public suspend fun UpdateExamItemMutation.execute(

  
    
      id: java.util.UUID,userId: String,status: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    UpdateExamItemMutation.Data,
    UpdateExamItemMutation.Variables
  > =
  ref(
    
      id=id,userId=userId,status=status,
  
    
  ).execute()


