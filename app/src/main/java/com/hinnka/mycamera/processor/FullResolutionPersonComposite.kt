package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

/** Stages one rendered original at a time, then blends lossless rows into one output bitmap. */
object FullResolutionPersonComposite {
    enum class Stage { FIRST_SOURCE, SECOND_SOURCE, BLEND }

    suspend fun render(
        cacheRoot: File,
        firstId: String,
        secondId: String,
        loadSource: suspend (String) -> Bitmap,
        mask: PersonCompositeTools.Mask,
        alignment: PersonCompositeTools.Alignment,
        region: CompositeRegion,
        onStage: (Stage) -> Unit,
    ): Bitmap {
        val directory = File(cacheRoot, "add_me_${UUID.randomUUID()}")
        check(directory.mkdirs())
        val firstFile = File(directory, "first.argb")
        val secondFile = File(directory, "second.argb")
        var output: Bitmap? = null
        suspend fun stage(id: String, file: File) {
            val source = loadSource(id)
            try { withContext(Dispatchers.IO) {
                CompositeArgbSpool.write(file, source.width, source.height) { y, row ->
                    source.getPixels(row, 0, source.width, 0, y, source.width, 1)
                }
            } } finally { if (!source.isRecycled) source.recycle() }
        }
        try {
            try {
            onStage(Stage.FIRST_SOURCE); stage(firstId, firstFile)
            onStage(Stage.SECOND_SOURCE); stage(secondId, secondFile)
            onStage(Stage.BLEND)
            withContext(Dispatchers.IO) {
                CompositeArgbSpool.open(firstFile).use { first ->
                    CompositeArgbSpool.open(secondFile).use { second ->
                        require(first.width == second.width && first.height == second.height) { "Capture dimensions changed" }
                        // Only one image-sized CPU output is now needed; inputs stay on disk.
                        PersonCompositeTools.preflightOutput(first.width, first.height)
                        val bitmap = Bitmap.createBitmap(first.width, first.height, Bitmap.Config.ARGB_8888)
                        output = bitmap
                        PersonCompositeTools.renderRows(first.width, first.height, mask, alignment, region,
                            first::readRow, second::readRow) { y, row ->
                            bitmap.setPixels(row, 0, first.width, 0, y, first.width, 1)
                        }
                    }
                }
            }
            } finally { withContext(Dispatchers.IO + NonCancellable) { directory.deleteRecursively() } }
            currentCoroutineContext().ensureActive()
            return output ?: error("Cannot allocate composite output")
        } catch (e: Throwable) {
            output?.let { if (!it.isRecycled) it.recycle() }; throw e
        }
    }
}
