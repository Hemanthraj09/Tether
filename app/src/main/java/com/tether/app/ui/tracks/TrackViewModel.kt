package com.tether.app.ui.tracks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.model.Group
import com.tether.app.data.repository.CompletionRepository
import com.tether.app.data.repository.LeetCodeRepository
import com.tether.app.data.repository.RaceRepository
import com.tether.app.data.snapshotFlow
import com.tether.app.data.tracks.Track
import com.tether.app.data.tracks.TrackItem
import com.tether.app.data.tracks.TrackRepository
import com.tether.app.domain.Completion
import com.tether.app.domain.CompletionSource
import com.tether.app.domain.TrackProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Row model for the track list. */
sealed class TrackRow {
    data class Header(val name: String, val done: Int, val total: Int) : TrackRow()
    data class Item(
        val item: TrackItem,
        val status: CompletionSource?,
        /** Other members who completed it: (initials, colour). */
        val solvers: List<Pair<String, String>>
    ) : TrackRow()
}

data class RaceStanding(
    val member: RaceRepository.Member,
    val progress: TrackProgress.MemberProgress,
    val isMe: Boolean
)

data class TrackUiState(
    val track: Track,
    val rows: List<TrackRow>,
    val myDone: Int,
    val standings: List<RaceStanding>
)

/**
 * A track, personal or as a group race (groupId set). Your completions come
 * from LeetCode sync or manual ticks; in race mode every member's progress is live.
 */
class TrackViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    val trackId: String = handle.get<String>("trackId") ?: ""
    val groupId: String = handle.get<String>("groupId") ?: ""

    private val me = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val completionRepository = CompletionRepository()
    private val raceRepository = RaceRepository()

    private val hideDone = MutableStateFlow(false)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val trackFlow: Flow<Track> = flow {
        TrackRepository(getApplication()).get(trackId)?.let { emit(it) }
    }

    private val memberUids: Flow<List<String>> =
        if (groupId.isEmpty()) flowOf(listOf(me))
        else FirebaseFirestore.getInstance().collection("groups").document(groupId)
            .snapshotFlow()
            .map { it?.toObject(Group::class.java)?.members ?: listOf(me) }
            .distinctUntilChanged()

    private val myCompletions: Flow<Map<String, Completion>> =
        if (me.isEmpty()) flowOf(emptyMap()) else completionRepository.observe(me)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val raceData: Flow<Pair<List<RaceRepository.Member>, Map<String, Set<String>>>> =
        if (groupId.isEmpty()) flowOf(emptyList<RaceRepository.Member>() to emptyMap())
        else memberUids.flatMapLatest { uids ->
            combine(raceRepository.observeMembers(uids), raceRepository.observeCompletedKeys(uids)) { m, c -> m to c }
        }

    val state: StateFlow<TrackUiState?> =
        combine(trackFlow, myCompletions, raceData, hideDone) { track, mine, (members, keysByMember), hide ->
            buildState(track, mine, members, keysByMember, hide)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hideCompleted: StateFlow<Boolean> = hideDone

    /** Members' LeetCode totals, read live from LeetCode (not from Firestore). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val leetCodeTotals: StateFlow<Map<String, Int>> = raceData
        .map { (members, _) -> members.mapNotNull { m -> m.leetcodeUsername?.let { m.uid to it } } }
        .distinctUntilChanged()
        .mapLatest { handles -> LeetCodeRepository(getApplication()).totals(handles) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setHideCompleted(hide: Boolean) {
        hideDone.value = hide
    }

    /** Tick / untick by hand. Verified (LeetCode) completions can't be unticked. */
    fun toggle(row: TrackRow.Item) {
        viewModelScope.launch {
            try {
                when (row.status) {
                    null -> completionRepository.markDone(row.item)
                    CompletionSource.SELF -> completionRepository.unmark(row.item.key)
                    CompletionSource.LEETCODE -> _messages.trySend("Verified on LeetCode ✓")
                }
            } catch (e: Exception) {
                _messages.trySend(e.message ?: "Couldn't update")
            }
        }
    }

    private fun buildState(
        track: Track,
        mine: Map<String, Completion>,
        members: List<RaceRepository.Member>,
        keysByMember: Map<String, Set<String>>,
        hide: Boolean
    ): TrackUiState {
        val myKeys = mine.keys
        val others = members.filter { it.uid != me }
        val rows = ArrayList<TrackRow>(track.itemCount + track.sections.size)
        for (section in track.sections) {
            val sectionRows = section.items
                .filter { !hide || it.key !in myKeys }
                .map { item ->
                    TrackRow.Item(
                        item = item,
                        status = mine[item.key]?.source,
                        solvers = others
                            .filter { item.key in keysByMember[it.uid].orEmpty() }
                            .map { it.initials to it.avatarColorHex }
                    )
                }
            if (sectionRows.isEmpty()) continue
            rows.add(TrackRow.Header(section.name, section.items.count { it.key in myKeys }, section.items.size))
            rows.addAll(sectionRows)
        }

        val standings = if (groupId.isEmpty()) emptyList() else {
            val byUid = members.associateBy { it.uid }
            TrackProgress.race(track, keysByMember, members.map { it.uid })
                .mapNotNull { p -> byUid[p.uid]?.let { RaceStanding(it, p, it.uid == me) } }
        }

        return TrackUiState(track, rows, TrackProgress.done(track, myKeys), standings)
    }
}
