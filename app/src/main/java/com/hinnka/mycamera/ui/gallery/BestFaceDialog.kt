package com.hinnka.mycamera.ui.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.bestface.BestFaceMerger
import com.hinnka.mycamera.gallery.MediaData
import kotlinx.coroutines.*

@Composable
fun BestFaceDialog(photos: List<MediaData>, onDismiss: () -> Unit, onSaved: suspend () -> Unit) {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    val key=photos.map { it.id }
    var session by remember(key) { mutableStateOf<BestFaceMerger.Session?>(null) }
    var choices by remember(key) { mutableStateOf<List<Int>>(emptyList()) }
    var person by remember(key) { mutableIntStateOf(0) }
    var preview by remember(key) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(key) { mutableStateOf(true) }
    var failed by remember(key) { mutableStateOf(false) }
    var saving by remember(key) { mutableStateOf(false) }
    var original by remember(key) { mutableStateOf(true) }
    LaunchedEffect(key) {
        try {
            val next=BestFaceMerger.open(context,photos)
            session=next;choices=List(next.options.size){0}
        } catch(c:CancellationException) { throw c }
        catch(_:Exception) { failed=true }
        catch(_:OutOfMemoryError) { failed=true }
        finally { loading=false }
    }
    LaunchedEffect(session,choices) {
        val current=session ?: return@LaunchedEffect
        if(choices.size!=current.options.size) return@LaunchedEffect
        loading=true
        try { preview=BestFaceMerger.render(context,current,choices,false);failed=false }
        catch(c:CancellationException) {throw c}
        catch(_:Exception) {failed=true}
        catch(_:OutOfMemoryError) {failed=true}
        finally {loading=false}
    }
    DisposableEffect(session) {val owned=session;onDispose {owned?.close()} }
    DisposableEffect(preview) {val owned=preview;onDispose {owned?.let {if(!it.isRecycled)it.recycle()}} }
    // Reads the latest remembered state even if dismissal precedes the next recomposition.
    DisposableEffect(key) { onDispose { session?.close();preview?.let {if(!it.isRecycled)it.recycle()} } }
    AlertDialog(onDismissRequest={if(!saving)onDismiss()},title={Text(stringResource(R.string.best_face_title))},
        text={Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.best_face_description))
            preview?.let { bitmap ->
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    Image(bitmap.asImageBitmap(),stringResource(R.string.best_face_preview),Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                    Canvas(Modifier.matchParentSize()) {
                        val face=session?.faces?.firstOrNull()?.getOrNull(person)
                        if(face!=null) {
                            val scale=min(size.width/bitmap.width,size.height/bitmap.height)
                            val dx=(size.width-bitmap.width*scale)/2;val dy=(size.height-bitmap.height*scale)/2
                            drawRect(Color.Yellow,Offset(dx+face.bounds.left*scale,dy+face.bounds.top*scale),
                                Size(face.bounds.width()*scale,face.bounds.height()*scale),style=Stroke(2.dp.toPx()))
                        }
                    }
                }
            }
            if(loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            session?.let {current ->
                Text(stringResource(R.string.best_face_size,current.frames[0].width,current.frames[0].height))
                if(current.rejectedFrames>0)Text(stringResource(R.string.best_face_rejected,current.rejectedFrames))
                if(current.options.isNotEmpty()) {
                    Row {
                        TextButton(enabled=!saving,onClick={person=(person+current.options.size-1)%current.options.size}) {Text("‹")}
                        Text(stringResource(R.string.best_face_person,person+1,current.options.size))
                        TextButton(enabled=!saving,onClick={person=(person+1)%current.options.size}) {Text("›")}
                    }
                    current.options[person].forEachIndexed {index,option ->
                        FilterChip(selected=choices.getOrNull(person)==index,enabled=!saving,
                            onClick={failed=false;choices=choices.toMutableList().apply {this[person]=index}},
                            label={Text(stringResource(if(option.frame==0)R.string.best_face_reference else R.string.best_face_source,option.frame+1))})
                    }
                    if(current.options[person].size==1)Text(stringResource(R.string.best_face_no_candidate))
                }
                Row {Checkbox(original,{original=it;if(preview!=null)failed=false},enabled=!saving);Text(stringResource(R.string.best_face_original))}
                Text(stringResource(R.string.best_face_export_note))
            }
            if(failed)Text(stringResource(R.string.best_face_failed),color=MaterialTheme.colorScheme.error)
        }},confirmButton={TextButton(enabled=!loading && !saving && !failed && preview!=null && choices.any {it>0},onClick={
            val current=session ?: return@TextButton
            saving=true
            scope.launch {
                try {BestFaceMerger.save(context,current,choices,original);onSaved();onDismiss()}
                catch(c:CancellationException) {throw c}
                catch(_:Exception) {failed=true}
                catch(_:OutOfMemoryError) {failed=true}
                finally {saving=false}
            }
        }) {Text(stringResource(R.string.best_face_save))}},dismissButton={TextButton(enabled=!saving,onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
