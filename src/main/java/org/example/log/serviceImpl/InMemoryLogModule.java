package org.example.log.serviceImpl;

import org.example.log.entity.LogEntry;
import org.example.log.service.LogModule;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class InMemoryLogModule implements LogModule {
    private final Map<Long, LogEntry> log = new HashMap<>();
    private Long lastIndex = 0L;
    ReentrantLock lock = new ReentrantLock();

    @Override
    public void write(LogEntry logEntry) {
        boolean success = false;
        try {
            lock.tryLock(3000, MILLISECONDS);
            logEntry.setIndex(getLastIndex() + 1);
            log.put(logEntry.getIndex(), logEntry);
            success = true;
        } catch (InterruptedException ignored) {
        } finally {
            if (success) {
                updateLastIndex(logEntry.getIndex());
            }
            lock.unlock();
        }

    }

    @Override
    public LogEntry read(Long index) {
        if (log.size() <= index || index<0) {
            return null;
        }
        return log.get(index);
    }

    @Override
    public void removeOnStartIndex(Long startIndex) {
        boolean success = false;
        int count = 0;
        try {
            lock.tryLock(3000, MILLISECONDS);
            for (long i = startIndex; i <= getLastIndex(); i++) {
                log.remove(i);
                ++count;
            }
            success = true;
        } catch (InterruptedException ignored) {
        } finally {
            if (success) {
                updateLastIndex(getLastIndex() - count);
            }
            lock.unlock();
        }
    }

    private LogEntry getPreLog(LogEntry logEntry) {
        LogEntry entry = read(logEntry.getIndex() - 1);

        if (entry == null) {
            entry = new LogEntry();
            entry.setIndex(0L);
            entry.setTerm(0);
            entry.setCommand(null);
        }
        return entry;
    }

    @Override
    public LogEntry getLast() {
        return log.get(getLastIndex());
    }

    @Override
    public Long getLastIndex() {
        return lastIndex;
    }

    private void updateLastIndex(Long index) {
        lastIndex = index;
    }
}
