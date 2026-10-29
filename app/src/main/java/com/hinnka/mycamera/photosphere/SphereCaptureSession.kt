package com.hinnka.mycamera.photosphere

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.PhotoProcessor
import com.hinnka.mycamera.processor.SequenceMemoryBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.*

/** Private disk-backed capture. Incomplete sessions are cancelled on a fresh entry, never resumed with a new sensor reference. */
internal class SphereCaptureSession(private val context: Context, val focal35: Double, val rotation: Int) {
    val id = UUID.randomUUID().toString()
    private val parent = File(context.filesDir, "sphere_sessions")
    val directory = File(parent, id)
    @Volatile var frames: List<SphereFrame> = emptyList()
        private set
    private val mutex = Mutex()
    private var closed = false
    private var initialized = false
    private val sourceIds = mutableListOf<String>()
    suspend fun initialize() = withContext(Dispatchers.IO) { mutex.withLock {
        if (closed || initialized) return@withLock
        parent.mkdirs()
        directory.mkdirs()
        parent.listFiles()?.filter { it != directory }?.forEach { old ->
            runCatching {
                val state = JSONObject(File(old, "session.json").readText())
                val ids = state.optJSONArray("privateSources") ?: JSONArray()
                for (i in 0 until ids.length()) deleteSource(ids.getString(i), state.getString("id"))
            }
            old.deleteRecursively()
        }
        persist()
        initialized = true
    } }
    private suspend fun deleteSource(sourceId: String, sessionId: String = id) {
        if (GalleryManager.loadMetadata(context, sourceId)?.customProperties?.get("addMeSessionId") == sessionId)
            GalleryManager.deletePhotoOnly(context, sourceId)
    }
    suspend fun discardSource(sourceId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        deleteSource(sourceId); sourceIds.remove(sourceId); if(!closed && initialized) persist()
    } }
    suspend fun accept(sourceId: String, basis: SphereCameraBasis) = withContext(Dispatchers.IO) { mutex.withLock {
        if(closed) { deleteSource(sourceId); return@withLock }
        check(initialized)
        sourceIds.add(sourceId); persist()
        var decoded: Bitmap? = null
        var baked: Bitmap? = null
        try {
            val metadata = GalleryManager.loadMetadata(context, sourceId) ?: error("Missing source metadata")
            check(metadata.customProperties["addMeSessionId"] == id)
            check(metadata.postCropRegion == null && metadata.postRotationDegrees == 0 && metadata.postStraightenDegrees == 0f && !metadata.postMirrorHorizontal) { context.getString(com.hinnka.mycamera.R.string.sphere_geometry) }
            val source = GalleryManager.getOriginalImageFile(context, sourceId) ?: error("Missing source")
            decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                check(SequenceMemoryBudget.snapshot(context).fits(info.size.width.toLong() * info.size.height * 24, info.size.width.toLong() * info.size.height * 4))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            baked = processor!!.processBitmap(context, sourceId, decoded!!, metadata, useComputationalAperture = false, applyFrameWatermark = false)
            val bitmap = baked!!
            check(abs(max(bitmap.width, bitmap.height).toDouble() / min(bitmap.width, bitmap.height) - 4.0 / 3) < .02) { "Native 4:3 source required" }
            val jpeg = File(directory, "frame_${frames.size}.jpg")
            jpeg.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            val focal = hypot(bitmap.width.toDouble(), bitmap.height.toDouble()) * focal35 / 43.2666153
            frames = frames + SphereFrame(jpeg, basis, SphereIntrinsics(bitmap.width, bitmap.height, focal, focal, bitmap.width / 2.0, bitmap.height / 2.0))
            persist()
            deleteSource(sourceId); sourceIds.remove(sourceId); persist()
        } finally {
            baked?.let { if (it !== decoded && !it.isRecycled) it.recycle() }
            decoded?.let { if (!it.isRecycled) it.recycle() }
        }
    } }
    var processor: PhotoProcessor? = null
    private fun persist() {
        val array = JSONArray()
        frames.forEach { f ->
            fun vector(v: SphereVec3) = JSONArray(listOf(v.x,v.y,v.z))
            array.put(JSONObject().put("jpeg",f.jpeg.name).put("width",f.intrinsics.width).put("height",f.intrinsics.height)
                .put("fx",f.intrinsics.fx).put("fy",f.intrinsics.fy).put("cx",f.intrinsics.cx).put("cy",f.intrinsics.cy)
                .put("right",vector(f.basis.right)).put("down",vector(f.basis.down)).put("forward",vector(f.basis.forward)))
        }
        val state = JSONObject().put("id",id).put("orientation",rotation).put("focal35",focal35)
            .put("recoveryPolicy","cancel_on_fresh_sensor_reference").put("poseTiming","held_sensor_basis_across_capture_callback")
            .put("frames",array).put("privateSources",JSONArray(sourceIds))
        val temp = File(directory,"session.tmp"); temp.writeText(state.toString()); check(temp.renameTo(File(directory,"session.json")))
    }
    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock {
        closed=true
        sourceIds.toList().forEach { deleteSource(it) }; sourceIds.clear(); frames=emptyList(); directory.deleteRecursively()
    } }
}
