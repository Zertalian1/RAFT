package org.example.node;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class NodeConfig {
    public int selfPort;
    public List<String> peerAddress;

    public NodeConfig(int selfPort, List<String> peerAddress) {
        this.selfPort = selfPort;
        this.peerAddress = peerAddress;
    }
}
