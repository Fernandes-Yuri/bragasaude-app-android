package br.com.bragasaude.domain

import br.com.bragasaude.data.local.SocialPostEntity

/** A cópia local não pode ampliar a visibilidade entregue pelo servidor. */
fun canDisplayCachedSocialPost(post: SocialPostEntity, userId: String, familyAuthors: Set<String>): Boolean =
    post.isVisible && when (post.visibility) {
        "PUBLIC" -> true
        "FAMILY" -> post.userId == userId || post.userId in familyAuthors
        else -> post.userId == userId
    }
