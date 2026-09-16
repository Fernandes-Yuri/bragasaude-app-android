
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


public interface GetExamsByUserQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetExamsByUserQuery.Data,
      GetExamsByUserQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val exams: List<ExamsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class ExamsItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val title: String,
  
    val category: String,
  
    val examDate: com.google.firebase.dataconnect.LocalDate,
  
    val fileUrl: String,
  
    val status: String,
  
    val createdAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp?,
  
    val aiExtractedData: String?,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetExamsByUser"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetExamsByUserQuery.ref(
  
    userId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetExamsByUserQuery.Data,
    GetExamsByUserQuery.Variables
  > =
  ref(
    
      GetExamsByUserQuery.Variables(
        userId=userId,
  
      )
    
  )

public suspend fun GetExamsByUserQuery.execute(

  
    
      userId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetExamsByUserQuery.Data,
    GetExamsByUserQuery.Variables
  > =
  ref(
    
      userId=userId,
  
    
  ).execute()


  public fun GetExamsByUserQuery.flow(
    
      userId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetExamsByUserQuery.Data> =
    ref(
        
          userId=userId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

