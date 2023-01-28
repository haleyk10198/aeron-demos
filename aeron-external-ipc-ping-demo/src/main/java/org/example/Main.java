package org.example;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class Main {

    //Start Media Driver sepperately
    public static void main(String[] args) {
        final String channel = "aeron:ipc";
        final int stream = 10;
        final int sendCount = 1_000_000;

        final IdleStrategy idleStrategySend = new BusySpinIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        final Aeron aeron = Aeron.connect();

        //construct the subs and pubs
        final Publication publication = aeron.addPublication(channel, stream);

        //construct the agents
        final SendAgent sendAgent = new SendAgent(publication, sendCount);

        //construct agent runners
        final AgentRunner sendAgentRunner = new AgentRunner(idleStrategySend,
                Throwable::printStackTrace, null, sendAgent);

        System.out.println("Starting SendingAgent...");
        //start the runners
        AgentRunner.startOnThread(sendAgentRunner);

        //wait for the final item to be received before closing
        barrier.await();

        //close the resources
        sendAgentRunner.close();
        aeron.close();
    }
}


