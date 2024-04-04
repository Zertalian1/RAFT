package org.example.node;

import lombok.NoArgsConstructor;
import org.example.log.entity.Command;
import org.example.log.entity.LogEntry;

@NoArgsConstructor
public class CASReplDict extends SyncObjConsumer{
    public void set(String key, String value, String oldValue) {
        for (int i = 0; i < 10; i++) {
            if (!logModule.lockOperation(key)) {
                continue;
            }
            String oldValueReal;
            String[] oldValueRealArr = logModule.get(key);
            if (oldValueRealArr != null) {
                oldValueReal = oldValueRealArr[0];
            } else {
                oldValueReal = "0";
            }
            if (oldValueReal.equals(oldValue)) {
                LogEntry logEntry = new LogEntry();
                logEntry.setCommand(new Command("set", new String[]{key, value}));
                replicationService.addEntry(logEntry);
            }
            logModule.unlockOperation(key);
        }
    }

    public String get(String key) {
        for (int i = 0; i < 10; i++) {
            if (!logModule.lockOperation(key)) {
                continue;
            }
            String[] value = logModule.get(key);
            logModule.unlockOperation(key);
            if (value != null) {
                return value[0];
            }
            return null;
        }
        System.out.println("Can't read value");
        return null;
    }
}
