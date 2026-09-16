
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



public interface CloseLeagueCycleMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      CloseLeagueCycleMutation.Data,
      CloseLeagueCycleMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val cycleId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val leagueCycle_update: LeagueCycleKey?,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "CloseLeagueCycle"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun CloseLeagueCycleMutation.ref(
  
    cycleId: java.util.UUID,

  
  
): com.google.firebase.dataconnect.MutationRef<
    CloseLeagueCycleMutation.Data,
    CloseLeagueCycleMutation.Variables
  > =
  ref(
    
      CloseLeagueCycleMutation.Variables(
        cycleId=cycleId,
  
      )
    
  )

public suspend fun CloseLeagueCycleMutation.execute(

  
    
      cycleId: java.util.UUID,

  

  ): com.google.firebase.dataconnect.MutationResult<
    CloseLeagueCycleMutation.Data,
    CloseLeagueCycleMutation.Variables
  > =
  ref(
    
      cycleId=cycleId,
  
    
  ).execute()


