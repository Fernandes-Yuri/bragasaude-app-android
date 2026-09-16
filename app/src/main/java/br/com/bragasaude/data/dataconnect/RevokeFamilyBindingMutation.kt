
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



public interface RevokeFamilyBindingMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      RevokeFamilyBindingMutation.Data,
      RevokeFamilyBindingMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val bindingId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val patientId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val familyBinding_update: FamilyBindingKey?,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "RevokeFamilyBinding"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun RevokeFamilyBindingMutation.ref(
  
    bindingId: java.util.UUID,patientId: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    RevokeFamilyBindingMutation.Data,
    RevokeFamilyBindingMutation.Variables
  > =
  ref(
    
      RevokeFamilyBindingMutation.Variables(
        bindingId=bindingId,patientId=patientId,
  
      )
    
  )

public suspend fun RevokeFamilyBindingMutation.execute(

  
    
      bindingId: java.util.UUID,patientId: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    RevokeFamilyBindingMutation.Data,
    RevokeFamilyBindingMutation.Variables
  > =
  ref(
    
      bindingId=bindingId,patientId=patientId,
  
    
  ).execute()


