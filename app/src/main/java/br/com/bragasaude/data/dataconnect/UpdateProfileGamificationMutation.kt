
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



public interface UpdateProfileGamificationMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      UpdateProfileGamificationMutation.Data,
      UpdateProfileGamificationMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
    val currentXp: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val totalXp: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val currentLevel: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val currentStreak: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val lastXpAt: com.google.firebase.dataconnect.OptionalVariable<@kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp?>,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var userId: String
        public var currentXp: Int?
        public var totalXp: Int?
        public var currentLevel: Int?
        public var currentStreak: Int?
        public var lastXpAt: com.google.firebase.Timestamp?
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          userId: String,
          block_: Builder.() -> Unit
        ): Variables {
          var userId= userId
            var currentXp: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var totalXp: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var currentLevel: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var currentStreak: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var lastXpAt: com.google.firebase.dataconnect.OptionalVariable<com.google.firebase.Timestamp?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            

          return object : Builder {
            override var userId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { userId = value_ }
              
            override var currentXp: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { currentXp = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var totalXp: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { totalXp = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var currentLevel: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { currentLevel = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var currentStreak: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { currentStreak = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var lastXpAt: com.google.firebase.Timestamp?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { lastXpAt = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              userId=userId,currentXp=currentXp,totalXp=totalXp,currentLevel=currentLevel,currentStreak=currentStreak,lastXpAt=lastXpAt,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val profile_update: ProfileKey?,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpdateProfileGamification"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpdateProfileGamificationMutation.ref(
  
    userId: String,

  
    block_: UpdateProfileGamificationMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    UpdateProfileGamificationMutation.Data,
    UpdateProfileGamificationMutation.Variables
  > =
  ref(
    
      UpdateProfileGamificationMutation.Variables.build(
        userId=userId,
  
    block_
      )
    
  )

public suspend fun UpdateProfileGamificationMutation.execute(

  
    
      userId: String,

  
    block_: UpdateProfileGamificationMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    UpdateProfileGamificationMutation.Data,
    UpdateProfileGamificationMutation.Variables
  > =
  ref(
    
      userId=userId,
  
    block_
    
  ).execute()


