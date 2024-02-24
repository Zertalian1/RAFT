package org.example.log.entity;

import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Command {
    private String key;
    private String value;
}
