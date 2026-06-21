package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.R

object AvatarHelper {
    val avatarDrawables = listOf(
        R.drawable.avatar_1,
        R.drawable.avatar_2,
        R.drawable.avatar_3,
        R.drawable.avatar_4,
        R.drawable.avatar_5,
        R.drawable.avatar_6,
        R.drawable.avatar_7,
        R.drawable.avatar_8,
        R.drawable.avatar_9,
        R.drawable.avatar_10
    )

    /**
     * Resolves an "avatar://INDEX" URI to a drawable resource ID.
     * Returns null if the URI is not an avatar URI, index is out of range, or index is 0
     * (index 0 was a gendered default; null falls through to the initials display).
     */
    fun resolveAvatarDrawable(uri: String): Int? {
        if (!uri.startsWith("avatar://")) return null
        val index = uri.removePrefix("avatar://").toIntOrNull() ?: return null
        if (index == 0) return null
        return avatarDrawables.getOrNull(index)
    }
}
