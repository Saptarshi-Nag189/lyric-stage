package com.lyricstage.lyrics

import com.lyricstage.core.lyrics.LyricsKind
import com.lyricstage.core.lyrics.LyricsLookup
import com.lyricstage.core.lyrics.LyricsSources
import com.lyricstage.lyrics.db.LyricsDao
import com.lyricstage.lyrics.db.LyricsEntity
import com.lyricstage.lyrics.db.MissDao
import com.lyricstage.lyrics.db.MissEntity
import com.lyricstage.lyrics.db.OffsetDao
import com.lyricstage.lyrics.db.OffsetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class OfflineTest {
    private class FakeLyricsDao : LyricsDao {
        val rows = MutableStateFlow<Map<String, LyricsEntity>>(emptyMap())
        override fun observe(key: String): Flow<LyricsEntity?> = rows.map { it[key] }
        override suspend fun get(key: String) = rows.value[key]
        override suspend fun withPrefix(prefix: String) = rows.value.values.filter { it.trackKey.startsWith(prefix) }
        override fun count(): Flow<Int> = rows.map { it.size }
        override fun size(): Flow<Long> = rows.map { r -> r.values.sumOf { it.raw.length.toLong() } }
        override suspend fun clearFetched() { rows.value = rows.value.filterValues { it.source == "import" } }
        override suspend fun upsert(entity: LyricsEntity) { rows.value = rows.value + (entity.trackKey to entity) }
    }

    private class FakeOffsets : OffsetDao {
        override fun observe(key: String): Flow<Long?> = flowOf(null)
        override suspend fun upsert(entity: OffsetEntity) = Unit
    }

    private class FakeMisses : MissDao {
        val rows = HashMap<String, Long>()
        override suspend fun at(key: String) = rows[key]
        override suspend fun upsert(entity: MissEntity) { rows[entity.trackKey] = entity.at }
        override suspend fun delete(key: String) { rows.remove(key) }
        override suspend fun clear() = rows.clear()
    }

    private val calls = AtomicInteger()
    private val failing = LyricsSources(listOf(LyricsSources.Source { _, _, _, _ -> calls.incrementAndGet(); LyricsLookup.Unavailable }))
    private val track = TrackQuery("artist|song|200", "Song", "Artist", null, 200_000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test fun cachedSongShowsWithNoConnection() = runBlocking {
        val dao = FakeLyricsDao()
        dao.upsert(LyricsEntity(track.key, "[00:01.00]Hello there", true, false, "lrclib", "Song", "Artist", 200_000, 0))
        val repo = LyricsRepository(dao, FakeOffsets(), FakeMisses(), failing, MutableStateFlow(false), scope)
        val state = withTimeout(2_000) { repo.observe(track, MutableStateFlow(true)).first { it is LyricsState.Ready } }
        assertEquals(LyricsKind.LINE_SYNCED, (state as LyricsState.Ready).lyrics.kind)
        assertEquals(0, calls.get())
    }

    @Test fun uncachedSongOfflineSaysSo() = runBlocking {
        val repo = LyricsRepository(FakeLyricsDao(), FakeOffsets(), FakeMisses(), failing, MutableStateFlow(false), scope)
        val state = withTimeout(4_000) { repo.observe(track, MutableStateFlow(true)).first { it != LyricsState.Loading } }
        assertEquals(LyricsState.Offline, state)
    }

    @Test fun staleOfflineFlagDoesNotBlockLookups() = runBlocking {
        // the connection callback last said offline, but a fresh check says online
        val dao = FakeLyricsDao()
        val found = LyricsSources(listOf(LyricsSources.Source { _, _, _, _ -> calls.incrementAndGet(); LyricsLookup.Found(true, "[00:01.00]Hello there", false, "lrclib") }))
        val repo = LyricsRepository(dao, FakeOffsets(), FakeMisses(), found, MutableStateFlow(false), scope, checkOnline = { true })
        val state = withTimeout(4_000) { repo.observe(track, MutableStateFlow(true)).first { it != LyricsState.Loading } }
        assertTrue(state is LyricsState.Ready)
        assertEquals(1, calls.get())
    }

    @Test fun prefetchSkipsWhileOffline() = runBlocking {
        val repo = LyricsRepository(FakeLyricsDao(), FakeOffsets(), FakeMisses(), failing, MutableStateFlow(false), scope)
        repo.prefetch(track)
        assertEquals(0, calls.get())
    }

    @Test fun missIsRememberedAndSkippedByPrefetch() = runBlocking {
        val misses = FakeMisses()
        val none = LyricsSources(listOf(LyricsSources.Source { _, _, _, _ -> calls.incrementAndGet(); LyricsLookup.NotFound }))
        val repo = LyricsRepository(FakeLyricsDao(), FakeOffsets(), misses, none, MutableStateFlow(true), scope)
        repo.prefetch(track)
        assertEquals(1, calls.get())
        assertTrue(track.key in misses.rows)
        repo.prefetch(track)
        assertEquals(1, calls.get())
    }
}
