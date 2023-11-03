package org.example

import io.aeron.Aeron
import io.aeron.driver.MediaDriver
import io.aeron.driver.ThreadingMode
import io.aeron.logbuffer.FragmentHandler
import org.agrona.concurrent.AgentRunner
import org.agrona.concurrent.BusySpinIdleStrategy
import org.agrona.concurrent.ShutdownSignalBarrier
import org.agrona.concurrent.SleepingIdleStrategy
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class Main {

    companion object {
        private const val CHANNEL = "aeron:ipc"
        private const val STREAM_ID = 10
        private const val JOB_SIZE = 1_000
        private val logger = LoggerFactory.getLogger(Main::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            //Step 1: Construct Media Driver, cleaning up media driver folder on start/stop
            val mediaDriverCtx = MediaDriver.Context()
                .dirDeleteOnStart(true)
                .threadingMode(ThreadingMode.SHARED)
                .sharedIdleStrategy(BusySpinIdleStrategy())
                .dirDeleteOnShutdown(true)
            val mediaDriver = MediaDriver.launchEmbedded(mediaDriverCtx)

            //Step 2: Construct Aeron, pointing at the media driver's folder
            val aeronCtx = Aeron.Context()
                .aeronDirectoryName(mediaDriver.aeronDirectoryName())
            val aeron = Aeron.connect(aeronCtx)

            val job = IotaJob(JOB_SIZE)

            val barrier = ShutdownSignalBarrier()

            val receiveAgent = IotaReceiveAgent({ aeron.addSubscription(CHANNEL, STREAM_ID) }, barrier, JOB_SIZE)
            val receiveRunner = AgentRunner(BusySpinIdleStrategy(),
                { logger.error(it.stackTraceToString()) }, null, receiveAgent
            )

            AgentRunner.startOnThread(receiveRunner)

            logger.info("Started receiver thread")

            val senders = (0 until 3).map {
                val sendAgent = job.newWorker { aeron.addPublication(CHANNEL, STREAM_ID) }
                AgentRunner(SleepingIdleStrategy(TimeUnit.SECONDS.toNanos(1)),
                    { logger.error(it.stackTraceToString()) }, null, sendAgent
                )
            }

            senders.forEach(AgentRunner::startOnThread)

            barrier.await()

            senders.forEach(AgentRunner::close)
            receiveRunner.close()

            logger.info("Closing Aeron, make sure JVM exits successfully!")
            aeron.close()
            mediaDriver.close()
        }
    }
}