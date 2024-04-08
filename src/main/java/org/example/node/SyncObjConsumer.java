package org.example.node;

import lombok.Getter;
import lombok.Setter;
import org.example.log.service.LogModule;

@Setter
@Getter
public abstract class SyncObjConsumer {
    protected ReplicationService replicationService;
    protected LogModule logModule;
}
