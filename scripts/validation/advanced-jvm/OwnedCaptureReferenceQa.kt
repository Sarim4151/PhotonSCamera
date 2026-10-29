package com.hinnka.mycamera.camera

import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlinx.coroutines.*

fun main() = runBlocking {
    val disposed = Collections.synchronizedList(mutableListOf<Int>())
    val owner = OwnedCaptureReference<Int> { disposed.add(it) }
    val waiting = async(start = CoroutineStart.UNDISPATCHED) { owner.await() }
    check(!waiting.isCompleted) { "Processing must wait for the reference callback" }
    check(owner.offer(1))
    check(waiting.await() == 1)
    check(disposed.isEmpty()) { "Reference must remain usable until processing finishes" }
    check(!owner.offer(2))
    check(disposed == listOf(2))
    owner.close(); owner.close()
    check(!owner.offer(3))
    check(disposed == listOf(2, 1, 3))

    val failure = IllegalStateException("capture failed")
    val failed = OwnedCaptureReference<Int> { disposed.add(it) }
    val failureWaiter = async(start = CoroutineStart.UNDISPATCHED) {
        runCatching { failed.await() }.exceptionOrNull()
    }
    failed.fail(failure)
    check(failureWaiter.await() === failure)
    check(runCatching { failed.await() }.exceptionOrNull() === failure)
    check(!failed.offer(4)); failed.close()

    val closed = OwnedCaptureReference<Int> { disposed.add(it) }
    val pending = async(start = CoroutineStart.UNDISPATCHED) { closed.await() }
    closed.close()
    check(runCatching { pending.await() }.exceptionOrNull() is CancellationException)
    check(!closed.offer(5))

    val cancelled = OwnedCaptureReference<Int> { disposed.add(it) }
    val cancelledWaiter = launch(start = CoroutineStart.UNDISPATCHED) { cancelled.await() }
    cancelledWaiter.cancelAndJoin()
    check(cancelled.offer(6))
    cancelled.close()
    check(disposed.count { it == 6 } == 1)

    val success = OwnedCaptureReference<Int> { disposed.add(it) }
    check(success.offer(7))
    success.fail(failure)
    check(success.await() == 7) { "A late failure must not replace the accepted reference" }
    success.close()

    lateinit var reentrant: OwnedCaptureReference<Int>
    reentrant = OwnedCaptureReference {
        val completed = CountDownLatch(1)
        thread { reentrant.close(); completed.countDown() }
        check(completed.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
            "Disposal must happen outside the ownership lock"
        }
    }
    check(reentrant.offer(8))
    reentrant.close()

    repeat(500) { index ->
        val released = Collections.synchronizedList(mutableListOf<Int>())
        val raced = OwnedCaptureReference<Int> { released.add(it) }
        val start = CountDownLatch(1)
        val offer = thread { start.await(); raced.offer(index) }
        val close = thread { start.await(); raced.close() }
        start.countDown(); offer.join(); close.join(); raced.close()
        check(released == listOf(index)) { "offer/close must release exactly once" }
    }
    println("OwnedCaptureReference QA passed")
}
