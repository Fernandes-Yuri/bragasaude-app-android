
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


import kotlinx.coroutines.flow.filterNotNull as _flow_filterNotNull
import kotlinx.coroutines.flow.map as _flow_map


public interface GetFamilyBindingsForCaregiverQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetFamilyBindingsForCaregiverQuery.Data,
      GetFamilyBindingsForCaregiverQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val caregiverId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val familyBindings: List<FamilyBindingsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class FamilyBindingsItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val connectionCode: String,
  
    val caregiverName: String,
  
    val caregiverRelation: String,
  
    val status: String,
  
    val createdAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
    val expiresAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
    val patient: Patient,
  
    val caregiver: Caregiver?,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class Patient(
  
    val id: String,
  
    val fullName: String,
  
  ) {
    
    
  }
      
        @kotlinx.serialization.Serializable
  public data class Caregiver(
  
    val id: String,
  
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetFamilyBindingsForCaregiver"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetFamilyBindingsForCaregiverQuery.ref(
  
    caregiverId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetFamilyBindingsForCaregiverQuery.Data,
    GetFamilyBindingsForCaregiverQuery.Variables
  > =
  ref(
    
      GetFamilyBindingsForCaregiverQuery.Variables(
        caregiverId=caregiverId,
  
      )
    
  )

public suspend fun GetFamilyBindingsForCaregiverQuery.execute(

  
    
      caregiverId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetFamilyBindingsForCaregiverQuery.Data,
    GetFamilyBindingsForCaregiverQuery.Variables
  > =
  ref(
    
      caregiverId=caregiverId,
  
    
  ).execute()


  public fun GetFamilyBindingsForCaregiverQuery.flow(
    
      caregiverId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetFamilyBindingsForCaregiverQuery.Data> =
    ref(
        
          caregiverId=caregiverId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

