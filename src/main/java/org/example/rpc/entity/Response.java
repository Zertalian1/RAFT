package org.example.rpc.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Response<T> implements Serializable {
    private T result;
    public static Response<String> ok() {
        return new Response<>("ok");
    }
    public static Response<String> fail() {
        return new Response<>("fail");
    }
}
