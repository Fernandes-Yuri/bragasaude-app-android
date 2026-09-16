
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



public interface UpsertProfileMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      DefaultConnector,
      UpsertProfileMutation.Data,
      UpsertProfileMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
    val fullName: String,
  
    val birthDate: com.google.firebase.dataconnect.OptionalVariable<com.google.firebase.dataconnect.LocalDate?>,
  
    val gender: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val height: com.google.firebase.dataconnect.OptionalVariable<Double?>,
  
    val weight: com.google.firebase.dataconnect.OptionalVariable<Double?>,
  
    val isSmoker: com.google.firebase.dataconnect.OptionalVariable<Boolean?>,
  
    val hasDiabetes: com.google.firebase.dataconnect.OptionalVariable<Boolean?>,
  
    val hasHypertension: com.google.firebase.dataconnect.OptionalVariable<Boolean?>,
  
    val hasThyroidIssue: com.google.firebase.dataconnect.OptionalVariable<Boolean?>,
  
    val hasRenalIssue: com.google.firebase.dataconnect.OptionalVariable<Boolean?>,
  
    val hasBoneIssue: com.google.firebase.dataconnect.OptionalVariable<Boolean?>,
  
    val hasMuscularIssue: com.google.firebase.dataconnect.OptionalVariable<Boolean?>,
  
    val hydrationTargetMl: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val dailyCalorieTarget: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val stepGoal: com.google.firebase.dataconnect.OptionalVariable<Int?>,
  
    val weightGoal: com.google.firebase.dataconnect.OptionalVariable<Double?>,
  
    val sleepStartTime: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val sleepEndTime: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val emergencyContactName: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val emergencyContactRelation: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val emergencyContactPhone: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val consentAcceptedAt: com.google.firebase.dataconnect.OptionalVariable<@kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp?>,
  
    val notificationsEnabled: com.google.firebase.dataconnect.OptionalVariable<Boolean?>,
  
    val locationEnabled: com.google.firebase.dataconnect.OptionalVariable<Boolean?>,
  
    val showInFeed: com.google.firebase.dataconnect.OptionalVariable<Boolean?>,
  
    val userRole: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val caregiverMode: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val avatarIdentifier: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var id: String
        public var fullName: String
        public var birthDate: com.google.firebase.dataconnect.LocalDate?
        public var gender: String?
        public var height: Double?
        public var weight: Double?
        public var isSmoker: Boolean?
        public var hasDiabetes: Boolean?
        public var hasHypertension: Boolean?
        public var hasThyroidIssue: Boolean?
        public var hasRenalIssue: Boolean?
        public var hasBoneIssue: Boolean?
        public var hasMuscularIssue: Boolean?
        public var hydrationTargetMl: Int?
        public var dailyCalorieTarget: Int?
        public var stepGoal: Int?
        public var weightGoal: Double?
        public var sleepStartTime: String?
        public var sleepEndTime: String?
        public var emergencyContactName: String?
        public var emergencyContactRelation: String?
        public var emergencyContactPhone: String?
        public var consentAcceptedAt: com.google.firebase.Timestamp?
        public var notificationsEnabled: Boolean?
        public var locationEnabled: Boolean?
        public var showInFeed: Boolean?
        public var userRole: String?
        public var caregiverMode: String?
        public var avatarIdentifier: String?
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          id: String,fullName: String,
          block_: Builder.() -> Unit
        ): Variables {
          var id= id
            var fullName= fullName
            var birthDate: com.google.firebase.dataconnect.OptionalVariable<com.google.firebase.dataconnect.LocalDate?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var gender: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var height: com.google.firebase.dataconnect.OptionalVariable<Double?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var weight: com.google.firebase.dataconnect.OptionalVariable<Double?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var isSmoker: com.google.firebase.dataconnect.OptionalVariable<Boolean?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var hasDiabetes: com.google.firebase.dataconnect.OptionalVariable<Boolean?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var hasHypertension: com.google.firebase.dataconnect.OptionalVariable<Boolean?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var hasThyroidIssue: com.google.firebase.dataconnect.OptionalVariable<Boolean?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var hasRenalIssue: com.google.firebase.dataconnect.OptionalVariable<Boolean?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var hasBoneIssue: com.google.firebase.dataconnect.OptionalVariable<Boolean?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var hasMuscularIssue: com.google.firebase.dataconnect.OptionalVariable<Boolean?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var hydrationTargetMl: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var dailyCalorieTarget: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var stepGoal: com.google.firebase.dataconnect.OptionalVariable<Int?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var weightGoal: com.google.firebase.dataconnect.OptionalVariable<Double?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var sleepStartTime: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var sleepEndTime: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var emergencyContactName: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var emergencyContactRelation: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var emergencyContactPhone: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var consentAcceptedAt: com.google.firebase.dataconnect.OptionalVariable<com.google.firebase.Timestamp?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var notificationsEnabled: com.google.firebase.dataconnect.OptionalVariable<Boolean?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var locationEnabled: com.google.firebase.dataconnect.OptionalVariable<Boolean?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var showInFeed: com.google.firebase.dataconnect.OptionalVariable<Boolean?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var userRole: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var caregiverMode: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var avatarIdentifier: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            

          return object : Builder {
            override var id: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { id = value_ }
              
            override var fullName: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { fullName = value_ }
              
            override var birthDate: com.google.firebase.dataconnect.LocalDate?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { birthDate = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var gender: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { gender = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var height: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { height = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var weight: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { weight = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var isSmoker: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { isSmoker = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var hasDiabetes: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { hasDiabetes = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var hasHypertension: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { hasHypertension = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var hasThyroidIssue: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { hasThyroidIssue = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var hasRenalIssue: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { hasRenalIssue = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var hasBoneIssue: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { hasBoneIssue = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var hasMuscularIssue: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { hasMuscularIssue = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var hydrationTargetMl: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { hydrationTargetMl = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var dailyCalorieTarget: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { dailyCalorieTarget = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var stepGoal: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { stepGoal = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var weightGoal: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { weightGoal = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var sleepStartTime: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { sleepStartTime = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var sleepEndTime: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { sleepEndTime = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var emergencyContactName: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { emergencyContactName = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var emergencyContactRelation: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { emergencyContactRelation = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var emergencyContactPhone: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { emergencyContactPhone = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var consentAcceptedAt: com.google.firebase.Timestamp?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { consentAcceptedAt = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var notificationsEnabled: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { notificationsEnabled = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var locationEnabled: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { locationEnabled = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var showInFeed: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { showInFeed = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var userRole: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { userRole = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var caregiverMode: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { caregiverMode = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var avatarIdentifier: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { avatarIdentifier = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              id=id,fullName=fullName,birthDate=birthDate,gender=gender,height=height,weight=weight,isSmoker=isSmoker,hasDiabetes=hasDiabetes,hasHypertension=hasHypertension,hasThyroidIssue=hasThyroidIssue,hasRenalIssue=hasRenalIssue,hasBoneIssue=hasBoneIssue,hasMuscularIssue=hasMuscularIssue,hydrationTargetMl=hydrationTargetMl,dailyCalorieTarget=dailyCalorieTarget,stepGoal=stepGoal,weightGoal=weightGoal,sleepStartTime=sleepStartTime,sleepEndTime=sleepEndTime,emergencyContactName=emergencyContactName,emergencyContactRelation=emergencyContactRelation,emergencyContactPhone=emergencyContactPhone,consentAcceptedAt=consentAcceptedAt,notificationsEnabled=notificationsEnabled,locationEnabled=locationEnabled,showInFeed=showInFeed,userRole=userRole,caregiverMode=caregiverMode,avatarIdentifier=avatarIdentifier,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val profile_upsert: ProfileKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpsertProfile"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpsertProfileMutation.ref(
  
    id: String,fullName: String,

  
    block_: UpsertProfileMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    UpsertProfileMutation.Data,
    UpsertProfileMutation.Variables
  > =
  ref(
    
      UpsertProfileMutation.Variables.build(
        id=id,fullName=fullName,
  
    block_
      )
    
  )

public suspend fun UpsertProfileMutation.execute(

  
    
      id: String,fullName: String,

  
    block_: UpsertProfileMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    UpsertProfileMutation.Data,
    UpsertProfileMutation.Variables
  > =
  ref(
    
      id=id,fullName=fullName,
  
    block_
    
  ).execute()


