
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


public interface GetFamilyBindingsForPatientQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetFamilyBindingsForPatientQuery.Data,
      GetFamilyBindingsForPatientQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val patientId: String,
  
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
  
  ) {
    
    
  }
      
        @kotlinx.serialization.Serializable
  public data class Caregiver(
  
    val id: String,
  
    val fullName: String,
  
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetFamilyBindingsForPatient"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetFamilyBindingsForPatientQuery.ref(
  
    patientId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetFamilyBindingsForPatientQuery.Data,
    GetFamilyBindingsForPatientQuery.Variables
  > =
  ref(
    
      GetFamilyBindingsForPatientQuery.Variables(
        patientId=patientId,
  
      )
    
  )

public suspend fun GetFamilyBindingsForPatientQuery.execute(

  
    
      patientId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetFamilyBindingsForPatientQuery.Data,
    GetFamilyBindingsForPatientQuery.Variables
  > =
  ref(
    
      patientId=patientId,
  
    
  ).execute()


  public fun GetFamilyBindingsForPatientQuery.flow(
    
      patientId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetFamilyBindingsForPatientQuery.Data> =
    ref(
        
          patientId=patientId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

