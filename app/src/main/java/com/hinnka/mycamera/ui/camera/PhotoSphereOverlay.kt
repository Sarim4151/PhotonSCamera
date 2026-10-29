package com.hinnka.mycamera.ui.camera

import android.hardware.*
import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.*
import com.hinnka.mycamera.photosphere.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import java.security.MessageDigest
import kotlin.math.*

private fun dot(a: SphereVec3,b: SphereVec3) = a.x*b.x+a.y*b.y+a.z*b.z
private fun angle(a: SphereVec3,b: SphereVec3) = Math.toDegrees(acos(dot(a,b).coerceIn(-1.0,1.0)))
private fun poseDistance(a: SphereCameraBasis,b: SphereCameraBasis) = max(angle(a.forward,b.forward),angle(a.right,b.right))
private fun targets(size: Size, focal35: Float): List<SphereVec3> {
    val f = hypot(size.width.toDouble(),size.height.toDouble())*focal35/43.2666153
    // Use the smaller FOV in both directions, so orientation never reduces overlap below 30%.
    val step = 2*atan(min(size.width,size.height)/(2*f))*.65
    val rings = ceil(PI/step).toInt()
    return (0..rings).flatMap { row ->
        val latitude = -PI/2+PI*row/rings
        val count = if (row==0 || row==rings) 1 else ceil(2*PI*max(.1,cos(latitude))/step).toInt()
        (0 until count).map { col ->
            val longitude = 2*PI*(if(row%2==0) col else count-1-col)/count
            SphereVec3(sin(longitude)*cos(latitude),-sin(latitude),cos(longitude)*cos(latitude))
        }
    }.sortedBy { if (abs(it.y)<.2) 0 else 1 }
}

@Composable
fun PhotoSphereOverlay(
    captureFrame: suspend (String)->String, photoProcessor: PhotoProcessor,
    captureSize: Size, focalLength35mmEquivalent: Float, displayRotation: Int,
    topControlsInset: Dp, bottomControlsInset: Dp,
    onCaptureActionChanged: ((()->Unit)?)->Unit, onSaved:(String)->Unit,
    onSessionClosed:(String)->Unit, onDismiss:()->Unit,
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val session=remember { SphereCaptureSession(context,focalLength35mmEquivalent.toDouble(),displayRotation).apply { processor=photoProcessor } }
    val grid=remember { targets(captureSize,focalLength35mmEquivalent) }
    var pose by remember { mutableStateOf<SphereCameraBasis?>(null) }
    var lastSensor by remember { mutableLongStateOf(0L) }
    var stableSince by remember { mutableLongStateOf(0L) }
    var anchor by remember { mutableStateOf<SphereCameraBasis?>(null) }
    var now by remember { mutableLongStateOf(0L) }
    var count by remember { mutableIntStateOf(0) }
    var initialized by remember { mutableStateOf(false) }
    var relativeSensor by remember { mutableStateOf(false) }
    var disposed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf(false) }
    var captureAnchor by remember { mutableStateOf<SphereCameraBasis?>(null) }
    var maximumMotion by remember { mutableDoubleStateOf(0.0) }
    var width by remember { mutableIntStateOf(4096) }
    var progress by remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(session) {
        try { session.initialize(); initialized=true } catch(e:Exception) { failure=e.localizedMessage }
    }
    LaunchedEffect(Unit) { while(true) { now=SystemClock.elapsedRealtime(); delay(100) } }
    val currentRotation by rememberUpdatedState(displayRotation)
    val currentCapture by rememberUpdatedState(captureFrame)
    val closed by rememberUpdatedState(onSessionClosed)
    DisposableEffect(Unit) {
        val manager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        relativeSensor=sensor?.type==Sensor.TYPE_GAME_ROTATION_VECTOR
        val listener=object:SensorEventListener {
            override fun onAccuracyChanged(sensor:Sensor?, accuracy:Int) { if(sensor?.type!=Sensor.TYPE_GAME_ROTATION_VECTOR && accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE) { stableSince=0L; if(captureAnchor!=null) maximumMotion=180.0 } }
            override fun onSensorChanged(event:SensorEvent) {
                if(event.sensor.type!=Sensor.TYPE_GAME_ROTATION_VECTOR && event.accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE) { stableSince=0; if(captureAnchor!=null) maximumMotion=180.0; return }
                val r=FloatArray(9); SensorManager.getRotationMatrixFromVector(r,event.values)
                fun column(i:Int,sign:Double=1.0)=SphereVec3(r[i]*sign,-r[6+i]*sign,r[3+i]*sign)
                val x=column(0); val y=column(1); fun neg(v:SphereVec3)=SphereVec3(-v.x,-v.y,-v.z)
                val basis=when(session.rotation) {
                    1->SphereCameraBasis(y,x,column(2,-1.0))
                    2->SphereCameraBasis(neg(x),y,column(2,-1.0))
                    3->SphereCameraBasis(neg(y),neg(x),column(2,-1.0))
                    else->SphereCameraBasis(x,neg(y),column(2,-1.0))
                }
                now=SystemClock.elapsedRealtime(); lastSensor=now
                if(stableSince==0L || anchor==null || poseDistance(anchor!!,basis)>.6) { anchor=basis; stableSince=now }
                captureAnchor?.let { maximumMotion=max(maximumMotion,poseDistance(it,basis)) }
                pose=basis
            }
        }
        available=sensor!=null && manager.registerListener(listener,sensor,SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    val aligned=pose?.let { count<grid.size && angle(it.forward,grid[count])<3.0 }==true
    val ready=initialized && available && now-lastSensor<250 && aligned && now-stableSince>650 && stableSince>0 && currentRotation==session.rotation
    fun shutter() {
        if(busy || !ready) return
        val accepted=pose ?: return
        busy=true; failure=null; maximumMotion=0.0; captureAnchor=accepted
        job=scope.launch {
            try {
                withContext(NonCancellable) {
                    val id=currentCapture(session.id)
                    if(disposed || maximumMotion>1.5 || currentRotation!=session.rotation || SystemClock.elapsedRealtime()-lastSensor>250) {
                        withContext(Dispatchers.IO) { session.discardSource(id) }
                        failure=context.getString(R.string.sphere_moved)
                    } else {
                        withContext(Dispatchers.IO) { session.accept(id,accepted) }
                        count=session.frames.size
                    }
                }
            } catch(e:Exception) { failure=e.localizedMessage ?: context.getString(R.string.group_source_failed) }
            finally { captureAnchor=null; busy=false }
        }
    }
    fun finish() {
        if(busy || count!=grid.size) return
        busy=true; failure=null
        job=scope.launch {
            try {
                val id=withContext(Dispatchers.IO) {
                    val output=File(session.directory,"sphere_${System.nanoTime()}.jpg")
                    val coroutineContext=currentCoroutineContext()
                    PhotoSphereRenderer.render(context,session.frames.toList(),output,width,
                        onProgress={done,total->progress=done.toFloat()/total.coerceAtLeast(1)},
                        checkCancelled={coroutineContext.ensureActive()})
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        val publishedId=UUID.randomUUID().toString()
                        val metadata=MediaMetadata(width=width,height=width/2,
                            customProperties=mapOf("photoSphereProjection" to "equirectangular",
                                "preserveEncodedMetadata" to "true", "sourceRecipeRendered" to "true",
                                "sequenceOperation" to "oriented_photo_sphere"))
                        try {
                            check(GalleryManager.preparePhoto(context,metadata,null,null,false,1f,
                                includeCropRegionInOutputSize=false,photoId=publishedId)==publishedId)
                            GalleryManager.saveEncodedJpegPhoto(context,publishedId,output,false,photoProcessor)
                            val saved=GalleryManager.getOriginalImageFile(context,publishedId)
                                ?: error(context.getString(R.string.group_source_failed))
                            fun digest(file:File):ByteArray {
                                val hash=MessageDigest.getInstance("SHA-256")
                                file.inputStream().buffered().use { input ->
                                    val buffer=ByteArray(65536)
                                    while(true) { val length=input.read(buffer); if(length<0) break; hash.update(buffer,0,length) }
                                }
                                return hash.digest()
                            }
                            check(saved.length()==output.length() && digest(saved).contentEquals(digest(output))) {
                                context.getString(R.string.sphere_metadata_failed)
                            }
                        } catch(e:Throwable) { GalleryManager.deletePhotoOnly(context,publishedId); throw e }
                        session.clear()
                        publishedId
                    }
                }
                onSaved(id)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { failure=e.localizedMessage }
            finally { busy=false }
        }
    }
    val action by rememberUpdatedState<()->Unit>({shutter()})
    DisposableEffect(Unit) {
        onCaptureActionChanged { action() }
        onDispose { disposed=true; onCaptureActionChanged(null); closed(session.id); scope.launch(Dispatchers.IO+NonCancellable) { session.clear() } }
    }
    BackHandler { if(!busy) onDismiss() }
    Column(Modifier.fillMaxWidth().padding(top=topControlsInset,bottom=bottomControlsInset).background(Color.Black.copy(alpha=.7f)).padding(12.dp)) {
        Text(stringResource(R.string.sphere_title),color=Color.White)
        Text(stringResource(R.string.sphere_instructions),color=Color.White)
        if(relativeSensor) Text(stringResource(R.string.sphere_relative_sensor),color=Color.Yellow)
        Text(stringResource(R.string.sphere_count,count,grid.size),color=Color.White)
        if(count<grid.size) {
            val target=grid[count]
            val p=pose?.forward
            Text(stringResource(R.string.sphere_target,Math.toDegrees(atan2(target.x,target.z)).toInt(),Math.toDegrees(asin(-target.y)).toInt(),p?.let{angle(it,target).toInt()} ?: 180),color=Color.White)
            Text(stringResource(R.string.sphere_aim,p?.let{Math.toDegrees(atan2(it.x,it.z)).toInt()} ?: 0,p?.let{Math.toDegrees(asin(-it.y.coerceIn(-1.0,1.0))).toInt()} ?: 0),color=Color.White)
        }
        Text(stringResource(when { !available->R.string.sphere_sensor_missing; currentRotation!=session.rotation->R.string.sphere_rotation; ready->R.string.sphere_ready; else->R.string.sphere_align }),color=if(ready) Color.Green else Color.White)
        Row { listOf(4096,8192).forEach { resolution -> TextButton(onClick={width=resolution},enabled=!busy) { Text(if(width==resolution) "✓ $resolution" else "$resolution") } } }
        Button(onClick=::finish,enabled=!busy && count==grid.size) { Text(stringResource(R.string.sphere_finish)) }
        if(busy) LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
        failure?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        TextButton(onClick={ if(busy && captureAnchor==null) job?.cancel() else if(!busy) onDismiss() },enabled=!busy || captureAnchor==null) { Text(stringResource(android.R.string.cancel)) }
    }
}
