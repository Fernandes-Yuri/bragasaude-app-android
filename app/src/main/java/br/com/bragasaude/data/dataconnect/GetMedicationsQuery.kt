
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


public interface GetMedicationsQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetMedicationsQuery.Data,
      GetMedicationsQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val medications: List<MedicationsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class MedicationsItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val name: String,
  
    val dosageMg: Double?,
  
    val pillQuantity: Int?,
  
    val scheduleTime: String,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetMedications"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetMedicationsQuery.ref(
  
    userId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetMedicationsQuery.Data,
    GetMedicationsQuery.Variables
  > =
  ref(
    
      GetMedicationsQuery.Variables(
        userId=userId,
  
      )
    
  )

public suspend fun GetMedicationsQuery.execute(

  
    
      userId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetMedicationsQuery.Data,
    GetMedicationsQuery.Variables
  > =
  ref(
    
      userId=userId,
  
    
  ).execute()


  public fun GetMedicationsQuery.flow(
    
      userId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetMedicationsQuery.Data> =
    ref(
        
          userId=userId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

