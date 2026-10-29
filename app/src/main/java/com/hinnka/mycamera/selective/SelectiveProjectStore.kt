package com.hinnka.mycamera.selective

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import com.hinnka.mycamera.selective.SelectiveEditor.Adjustments
import com.hinnka.mycamera.selective.SelectiveEditor.Layer
import com.hinnka.mycamera.selective.SelectiveEditor.Mask
import com.hinnka.mycamera.selective.SelectiveEditor.Point
import com.hinnka.mycamera.selective.SelectiveEditor.Project
import com.hinnka.mycamera.selective.SelectiveEditor.Tool
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** A private, source-associated, versioned project. AtomicFile preserves the last complete document. */
class SelectiveProjectStore(context:Context,private val sourceId:String) {
    private val mutex=Mutex()
    private var savedRevision = -1L
    private val key=MessageDigest.getInstance("SHA-256").digest(sourceId.toByteArray(Charsets.UTF_8))
        .joinToString(""){"%02x".format(it.toInt() and 255)}
    private val file=AtomicFile(File(context.filesDir,"selective-projects/$key.json"))

    suspend fun load():Project=withContext(Dispatchers.IO) {mutex.withLock {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists())return@withLock Project(sourceId)
        val bytes=file.openRead().use {input->
            val output=ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            while(true) {
                val count=input.read(buffer)
                if(count<0)break
                check(output.size()+count<=1024*1024)
                output.write(buffer,0,count)
            }
            output.toByteArray()
        }
        check(bytes.size<=1024*1024)
        val root=JSONObject(String(bytes,Charsets.UTF_8))
        check(root.getInt("schema") in 1..2 && root.getString("sourceId")==sourceId)
        val items=root.getJSONArray("layers");check(items.length() in 1..12)
        fun number(o:JSONObject,k:String,min:Float,max:Float):Float = o.getDouble(k).toFloat().also {check(it.isFinite() && it in min..max)}
        val layers=(0 until items.length()).map {i->
            val o=items.getJSONObject(i);val m=o.getJSONObject("mask");val p=m.getJSONArray("points");check(p.length()<=256)
            val points=(0 until p.length()).map {n->val q=p.getJSONObject(n);Point(number(q,"x",0f,1f),number(q,"y",0f,1f))}
            val tool=Tool.valueOf(m.getString("tool"))
            val semantic=if(tool==Tool.SUBJECT || tool==Tool.BACKGROUND) {
                check(root.getInt("schema")==2)
                val grid=m.getJSONObject("semantic");val w=grid.getInt("width");val h=grid.getInt("height")
                check(w in 1..128 && h in 1..128)
                val encoded=grid.getString("confidence");check(encoded.length<=22000)
                val values=Base64.decode(encoded,Base64.NO_WRAP);check(values.size==w*h)
                SemanticMasks.PersonMask(w,h,values)
            } else null
            val a=o.getJSONObject("adjustment")
            Layer(id=o.getString("id").also {check(it.isNotBlank() && it.length<=128)},name=o.optString("name").take(80),
                mask=Mask(tool,points,number(m,"radius",.02f,.5f),semantic),
                adjustment=Adjustments(number(a,"exposure",-2f,2f),number(a,"warmth",-1f,1f),number(a,"saturation",-1f,1f)),enabled=o.getBoolean("enabled"))
        }
        check(layers.map {it.id}.distinct().size==layers.size)
        val selected=root.getString("selectedId").takeIf {id->layers.any {it.id==id}} ?: layers.first().id
        val revision=root.optLong("revision",0).coerceAtLeast(0)
        savedRevision=revision
        Project(sourceId,layers,selected,revision)
    }}

    suspend fun save(project:Project)=withContext(Dispatchers.IO) {mutex.withLock {
        check(project.sourceId==sourceId && project.layers.size in 1..12)
        if(project.revision<savedRevision)return@withLock
        check(project.layers.any {it.id==project.selectedId})
        check(project.layers.map {it.id}.distinct().size==project.layers.size)
        val layers=JSONArray()
        for(layer in project.layers) {
            check(layer.mask.points.size<=256 && layer.mask.radius.isFinite() && layer.mask.radius in .02f.. .5f)
            check(layer.adjustment.exposure.isFinite() && layer.adjustment.exposure in -2f..2f)
            check(layer.adjustment.warmth.isFinite() && layer.adjustment.warmth in -1f..1f)
            check(layer.adjustment.saturation.isFinite() && layer.adjustment.saturation in -1f..1f)
            check(layer.mask.points.all {it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f})
            val points=JSONArray()
            layer.mask.points.forEach {points.put(JSONObject().put("x",it.x).put("y",it.y))}
            val maskJson=JSONObject().put("tool",layer.mask.tool.name).put("radius",layer.mask.radius).put("points",points)
            if(layer.mask.tool==Tool.SUBJECT || layer.mask.tool==Tool.BACKGROUND) {
                val grid=layer.mask.semantic ?: error("Missing semantic mask")
                check(grid.width in 1..128 && grid.height in 1..128)
                maskJson.put("semantic",JSONObject().put("width",grid.width).put("height",grid.height)
                    .put("confidence",Base64.encodeToString(grid.confidence,Base64.NO_WRAP)))
            }
            layers.put(JSONObject().put("id",layer.id).put("name",layer.name).put("enabled",layer.enabled)
                .put("mask",maskJson)
                .put("adjustment",JSONObject().put("exposure",layer.adjustment.exposure).put("warmth",layer.adjustment.warmth).put("saturation",layer.adjustment.saturation)))
        }
        val bytes=JSONObject().put("schema",2).put("sourceId",sourceId).put("selectedId",project.selectedId)
            .put("revision",project.revision).put("layers",layers).toString().toByteArray(Charsets.UTF_8)
        check(bytes.size<=1024*1024)
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            check(file.baseFile.parentFile!!.let {it.isDirectory || it.mkdirs()})
            val output=file.startWrite()
            try {output.write(bytes);file.finishWrite(output);savedRevision=project.revision}catch(failure:Throwable){file.failWrite(output);throw failure}
        }
    }}
}
