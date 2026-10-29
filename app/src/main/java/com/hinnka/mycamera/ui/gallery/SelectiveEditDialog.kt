package com.hinnka.mycamera.ui.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hinnka.mycamera.R
import com.hinnka.mycamera.selective.SelectiveEditor
import com.hinnka.mycamera.selective.SelectiveEditor.Tool
import com.hinnka.mycamera.selective.SelectiveEditor.Point
import com.hinnka.mycamera.selective.SelectiveEditor.Layer
import com.hinnka.mycamera.selective.SelectiveEditor.Project
import com.hinnka.mycamera.selective.SelectiveProjectStore
import com.hinnka.mycamera.selective.SemanticMasks
import com.hinnka.mycamera.processor.SequenceMemoryBudget
import kotlinx.coroutines.*

private class SelectivePreviewOwner {var bitmap:Bitmap?=null}

@Composable
fun SelectiveEditDialog(sourceId:String,sourceWidth:Int,sourceHeight:Int,load:suspend(Int)->Bitmap?,onDismiss:()->Unit,onSaved:suspend()->Unit) {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    val store=remember(sourceId){SelectiveProjectStore(context,sourceId)}
    val previewOwner=remember(sourceId){SelectivePreviewOwner()}
    var project by remember(sourceId){mutableStateOf(Project(sourceId))}
    var undo by remember(sourceId){mutableStateOf<List<Project>>(emptyList())}
    var redo by remember(sourceId){mutableStateOf<List<Project>>(emptyList())}
    var transaction by remember(sourceId){mutableStateOf<Project?>(null)}
    var projectReady by remember(sourceId){mutableStateOf(false)}
    var storageFailure by remember(sourceId){mutableStateOf(false)}
    var source by remember(sourceId){mutableStateOf<Bitmap?>(null)}
    var preview by remember(sourceId){mutableStateOf<Bitmap?>(null)}
    var overlay by remember{mutableStateOf(true)}
    var originalSize by remember{mutableStateOf(true)}
    var loading by remember{mutableStateOf(true)}
    var saving by remember{mutableStateOf(false)}
    var failed by remember{mutableStateOf(false)}
    var drawing by remember{mutableStateOf(false)}
    var segmenting by remember(sourceId){mutableStateOf(false)}
    var semanticFailure by remember(sourceId){mutableStateOf(false)}
    var detected by remember(sourceId){mutableStateOf<SemanticMasks.PersonMask?>(null)}
    val selectedIndex=project.layers.indexOfFirst {it.id==project.selectedId}.coerceAtLeast(0)
    val layer=project.layers[selectedIndex]
    val editable=projectReady && !saving && !segmenting
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val latestProject by rememberUpdatedState(project)
    val latestReady by rememberUpdatedState(projectReady)
    DisposableEffect(lifecycle,store) {
        val observer=LifecycleEventObserver {_,event->
            if(event==Lifecycle.Event.ON_STOP && latestReady) {
                val snapshot=latestProject
                // The bounded atomic project write must finish across activity disposal.
                scope.launch(NonCancellable) {
                    try {store.save(snapshot)}
                    catch(_:Exception){storageFailure=true}
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    fun commit(next:Project) {
        if(next.layers==project.layers && next.selectedId==project.selectedId)return
        undo=(undo+project).takeLast(64);redo=emptyList()
        project=next.copy(revision=project.revision+1)
    }
    fun beginEdit(){if(transaction==null)transaction=project}
    fun finishEdit(){
        transaction?.let {before->if(before.layers!=project.layers){undo=(undo+before).takeLast(64);redo=emptyList()}}
        transaction=null
    }
    fun changeLayer(next:Layer,checkpoint:Boolean=true){
        val nextProject=project.copy(layers=project.layers.map {if(it.id==next.id)next else it})
        if(checkpoint)commit(nextProject) else project=nextProject.copy(revision=project.revision+1)
    }
    fun close(){
        if(saving || segmenting)return
        if(!projectReady){onDismiss();return}
        finishEdit();saving=true
        scope.launch {
            try {store.save(project);onDismiss()}
            catch(c:CancellationException){throw c}
            catch(_:Exception){storageFailure=true}
            finally{saving=false}
        }
    }
    LaunchedEffect(sourceId) {
        try {project=store.load();projectReady=true;storageFailure=false}
        catch(c:CancellationException){throw c}
        catch(_:Exception){storageFailure=true}
        try {source=load(768) ?: error("Photo unavailable")}
        catch(c:CancellationException){throw c}
        catch(_:OutOfMemoryError){failed=true}
        catch(_:Exception){failed=true}
        finally {loading=false}
    }
    LaunchedEffect(project,projectReady,drawing,transaction) {
        if(!projectReady || drawing || transaction!=null)return@LaunchedEffect
        try {delay(350);store.save(project);storageFailure=false}
        catch(c:CancellationException){throw c}
        catch(_:Exception){storageFailure=true}
    }
    LaunchedEffect(source,project.layers,selectedIndex,overlay,drawing,transaction,segmenting) {
        val base=source ?: return@LaunchedEffect
        if(drawing || segmenting)return@LaunchedEffect
        var owned:Bitmap?=null;loading=true
        try {
            delay(100);owned=SelectiveEditor.render(base,project.layers,if(overlay)selectedIndex else -1)
            currentCoroutineContext().ensureActive()
            val old=previewOwner.bitmap;previewOwner.bitmap=owned
            preview=owned;owned=null;old?.recycle();failed=false
        } catch(c:CancellationException){throw c}
        catch(_:OutOfMemoryError){failed=true}
        catch(_:Exception){failed=true}
        finally {owned?.recycle();loading=false}
    }
    DisposableEffect(previewOwner){onDispose{previewOwner.bitmap?.recycle();previewOwner.bitmap=null}}
    // Gallery owns source/cache bitmaps. Only independently allocated outputs are recycled here.
    AlertDialog(onDismissRequest={close()},title={Text(stringResource(R.string.selective_title))},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.selective_layers_help))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                project.layers.forEachIndexed {index,item->
                    FilterChip(selected=item.id==project.selectedId,enabled=editable && !drawing && transaction==null,
                        onClick={commit(project.copy(selectedId=item.id))},label={Text(stringResource(R.string.selective_layer,index+1))})
                }
            }
            Row {
                TextButton(enabled=editable && project.layers.size<12 && transaction==null,onClick={
                    val added=Layer();commit(project.copy(layers=project.layers+added,selectedId=added.id))
                }){Text(stringResource(R.string.selective_add_layer))}
                TextButton(enabled=editable && project.layers.size>1 && transaction==null,onClick={
                    val remaining=project.layers.filter {it.id!=layer.id};commit(project.copy(layers=remaining,selectedId=remaining.first().id))
                }){Text(stringResource(R.string.selective_remove_layer))}
            }
            Row {
                TextButton(enabled=editable && undo.isNotEmpty() && transaction==null,onClick={
                    val previous=undo.last();undo=undo.dropLast(1);redo=(redo+project).takeLast(64);project=previous.copy(revision=project.revision+1)
                }){Text(stringResource(R.string.selective_undo))}
                TextButton(enabled=editable && redo.isNotEmpty() && transaction==null,onClick={
                    val next=redo.last();redo=redo.dropLast(1);undo=(undo+project).takeLast(64);project=next.copy(revision=project.revision+1)
                }){Text(stringResource(R.string.selective_redo))}
                Checkbox(checked=layer.enabled,enabled=editable,onCheckedChange={changeLayer(layer.copy(enabled=it))})
                Text(stringResource(R.string.selective_enabled))
            }
            val shown=preview ?: source
            if(shown!=null)Image(shown.asImageBitmap(),stringResource(R.string.selective_preview),Modifier.fillMaxWidth().aspectRatio(shown.width.toFloat()/shown.height)
                .pointerInput(layer.id,layer.mask.tool,editable,shown.width,shown.height){
                    if(editable && layer.mask.tool!=Tool.SUBJECT && layer.mask.tool!=Tool.BACKGROUND)detectDragGestures(onDragStart={p->
                        beginEdit();drawing=true
                        changeLayer(project.layers.first {it.id==project.selectedId}.let {it.copy(mask=it.mask.copy(points=listOf(Point((p.x/size.width).coerceIn(0f,1f),(p.y/size.height).coerceIn(0f,1f)))))},false)
                    },onDragEnd={drawing=false;finishEdit()},onDragCancel={
                        transaction?.let {project=it.copy(revision=project.revision+1)};transaction=null;drawing=false
                    },onDrag={change,_->
                        change.consume();val p=Point((change.position.x/size.width).coerceIn(0f,1f),(change.position.y/size.height).coerceIn(0f,1f))
                        val current=project.layers.first {it.id==project.selectedId}
                        val points=if(current.mask.tool==Tool.BRUSH)(current.mask.points+p).let{if(it.size>256)it.filterIndexed{i,_->i%2==0}else it}
                            else listOf(current.mask.points.firstOrNull() ?: p,p)
                        changeLayer(current.copy(mask=current.mask.copy(points=points)),false)
                    })
                },contentScale=ContentScale.Fit)
            if(loading || saving || segmenting)LinearProgressIndicator(Modifier.fillMaxWidth())
            Row {listOf(Tool.BRUSH to R.string.selective_brush,Tool.LINEAR to R.string.selective_linear,Tool.RADIAL to R.string.selective_radial).forEach{(kind,label)->
                FilterChip(selected=layer.mask.tool==kind,onClick={changeLayer(layer.copy(mask=layer.mask.copy(tool=kind,points=emptyList(),semantic=null)))},enabled=editable,label={Text(stringResource(label))})
            }}
            Row {
                listOf(Tool.SUBJECT to R.string.selective_subject,Tool.BACKGROUND to R.string.selective_background).forEach {(kind,label)->
                    FilterChip(selected=layer.mask.tool==kind,enabled=editable && source!=null && !drawing && transaction==null,onClick={
                        val input=source ?: return@FilterChip
                        val targetId=layer.id
                        segmenting=true;semanticFailure=false
                        scope.launch {
                            try {
                                val grid=detected ?: SemanticMasks.compact(SemanticMasks.detectPerson(input)).also {detected=it}
                                currentCoroutineContext().ensureActive()
                                val target=project.layers.first {it.id==targetId}
                                changeLayer(target.copy(mask=target.mask.copy(tool=kind,points=emptyList(),semantic=grid)))
                            } catch(c:CancellationException){throw c}
                            catch(_:OutOfMemoryError){semanticFailure=true}
                            catch(_:Exception){semanticFailure=true}
                            finally{segmenting=false}
                        }
                    },label={Text(stringResource(label))})
                }
            }
            Text(stringResource(R.string.selective_semantic_help))
            if(semanticFailure)Text(stringResource(R.string.selective_semantic_failed),color=MaterialTheme.colorScheme.error)
            if(layer.mask.tool==Tool.BRUSH){
                Text(stringResource(R.string.selective_radius))
                Slider(layer.mask.radius,{beginEdit();changeLayer(layer.copy(mask=layer.mask.copy(radius=it)),false)},onValueChangeFinished={finishEdit()},valueRange=.02f.. .5f,enabled=editable)
            }
            Row{Checkbox(overlay,{overlay=it},enabled=!saving);Text(stringResource(R.string.selective_overlay))}
            TextButton(onClick={changeLayer(layer.copy(mask=layer.mask.copy(tool=if(layer.mask.tool==Tool.SUBJECT || layer.mask.tool==Tool.BACKGROUND)Tool.BRUSH else layer.mask.tool,points=emptyList(),semantic=null)))},enabled=editable){Text(stringResource(R.string.selective_clear))}
            Text(stringResource(R.string.selective_exposure,layer.adjustment.exposure))
            Slider(layer.adjustment.exposure,{beginEdit();changeLayer(layer.copy(adjustment=layer.adjustment.copy(exposure=it)),false)},onValueChangeFinished={finishEdit()},valueRange=-2f..2f,enabled=editable)
            Text(stringResource(R.string.selective_warmth,(layer.adjustment.warmth*100).toInt()))
            Slider(layer.adjustment.warmth,{beginEdit();changeLayer(layer.copy(adjustment=layer.adjustment.copy(warmth=it)),false)},onValueChangeFinished={finishEdit()},valueRange=-1f..1f,enabled=editable)
            Text(stringResource(R.string.selective_saturation,(layer.adjustment.saturation*100).toInt()))
            Slider(layer.adjustment.saturation,{beginEdit();changeLayer(layer.copy(adjustment=layer.adjustment.copy(saturation=it)),false)},onValueChangeFinished={finishEdit()},valueRange=-1f..1f,enabled=editable)
            Row{Checkbox(originalSize,{originalSize=it;failed=false},enabled=!saving);Text(stringResource(R.string.selective_original))}
            Text(stringResource(R.string.selective_export))
            if(failed)Text(stringResource(R.string.selective_failed),color=MaterialTheme.colorScheme.error)
            if(storageFailure)Text(stringResource(R.string.selective_project_failed),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(enabled=editable && source!=null && !loading && !drawing && transaction==null && !failed && project.layers.any {it.enabled && it.mask.hasContent && (it.adjustment.exposure!=0f || it.adjustment.warmth!=0f || it.adjustment.saturation!=0f)},onClick={
        saving=true;failed=false;scope.launch{
            var output:Bitmap?=null
            try {
                store.save(project);storageFailure=false
                if(originalSize){check(sourceWidth>0 && sourceHeight>0 && SequenceMemoryBudget.snapshot().fits(sourceWidth.toLong()*sourceHeight*16L+32L*1024*1024,32L*1024*1024))}
                val full=load(if(originalSize)Int.MAX_VALUE else 2048) ?: error("Photo unavailable")
                output=SelectiveEditor.render(full,project.layers);SelectiveEditor.save(context,sourceId,output!!);onSaved();onDismiss()
            }catch(c:CancellationException){throw c}
            catch(_:OutOfMemoryError){failed=true}
            catch(_:Exception){failed=true}
            finally{output?.recycle();saving=false}
        }
    }){Text(stringResource(R.string.selective_save))}},dismissButton={TextButton(enabled=!saving && !segmenting,onClick={close()}){Text(stringResource(R.string.selective_close_project))}})
}
