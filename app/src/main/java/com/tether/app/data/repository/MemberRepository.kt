package com.tether.app.data.repository

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.snapshotFlow
import com.tether.app.domain.CompletionEntry
import com.tether.app.utils.Formatters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Group members' public profiles (names, LeetCode handles) and completion indexes. */
class MemberRepository {

    data class Member(
        val uid: String,
        val name: String,
        val initials: String,
        val avatarColorHex: String,
        val leetcodeUsername: String?
    )

    private val firestore = FirebaseFirestore.getInstance()
    private val completions = CompletionRepository()

    fun observeMembers(memberUids: List<String>): Flow<List<Member>> {
        if (memberUids.isEmpty()) return flowOf(emptyList())
        val chunks = memberUids.chunked(10).map { chunk ->
            firestore.collection("users").whereIn(FieldPath.documentId(), chunk).snapshotFlow()
                .map { snapshot ->
                    snapshot?.documents?.associate { doc ->
                        val name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: "Unknown"
                        doc.id to Member(
                            uid = doc.id,
                            name = name,
                            initials = Formatters.initials(name),
                            avatarColorHex = Formatters.avatarColor(doc.id),
                            leetcodeUsername = doc.getString(LeetCodeRepository.FIELD_USERNAME)
                        )
                    } ?: emptyMap()
                }
        }
        return combine(chunks) { maps ->
            val byId = maps.fold(emptyMap<String, Member>()) { acc, m -> acc + m }
            memberUids.map { uid ->
                byId[uid] ?: Member(uid, "Unknown", "U", Formatters.avatarColor(uid), null)
            }
        }
    }

    /** Each member's completion index (uid → key → entry): one document per member. */
    fun observeCompletions(memberUids: List<String>): Flow<Map<String, Map<String, CompletionEntry>>> =
        completions.observeMany(memberUids)
}
