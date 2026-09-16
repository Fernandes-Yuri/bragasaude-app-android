
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



public interface UpdateLeagueMembershipXpMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      UpdateLeagueMembershipXpMutation.Data,
      UpdateLeagueMembershipXpMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val xpEarned: Int,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val leagueMembership_update: LeagueMembershipKey?,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpdateLeagueMembershipXp"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpdateLeagueMembershipXpMutation.ref(
  
    id: java.util.UUID,xpEarned: Int,

  
  
): com.google.firebase.dataconnect.MutationRef<
    UpdateLeagueMembershipXpMutation.Data,
    UpdateLeagueMembershipXpMutation.Variables
  > =
  ref(
    
      UpdateLeagueMembershipXpMutation.Variables(
        id=id,xpEarned=xpEarned,
  
      )
    
  )

public suspend fun UpdateLeagueMembershipXpMutation.execute(

  
    
      id: java.util.UUID,xpEarned: Int,

  

  ): com.google.firebase.dataconnect.MutationResult<
    UpdateLeagueMembershipXpMutation.Data,
    UpdateLeagueMembershipXpMutation.Variables
  > =
  ref(
    
      id=id,xpEarned=xpEarned,
  
    
  ).execute()


