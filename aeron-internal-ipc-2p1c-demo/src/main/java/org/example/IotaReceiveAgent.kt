package org.example

import io.aeron.Publication
import io.aeron.Subscription
import io.aeron.logbuffer.FragmentHandler
import org.agrona.concurrent.Agent
import org.agrona.concurrent.ShutdownSignalBarrier
import org.agrona.concurrent.UnsafeBuffer
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class IotaReceiveAgent(
    builder: () -> Subscription,
    private val barrier: ShutdownSignalBarrier,
    expected: Int
) : Agent {

    companion object {
        private val logger = LoggerFactory.getLogger(IotaReceiveAgent::class.java)
    }

    private val subscription = builder()

    private var remaining = expected

    private val readBuffer = UnsafeBuffer(ByteBuffer.allocateDirect(2048))

    private val handler = FragmentHandler { buffer, offset, length, _ ->
        logger.info("Received fragment of length=$length")
        readBuffer.wrap(buffer, offset, length)
        with(readBuffer.byteBuffer().asIntBuffer()) {
            while (hasRemaining()) {
                val received = get()
                if(received != 0) {
                    remaining--
                    logger.info("Received: $received, Remaining: $remaining")
                }
            }

            if (remaining <= 0) {
                barrier.signal()
            }
        }
    }

    override fun doWork(): Int {
        subscription.poll(handler, 1000)

        return remaining
    }

    override fun onClose() {
        super.onClose()
        subscription.close()
    }

    override fun roleName(): String = "Receiver"
}