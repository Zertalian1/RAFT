package org.example.distributedBlocking;

import lombok.*;
import org.example.log.entity.Command;
import org.example.log.entity.LogEntry;
import org.example.log.service.LogModule;
import org.example.node.DefaultNode;
import org.example.node.NodeStatus;
import org.example.node.ReplError;
import org.example.node.ReplicationService;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TtlTask  implements Runnable{
    private DefaultNode node;
    private LogModule logModule;
    private ReplicationService replicationService;

    @Override
    public void run() {
        if (node.getNodeStatus() != NodeStatus.LEADER) {
            return;
        }
        node.lokMutex();
        if (node.getNodeStatus() != NodeStatus.LEADER) {
            node.unlockMutex();
            return;
        }
        Map<String, String[]> entry = logModule.getAll();
        entry.forEach( (String key, String[] value) -> {
            String[] params = value[0].split("\\.");
            if (params[0].equals("1")) {
                long current = System.currentTimeMillis();
                if (current-Long.parseLong(params[3], 10)>Long.parseLong(params[2], 10)) {
                    LogEntry logEntry = new LogEntry();
                    logEntry.setCommand(new Command("set", new String[]{key, "0"}));
                    try {
                        replicationService.addEntryNotSave(logEntry);
                        System.out.println("мьютекс с именем "+key+" освобождён автоматически");
                    } catch (ReplError ignored) {}
                }
            }
        });
        node.unlockMutex();
    }
}
