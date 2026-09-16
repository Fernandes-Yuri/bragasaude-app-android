
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



public interface CreateExamItemMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      CreateExamItemMutation.Data,
      CreateExamItemMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val examId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
  
    val userId: String,
  
    val itemKey: String,
  
    val itemName: String,
  
    val valueNumeric: com.google.firebase.dataconnect.OptionalVariable<Double?>,
  
    val valueText: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val unit: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val referenceText: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val status: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val measuredAt: com.google.firebase.dataconnect.OptionalVariable<@kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp?>,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var examId: java.util.UUID
        public var userId: String
        public var itemKey: String
        public var itemName: String
        public var valueNumeric: Double?
        public var valueText: String?
        public var unit: String?
        public var referenceText: String?
        public var status: String?
        public var measuredAt: com.google.firebase.Timestamp?
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          examId: java.util.UUID,userId: String,itemKey: String,itemName: String,
          block_: Builder.() -> Unit
        ): Variables {
          var examId= examId
            var userId= userId
            var itemKey= itemKey
            var itemName= itemName
            var valueNumeric: com.google.firebase.dataconnect.OptionalVariable<Double?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var valueText: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var unit: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var referenceText: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var status: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var measuredAt: com.google.firebase.dataconnect.OptionalVariable<com.google.firebase.Timestamp?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            

          return object : Builder {
            override var examId: java.util.UUID
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { examId = value_ }
              
            override var userId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { userId = value_ }
              
            override var itemKey: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { itemKey = value_ }
              
            override var itemName: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { itemName = value_ }
              
            override var valueNumeric: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { valueNumeric = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var valueText: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { valueText = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var unit: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { unit = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var referenceText: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { referenceText = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var status: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { status = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var measuredAt: com.google.firebase.Timestamp?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { measuredAt = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              examId=examId,userId=userId,itemKey=itemKey,itemName=itemName,valueNumeric=valueNumeric,valueText=valueText,unit=unit,referenceText=referenceText,status=status,measuredAt=measuredAt,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val examItem_insert: ExamItemKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "CreateExamItem"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun CreateExamItemMutation.ref(
  
    examId: java.util.UUID,userId: String,itemKey: String,itemName: String,

  
    block_: CreateExamItemMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    CreateExamItemMutation.Data,
    CreateExamItemMutation.Variables
  > =
  ref(
    
      CreateExamItemMutation.Variables.build(
        examId=examId,userId=userId,itemKey=itemKey,itemName=itemName,
  
    block_
      )
    
  )

public suspend fun CreateExamItemMutation.execute(

  
    
      examId: java.util.UUID,userId: String,itemKey: String,itemName: String,

  
    block_: CreateExamItemMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    CreateExamItemMutation.Data,
    CreateExamItemMutation.Variables
  > =
  ref(
    
      examId=examId,userId=userId,itemKey=itemKey,itemName=itemName,
  
    block_
    
  ).execute()


