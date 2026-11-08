package com.lyricstage.media

/** One song in the player's own queue, as the media session reports it. */
data class QueueItem(
    /** The session's id for this item; what skipToQueueItem wants. */
    val id: Long,
    val title: String,
    val artist: String?,
)

/** The queue of the session being shown. */
data class QueueState(
    val items: List<QueueItem>,
    /** The queue item playing now, if the session says which. */
    val activeId: Long?,
    /** Whether the session accepts jumping to a queue item. */
    val canSkipToQueue: Boolean,
)
