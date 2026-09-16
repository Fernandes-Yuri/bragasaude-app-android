
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



public interface CreateMedicationMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      CreateMedicationMutation.Data,
      CreateMedicationMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
    val name: String,
  
    val dosageMg: com.google.firebase.dataconnect.OptionalVariable<Double?>,
  
    val pillQuantity: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val scheduleTime: String,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var userId: String
        public var name: String
        public var dosageMg: Double?
        public var pillQuantity: Int?
        public var scheduleTime: String
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          userId: String,name: String,scheduleTime: String,
          block_: Builder.() -> Unit
        ): Variables {
          var userId= userId
            var name= name
            var dosageMg: com.google.firebase.dataconnect.OptionalVariable<Double?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var pillQuantity: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var scheduleTime= scheduleTime
            

          return object : Builder {
            override var userId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { userId = value_ }
              
            override var name: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { name = value_ }
              
            override var dosageMg: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { dosageMg = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var pillQuantity: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { pillQuantity = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var scheduleTime: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { scheduleTime = value_ }
              
            
          }.apply(block_)
          .let {
            Variables(
              userId=userId,name=name,dosageMg=dosageMg,pillQuantity=pillQuantity,scheduleTime=scheduleTime,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val medication_insert: MedicationKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "CreateMedication"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun CreateMedicationMutation.ref(
  
    userId: String,name: String,scheduleTime: String,

  
    block_: CreateMedicationMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    CreateMedicationMutation.Data,
    CreateMedicationMutation.Variables
  > =
  ref(
    
      CreateMedicationMutation.Variables.build(
        userId=userId,name=name,scheduleTime=scheduleTime,
  
    block_
      )
    
  )

public suspend fun CreateMedicationMutation.execute(

  
    
      userId: String,name: String,scheduleTime: String,

  
    block_: CreateMedicationMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    CreateMedicationMutation.Data,
    CreateMedicationMutation.Variables
  > =
  ref(
    
      userId=userId,name=name,scheduleTime=scheduleTime,
  
    block_
    
  ).execute()


