
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



public interface MarkFamilyMessageAsReadMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      MarkFamilyMessageAsReadMutation.Data,
      MarkFamilyMessageAsReadMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val messageId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val patientId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val familyMessage_updateMany: Int,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "MarkFamilyMessageAsRead"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun MarkFamilyMessageAsReadMutation.ref(
  
    messageId: java.util.UUID,patientId: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    MarkFamilyMessageAsReadMutation.Data,
    MarkFamilyMessageAsReadMutation.Variables
  > =
  ref(
    
      MarkFamilyMessageAsReadMutation.Variables(
        messageId=messageId,patientId=patientId,
  
      )
    
  )

public suspend fun MarkFamilyMessageAsReadMutation.execute(

  
    
      messageId: java.util.UUID,patientId: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    MarkFamilyMessageAsReadMutation.Data,
    MarkFamilyMessageAsReadMutation.Variables
  > =
  ref(
    
      messageId=messageId,patientId=patientId,
  
    
  ).execute()


