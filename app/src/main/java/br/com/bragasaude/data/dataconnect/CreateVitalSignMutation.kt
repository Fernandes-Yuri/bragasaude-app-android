
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



public interface CreateVitalSignMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      CreateVitalSignMutation.Data,
      CreateVitalSignMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
    val systolicPressure: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val diastolicPressure: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val heartRate: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val glucoseLevel: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val glucoseType: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val hydrationMl: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val measuredAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var userId: String
        public var systolicPressure: Int?
        public var diastolicPressure: Int?
        public var heartRate: Int?
        public var glucoseLevel: Int?
        public var glucoseType: String?
        public var hydrationMl: Int?
        public var measuredAt: com.google.firebase.Timestamp
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          userId: String,measuredAt: com.google.firebase.Timestamp,
          block_: Builder.() -> Unit
        ): Variables {
          var userId= userId
            var systolicPressure: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var diastolicPressure: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var heartRate: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var glucoseLevel: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var glucoseType: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var hydrationMl: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var measuredAt= measuredAt
            

          return object : Builder {
            override var userId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { userId = value_ }
              
            override var systolicPressure: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { systolicPressure = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var diastolicPressure: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { diastolicPressure = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var heartRate: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { heartRate = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var glucoseLevel: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { glucoseLevel = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var glucoseType: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { glucoseType = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var hydrationMl: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { hydrationMl = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var measuredAt: com.google.firebase.Timestamp
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { measuredAt = value_ }
              
            
          }.apply(block_)
          .let {
            Variables(
              userId=userId,systolicPressure=systolicPressure,diastolicPressure=diastolicPressure,heartRate=heartRate,glucoseLevel=glucoseLevel,glucoseType=glucoseType,hydrationMl=hydrationMl,measuredAt=measuredAt,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val vitalSign_insert: VitalSignKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "CreateVitalSign"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun CreateVitalSignMutation.ref(
  
    userId: String,measuredAt: com.google.firebase.Timestamp,

  
    block_: CreateVitalSignMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    CreateVitalSignMutation.Data,
    CreateVitalSignMutation.Variables
  > =
  ref(
    
      CreateVitalSignMutation.Variables.build(
        userId=userId,measuredAt=measuredAt,
  
    block_
      )
    
  )

public suspend fun CreateVitalSignMutation.execute(

  
    
      userId: String,measuredAt: com.google.firebase.Timestamp,

  
    block_: CreateVitalSignMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    CreateVitalSignMutation.Data,
    CreateVitalSignMutation.Variables
  > =
  ref(
    
      userId=userId,measuredAt=measuredAt,
  
    block_
    
  ).execute()


