package org.example.node;

import lombok.Setter;
import org.example.log.service.LogModule;

@Setter
public abstract class SyncObjConsumer {
    protected ReplicationService replicationService;
    protected LogModule logModule;
}
