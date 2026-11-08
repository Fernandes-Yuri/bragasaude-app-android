package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.FamilyBindingEntity
import br.com.bragasaude.data.local.FamilyDao
import br.com.bragasaude.data.local.MedicationDao
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.data.local.NotificationDao
import br.com.bragasaude.data.local.NotificationItemEntity
import br.com.bragasaude.data.local.NotificationSyncStateEntity
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.SocialBlockEntity
import br.com.bragasaude.data.local.SocialFeedDao
import br.com.bragasaude.data.local.SocialPostEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.ui.util.Screen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class NotificationCenterRepositoryTest {

    private lateinit var notificationDao: NotificationDao
    private lateinit var familyDao: FamilyDao
    private lateinit var profileDao: ProfileDao
    private lateinit var socialFeedDao: SocialFeedDao
    private lateinit var medicationDao: MedicationDao
    private lateinit var apiClient: BragaApiClient
    private lateinit var auth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private val clockMs = AtomicLong(1_700_000_000_000L)

    private lateinit var repository: NotificationCenterRepository

    @Before
    fun setUp() {
        notificationDao = mockk(relaxed = true)
        familyDao = mockk(relaxed = true)
        profileDao = mockk(relaxed = true)
        socialFeedDao = mockk(relaxed = true)
        medicationDao = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        mockUser = mockk(relaxed = true)

        every { auth.currentUser } returns mockUser
        every { mockUser.uid } returns "mariana-uid"

        repository = NotificationCenterRepository(
            notificationDao = notificationDao,
            familyDao = familyDao,
            profileDao = profileDao,
            socialFeedDao = socialFeedDao,
            medicationDao = medicationDao,
            apiClient = apiClient,
            auth = auth,
            clock = { clockMs.get() }
        )
    }

    private fun sampleItem(
        id: String = "notif-1",
        recipientId: String = "mariana-uid",
        revision: Long = 10L,
        kind: String = "FAMILY_MESSAGE",
        category: String = "FAMILY",
        resourceId: String = "fred-uid",
        patientId: String? = "fred-uid",
        actorId: String? = "fred-uid",
        isRead: Boolean = false,
        unreadCount: Int = 1,
        expiresAt: Long = 1_800_000_000_000L,
        deleted: Boolean = false
    ) = NotificationItemEntity(
        recipientId = recipientId,
        id = id,
        revision = revision,
        kind = kind,
        category = category,
        actorId = actorId,
        patientId = patientId,
        resourceType = "family_chat",
        resourceId = resourceId,
        occurredAt = 1_699_999_000_000L,
        expiresAt = expiresAt,
        count = 1,
        unreadCount = unreadCount,
        isRead = isRead,
        deleted = deleted
    )

    // ==================================================================
    // [1] BADGE FORMATTING & ACCESSIBILITY
    // ==================================================================
    @Test
    fun `badge formatting caps at 99 plus and nulls out zero`() {
        assertNull(NotificationCenterRepository.formatBadgeCount(0))
        assertNull(NotificationCenterRepository.formatBadgeCount(-1))
        assertEquals("1", NotificationCenterRepository.formatBadgeCount(1))
        assertEquals("99", NotificationCenterRepository.formatBadgeCount(99))
        assertEquals("99+", NotificationCenterRepository.formatBadgeCount(100))
        assertEquals("99+", NotificationCenterRepository.formatBadgeCount(999))
    }

    @Test
    fun `badge content description provides accessible Portuguese phrases`() {
        assertEquals("Notificações, sem avisos não lidos", NotificationCenterRepository.badgeContentDescription(0))
        assertEquals("Notificações, 1 aviso não lido", NotificationCenterRepository.badgeContentDescription(1))
        assertEquals("Notificações, 5 avisos não lidos", NotificationCenterRepository.badgeContentDescription(5))
        assertEquals("Notificações, mais de 99 avisos não lidos", NotificationCenterRepository.badgeContentDescription(120))
    }

    // ==================================================================
    // [2] SYNC: SNAPSHOT AND INCREMENTAL CHANGES
    // ==================================================================
    @Test
    fun `syncNotifications performs initial snapshot when sync state is absent`() = runBlocking {
        coEvery { notificationDao.getSyncState("mariana-uid") } returns null
        coEvery { notificationDao.getActiveItemsOneShot("mariana-uid", any()) } returns emptyList()

        val item1 = sampleItem("item-1", revision = 5L)
        val item2 = sampleItem("item-2", revision = 12L)
        val remotePage = BragaApiClient.RemoteNotificationPage(
            items = listOf(item1, item2),
            snapshotRevision = 12L,
            latestRevision = 12L,
            unreadCount = 2,
            hasMore = false,
            nextCursor = null
        )
        coEvery { apiClient.getNotifications("mariana-uid", cursor = null, limit = any()) } returns remotePage

        val res = repository.syncNotifications()
        assertTrue(res.isSuccess)

        coVerify(exactly = 1) { notificationDao.purgeExpired(clockMs.get()) }
        coVerify(exactly = 1) { notificationDao.applyRemoteItem(item1, null) }
        coVerify(exactly = 1) { notificationDao.applyRemoteItem(item2, null) }
        coVerify(exactly = 1) {
            notificationDao.upsertSyncState(match<NotificationSyncStateEntity> {
                it.recipientId == "mariana-uid" &&
                    it.cursorRevision == 12L &&
                    it.latestRevision == 12L &&
                    !it.hasMorePages
            })
        }
    }

    @Test
    fun `syncNotifications applies incremental changes when sync state exists`() = runBlocking {
        val existingState = NotificationSyncStateEntity(
            recipientId = "mariana-uid",
            cursorRevision = 20L,
            latestRevision = 20L,
            nextPageCursor = null,
            hasMorePages = false,
            lastSyncedAt = 1000L
        )
        coEvery { notificationDao.getSyncState("mariana-uid") } returns existingState

        val deltaItem = sampleItem("item-delta", revision = 25L)
        val changesPage = BragaApiClient.RemoteNotificationChangesPage(
            changes = listOf(BragaApiClient.RemoteNotificationChange(revision = 25L, item = deltaItem)),
            nextCursor = 25L,
            latestRevision = 25L,
            unreadCount = 1,
            hasMore = false
        )
        coEvery { apiClient.getNotificationChanges("mariana-uid", afterRevision = 20L, limit = any()) } returns changesPage

        val res = repository.syncNotifications()
        assertTrue(res.isSuccess)

        coVerify(exactly = 1) { notificationDao.applyRemoteItem(deltaItem, null) }
        coVerify(exactly = 1) {
            notificationDao.upsertSyncState(match<NotificationSyncStateEntity> {
                it.recipientId == "mariana-uid" &&
                    it.cursorRevision == 25L &&
                    it.latestRevision == 25L
            })
        }
    }

    // ==================================================================
    // [3] CHAT READ RECEIPT INDEPENDENCE (§10.222)
    // ==================================================================
    @Test
    fun `reading notification in center never emits chat read receipts`() = runBlocking {
        val item = sampleItem("chat-group-1", kind = "FAMILY_MESSAGE", revision = 15L)
        coEvery { notificationDao.markItemReadLocal("mariana-uid", "chat-group-1", any()) } returns item.copy(
            isRead = true,
            pendingReadThroughRevision = 15L
        )
        coEvery { apiClient.markNotificationRead("chat-group-1", 15L, false) } returns BragaApiClient.RemoteNotificationMutationResult(
            latestRevision = 15L,
            unreadCount = 0
        )

        val success = repository.markItemRead("chat-group-1")
        assertTrue(success)

        coVerify(exactly = 1) { apiClient.markNotificationRead("chat-group-1", 15L, false) }
        coVerify(exactly = 0) { apiClient.markFamilyMessagesRead(any(), any()) }
        coVerify(exactly = 1) { notificationDao.clearPendingReadAck("mariana-uid", "chat-group-1", 15L) }
    }

    @Test
    fun `reconcileRealChatRead clears unread chat notification when chat is actually read`() = runBlocking {
        repository.reconcileRealChatRead("fred-patient-id")
        coVerify(exactly = 1) {
            notificationDao.reconcileChatConversationReadLocal("mariana-uid", "fred-patient-id", clockMs.get())
        }
    }

    // ==================================================================
    // [4] MARK ALL READ THROUGH REVISION WATERMARK (§10.221)
    // ==================================================================
    @Test
    fun `markAllRead enforces watermark so newer concurrent events remain unread`() = runBlocking {
        val state = NotificationSyncStateEntity(
            recipientId = "mariana-uid",
            cursorRevision = 50L,
            latestRevision = 50L,
            nextPageCursor = null,
            hasMorePages = false,
            lastSyncedAt = 1000L
        )
        coEvery { notificationDao.getSyncState("mariana-uid") } returns state
        coEvery { notificationDao.getMaxLocalRevision("mariana-uid") } returns 45L
        coEvery { apiClient.markAllNotificationsRead(50L) } returns BragaApiClient.RemoteNotificationMutationResult(
            latestRevision = 50L,
            unreadCount = 0
        )

        val ok = repository.markAllRead(explicitThroughRevision = 50L)
        assertTrue(ok)

        coVerify(exactly = 1) {
            notificationDao.markAllReadThroughRevisionLocal("mariana-uid", 50L, clockMs.get())
        }
        coVerify(exactly = 1) { apiClient.markAllNotificationsRead(50L) }
    }

    // ==================================================================
    // [5] CURSOR PAGINATION LOAD MORE
    // ==================================================================
    @Test
    fun `loadMore appends next page without duplicating existing items`() = runBlocking {
        val state = NotificationSyncStateEntity(
            recipientId = "mariana-uid",
            cursorRevision = 30L,
            latestRevision = 30L,
            nextPageCursor = "opaque-cursor-p2",
            hasMorePages = true,
            lastSyncedAt = 1000L
        )
        coEvery { notificationDao.getSyncState("mariana-uid") } returns state

        val page2Item = sampleItem("p2-item-1", revision = 15L)
        val remotePage = BragaApiClient.RemoteNotificationPage(
            items = listOf(page2Item),
            snapshotRevision = 30L,
            latestRevision = 30L,
            unreadCount = 0,
            hasMore = false,
            nextCursor = null
        )
        coEvery {
            apiClient.getNotifications("mariana-uid", cursor = "opaque-cursor-p2", limit = any(), category = null)
        } returns remotePage

        val res = repository.loadMore()
        assertTrue(res.isSuccess)
        assertFalse("Última página atingida", res.getOrThrow())

        coVerify(exactly = 1) { notificationDao.applyRemoteItem(page2Item, null) }
        coVerify(exactly = 1) {
            notificationDao.upsertSyncState(match<NotificationSyncStateEntity> {
                it.recipientId == "mariana-uid" &&
                    !it.hasMorePages &&
                    it.nextPageCursor == null
            })
        }
    }

    // ==================================================================
    // [6] ACCOUNT ISOLATION AND NAVIGATION RESOLUTION (§10.231)
    // ==================================================================
    @Test
    fun `openItem rejects foreign session notification`() = runBlocking {
        val foreignItem = sampleItem("foreign-item", recipientId = "fred-uid")
        val result = repository.openItemAndResolveNavigation(foreignItem)
        assertTrue(result is NotificationNavigationResult.Unavailable)
        assertEquals("Este aviso pertence a outra sessão.", (result as NotificationNavigationResult.Unavailable).message)
    }

    @Test
    fun `openItem rejects expired or deleted notification`() = runBlocking {
        val expired = sampleItem("expired-item", expiresAt = clockMs.get() - 1000L)
        val result1 = repository.openItemAndResolveNavigation(expired)
        assertTrue(result1 is NotificationNavigationResult.Unavailable)

        val deleted = sampleItem("deleted-item", deleted = true)
        val result2 = repository.openItemAndResolveNavigation(deleted)
        assertTrue(result2 is NotificationNavigationResult.Unavailable)
    }

    @Test
    fun `openItem for revoked binding purges patient data and returns unavailable`() = runBlocking {
        val item = sampleItem(
            id = "binding-revoked-item",
            kind = "FAMILY_BINDING_REVOKED",
            patientId = "fred-uid"
        )
        coEvery { notificationDao.markItemReadLocal(any(), any(), any()) } returns item.copy(isRead = true)

        val result = repository.openItemAndResolveNavigation(item)
        assertTrue(result is NotificationNavigationResult.Unavailable)
        assertTrue((result as NotificationNavigationResult.Unavailable).message.contains("encerrado"))
        coVerify(exactly = 1) { notificationDao.deleteForRevokedPatient("mariana-uid", "fred-uid") }
    }

    @Test
    fun `openItem for deleted post returns unavailable without opening feed post`() = runBlocking {
        val item = sampleItem(
            id = "reaction-item",
            kind = "POST_REACTION",
            resourceId = "post-deleted-id",
            category = "COMMUNITY"
        )
        coEvery { notificationDao.markItemReadLocal(any(), any(), any()) } returns item.copy(isRead = true)
        coEvery { socialFeedDao.getBlocks("mariana-uid") } returns emptyList()
        coEvery { socialFeedDao.getPostById("post-deleted-id") } returns SocialPostEntity(
            id = "post-deleted-id",
            userId = "mariana-uid",
            title = "Removido",
            isVisible = false
        )

        val result = repository.openItemAndResolveNavigation(item)
        assertTrue(result is NotificationNavigationResult.Unavailable)
        assertTrue((result as NotificationNavigationResult.Unavailable).message.contains("removida"))
    }

    @Test
    fun `openItem resolves valid navigation targets`() = runBlocking {
        val chatItem = sampleItem("chat-item", kind = "FAMILY_MESSAGE", patientId = "fred-uid")
        coEvery { notificationDao.markItemReadLocal(any(), any(), any()) } returns chatItem.copy(isRead = true)
        coEvery { familyDao.existsActiveLink("fred-uid", "mariana-uid") } returns FamilyBindingEntity(
            id = "binding-active",
            patientUserId = "fred-uid",
            caregiverUserId = "mariana-uid",
            caregiverName = "Mariana",
            caregiverRelation = "Filha",
            connectionCode = "A1B2C3D4",
            status = "ACTIVE"
        )

        val chatNav = repository.openItemAndResolveNavigation(chatItem)
        assertEquals(NotificationNavigationResult.Navigate(Screen.FamilyChat(patientId = "fred-uid")), chatNav)

        val consultItem = sampleItem(
            id = "consult-item",
            kind = "CONSULTATION_CREATED",
            resourceId = "consult-123",
            patientId = "fred-uid"
        )
        coEvery { notificationDao.markItemReadLocal(any(), any(), any()) } returns consultItem.copy(isRead = true)
        val consultNav = repository.openItemAndResolveNavigation(consultItem)
        assertEquals(
            NotificationNavigationResult.Navigate(Screen.Consultations(patientId = "fred-uid", consultationId = "consult-123")),
            consultNav
        )

        val stockItem = sampleItem("stock-item", kind = "MEDICATION_LOW_STOCK", patientId = null)
        coEvery { notificationDao.markItemReadLocal(any(), any(), any()) } returns stockItem.copy(isRead = true)
        val stockNav = repository.openItemAndResolveNavigation(stockItem)
        assertEquals(NotificationNavigationResult.Navigate(Screen.MedicationStock), stockNav)
    }

    // ==================================================================
    // [7] HUMAN READABLE FORMATTING IN PORTUGUESE (toDisplayItem)
    // ==================================================================
    @Test
    fun `toDisplayItem resolves patient name and humanized titles without sensitive leaks`() = runBlocking {
        coEvery { profileDao.getProfileOneShot("fred-uid") } returns ProfileEntity(
            userId = "fred-uid",
            fullName = "José Fred"
        )

        val chatItem = sampleItem(
            id = "chat-1",
            kind = "FAMILY_MESSAGE",
            patientId = "fred-uid",
            actorId = "fred-uid"
        )
        val displayChat = repository.toDisplayItem(chatItem)
        assertEquals("Nova mensagem de José Fred", displayChat.title)
        assertTrue(displayChat.body.contains("José Fred"))
        assertEquals("Família", displayChat.categoryLabel)

        val medItem = sampleItem(
            id = "med-1",
            kind = "MEDICATION_LOW_STOCK",
            resourceId = "losartana-id",
            category = "CARE"
        )
        coEvery { medicationDao.getById("losartana-id") } returns MedicationEntity(
            id = "losartana-id",
            userId = "mariana-uid",
            name = "Losartana 50mg",
            currentUnits = 3
        )
        val displayMed = repository.toDisplayItem(medItem)
        assertEquals("Estoque baixo: Losartana 50mg", displayMed.title)
        assertEquals("Cuidado", displayMed.categoryLabel)
    }
}
