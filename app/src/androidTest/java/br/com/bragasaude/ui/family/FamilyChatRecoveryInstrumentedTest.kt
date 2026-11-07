package br.com.bragasaude.ui.family

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import br.com.bragasaude.data.local.FamilyMessageEntity
import br.com.bragasaude.data.local.Migrations
import br.com.bragasaude.data.local.security.FamilyE2eeManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class FamilyChatRecoveryInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun androidKeystoreRetainsActiveKeyAfterRevokedBindingArrives() {
        val id = UUID.randomUUID().toString()
        val patient = "qa-patient-$id"
        val caregiver = "qa-caregiver-$id"
        val p = FamilyE2eeManager(context)
        val c = FamilyE2eeManager(context)
        val old = FamilyE2eeManager(context)
        val pp = p.getOrCreatePublicKeyBase64(patient)
        val cp = c.getOrCreatePublicKeyBase64(caregiver)
        val revoked = old.getOrCreatePublicKeyBase64("qa-old-$id")
        for (manager in listOf(p,c)) {
            manager.registerBindingKeys(patient,caregiver,pp,cp,"ACTIVE")
            manager.registerBindingKeys(patient,caregiver,null,revoked,"REVOKED")
        }
        val outgoing = p.encryptForConversation(patient,patient,"Recado de teste")
        assertEquals("Recado de teste",c.decryptForConversation(caregiver,patient,outgoing,patient).text)
        val reply = c.encryptForConversation(caregiver,patient,"Resposta de teste")
        assertEquals("Resposta de teste",p.decryptForConversation(patient,patient,reply,caregiver).text)
    }

    @Test fun unreadableMessageHasVisibleExplanation() {
        val pending = FamilyMessageEntity("pending","qa","Teste","","CUSTOM",awaitingDecryption=true)
        compose.setContent { MaterialTheme { ChatBubble(pending,isFromMe=false) } }
        compose.onNodeWithText("Não foi possível abrir esta mensagem. Abra a conversa nos dois aparelhos para atualizar as chaves.").assertIsDisplayed()
    }

    @Test fun pendingMessageCannotDisplaySentOrReadTicks() {
        val outgoing = FamilyMessageEntity("pending","qa","Teste","Mensagem de teste","CUSTOM",pendingSync=true,isRead=true)
        compose.setContent { MaterialTheme { ChatBubble(outgoing,isFromMe=true) } }
        compose.onNodeWithContentDescription("Aguardando envio").assertIsDisplayed()
        compose.onNodeWithContentDescription("Visualizada").assertDoesNotExist()
        compose.onNodeWithContentDescription("Enviada").assertDoesNotExist()
    }

    @Test fun migrationPreservesExistingMessagesAndDefaultsToReadable() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(65) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE family_messages_local (id TEXT NOT NULL PRIMARY KEY, messageText TEXT NOT NULL)")
                        db.execSQL("INSERT INTO family_messages_local VALUES ('existing','Preservada')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        try {
            val db = helper.writableDatabase
            Migrations.MIGRATION_65_66.migrate(db)
            db.query("SELECT messageText, awaitingDecryption FROM family_messages_local WHERE id='existing'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Preservada",it.getString(0))
                assertEquals(0,it.getInt(1))
            }
        } finally { helper.close() }
    }
}
