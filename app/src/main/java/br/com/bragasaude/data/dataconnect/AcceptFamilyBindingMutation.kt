
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



public interface AcceptFamilyBindingMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      AcceptFamilyBindingMutation.Data,
      AcceptFamilyBindingMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val code: String,
  
    val caregiverUserId: String,
  
    val caregiverName: String,
  
    val caregiverRelation: String,
  
    val now: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val familyBinding_updateMany: Int,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "AcceptFamilyBinding"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun AcceptFamilyBindingMutation.ref(
  
    code: String,caregiverUserId: String,caregiverName: String,caregiverRelation: String,now: com.google.firebase.Timestamp,

  
  
): com.google.firebase.dataconnect.MutationRef<
    AcceptFamilyBindingMutation.Data,
    AcceptFamilyBindingMutation.Variables
  > =
  ref(
    
      AcceptFamilyBindingMutation.Variables(
        code=code,caregiverUserId=caregiverUserId,caregiverName=caregiverName,caregiverRelation=caregiverRelation,now=now,
  
      )
    
  )

public suspend fun AcceptFamilyBindingMutation.execute(

  
    
      code: String,caregiverUserId: String,caregiverName: String,caregiverRelation: String,now: com.google.firebase.Timestamp,

  

  ): com.google.firebase.dataconnect.MutationResult<
    AcceptFamilyBindingMutation.Data,
    AcceptFamilyBindingMutation.Variables
  > =
  ref(
    
      code=code,caregiverUserId=caregiverUserId,caregiverName=caregiverName,caregiverRelation=caregiverRelation,now=now,
  
    
  ).execute()


