
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



public interface MarkCycleAsErrorMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      MarkCycleAsErrorMutation.Data,
      MarkCycleAsErrorMutation.Variables
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
    public val operationName: String = "MarkCycleAsError"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun MarkCycleAsErrorMutation.ref(
  
    cycleId: java.util.UUID,

  
  
): com.google.firebase.dataconnect.MutationRef<
    MarkCycleAsErrorMutation.Data,
    MarkCycleAsErrorMutation.Variables
  > =
  ref(
    
      MarkCycleAsErrorMutation.Variables(
        cycleId=cycleId,
  
      )
    
  )

public suspend fun MarkCycleAsErrorMutation.execute(

  
    
      cycleId: java.util.UUID,

  

  ): com.google.firebase.dataconnect.MutationResult<
    MarkCycleAsErrorMutation.Data,
    MarkCycleAsErrorMutation.Variables
  > =
  ref(
    
      cycleId=cycleId,
  
    
  ).execute()


