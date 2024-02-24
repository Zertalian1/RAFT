package org.example.task;

import lombok.AllArgsConstructor;
import org.example.node.DefaultNode;
import org.example.node.NodeStatus;
import org.example.node.entity.ReplicationFailModel;
import org.example.thread.RaftThreadPool;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ReplicationFailQueueConsumer implements Runnable {
    private final DefaultNode node;
    private LinkedBlockingQueue<ReplicationFailModel> replicationFailQueue = new LinkedBlockingQueue<>(2048);

    public ReplicationFailQueueConsumer(DefaultNode node) {
        this.node = node;
    }

    @Override
    public void run() {
        while (true) {
            try {
                ReplicationFailModel model = replicationFailQueue.take();
                if (node.getNodeStatus() != NodeStatus.LEADER) {
                    replicationFailQueue.clear();
                    continue;
                }

                Callable callable = model.getCallable();
                Future<Boolean> future = RaftThreadPool.submit(callable);
                Boolean r = future.get(3000, MILLISECONDS);
                if (r) {
                    tryApplyStateMachine(model);
                }
            } catch (Exception ignore) {
            }
        }
    }

    private void tryApplyStateMachine(ReplicationFailModel model) {

        String success = node.getStateMachine().getString(model.getSuccessKey());
        node.stateMachine.setString(model.successKey, String.valueOf(Integer.valueOf(success) + 1));

        String count = stateMachine.getString(model.countKey);

        if (Integer.valueOf(success) >= Integer.valueOf(count) / 2) {
            stateMachine.apply(model.logEntry);
            stateMachine.delString(model.countKey, model.successKey);
        }
    }
}
