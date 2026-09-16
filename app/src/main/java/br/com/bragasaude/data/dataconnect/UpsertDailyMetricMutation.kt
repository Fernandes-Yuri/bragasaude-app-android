
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



public interface UpsertDailyMetricMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      UpsertDailyMetricMutation.Data,
      UpsertDailyMetricMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val userId: String,
  
    val date: com.google.firebase.dataconnect.LocalDate,
  
    val steps: Int,
  
    val distanceMeters: Double,
  
    val distanceGpsMeters: com.google.firebase.dataconnect.OptionalVariable<Double?>,
  
    val distanceStepsMeters: com.google.firebase.dataconnect.OptionalVariable<Double?>,
  
    val distanceFinalMeters: com.google.firebase.dataconnect.OptionalVariable<Double?>,
  
    val reliabilityScore: com.google.firebase.dataconnect.OptionalVariable<Double?>,
  
    val caloriesBurned: Double,
  
    val activeMinutes: Int,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var id: java.util.UUID
        public var userId: String
        public var date: com.google.firebase.dataconnect.LocalDate
        public var steps: Int
        public var distanceMeters: Double
        public var distanceGpsMeters: Double?
        public var distanceStepsMeters: Double?
        public var distanceFinalMeters: Double?
        public var reliabilityScore: Double?
        public var caloriesBurned: Double
        public var activeMinutes: Int
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          id: java.util.UUID,userId: String,date: com.google.firebase.dataconnect.LocalDate,steps: Int,distanceMeters: Double,caloriesBurned: Double,activeMinutes: Int,
          block_: Builder.() -> Unit
        ): Variables {
          var id= id
            var userId= userId
            var date= date
            var steps= steps
            var distanceMeters= distanceMeters
            var distanceGpsMeters: com.google.firebase.dataconnect.OptionalVariable<Double?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var distanceStepsMeters: com.google.firebase.dataconnect.OptionalVariable<Double?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var distanceFinalMeters: com.google.firebase.dataconnect.OptionalVariable<Double?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var reliabilityScore: com.google.firebase.dataconnect.OptionalVariable<Double?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var caloriesBurned= caloriesBurned
            var activeMinutes= activeMinutes
            

          return object : Builder {
            override var id: java.util.UUID
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { id = value_ }
              
            override var userId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { userId = value_ }
              
            override var date: com.google.firebase.dataconnect.LocalDate
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { date = value_ }
              
            override var steps: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { steps = value_ }
              
            override var distanceMeters: Double
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { distanceMeters = value_ }
              
            override var distanceGpsMeters: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { distanceGpsMeters = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var distanceStepsMeters: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { distanceStepsMeters = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var distanceFinalMeters: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { distanceFinalMeters = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var reliabilityScore: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { reliabilityScore = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var caloriesBurned: Double
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { caloriesBurned = value_ }
              
            override var activeMinutes: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { activeMinutes = value_ }
              
            
          }.apply(block_)
          .let {
            Variables(
              id=id,userId=userId,date=date,steps=steps,distanceMeters=distanceMeters,distanceGpsMeters=distanceGpsMeters,distanceStepsMeters=distanceStepsMeters,distanceFinalMeters=distanceFinalMeters,reliabilityScore=reliabilityScore,caloriesBurned=caloriesBurned,activeMinutes=activeMinutes,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val dailyMetric_upsert: DailyMetricKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpsertDailyMetric"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpsertDailyMetricMutation.ref(
  
    id: java.util.UUID,userId: String,date: com.google.firebase.dataconnect.LocalDate,steps: Int,distanceMeters: Double,caloriesBurned: Double,activeMinutes: Int,

  
    block_: UpsertDailyMetricMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    UpsertDailyMetricMutation.Data,
    UpsertDailyMetricMutation.Variables
  > =
  ref(
    
      UpsertDailyMetricMutation.Variables.build(
        id=id,userId=userId,date=date,steps=steps,distanceMeters=distanceMeters,caloriesBurned=caloriesBurned,activeMinutes=activeMinutes,
  
    block_
      )
    
  )

public suspend fun UpsertDailyMetricMutation.execute(

  
    
      id: java.util.UUID,userId: String,date: com.google.firebase.dataconnect.LocalDate,steps: Int,distanceMeters: Double,caloriesBurned: Double,activeMinutes: Int,

  
    block_: UpsertDailyMetricMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    UpsertDailyMetricMutation.Data,
    UpsertDailyMetricMutation.Variables
  > =
  ref(
    
      id=id,userId=userId,date=date,steps=steps,distanceMeters=distanceMeters,caloriesBurned=caloriesBurned,activeMinutes=activeMinutes,
  
    block_
    
  ).execute()


