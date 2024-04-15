package org.example.distributedBlocking;

import lombok.NoArgsConstructor;
import org.example.log.entity.Command;
import org.example.log.entity.LogEntry;
import org.example.node.ReplError;
import org.example.node.SyncObjConsumer;

import java.math.BigInteger;
import java.util.concurrent.Semaphore;

@NoArgsConstructor
public class CASReplDict extends SyncObjConsumer {
    private BigInteger counter = BigInteger.ZERO;
    private final Semaphore mutex = new Semaphore(1);

    private String send(String key, String value, Long ttl) throws ReplError {
        counter = counter.add(BigInteger.ONE);
        LogEntry logEntry = new LogEntry();
        long current = System.currentTimeMillis();
        logEntry.setCommand(new Command("set", new String[]{
                key,
                String.join(".",value, counter.toString(), ttl.toString(), String.valueOf(current))
        }));
        try {
            replicationService.addEntry(logEntry);
        } catch (ReplError e) {
            mutex.release();
            System.out.println(e.getMessage());
            throw e;
        }
        return counter.toString();
    }

    /*
    * должны сохраннить:
    *  1) значение
    *  2) уникальный код
    *  3) время начала
    *  4) срок действия
    */
    private String compareAndSwap(String key, String value, String oldValue, Long ttl, String code) throws ReplError {

        if (!mutex.tryAcquire()) {
            return null;
        }
        String oldValueReal = "";
        if (logModule.get(key) != null) {
            oldValueReal = logModule.get(key)[0];
        }
        if (!oldValueReal.isEmpty()) {
            String[] params = oldValueReal.split("\\.");
            if (params[0].equals("0") && params[0].equals(oldValue) && value.equals("1")) {
                String result = send(key, value, ttl);
                mutex.release();
                return result;
            }
            if (params[0].equals("1") && params[1].equals(code)) {
                String result = send(key, value, ttl);
                mutex.release();
                return result;
            }
        } else if(oldValue.equals("0")){
            String result = send(key, value, ttl);
            mutex.release();
            return result;
        }
        mutex.release();
        return null;
    }

    private String simpleLock(Lock lock) {
        try {
            return compareAndSwap(lock.getName(), "1", "0", lock.getTtl(), lock.getVersion());
        } catch (ReplError e) {
            return null;
        }
    }

    public String lock(Lock lock) {
        String locked;
        for (int i = 0;; i++) {
            System.out.println("Attempt" + i);
            locked = simpleLock(lock);
            if (locked != null ) {
                return locked;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public String renew(Lock lock) {
        try {
            return compareAndSwap(lock.getName(), "1", "1", lock.getTtl(), lock.getVersion());
        } catch (ReplError e) {
            return null;
        }
    }

    public String unlock(Lock lock) {
        try {
            return compareAndSwap(lock.getName(), "0", "1", -1L, lock.getVersion());
        } catch (ReplError e) {
            return null;
        }
    }
}
