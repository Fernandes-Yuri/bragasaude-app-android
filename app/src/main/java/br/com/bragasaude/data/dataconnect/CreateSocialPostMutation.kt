
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



public interface CreateSocialPostMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      CreateSocialPostMutation.Data,
      CreateSocialPostMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
    val postType: String,
  
    val title: String,
  
    val description: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val relatedMilestoneId: com.google.firebase.dataconnect.OptionalVariable<@kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID?>,
  
    val createdAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var userId: String
        public var postType: String
        public var title: String
        public var description: String?
        public var relatedMilestoneId: java.util.UUID?
        public var createdAt: com.google.firebase.Timestamp
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          userId: String,postType: String,title: String,createdAt: com.google.firebase.Timestamp,
          block_: Builder.() -> Unit
        ): Variables {
          var userId= userId
            var postType= postType
            var title= title
            var description: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var relatedMilestoneId: com.google.firebase.dataconnect.OptionalVariable<java.util.UUID?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var createdAt= createdAt
            

          return object : Builder {
            override var userId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { userId = value_ }
              
            override var postType: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { postType = value_ }
              
            override var title: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { title = value_ }
              
            override var description: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { description = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var relatedMilestoneId: java.util.UUID?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { relatedMilestoneId = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var createdAt: com.google.firebase.Timestamp
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { createdAt = value_ }
              
            
          }.apply(block_)
          .let {
            Variables(
              userId=userId,postType=postType,title=title,description=description,relatedMilestoneId=relatedMilestoneId,createdAt=createdAt,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val socialPost_insert: SocialPostKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "CreateSocialPost"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun CreateSocialPostMutation.ref(
  
    userId: String,postType: String,title: String,createdAt: com.google.firebase.Timestamp,

  
    block_: CreateSocialPostMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    CreateSocialPostMutation.Data,
    CreateSocialPostMutation.Variables
  > =
  ref(
    
      CreateSocialPostMutation.Variables.build(
        userId=userId,postType=postType,title=title,createdAt=createdAt,
  
    block_
      )
    
  )

public suspend fun CreateSocialPostMutation.execute(

  
    
      userId: String,postType: String,title: String,createdAt: com.google.firebase.Timestamp,

  
    block_: CreateSocialPostMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    CreateSocialPostMutation.Data,
    CreateSocialPostMutation.Variables
  > =
  ref(
    
      userId=userId,postType=postType,title=title,createdAt=createdAt,
  
    block_
    
  ).execute()


