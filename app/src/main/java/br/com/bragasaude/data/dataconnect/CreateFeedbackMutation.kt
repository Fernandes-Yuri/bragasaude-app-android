
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



public interface CreateFeedbackMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      CreateFeedbackMutation.Data,
      CreateFeedbackMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userEmail: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val userName: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val category: String,
  
    val title: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val message: String,
  
    val inputMethod: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val appVersion: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val deviceInfo: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val createdAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var userEmail: String?
        public var userName: String?
        public var category: String
        public var title: String?
        public var message: String
        public var inputMethod: String?
        public var appVersion: String?
        public var deviceInfo: String?
        public var createdAt: com.google.firebase.Timestamp
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          category: String,message: String,createdAt: com.google.firebase.Timestamp,
          block_: Builder.() -> Unit
        ): Variables {
          var userEmail: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var userName: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var category= category
            var title: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var message= message
            var inputMethod: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var appVersion: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var deviceInfo: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var createdAt= createdAt
            

          return object : Builder {
            override var userEmail: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { userEmail = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var userName: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { userName = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var category: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { category = value_ }
              
            override var title: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { title = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var message: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { message = value_ }
              
            override var inputMethod: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { inputMethod = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var appVersion: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { appVersion = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var deviceInfo: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { deviceInfo = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var createdAt: com.google.firebase.Timestamp
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { createdAt = value_ }
              
            
          }.apply(block_)
          .let {
            Variables(
              userEmail=userEmail,userName=userName,category=category,title=title,message=message,inputMethod=inputMethod,appVersion=appVersion,deviceInfo=deviceInfo,createdAt=createdAt,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val feedback_insert: FeedbackKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "CreateFeedback"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun CreateFeedbackMutation.ref(
  
    category: String,message: String,createdAt: com.google.firebase.Timestamp,

  
    block_: CreateFeedbackMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    CreateFeedbackMutation.Data,
    CreateFeedbackMutation.Variables
  > =
  ref(
    
      CreateFeedbackMutation.Variables.build(
        category=category,message=message,createdAt=createdAt,
  
    block_
      )
    
  )

public suspend fun CreateFeedbackMutation.execute(

  
    
      category: String,message: String,createdAt: com.google.firebase.Timestamp,

  
    block_: CreateFeedbackMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    CreateFeedbackMutation.Data,
    CreateFeedbackMutation.Variables
  > =
  ref(
    
      category=category,message=message,createdAt=createdAt,
  
    block_
    
  ).execute()


