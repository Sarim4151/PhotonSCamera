package com.hinnka.mycamera.ui.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.panorama.PanoramaStitcher
import kotlinx.coroutines.*

@Composable
fun PanoramaDialog(photos:List<MediaData>,onDismiss:()->Unit,onSaved:suspend()->Unit) {
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope();val key=photos.map{it.id}
    var plan by remember(key){mutableStateOf<PanoramaStitcher.Plan?>(null)}
    var preview by remember(key){mutableStateOf<Bitmap?>(null)}
    var busy by remember(key){mutableStateOf(true)};var saving by remember(key){mutableStateOf(false)}
    var failed by remember(key){mutableStateOf(false)}
    LaunchedEffect(key){var owned:Bitmap?=null
        try{val aligned=PanoramaStitcher.align(context,photos);owned=PanoramaStitcher.render(context,aligned,false);plan=aligned;preview=owned;owned=null}
        catch(c:CancellationException){throw c}catch(_:Exception){failed=true}catch(_:OutOfMemoryError){failed=true}
        finally{owned?.recycle();busy=false}
    }
    DisposableEffect(key){onDispose{preview?.let{if(!it.isRecycled)it.recycle()}}}
    AlertDialog(onDismissRequest={if(!saving)onDismiss()},title={Text(stringResource(R.string.panorama_title))},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(stringResource(R.string.panorama_help))
            preview?.let{Image(it.asImageBitmap(),stringResource(R.string.panorama_preview),Modifier.fillMaxWidth().height(200.dp),contentScale=ContentScale.Fit)}
            plan?.let{val size=PanoramaStitcher.dimensions(it);Text(stringResource(R.string.panorama_size,size.first,size.second))}
            if(busy||saving)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(failed)Text(stringResource(R.string.panorama_failed),color=MaterialTheme.colorScheme.error)
        }},confirmButton={TextButton(enabled=plan!=null&&!busy&&!saving&&!failed,onClick={val selected=plan ?: return@TextButton;saving=true
            scope.launch{try{PanoramaStitcher.save(context,selected);onSaved();onDismiss()}catch(c:CancellationException){throw c}catch(_:Exception){failed=true}catch(_:OutOfMemoryError){failed=true}finally{saving=false}}
        }){Text(stringResource(R.string.panorama_save))}},dismissButton={TextButton(enabled=!saving,onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
