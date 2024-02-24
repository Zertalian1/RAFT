package org.example.rpc.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Request<T> implements Serializable {
    private int cmd = -1;
    private T body;
    private String url;
}
