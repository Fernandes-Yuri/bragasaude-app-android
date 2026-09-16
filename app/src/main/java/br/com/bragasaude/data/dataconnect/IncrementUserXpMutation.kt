
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



public interface IncrementUserXpMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      IncrementUserXpMutation.Data,
      IncrementUserXpMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
    val xpAmount: Int,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val profile_update: ProfileKey?,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "IncrementUserXp"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun IncrementUserXpMutation.ref(
  
    userId: String,xpAmount: Int,

  
  
): com.google.firebase.dataconnect.MutationRef<
    IncrementUserXpMutation.Data,
    IncrementUserXpMutation.Variables
  > =
  ref(
    
      IncrementUserXpMutation.Variables(
        userId=userId,xpAmount=xpAmount,
  
      )
    
  )

public suspend fun IncrementUserXpMutation.execute(

  
    
      userId: String,xpAmount: Int,

  

  ): com.google.firebase.dataconnect.MutationResult<
    IncrementUserXpMutation.Data,
    IncrementUserXpMutation.Variables
  > =
  ref(
    
      userId=userId,xpAmount=xpAmount,
  
    
  ).execute()


