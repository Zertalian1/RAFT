package org.example.node;

import lombok.NoArgsConstructor;
import org.example.log.entity.Command;
import org.example.log.entity.LogEntry;

@NoArgsConstructor
public class ReplDict extends SyncObjConsumer{

    public void set(String key, String value) {
        LogEntry logEntry = new LogEntry();
        logEntry.setCommand(new Command("set", new String[] {key, value}));
        try {
            replicationService.addEntry(logEntry);
        } catch (ReplError e) {
            System.out.println(e.getMessage());
        }
    }

    public String get(String key) {
        String[] value = logModule.get(key);
        if (value != null) {
            return value[0];
        }
        return null;
    }
}
