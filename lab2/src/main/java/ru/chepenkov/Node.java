package ru.chepenkov;

import lombok.Data;

import java.util.concurrent.locks.ReentrantLock;

@Data
public class Node implements Comparable<Node> {
    private String value;
    private Node next;
    public final ReentrantLock lock = new ReentrantLock();

    public Node(String value) {
        this.value = value;
    }

    @Override
    public int compareTo(Node o) {
        return this.value.compareTo(o.value);
    }
}
