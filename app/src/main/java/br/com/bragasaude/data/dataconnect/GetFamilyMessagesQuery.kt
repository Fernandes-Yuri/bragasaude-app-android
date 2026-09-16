
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


public interface GetFamilyMessagesQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetFamilyMessagesQuery.Data,
      GetFamilyMessagesQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val patientId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val familyMessages: List<FamilyMessagesItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class FamilyMessagesItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val sender: Sender,
  
    val senderName: String,
  
    val messageText: String,
  
    val iconType: String,
  
    val isRead: Boolean,
  
    val sentAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class Sender(
  
    val id: String,
  
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetFamilyMessages"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetFamilyMessagesQuery.ref(
  
    patientId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetFamilyMessagesQuery.Data,
    GetFamilyMessagesQuery.Variables
  > =
  ref(
    
      GetFamilyMessagesQuery.Variables(
        patientId=patientId,
  
      )
    
  )

public suspend fun GetFamilyMessagesQuery.execute(

  
    
      patientId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetFamilyMessagesQuery.Data,
    GetFamilyMessagesQuery.Variables
  > =
  ref(
    
      patientId=patientId,
  
    
  ).execute()


  public fun GetFamilyMessagesQuery.flow(
    
      patientId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetFamilyMessagesQuery.Data> =
    ref(
        
          patientId=patientId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

