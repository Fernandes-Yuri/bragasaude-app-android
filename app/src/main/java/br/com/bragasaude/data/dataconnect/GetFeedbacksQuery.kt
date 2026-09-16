
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


public interface GetFeedbacksQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      DefaultConnector,
      GetFeedbacksQuery.Data,
      GetFeedbacksQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val feedbacks: List<FeedbacksItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class FeedbacksItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val userId: String?,
  
    val userEmail: String?,
  
    val userName: String?,
  
    val category: String,
  
    val title: String?,
  
    val message: String,
  
    val inputMethod: String?,
  
    val appVersion: String?,
  
    val deviceInfo: String?,
  
    val status: String,
  
    val createdAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetFeedbacks"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetFeedbacksQuery.ref(
  
    userId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetFeedbacksQuery.Data,
    GetFeedbacksQuery.Variables
  > =
  ref(
    
      GetFeedbacksQuery.Variables(
        userId=userId,
  
      )
    
  )

public suspend fun GetFeedbacksQuery.execute(

  
    
      userId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetFeedbacksQuery.Data,
    GetFeedbacksQuery.Variables
  > =
  ref(
    
      userId=userId,
  
    
  ).execute()


  public fun GetFeedbacksQuery.flow(
    
      userId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetFeedbacksQuery.Data> =
    ref(
        
          userId=userId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

