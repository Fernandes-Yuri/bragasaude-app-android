package br.com.bragasaude.domain

import br.com.bragasaude.data.local.SocialPostEntity
import org.junit.Assert.*
import org.junit.Test

class SocialFeedAccessTest {
    private val post = SocialPostEntity(id="post", userId="author", postType="milestone", title="Meta", visibility="FAMILY")
    @Test fun cachedFamilyPostDisappearsWhenLinkIsRevokedOrAccountChanges() {
        assertTrue(canDisplayCachedSocialPost(post, "caregiver", setOf("author")))
        assertFalse(canDisplayCachedSocialPost(post, "caregiver", emptySet()))
        assertFalse(canDisplayCachedSocialPost(post, "another-account", emptySet()))
        assertTrue(canDisplayCachedSocialPost(post, "author", emptySet()))
    }
    @Test fun blankAuthorCannotAppearAsValidCommunityPost() {
        assertFalse(canDisplayCachedSocialPost(post.copy(userId = "", visibility = "PUBLIC"), "u", emptySet()))
    }
    @Test fun unknownVisibilityIsNeverTreatedAsPublic() {
        assertFalse(canDisplayCachedSocialPost(post.copy(visibility="UNKNOWN"), "caregiver", setOf("author")))
        assertTrue(canDisplayCachedSocialPost(post.copy(visibility="PUBLIC"), "caregiver", emptySet()))
        assertFalse(canDisplayCachedSocialPost(post.copy(visibility="PUBLIC", isVisible=false), "caregiver", emptySet()))
    }
}
