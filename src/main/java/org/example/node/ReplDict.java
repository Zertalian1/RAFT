package org.example.node;

import lombok.NoArgsConstructor;
import org.example.log.entity.Command;
import org.example.log.entity.LogEntry;

@NoArgsConstructor
public class ReplDict extends SyncObjConsumer{

    public void set(String key, String value) {
        LogEntry logEntry = new LogEntry();
        logEntry.setCommand(new Command("KVStorage", new String[] {key, value}));
        replicationService.addEntry(logEntry);
    }

    public String get(String key) {
        String[] value = logModule.get("KVStorage", key);
        if (value != null) {
            return value[0];
        }
        return null;
    }
}
