package org.example.server;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@ToString
@Getter
@Setter
@NoArgsConstructor
public class PeerSet implements Serializable {
    private final List<Peer> peers = new ArrayList<>();
    private volatile Peer leader;
    private int port;
    public void addPeer(Peer peer) {
        peers.add(peer);
    }

    public int size() {
        return peers.size()+1;
    }
}
