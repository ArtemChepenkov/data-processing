package ru.chepenkov;

public class MyLinkedList {
    private final Node head = new Node(""); // фиктивная голова

    public Node getHead() { return head; }

    public void addFirst(String value) {
        Node n = new Node(value);
        head.lock.lock();
        try {
            n.setNext(head.getNext());
            head.setNext(n);
        } finally {
            head.lock.unlock();
        }
    }

    public void printList() {
        Node prev = head;
        prev.lock.lock();
        try {
            Node cur = prev.getNext();
            while (cur != null) {
                cur.lock.lock();
                try {
                    System.out.println(cur.getValue());
                    prev.lock.unlock();
                    prev = cur;
                    cur = cur.getNext();
                } finally {
                    // следующий lock/unlock пары продолжаются в цикле
                }
            }
        } finally {
            prev.lock.unlock();
        }
    }
}
