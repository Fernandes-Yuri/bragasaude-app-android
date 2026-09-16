
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



public interface LogMedicationTakenMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      LogMedicationTakenMutation.Data,
      LogMedicationTakenMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
    val medicationId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val takenAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val medicationLog_insert: MedicationLogKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "LogMedicationTaken"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun LogMedicationTakenMutation.ref(
  
    userId: String,medicationId: java.util.UUID,takenAt: com.google.firebase.Timestamp,

  
  
): com.google.firebase.dataconnect.MutationRef<
    LogMedicationTakenMutation.Data,
    LogMedicationTakenMutation.Variables
  > =
  ref(
    
      LogMedicationTakenMutation.Variables(
        userId=userId,medicationId=medicationId,takenAt=takenAt,
  
      )
    
  )

public suspend fun LogMedicationTakenMutation.execute(

  
    
      userId: String,medicationId: java.util.UUID,takenAt: com.google.firebase.Timestamp,

  

  ): com.google.firebase.dataconnect.MutationResult<
    LogMedicationTakenMutation.Data,
    LogMedicationTakenMutation.Variables
  > =
  ref(
    
      userId=userId,medicationId=medicationId,takenAt=takenAt,
  
    
  ).execute()


