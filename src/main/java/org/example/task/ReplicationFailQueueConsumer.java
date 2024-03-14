package org.example.task;

import org.example.node.DefaultNode;
import org.example.node.NodeStatus;
import org.example.node.entity.ReplicationFailModel;
import org.example.thread.RaftThreadPool;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/*Штука нужна для этого условия
* Если подписчик выходит из строя или работает медленно, или сеть теряет пакеты,
* ведущий будет продолжать повторять AppendEntries RPC (даже после ответа клиента)
* до тех пор, пока все подписчики окончательно не сохранят все записи журнала.*/
public class ReplicationFailQueueConsumer implements Runnable {
    private final DefaultNode node;
    private final LinkedBlockingQueue<ReplicationFailModel> replicationFailQueue = new LinkedBlockingQueue<>(2048);

    public ReplicationFailQueueConsumer(DefaultNode node) {
        this.node = node;
    }

    public void addToQueue(ReplicationFailModel model) throws InterruptedException {
        replicationFailQueue.put(model);
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
                if (model.getCountKey() > 5) {
                    continue;
                }
                Callable callable = model.getCallable();
                Future<Boolean> future = RaftThreadPool.submit(callable);
                Boolean r = future.get(3000, MILLISECONDS);
                if (r) {
                    tryApplyStateMachine(model);
                } else {
                    model.setCountKey(model.getCountKey()+1);
                }
            } catch (Exception ignore) {
            }
        }
    }

    private void tryApplyStateMachine(ReplicationFailModel model) {
        Integer success = node.getStateMachine().getSuccessIndex(model.getSuccessKey());
        if (success == null) {
            return;
        }
        node.stateMachine.setSuccessIndex(model.getSuccessKey(), success + 1);
        int count = node.getPeerSet().size();
        if (success < count / 2.0 && success+1 >= count / 2.0) {
            node.stateMachine.apply(model.getLogEntry());
            node.stateMachine.deleteSuccessIndex(model.getSuccessKey());
        }
    }
}
