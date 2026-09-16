
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


public interface GetClinicalReferencesQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetClinicalReferencesQuery.Data,
      Unit
    >
{
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val clinicalReferences: List<ClinicalReferencesItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class ClinicalReferencesItem(
  
    val itemKey: String,
  
    val itemName: String,
  
    val category: String,
  
    val unit: String?,
  
    val minTarget: Double?,
  
    val maxTarget: Double?,
  
    val minCritical: Double?,
  
    val maxCritical: Double?,
  
    val interpretationHint: String?,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetClinicalReferences"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Unit> =
      kotlinx.serialization.serializer()
  }
}

public fun GetClinicalReferencesQuery.ref(
  
): com.google.firebase.dataconnect.QueryRef<
    GetClinicalReferencesQuery.Data,
    Unit
  > =
  ref(
    
      Unit
    
  )

public suspend fun GetClinicalReferencesQuery.execute(

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetClinicalReferencesQuery.Data,
    Unit
  > =
  ref(
    
  ).execute()


  public fun GetClinicalReferencesQuery.flow(
    
    ): kotlinx.coroutines.flow.Flow<GetClinicalReferencesQuery.Data> =
    ref(
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

