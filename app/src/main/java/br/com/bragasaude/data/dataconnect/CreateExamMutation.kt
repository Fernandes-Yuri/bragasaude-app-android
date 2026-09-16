
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



public interface CreateExamMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      CreateExamMutation.Data,
      CreateExamMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val userId: String,
  
    val title: String,
  
    val category: String,
  
    val examDate: com.google.firebase.dataconnect.LocalDate,
  
    val fileUrl: String,
  
    val aiExtractedData: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var userId: String
        public var title: String
        public var category: String
        public var examDate: com.google.firebase.dataconnect.LocalDate
        public var fileUrl: String
        public var aiExtractedData: String?
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          userId: String,title: String,category: String,examDate: com.google.firebase.dataconnect.LocalDate,fileUrl: String,
          block_: Builder.() -> Unit
        ): Variables {
          var userId= userId
            var title= title
            var category= category
            var examDate= examDate
            var fileUrl= fileUrl
            var aiExtractedData: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            

          return object : Builder {
            override var userId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { userId = value_ }
              
            override var title: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { title = value_ }
              
            override var category: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { category = value_ }
              
            override var examDate: com.google.firebase.dataconnect.LocalDate
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { examDate = value_ }
              
            override var fileUrl: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { fileUrl = value_ }
              
            override var aiExtractedData: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { aiExtractedData = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              userId=userId,title=title,category=category,examDate=examDate,fileUrl=fileUrl,aiExtractedData=aiExtractedData,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val exam_insert: ExamKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "CreateExam"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun CreateExamMutation.ref(
  
    userId: String,title: String,category: String,examDate: com.google.firebase.dataconnect.LocalDate,fileUrl: String,

  
    block_: CreateExamMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    CreateExamMutation.Data,
    CreateExamMutation.Variables
  > =
  ref(
    
      CreateExamMutation.Variables.build(
        userId=userId,title=title,category=category,examDate=examDate,fileUrl=fileUrl,
  
    block_
      )
    
  )

public suspend fun CreateExamMutation.execute(

  
    
      userId: String,title: String,category: String,examDate: com.google.firebase.dataconnect.LocalDate,fileUrl: String,

  
    block_: CreateExamMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    CreateExamMutation.Data,
    CreateExamMutation.Variables
  > =
  ref(
    
      userId=userId,title=title,category=category,examDate=examDate,fileUrl=fileUrl,
  
    block_
    
  ).execute()


