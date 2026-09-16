
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



public interface CreateFamilyMessageMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      CreateFamilyMessageMutation.Data,
      CreateFamilyMessageMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val patientId: String,
  
    val senderName: String,
  
    val messageText: String,
  
    val iconType: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val familyMessage_insert: FamilyMessageKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "CreateFamilyMessage"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun CreateFamilyMessageMutation.ref(
  
    patientId: String,senderName: String,messageText: String,iconType: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    CreateFamilyMessageMutation.Data,
    CreateFamilyMessageMutation.Variables
  > =
  ref(
    
      CreateFamilyMessageMutation.Variables(
        patientId=patientId,senderName=senderName,messageText=messageText,iconType=iconType,
  
      )
    
  )

public suspend fun CreateFamilyMessageMutation.execute(

  
    
      patientId: String,senderName: String,messageText: String,iconType: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    CreateFamilyMessageMutation.Data,
    CreateFamilyMessageMutation.Variables
  > =
  ref(
    
      patientId=patientId,senderName=senderName,messageText=messageText,iconType=iconType,
  
    
  ).execute()


