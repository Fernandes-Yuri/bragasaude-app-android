
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



public interface JoinLeagueCycleMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      JoinLeagueCycleMutation.Data,
      JoinLeagueCycleMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
    val cycleId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val leagueMembership_insert: LeagueMembershipKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "JoinLeagueCycle"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun JoinLeagueCycleMutation.ref(
  
    userId: String,cycleId: java.util.UUID,

  
  
): com.google.firebase.dataconnect.MutationRef<
    JoinLeagueCycleMutation.Data,
    JoinLeagueCycleMutation.Variables
  > =
  ref(
    
      JoinLeagueCycleMutation.Variables(
        userId=userId,cycleId=cycleId,
  
      )
    
  )

public suspend fun JoinLeagueCycleMutation.execute(

  
    
      userId: String,cycleId: java.util.UUID,

  

  ): com.google.firebase.dataconnect.MutationResult<
    JoinLeagueCycleMutation.Data,
    JoinLeagueCycleMutation.Variables
  > =
  ref(
    
      userId=userId,cycleId=cycleId,
  
    
  ).execute()


