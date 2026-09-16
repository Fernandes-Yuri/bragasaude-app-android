
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



public interface UpdateLeagueMembershipOutcomeMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      UpdateLeagueMembershipOutcomeMutation.Data,
      UpdateLeagueMembershipOutcomeMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val membershipId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val rankAtClose: Int,
  
    val outcome: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val leagueMembership_update: LeagueMembershipKey?,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpdateLeagueMembershipOutcome"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpdateLeagueMembershipOutcomeMutation.ref(
  
    membershipId: java.util.UUID,rankAtClose: Int,outcome: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    UpdateLeagueMembershipOutcomeMutation.Data,
    UpdateLeagueMembershipOutcomeMutation.Variables
  > =
  ref(
    
      UpdateLeagueMembershipOutcomeMutation.Variables(
        membershipId=membershipId,rankAtClose=rankAtClose,outcome=outcome,
  
      )
    
  )

public suspend fun UpdateLeagueMembershipOutcomeMutation.execute(

  
    
      membershipId: java.util.UUID,rankAtClose: Int,outcome: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    UpdateLeagueMembershipOutcomeMutation.Data,
    UpdateLeagueMembershipOutcomeMutation.Variables
  > =
  ref(
    
      membershipId=membershipId,rankAtClose=rankAtClose,outcome=outcome,
  
    
  ).execute()


