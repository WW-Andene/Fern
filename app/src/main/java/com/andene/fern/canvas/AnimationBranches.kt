package com.andene.fern.canvas

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/*
 * Branches and merges for animations (roadmap item 153), after version control: try an
 * alternative take of an animation on a branch, switch between takes, and merge one back -
 * frame by frame, a three-way merge against the frame as it was when the branch was made.
 */

/** An animation's content: its strokes and bones (each on its frame), the X-sheet holds and the frame count. */
class AnimationSnapshot(val strokes: List<Stroke>, val bones: List<Bone>, val holds: Map<Int, Int>, val frameCount: Int) {
    /** Frame [frame]'s content, comparable between snapshots: its strokes' bytes, its bones and its hold. */
    fun frameKey(frame: Int): FrameKey {
        val out = ByteArrayOutputStream()
        StrokeCodec.write(DataOutputStream(out), strokes.filter { it.animFrame == frame })
        return FrameKey(out.toByteArray(), bones.filter { it.animFrame == frame }.toSet(), holds[frame] ?: 1, frame < frameCount)
    }
}

/** One frame's content in a comparable form; frames past the end compare as absent. */
class FrameKey(private val strokes: ByteArray, private val bones: Set<Bone>, private val hold: Int, private val exists: Boolean) {
    override fun equals(other: Any?) = other is FrameKey && exists == other.exists && hold == other.hold && bones == other.bones && strokes.contentEquals(other.strokes)
    override fun hashCode() = strokes.contentHashCode() * 31 + bones.hashCode()
}

/**
 * A branch: its [name], the content it started from ([base]) and its latest content ([head]).
 * The branch being worked on keeps its latest content in the document itself; its [head] is
 * brought up to date when switching away.
 */
class AnimationBranch(val id: String, val name: String, val base: AnimationSnapshot, val head: AnimationSnapshot)

/** Which side wins a frame both branches changed since they parted. */
enum class MergePreference { OURS, THEIRS }

/** What a merge did: the frames it took from the other branch, and those both had changed ([conflicts]), however they were settled. */
data class MergeReport(val taken: List<Int>, val conflicts: List<Int>)

/**
 * Frame by frame, which frames of [theirs] a merge into [ours] takes, given their common
 * starting point [base]: frames only they changed, and frames both changed when [preference]
 * is THEIRS. Frames only we changed, or nobody changed, stay ours.
 */
fun planMerge(base: AnimationSnapshot, ours: AnimationSnapshot, theirs: AnimationSnapshot, preference: MergePreference): MergeReport {
    val taken = ArrayList<Int>()
    val conflicts = ArrayList<Int>()
    for (frame in 0 until maxOf(ours.frameCount, theirs.frameCount)) {
        val b = base.frameKey(frame)
        val o = ours.frameKey(frame)
        val t = theirs.frameKey(frame)
        val theyChanged = t != b
        val weChanged = o != b
        if (!theyChanged || t == o) continue
        if (weChanged) {
            conflicts.add(frame)
            if (preference == MergePreference.THEIRS) taken.add(frame)
        } else {
            taken.add(frame)
        }
    }
    return MergeReport(taken, conflicts)
}

/**
 * The branches file: magic "FBRN", a version, the current branch's id, then each branch's id,
 * name, and its base and head snapshots (strokes as [StrokeCodec] blocks, bones and holds as JSON).
 */
object AnimationBranchFile {
    private const val MAGIC = 0x4642524E // "FBRN"
    private const val VERSION = 1

    fun write(branches: List<AnimationBranch>, currentId: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeUTF(currentId)
            out.writeInt(branches.size)
            for (branch in branches) {
                out.writeUTF(branch.id)
                out.writeUTF(branch.name)
                writeSnapshot(out, branch.base)
                writeSnapshot(out, branch.head)
            }
        }
        return bytes.toByteArray()
    }

    /** The branches and the current branch's id; throws [IOException] on a damaged or newer file. */
    fun read(bytes: ByteArray): Pair<List<AnimationBranch>, String> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        if (input.readInt() != MAGIC) throw IOException("Not a branches file")
        if (input.readInt() > VERSION) throw IOException("Newer branches file")
        val current = input.readUTF()
        val count = input.readInt()
        if (count !in 0..MAX_BRANCHES) throw IOException("Bad branch count $count")
        val branches = List(count) { AnimationBranch(input.readUTF(), input.readUTF(), readSnapshot(input), readSnapshot(input)) }
        return branches to current
    }

    private fun writeSnapshot(out: DataOutputStream, snapshot: AnimationSnapshot) {
        StrokeCodec.write(out, snapshot.strokes)
        val extra = JSONObject()
            .put("frames", snapshot.frameCount)
            .put("holds", JSONObject().apply { for ((f, h) in snapshot.holds) put(f.toString(), h) })
            .put("bones", JSONArray().apply {
                for (b in snapshot.bones) put(
                    JSONObject().put("id", b.id).put("parent", b.parentId ?: JSONObject.NULL).put("frame", b.animFrame)
                        .put("hx", b.head.x).put("hy", b.head.y).put("tx", b.tail.x).put("ty", b.tail.y).put("dynamic", b.dynamic)
                        .apply { if (b.minTurn != null && b.maxTurn != null) put("minTurn", b.minTurn).put("maxTurn", b.maxTurn) },
                )
            })
            .toString().toByteArray(Charsets.UTF_8)
        out.writeInt(extra.size)
        out.write(extra)
    }

    private fun readSnapshot(input: DataInputStream): AnimationSnapshot {
        val strokes = StrokeCodec.read(input)
        val size = input.readInt()
        if (size !in 0..MAX_EXTRA_BYTES) throw IOException("Bad snapshot size $size")
        val json = JSONObject(String(ByteArray(size).also { input.readFully(it) }, Charsets.UTF_8))
        val holds = json.optJSONObject("holds")?.let { h -> h.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to h.getInt(k) } }.toMap() }.orEmpty()
        val bones = json.optJSONArray("bones")?.let { array ->
            (0 until array.length()).map { i ->
                val e = array.getJSONObject(i)
                Bone(
                    e.getString("id"), if (e.isNull("parent")) null else e.getString("parent"),
                    WorldPoint(e.getDouble("hx"), e.getDouble("hy")), WorldPoint(e.getDouble("tx"), e.getDouble("ty")), e.optInt("frame", 0),
                    minTurn = if (e.has("minTurn")) e.getDouble("minTurn") else null,
                    maxTurn = if (e.has("maxTurn")) e.getDouble("maxTurn") else null,
                    dynamic = e.optBoolean("dynamic", false),
                )
            }
        }.orEmpty()
        return AnimationSnapshot(strokes, bones, holds, json.optInt("frames", 1).coerceAtLeast(1))
    }

    private const val MAX_BRANCHES = 256
    private const val MAX_EXTRA_BYTES = 64 * 1024 * 1024
}

/** The branch a document starts on. */
const val MAIN_BRANCH_NAME = "main"
