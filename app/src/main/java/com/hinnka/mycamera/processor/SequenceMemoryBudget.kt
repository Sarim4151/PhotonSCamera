package com.hinnka.mycamera.processor

import android.app.ActivityManager
import android.content.Context
import com.hinnka.mycamera.MyCameraApplication
import kotlin.math.min

/** Android API 30+ bitmap storage is native; primitive arrays and registration live on Java heap.
 * Keep independent limits so native-sized images are not charged against the Java heap twice. */
internal data class SequenceMemoryBudget(val nativeBytes: Long, val javaBytes: Long) {
    fun fits(nativeNeeded: Long, javaNeeded: Long): Boolean =
        nativeNeeded >= 0 && javaNeeded >= 0 && nativeNeeded < nativeBytes && javaNeeded < javaBytes

    companion object {
        private const val MIB = 1024L * 1024
        fun snapshot(context: Context? = null): SequenceMemoryBudget {
            val runtime = Runtime.getRuntime()
            val freeJava = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()).coerceAtLeast(0)
            val javaAllowance = freeJava * 3 / 4
            // A missing application/service occurs in plain JVM probes. Never treat it as unlimited.
            val info = runCatching {
                val owner = context ?: MyCameraApplication.instance
                val manager = owner.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return@runCatching null
                ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
            }.getOrNull()
            val nativeAllowance = if (info == null) min(runtime.maxMemory() / 2, 128 * MIB)
                else if (info.lowMemory) 0L
                else min(min((info.availMem - info.threshold).coerceAtLeast(0) / 4,
                    info.totalMem.coerceAtLeast(0) / 8), 512 * MIB)
            return SequenceMemoryBudget(nativeAllowance, if (info?.lowMemory == true) 0L else javaAllowance)
        }
    }
}
