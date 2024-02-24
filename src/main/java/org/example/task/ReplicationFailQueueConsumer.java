package org.example.task;

import lombok.AllArgsConstructor;
import org.example.node.DefaultNode;
import org.example.node.NodeStatus;
import org.example.thread.RaftThreadPool;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@AllArgsConstructor
public class ReplicationFailQueueConsumer implements Runnable {
    private DefaultNode node;
    @Override
    public void run() {
        while (true) {
            try {
                ReplicationFailModel model = replicationFailQueue.take();
                if (node.getNodeStatus() != NodeStatus.LEADER) {
                    replicationFailQueue.clear();
                    continue;
                }

                Callable callable = model.callable;
                Future<Boolean> future = RaftThreadPool.submit(callable);
                Boolean r = future.get(3000, MILLISECONDS);
                if (r) {
                    tryApplyStateMachine(model);
                }
            } catch (Exception ignore) {
            }
        }
    }
}
