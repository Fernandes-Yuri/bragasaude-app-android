
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


public interface GetExamItemsByUserQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetExamItemsByUserQuery.Data,
      GetExamItemsByUserQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val examItems: List<ExamItemsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class ExamItemsItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val itemKey: String,
  
    val itemName: String,
  
    val valueNumeric: Double?,
  
    val valueText: String?,
  
    val unit: String?,
  
    val referenceText: String?,
  
    val status: String?,
  
    val measuredAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp?,
  
    val exam: Exam,
  
    val user: User,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class Exam(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
  ) {
    
    
  }
      
        @kotlinx.serialization.Serializable
  public data class User(
  
    val id: String,
  
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetExamItemsByUser"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetExamItemsByUserQuery.ref(
  
    userId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetExamItemsByUserQuery.Data,
    GetExamItemsByUserQuery.Variables
  > =
  ref(
    
      GetExamItemsByUserQuery.Variables(
        userId=userId,
  
      )
    
  )

public suspend fun GetExamItemsByUserQuery.execute(

  
    
      userId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetExamItemsByUserQuery.Data,
    GetExamItemsByUserQuery.Variables
  > =
  ref(
    
      userId=userId,
  
    
  ).execute()


  public fun GetExamItemsByUserQuery.flow(
    
      userId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetExamItemsByUserQuery.Data> =
    ref(
        
          userId=userId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

