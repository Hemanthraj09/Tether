package com.tether.app.domain

import com.tether.app.data.tracks.Track

/** Progress maths for tracks and group races (pure, unit-tested). */
object TrackProgress {

    data class SectionProgress(val name: String, val done: Int, val total: Int)

    data class MemberProgress(val uid: String, val done: Int, val total: Int) {
        val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    }

    fun done(track: Track, completedKeys: Set<String>): Int =
        track.items.count { it.key in completedKeys }

    fun sections(track: Track, completedKeys: Set<String>): List<SectionProgress> =
        track.sections.map { s ->
            SectionProgress(s.name, s.items.count { it.key in completedKeys }, s.items.size)
        }

    /** Members ranked by items done; ties keep [memberOrder]. */
    fun race(
        track: Track,
        completedByMember: Map<String, Set<String>>,
        memberOrder: List<String>
    ): List<MemberProgress> =
        memberOrder
            .map { uid -> MemberProgress(uid, done(track, completedByMember[uid].orEmpty()), track.itemCount) }
            .sortedByDescending { it.done }
}
