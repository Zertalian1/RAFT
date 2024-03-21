package org.example.log.entity;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class LogEntry implements Serializable, Comparable<LogEntry> {
    private Long index;
    private long term;
    private Command command;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LogEntry logEntry = (LogEntry) o;
        return term == logEntry.term &&
                Objects.equals(index, logEntry.index) &&
                Objects.equals(command, logEntry.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, term, command);
    }

    @Override
    public int compareTo(LogEntry o) {
        if (o == null) {
            return -1;
        }
        if (this.index > o.getIndex()) {
            return 1;
        }
        return -1;
    }
}
