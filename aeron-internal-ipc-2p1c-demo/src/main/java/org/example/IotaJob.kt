package org.example

import io.aeron.Publication
import io.aeron.logbuffer.BufferClaim
import org.agrona.concurrent.Agent
import org.agrona.concurrent.AtomicBuffer
import org.agrona.concurrent.UnsafeBuffer
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

class IotaJob(size: Int) {
    companion object {
        private val logger = LoggerFactory.getLogger(IotaJob::class.java)
    }

    private val buffer: AtomicBuffer
    private val bufferSize: Int = size * Int.SIZE_BYTES

    init {
        val byteBuffer = ByteBuffer.allocateDirect(bufferSize)
        byteBuffer.asIntBuffer()
            .put(IntArray(size) { it + 1 })
        buffer = UnsafeBuffer(byteBuffer)
    }

    private val workerCount = AtomicInteger(0)

    private val streamPosition = AtomicInteger(0)

    private fun claimWorkload(maxSize: Int, worker: (Int, Int) -> Unit) {
        val start = streamPosition.getAndAdd(maxSize)
        val end = min(start + maxSize, bufferSize)

        worker(start, end)
    }

    private fun remaining(): Int {
        return max(0, bufferSize - streamPosition.get())
    }

    fun newWorker(builder: () -> Publication): Agent {
        return object : Agent {
            private val workerId = workerCount.getAndIncrement()
            private val writeBuffer = BufferClaim()
            private var workerConsumed = 0
            private val publication = builder()

            override fun doWork(): Int {
                val status = publication.tryClaim(publication.maxPayloadLength(), writeBuffer)
                val isError: Long.() -> Boolean = { this < 0 }
                if (!status.isError()) {
                    claimWorkload(writeBuffer.length()) { start, end ->
                        if (start >= bufferSize) {
                            writeBuffer.abort()
                            return@claimWorkload
                        }
                        val size = end - start
                        writeBuffer.putBytes(buffer, start, size)
                        writeBuffer.commit()
                        workerConsumed += size
                    }
                }

                return remaining()
            }

            override fun onClose() {
                super.onClose()
                publication.close()
                logger.info("Worker #$workerId consumed $workerConsumed bytes")
            }

            override fun roleName(): String {
                return "$workerId"
            }
        }
    }

}