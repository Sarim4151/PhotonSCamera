package com.hinnka.mycamera.ui.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hinnka.mycamera.R
import com.hinnka.mycamera.actionpan.ActionPanEditor
import com.hinnka.mycamera.processor.SequenceMemoryBudget
import com.hinnka.mycamera.selective.SemanticMasks
import kotlinx.coroutines.*

private class ActionPanPreviewOwner { var bitmap:Bitmap?=null }

/** Gallery cache retains source bitmap ownership; this dialog owns only rendered previews/outputs. */
@Composable
fun ActionPanDialog(sourceId:String,sourceWidth:Int,sourceHeight:Int,load:suspend(Int)->Bitmap?,onDismiss:()->Unit,onSaved:suspend()->Unit) {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    val owner=remember(sourceId){ActionPanPreviewOwner()}
    var source by remember(sourceId){mutableStateOf<Bitmap?>(null)}
    var mask by remember(sourceId){mutableStateOf<SemanticMasks.PersonMask?>(null)}
    var preview by remember(sourceId){mutableStateOf<Bitmap?>(null)}
    var direction by remember(sourceId){mutableFloatStateOf(0f)}
    var strength by remember(sourceId){mutableFloatStateOf(.5f)}
    var originalSize by remember(sourceId){mutableStateOf(true)}
    var loading by remember(sourceId){mutableStateOf(true)}
    var rendering by remember(sourceId){mutableStateOf(false)}
    var saving by remember(sourceId){mutableStateOf(false)}
    var failed by remember(sourceId){mutableStateOf(false)}
    var detectionFailed by remember(sourceId){mutableStateOf(false)}
    var retry by remember(sourceId){mutableIntStateOf(0)}
    DisposableEffect(owner){onDispose {owner.bitmap?.recycle();owner.bitmap=null}}
    LaunchedEffect(sourceId,retry) {
        loading=true;detectionFailed=false;mask=null
        try {
            val input=source ?: (load(768) ?: error("Photo unavailable")).also {source=it}
            mask=SemanticMasks.detectPerson(input)
        } catch(c:CancellationException){throw c}
        catch(_:OutOfMemoryError){detectionFailed=true}
        catch(_:Exception){detectionFailed=true}
        finally {loading=false}
    }
    LaunchedEffect(source,mask,direction,strength) {
        val input=source ?: return@LaunchedEffect
        val selectedMask=mask ?: return@LaunchedEffect
        var owned:Bitmap?=null;rendering=true
        try {
            delay(180)
            owned=ActionPanEditor.render(input,selectedMask,direction,strength)
            currentCoroutineContext().ensureActive()
            val previous=owner.bitmap
            owner.bitmap=owned;preview=owned;owned=null
            previous?.recycle();failed=false
        } catch(c:CancellationException){throw c}
        catch(_:OutOfMemoryError){failed=true}
        catch(_:Exception){failed=true}
        finally {owned?.recycle();rendering=false}
    }
    AlertDialog(onDismissRequest={if(!saving)onDismiss()},title={Text(stringResource(R.string.action_pan_title))},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.action_pan_help))
            val shown=preview ?: source
            if(shown!=null)Image(shown.asImageBitmap(),stringResource(R.string.action_pan_preview),Modifier.fillMaxWidth().aspectRatio(shown.width.toFloat()/shown.height))
            if(loading || rendering || saving)LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.action_pan_direction,direction.toInt()))
            Slider(direction,{direction=it},valueRange=0f..180f,enabled=mask!=null && !saving)
            Text(stringResource(R.string.action_pan_strength,(strength*100).toInt()))
            Slider(strength,{strength=it},valueRange=0f..1f,enabled=mask!=null && !saving)
            Row {Checkbox(originalSize,{originalSize=it;failed=false},enabled=!saving);Text(stringResource(R.string.action_pan_original))}
            if(detectionFailed){
                Text(stringResource(R.string.action_pan_mask_failed),color=MaterialTheme.colorScheme.error)
                TextButton(onClick={retry++},enabled=!loading && !saving){Text(stringResource(R.string.action_pan_retry))}
            }
            if(failed)Text(stringResource(R.string.action_pan_failed),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(enabled=mask!=null && !loading && !rendering && !saving && strength>0f && !failed,onClick={
        val selectedMask=mask ?: return@TextButton
        val selectedDirection=direction;val selectedStrength=strength;val fullSize=originalSize
        saving=true;failed=false
        scope.launch {
            var output:Bitmap?=null
            try {
                if(fullSize) {
                    require(sourceWidth>0 && sourceHeight>0)
                    // Include gallery source decoding/recipe workspace before loading original.
                    check(SequenceMemoryBudget.snapshot().fits(sourceWidth.toLong()*sourceHeight*16+32L*1024*1024,32L*1024*1024))
                    ActionPanEditor.preflight(sourceWidth,sourceHeight)
                }
                val full=load(if(fullSize)Int.MAX_VALUE else 2048) ?: error("Photo unavailable")
                output=ActionPanEditor.render(full,selectedMask,selectedDirection,selectedStrength)
                ActionPanEditor.save(context,sourceId,output!!,selectedDirection,selectedStrength)
                onSaved();onDismiss()
            } catch(c:CancellationException){throw c}
            catch(_:OutOfMemoryError){failed=true}
            catch(_:Exception){failed=true}
            finally {output?.recycle();saving=false}
        }
    }){Text(stringResource(R.string.action_pan_save))}},dismissButton={TextButton(onClick=onDismiss,enabled=!saving){Text(stringResource(R.string.action_pan_close))}})
}
