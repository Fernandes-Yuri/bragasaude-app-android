
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



public interface CreateLeagueCycleMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      CreateLeagueCycleMutation.Data,
      CreateLeagueCycleMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val level: Int,
  
    val weekStartDate: com.google.firebase.dataconnect.LocalDate,
  
    val weekEndDate: com.google.firebase.dataconnect.LocalDate,
  
    val status: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val leagueCycle_insert: LeagueCycleKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "CreateLeagueCycle"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun CreateLeagueCycleMutation.ref(
  
    level: Int,weekStartDate: com.google.firebase.dataconnect.LocalDate,weekEndDate: com.google.firebase.dataconnect.LocalDate,status: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    CreateLeagueCycleMutation.Data,
    CreateLeagueCycleMutation.Variables
  > =
  ref(
    
      CreateLeagueCycleMutation.Variables(
        level=level,weekStartDate=weekStartDate,weekEndDate=weekEndDate,status=status,
  
      )
    
  )

public suspend fun CreateLeagueCycleMutation.execute(

  
    
      level: Int,weekStartDate: com.google.firebase.dataconnect.LocalDate,weekEndDate: com.google.firebase.dataconnect.LocalDate,status: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    CreateLeagueCycleMutation.Data,
    CreateLeagueCycleMutation.Variables
  > =
  ref(
    
      level=level,weekStartDate=weekStartDate,weekEndDate=weekEndDate,status=status,
  
    
  ).execute()


