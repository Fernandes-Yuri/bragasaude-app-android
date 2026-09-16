
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



public interface CreateFamilyBindingMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      CreateFamilyBindingMutation.Data,
      CreateFamilyBindingMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val patientId: String,
  
    val caregiverName: String,
  
    val caregiverRelation: String,
  
    val connectionCode: String,
  
    val createdAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
    val expiresAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val familyBinding_insert: FamilyBindingKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "CreateFamilyBinding"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun CreateFamilyBindingMutation.ref(
  
    patientId: String,caregiverName: String,caregiverRelation: String,connectionCode: String,createdAt: com.google.firebase.Timestamp,expiresAt: com.google.firebase.Timestamp,

  
  
): com.google.firebase.dataconnect.MutationRef<
    CreateFamilyBindingMutation.Data,
    CreateFamilyBindingMutation.Variables
  > =
  ref(
    
      CreateFamilyBindingMutation.Variables(
        patientId=patientId,caregiverName=caregiverName,caregiverRelation=caregiverRelation,connectionCode=connectionCode,createdAt=createdAt,expiresAt=expiresAt,
  
      )
    
  )

public suspend fun CreateFamilyBindingMutation.execute(

  
    
      patientId: String,caregiverName: String,caregiverRelation: String,connectionCode: String,createdAt: com.google.firebase.Timestamp,expiresAt: com.google.firebase.Timestamp,

  

  ): com.google.firebase.dataconnect.MutationResult<
    CreateFamilyBindingMutation.Data,
    CreateFamilyBindingMutation.Variables
  > =
  ref(
    
      patientId=patientId,caregiverName=caregiverName,caregiverRelation=caregiverRelation,connectionCode=connectionCode,createdAt=createdAt,expiresAt=expiresAt,
  
    
  ).execute()


