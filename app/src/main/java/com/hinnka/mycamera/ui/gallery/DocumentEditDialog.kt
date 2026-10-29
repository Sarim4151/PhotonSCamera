package com.hinnka.mycamera.ui.gallery

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.document.DocumentEditor
import kotlinx.coroutines.*
import kotlin.math.hypot

@Composable
fun DocumentEditDialog(sourceId:String,sourceWidth:Int,sourceHeight:Int,load:suspend(Int)->Bitmap?,onDismiss:()->Unit,onSaved:suspend()->Unit) {
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope();val clipboard=LocalClipboardManager.current
    var source by remember(sourceId){mutableStateOf<Bitmap?>(null)}
    var preview by remember(sourceId){mutableStateOf<Bitmap?>(null)}
    var corners by remember(sourceId){mutableStateOf(DocumentEditor.frame)}
    var bw by remember{mutableStateOf(false)};var hindi by remember{mutableStateOf(false)}
    var original by remember{mutableStateOf(true)};var busy by remember{mutableStateOf(false)}
    var failed by remember{mutableStateOf(false)};var text by remember{mutableStateOf("")}
    var rendering by remember{mutableStateOf(false)}
    suspend fun output():Bitmap {
        if(original)DocumentEditor.requireSourceBudget(sourceWidth,sourceHeight,context)
        val full=load(if(original)Int.MAX_VALUE else 2048) ?: error("Photo unavailable")
        if(original)check((full.width==sourceWidth && full.height==sourceHeight)||(full.width==sourceHeight && full.height==sourceWidth))
        return DocumentEditor.render(full,corners,bw)
    }
    val pdfLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri ->
        if(uri!=null){busy=true;scope.launch(start=CoroutineStart.UNDISPATCHED){
            var out:Bitmap?=null;val written=java.util.concurrent.atomic.AtomicBoolean(false)
            try{out=output();DocumentEditor.pdf(context,uri,out!!,text){written.set(true)};failed=false}
            catch(c:CancellationException){throw c}catch(_:Exception){failed=true}catch(_:OutOfMemoryError){failed=true}
            finally{out?.recycle();if(!written.get())DocumentEditor.deleteCreatedPdf(context,uri);busy=false}
        }}
    }
    LaunchedEffect(sourceId){try{source=load(1024)}catch(c:CancellationException){throw c}catch(_:Exception){failed=true}catch(_:OutOfMemoryError){failed=true}}
    LaunchedEffect(source,corners,bw){val base=source ?: return@LaunchedEffect;var owned:Bitmap?=null;rendering=true;text=""
        try{delay(100);owned=DocumentEditor.render(base,corners,bw);preview=owned;owned=null;failed=false}catch(c:CancellationException){throw c}catch(_:Exception){failed=true}catch(_:OutOfMemoryError){failed=true}finally{owned?.recycle();rendering=false}}
    DisposableEffect(preview){val owned=preview;onDispose{owned?.let {if(!it.isRecycled)it.recycle()}}}
    DisposableEffect(sourceId){onDispose{preview?.let {if(!it.isRecycled)it.recycle()}}}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(stringResource(R.string.document_title))},text={Column(Modifier.verticalScroll(rememberScrollState())){
        Text(stringResource(R.string.document_help))
        source?.let { base ->
            Box(Modifier.fillMaxWidth().aspectRatio(base.width.toFloat()/base.height).pointerInput(busy){var selected=0
                if(!busy)detectDragGestures(onDragStart={p ->selected=corners.indices.minByOrNull { i ->hypot(corners[i].x*size.width-p.x,corners[i].y*size.height-p.y)} ?: 0},onDrag={change,_->change.consume();val next=corners.toMutableList();next[selected]=DocumentEditor.Corner((change.position.x/size.width).coerceIn(0f,1f),(change.position.y/size.height).coerceIn(0f,1f));if(DocumentEditor.valid(next))corners=next})}){
                Image(base.asImageBitmap(),stringResource(R.string.document_preview),Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()){corners.forEachIndexed { i,c ->val next=corners[(i+1)%4];drawLine(Color.Yellow,Offset(c.x*size.width,c.y*size.height),Offset(next.x*size.width,next.y*size.height),3f);drawCircle(Color.Yellow,9f,Offset(c.x*size.width,c.y*size.height)) }}
            }
        }
        preview?.let { Image(it.asImageBitmap(),stringResource(R.string.document_preview),Modifier.fillMaxWidth().heightIn(max=220.dp)) }
        if(busy||rendering)LinearProgressIndicator(Modifier.fillMaxWidth())
        Row{Checkbox(bw,{bw=it},enabled=!busy);Text(stringResource(R.string.document_bw))}
        Row{Checkbox(hindi,{hindi=it;text=""},enabled=!busy);Text(stringResource(R.string.document_hindi))}
        Row{Checkbox(original,{original=it},enabled=!busy);Text(stringResource(R.string.document_original))}
        Text(stringResource(R.string.document_pdf_help))
        TextButton(enabled=preview!=null&&!busy&&!rendering,onClick={busy=true;scope.launch{var ocrInput:Bitmap?=null;try{ocrInput=DocumentEditor.render(source ?: error("Photo unavailable"),corners,bw);text=DocumentEditor.recognize(ocrInput!!,hindi);failed=false}catch(c:CancellationException){throw c}catch(_:Exception){failed=true}catch(_:OutOfMemoryError){failed=true}finally{ocrInput?.recycle();busy=false}}}){Text(stringResource(R.string.document_ocr))}
        if(text.isNotBlank()){Text(text);TextButton(onClick={clipboard.setText(AnnotatedString(text))}){Text(stringResource(R.string.document_copy))}}
        TextButton(enabled=preview!=null&&!busy&&!rendering,onClick={pdfLauncher.launch("Document.pdf")}){Text(stringResource(R.string.document_pdf))}
        if(failed)Text(stringResource(R.string.document_failed),color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(enabled=preview!=null&&!busy&&!rendering,onClick={busy=true;scope.launch{var out:Bitmap?=null;try{out=output();DocumentEditor.save(context,sourceId,out!!);onSaved();onDismiss()}catch(c:CancellationException){throw c}catch(_:Exception){failed=true}catch(_:OutOfMemoryError){failed=true}finally{out?.recycle();busy=false}}}){Text(stringResource(R.string.document_save))}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
