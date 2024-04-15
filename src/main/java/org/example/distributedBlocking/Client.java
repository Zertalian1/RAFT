package org.example.distributedBlocking;

import lombok.AllArgsConstructor;

import java.util.Queue;
import java.util.concurrent.Semaphore;

@AllArgsConstructor
public class Client implements Runnable{
    Queue<Integer> comandQueue;
    Integer index;
    CASReplDict consumer;

    @Override
    public void run() {
        try {
            Lock lock = new Lock("test", 10000L, "");
            while (true) {
                Integer element = comandQueue.peek();
                if (element != null && index == element) {
                    comandQueue.poll();
                    String uid = consumer.unlock(lock);
                    if (uid == null) {
                        System.out.println("Не получилось отпустить мьютекс");
                    } else {
                        System.out.println("Мьютекс отпущен uid="+uid);
                    }
                }
                if (element != null && index+1 == element) {
                    comandQueue.poll();
                    String uid = consumer.lock(lock);
                    if (uid == null) {
                        System.out.println("Не получилось залочить мьютекс");
                    } else {
                        System.out.println("Мьютекс залочен uid="+uid);
                    }
                    lock.setVersion(uid);
                }
                if (element != null && index+2 == element) {
                    comandQueue.poll();
                    String uid = consumer.renew(lock);
                    if (uid == null) {
                        System.out.println("Не получилось залочить мьютекс");
                    } else {
                        System.out.println("Мьютекс залочен uid="+uid);
                    }
                    lock.setVersion(uid);
                }
                Thread.sleep(400);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}
