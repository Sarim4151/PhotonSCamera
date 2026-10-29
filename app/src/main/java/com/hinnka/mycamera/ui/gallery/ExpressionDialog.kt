package com.hinnka.mycamera.ui.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.expression.ExpressionEditor
import com.hinnka.mycamera.expression.ExpressionWarp
import kotlinx.coroutines.*

@Composable
fun ExpressionDialog(sourceId: String, load: suspend (Int) -> Bitmap?, onDismiss: () -> Unit, onSaved: suspend () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var source by remember(sourceId) { mutableStateOf<Bitmap?>(null) }
    var faces by remember(sourceId) { mutableStateOf<List<ExpressionWarp.Face>>(emptyList()) }
    var selected by remember(sourceId) { mutableIntStateOf(0) }
    var strength by remember(sourceId) { mutableFloatStateOf(.5f) }
    var relax by remember(sourceId) { mutableStateOf(false) }
    var preview by remember(sourceId) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(sourceId) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var originalSize by remember { mutableStateOf(false) }
    var retryKey by remember(sourceId) { mutableIntStateOf(0) }
    LaunchedEffect(sourceId, retryKey) {
        loading=true; error=false
        faces=emptyList(); selected=0; preview=null
        try {
            source=load(if (retryKey > 0) 2048 else 1024) ?: error("Photo unavailable")
            faces=ExpressionEditor.detect(context,source!!)
        } catch(c: CancellationException) { throw c }
        catch(_: OutOfMemoryError) { error=true; faces=emptyList(); source=null }
        catch(_: Exception) { error=true; faces=emptyList(); source=null }
        finally { loading=false }
    }
    LaunchedEffect(source,faces,selected,strength,relax) {
        val bitmap=source ?: return@LaunchedEffect
        val face=faces.getOrNull(selected) ?: return@LaunchedEffect
        loading=true
        var owned: Bitmap?=null
        try {
            delay(80)
            owned=ExpressionEditor.render(bitmap,face,strength,relax)
            preview=owned; owned=null
            error=false
        } catch(c: CancellationException) { throw c }
        catch(_: OutOfMemoryError) { error=true; preview=null }
        catch(_: Exception) { error=true; preview=null }
        finally { owned?.recycle(); loading=false }
    }
    DisposableEffect(preview) { val bitmap=preview; onDispose { bitmap?.recycle() } }
    // getPreviewBitmap returns repository-owned/cacheable bitmaps: never recycle the source.
    AlertDialog(onDismissRequest={ if(!saving) onDismiss() },title={Text(stringResource(R.string.expression_title))},
        text={Column {
            Text(stringResource(R.string.expression_description))
            (preview ?: source)?.let { Image(it.asImageBitmap(),null,Modifier.fillMaxWidth().height(220.dp),contentScale=ContentScale.Fit) }
            if(loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(faces.size>1) Row {
                TextButton(enabled=!saving,onClick={ selected=(selected+faces.size-1)%faces.size }) { Text("‹") }
                Text(stringResource(R.string.expression_face,selected+1,faces.size))
                TextButton(enabled=!saving,onClick={selected=(selected+1)%faces.size}) {Text("›")}
            }
            Row {
                FilterChip(!relax,{relax=false},label={Text(stringResource(R.string.expression_smile))},enabled=!saving)
                Spacer(Modifier.width(8.dp))
                FilterChip(relax,{relax=true},label={Text(stringResource(R.string.expression_relax))},enabled=!saving)
            }
            Slider(strength,{strength=it},enabled=!saving && faces.isNotEmpty())
            Text(stringResource(R.string.expression_strength,(strength*100).toInt()))
            Row { Checkbox(originalSize,{originalSize=it},enabled=!saving); Text(stringResource(R.string.expression_original_size)) }
            Text(stringResource(R.string.expression_export_size))
            if(!loading && faces.isEmpty()) Text(stringResource(R.string.expression_no_face))
            if(!loading && faces.isEmpty()) TextButton(enabled=!saving, onClick={retryKey++}) {
                Text(stringResource(R.string.expression_retry_detection))
            }
            if(error) Text(stringResource(R.string.expression_failed),color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={TextButton(enabled=!loading && !saving && !error && preview!=null && faces.getOrNull(selected)!=null && strength>0,
            onClick={saving=true; error=false; scope.launch {
                var rendered: Bitmap?=null
                try {
                    val full=load(if(originalSize) Int.MAX_VALUE else 2048) ?: error("Photo unavailable")
                    val base=source ?: error("Photo unavailable")
                    val face=faces[selected]
                    fun scale(p:ExpressionWarp.Point)=ExpressionWarp.Point(p.x*full.width/base.width,p.y*full.height/base.height)
                    rendered=ExpressionEditor.render(full,ExpressionWarp.Face(scale(face.left),scale(face.right),scale(face.upper),scale(face.lower)),strength,relax)
                    ExpressionEditor.save(context,sourceId,rendered!!)
                    onSaved(); onDismiss()
                } catch(c:CancellationException) {throw c}
                catch(_:OutOfMemoryError) {error=true}
                catch(_:Exception) {error=true}
                finally {rendered?.recycle();saving=false}
            }}) {Text(stringResource(R.string.expression_save))}},
        dismissButton={TextButton(enabled=!saving,onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
