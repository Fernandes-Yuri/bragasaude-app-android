package br.com.bragasaude.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrações explícitas do Room — Braga Saúde.
 */
object Migrations {

    /**
     * 19 -> 20: adiciona as colunas da Fase 2 (reconciliação de atividade) em
     * `daily_metrics_local`, preservando os valores legados de `distanceMeters`.
     */
    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE daily_metrics_local ADD COLUMN distanceGpsMeters REAL NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE daily_metrics_local ADD COLUMN distanceStepsMeters REAL NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE daily_metrics_local ADD COLUMN distanceFinalMeters REAL NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE daily_metrics_local ADD COLUMN reliabilityScore REAL NOT NULL DEFAULT 0.5"
            )
            db.execSQL(
                "UPDATE daily_metrics_local SET distanceFinalMeters = distanceMeters WHERE distanceFinalMeters = 0"
            )
        }
    }

    /**
     * 20 -> 21 (Fase 3 — Gamificação): cria a tabela `xp_awards_local`, usada
     * pelo anti-farming (uma concessão de XP por tipo de ação por dia).
     */
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS xp_awards_local (" +
                    "userId TEXT NOT NULL, " +
                    "date TEXT NOT NULL, " +
                    "actionType TEXT NOT NULL, " +
                    "xp INTEGER NOT NULL, " +
                    "awardedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY (userId, date, actionType))"
            )
        }
    }

    /**
     * 21 -> 22 (Conformidade AHA/ADA/OMS 2026): adiciona colunas institucionais
     * na tabela `clinical_references_local`.
     */
    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE clinical_references_local ADD COLUMN institution TEXT DEFAULT 'AHA'"
            )
            db.execSQL(
                "ALTER TABLE clinical_references_local ADD COLUMN documentVersion TEXT DEFAULT '2026'"
            )
            db.execSQL(
                "ALTER TABLE clinical_references_local ADD COLUMN parameter TEXT"
            )
        }
    }

    /**
     * 22 -> 29 (Ponte Familiar & Modo Cuidador Inicial):
     * Cria as tabelas `family_bindings_local`, `family_messages_local` e `grocery_list_local`.
     */
    val MIGRATION_22_29 = object : Migration(22, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS family_bindings_local (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "patientUserId TEXT NOT NULL, " +
                    "caregiverUserId TEXT NOT NULL, " +
                    "caregiverName TEXT NOT NULL, " +
                    "caregiverRelation TEXT NOT NULL, " +
                    "connectionCode TEXT NOT NULL, " +
                    "status TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS family_messages_local (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "patientUserId TEXT NOT NULL, " +
                    "senderName TEXT NOT NULL, " +
                    "messageText TEXT NOT NULL, " +
                    "iconType TEXT NOT NULL, " +
                    "isRead INTEGER NOT NULL DEFAULT 0, " +
                    "sentAt INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS grocery_list_local (" +
                    "remoteId TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "weekStartDate TEXT NOT NULL, " +
                    "foodId TEXT NOT NULL, " +
                    "foodName TEXT NOT NULL, " +
                    "category TEXT NOT NULL, " +
                    "suggestedServingWeekGrams INTEGER NOT NULL, " +
                    "purchaseWeightGrams INTEGER NOT NULL, " +
                    "purchaseUnitText TEXT NOT NULL, " +
                    "estimatedPriceBrl REAL NOT NULL, " +
                    "isCheckedInPantry INTEGER NOT NULL DEFAULT 0, " +
                    "createdAt INTEGER NOT NULL)"
            )
        }
    }

    /**
     * 29 -> 30 (Módulo 3 — Segurança de Convites): adiciona a coluna
     * `expiresAt` na tabela `family_bindings_local` para validade de 7 dias.
     */
    val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE family_bindings_local ADD COLUMN expiresAt INTEGER NOT NULL DEFAULT 0"
            )
            // Define expiração de 7 dias para vínculos pendentes existentes
            db.execSQL(
                "UPDATE family_bindings_local SET expiresAt = createdAt + 604800000 WHERE status = 'PENDING' AND expiresAt = 0"
            )
            // Para vínculos ativos, define expiração como 0 (sem expiração)
            db.execSQL(
                "UPDATE family_bindings_local SET expiresAt = 0 WHERE status = 'ACTIVE' AND expiresAt = 0"
            )
        }
    }

    /**
     * 30 -> 31 (Módulo 4 — Sincronização em Nuvem): adiciona colunas de sync
     * (`remoteId` e `pendingSync`) na tabela `family_bindings_local` para o
     * contrato offline-first com o Firebase Data Connect.
     */
    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE family_bindings_local ADD COLUMN remoteId TEXT"
            )
            db.execSQL(
                "ALTER TABLE family_bindings_local ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * 31 -> 32 (Módulo 6 — Modos de Cuidador): adiciona `caregiverMode` na tabela
     * `profiles_local` para distinguir HÍBRIDO (acompanha familiar + própria saúde)
     * de VIEWER_ONLY (só acompanha familiar).
     */
    val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE profiles_local ADD COLUMN caregiverMode TEXT"
            )
        }
    }

    /**
     * 32 -> 33 (TASK-VIT-01 — Disparo de Alerta Remoto ao Cuidador): cria a tabela
     * `vital_alert_log_local` para controle de duplicacao de alerts criticos
     * e persistencia de registros de notificacoes locais aos cuidadores.
     */
    val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS vital_alert_log_local (" +
                    "caregiverUserId TEXT NOT NULL, " +
                    "alertType TEXT NOT NULL, " +
                    "patientUserId TEXT NOT NULL, " +
                    "alertTitle TEXT NOT NULL DEFAULT '', " +
                    "alertText TEXT NOT NULL DEFAULT '', " +
                    "lastSentAtMs INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (caregiverUserId, alertType))"
            )
        }
    }

    /**
     * 33 -> 34 (TASK-DB-03 — Suporte a Sincronizacao de Mensagens e Avatar):
     * - Adiciona `avatarIdentifier` e `customPhotoUri` em `profiles_local`
     * - Adiciona `senderUserId`, `remoteId` e `pendingSync` em `family_messages_local`
     */
    val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles_local ADD COLUMN avatarIdentifier TEXT")
            db.execSQL("ALTER TABLE profiles_local ADD COLUMN customPhotoUri TEXT")
            db.execSQL("ALTER TABLE family_messages_local ADD COLUMN senderUserId TEXT")
            db.execSQL("ALTER TABLE family_messages_local ADD COLUMN remoteId TEXT")
            db.execSQL("ALTER TABLE family_messages_local ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * 34 -> 35 (Auditoria, Telemetria e Visibilidade Social):
     * - Cria a tabela `audit_logs_local` para eventos e telemetria de auditoria
     * - Adiciona coluna `visibility` na tabela `social_posts_local`
     */
    val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS audit_logs_local (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "eventType TEXT NOT NULL, " +
                    "action TEXT NOT NULL, " +
                    "metadataJson TEXT, " +
                    "timestamp INTEGER NOT NULL, " +
                    "pendingSync INTEGER NOT NULL DEFAULT 1)"
            )
            db.execSQL(
                "ALTER TABLE social_posts_local ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PUBLIC'"
            )
        }
    }

    /** Lista todas as migrations para registrar no DatabaseModule. */
    val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles_local ADD COLUMN basicProfileComplete INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profiles_local ADD COLUMN selfCareComplete INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profiles_local ADD COLUMN selfCareSetupPending INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Preserva os registros existentes ao adicionar a confirmação por WhatsApp. */
    val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vital_signs_local ADD COLUMN confirmedVia TEXT")
            db.execSQL("ALTER TABLE vital_signs_local ADD COLUMN confirmationCode TEXT")
        }
    }

    val MIGRATION_37_38 = object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS wearable_readings_local (userId TEXT NOT NULL, recordKey TEXT NOT NULL, metric TEXT NOT NULL, value REAL NOT NULL, measuredAt INTEGER NOT NULL, sourcePackage TEXT NOT NULL, deviceModel TEXT, PRIMARY KEY(userId, recordKey))")
        }
    }

    val MIGRATION_38_39 = object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vital_signs_local ADD COLUMN oxygenSaturation INTEGER")
        }
    }

    val MIGRATION_39_40 = object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE medications_local ADD COLUMN scheduleTimes TEXT")
            db.execSQL("ALTER TABLE medication_logs_local ADD COLUMN scheduledFor TEXT")
        }
    }

    val MIGRATION_40_41 = object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS orb_conversations (id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, title TEXT NOT NULL, updatedAt INTEGER NOT NULL, messagesJson TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_orb_conversations_userId ON orb_conversations(userId)")
        }
    }

    val MIGRATION_41_42 = object : Migration(41, 42) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles_local ADD COLUMN phone TEXT")
        }
    }

    val MIGRATION_42_43 = object : Migration(42, 43) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS consultations_local (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "caregiverUserId TEXT, " +
                    "caregiverName TEXT, " +
                    "caregiverRelation TEXT, " +
                    "title TEXT NOT NULL, " +
                    "description TEXT, " +
                    "scheduledDate INTEGER NOT NULL, " +
                    "status TEXT NOT NULL DEFAULT 'SUGGESTED', " +
                    "createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "googleCalendarEventId TEXT, " +
                    "pendingSync INTEGER NOT NULL DEFAULT 0)"
            )
        }
    }

    /**
     * 43 -> 44 (D47 — Mensagens Familiares Efêmeras): adiciona `expiresAt`
     * (TTL individual de 24h) e `deletedAt` (tombstone de exclusão pelo
     * usuário) em `family_messages_local`. Mensagens legadas recebem o TTL
     * contado a partir do `sentAt` original — nenhuma ultrapassa 24h de vida.
     */
    val MIGRATION_43_44 = object : Migration(43, 44) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE family_messages_local ADD COLUMN expiresAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE family_messages_local ADD COLUMN deletedAt INTEGER")
            // Backfill: TTL de 24h a partir do envio original
            db.execSQL("UPDATE family_messages_local SET expiresAt = sentAt + 86400000 WHERE expiresAt = 0")
            // Higiene imediata: remove o que já nasceu expirado
            db.execSQL("DELETE FROM family_messages_local WHERE expiresAt > 0 AND expiresAt < " + System.currentTimeMillis())
        }
    }

    /**
     * 44 -> 45 (Vínculo de WhatsApp por TOTP): a conta guarda o segredo TOTP
     * "sob o capo" e o número de WhatsApp já vinculado. Arquitetura temporária
     * até a Meta aprovar o template de autenticação.
     */
    val MIGRATION_44_45 = object : Migration(44, 45) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles_local ADD COLUMN whatsappTotpSecret TEXT")
            db.execSQL("ALTER TABLE profiles_local ADD COLUMN whatsappPhone TEXT")
        }
    }

    val ALL = arrayOf(
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_29,
        MIGRATION_29_30,
        MIGRATION_30_31,
        MIGRATION_31_32,
        MIGRATION_32_33,
        MIGRATION_33_34,
        MIGRATION_34_35,
        MIGRATION_35_36,
        MIGRATION_36_37,
        MIGRATION_37_38,
        MIGRATION_38_39,
        MIGRATION_39_40,
        MIGRATION_40_41,
        MIGRATION_41_42,
        MIGRATION_42_43,
        MIGRATION_43_44,
        MIGRATION_44_45
    )
}
