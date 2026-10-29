package com.hinnka.mycamera.camera

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** Owns a callback reference until the processing caller closes it in finally. */
internal class OwnedCaptureReference<T : Any>(private val dispose: (T) -> Unit) : AutoCloseable {
    private val lock = Any()
    private val ready = CompletableDeferred<Unit>()
    private var value: T? = null
    private var failure: Throwable? = null
    private var closed = false

    /** Transfers ownership, including disposal of rejected duplicate or late values. */
    fun offer(value: T): Boolean {
        val accepted = synchronized(lock) {
            if (closed || failure != null || this.value != null) {
                false
            } else {
                this.value = value
                true
            }
        }
        if (accepted) ready.complete(Unit) else dispose(value)
        return accepted
    }

    fun fail(error: Throwable) {
        val failed = synchronized(lock) {
            if (closed || value != null || failure != null) {
                false
            } else {
                failure = error
                true
            }
        }
        if (failed) ready.complete(Unit)
    }

    /** Awaiting does not transfer ownership; close only after all reference use ends. */
    suspend fun await(): T {
        ready.await()
        return synchronized(lock) {
            if (closed) throw CancellationException("Capture reference closed")
            failure?.let { throw it }
            checkNotNull(value)
        }
    }

    override fun close() {
        val released = synchronized(lock) {
            if (closed) return
            closed = true
            value.also { value = null }
        }
        ready.cancel(CancellationException("Capture reference closed"))
        released?.let(dispose)
    }
}
