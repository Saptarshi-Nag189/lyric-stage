package com.lyricstage.media

/** One song in the player's own queue, as the media session reports it. */
data class QueueItem(
    /** The session's id for this item; what skipToQueueItem wants. */
    val id: Long,
    val title: String,
    val artist: String?,
    /** The item's own media id or URI, when the session publishes one; the second way to
     *  play it, for players that ignore queue jumps. */
    val mediaId: String?,
)

/** The queue of the session being shown. */
data class QueueState(
    val items: List<QueueItem>,
    /** The queue item playing now, if the session says which. */
    val activeId: Long?,
)
