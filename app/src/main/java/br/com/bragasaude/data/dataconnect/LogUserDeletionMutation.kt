
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



public interface LogUserDeletionMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      LogUserDeletionMutation.Data,
      LogUserDeletionMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
    val deletedAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
    val ipAddress: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val tablesAffected: String,
  
    val storageFilesCleaned: Int,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var userId: String
        public var deletedAt: com.google.firebase.Timestamp
        public var ipAddress: String?
        public var tablesAffected: String
        public var storageFilesCleaned: Int
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          userId: String,deletedAt: com.google.firebase.Timestamp,tablesAffected: String,storageFilesCleaned: Int,
          block_: Builder.() -> Unit
        ): Variables {
          var userId= userId
            var deletedAt= deletedAt
            var ipAddress: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var tablesAffected= tablesAffected
            var storageFilesCleaned= storageFilesCleaned
            

          return object : Builder {
            override var userId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { userId = value_ }
              
            override var deletedAt: com.google.firebase.Timestamp
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { deletedAt = value_ }
              
            override var ipAddress: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { ipAddress = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var tablesAffected: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { tablesAffected = value_ }
              
            override var storageFilesCleaned: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { storageFilesCleaned = value_ }
              
            
          }.apply(block_)
          .let {
            Variables(
              userId=userId,deletedAt=deletedAt,ipAddress=ipAddress,tablesAffected=tablesAffected,storageFilesCleaned=storageFilesCleaned,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val userDeletionLog_insert: UserDeletionLogKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "LogUserDeletion"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun LogUserDeletionMutation.ref(
  
    userId: String,deletedAt: com.google.firebase.Timestamp,tablesAffected: String,storageFilesCleaned: Int,

  
    block_: LogUserDeletionMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    LogUserDeletionMutation.Data,
    LogUserDeletionMutation.Variables
  > =
  ref(
    
      LogUserDeletionMutation.Variables.build(
        userId=userId,deletedAt=deletedAt,tablesAffected=tablesAffected,storageFilesCleaned=storageFilesCleaned,
  
    block_
      )
    
  )

public suspend fun LogUserDeletionMutation.execute(

  
    
      userId: String,deletedAt: com.google.firebase.Timestamp,tablesAffected: String,storageFilesCleaned: Int,

  
    block_: LogUserDeletionMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    LogUserDeletionMutation.Data,
    LogUserDeletionMutation.Variables
  > =
  ref(
    
      userId=userId,deletedAt=deletedAt,tablesAffected=tablesAffected,storageFilesCleaned=storageFilesCleaned,
  
    block_
    
  ).execute()


