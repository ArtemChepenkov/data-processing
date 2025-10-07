package ru.chepenkov;

public class SortingThread implements Runnable {
    private final MyLinkedList list;
    private final long delay;

    public SortingThread(MyLinkedList list, long delay) {
        this.list = list;
        this.delay = delay;
    }

    @Override
    public void run() {
        while (true) {
            boolean swapped = true;

            while (swapped) {
                swapped = false;
                Node prev = list.getHead();

                while (true) {
                    prev.lock.lock();
                    Node cur0 = null;
                    Node cur1 = null;

                    try {
                        cur0 = prev.getNext();
                        if (cur0 == null) break;

                        cur0.lock.lock();
                        try {
                            cur1 = cur0.getNext();
                            if (cur1 == null) break;

                            cur1.lock.lock();
                            try {
                                if (cur1.compareTo(cur0) < 0) {
                                    swapped = true;
                                    // переставляем cur0 и cur1
                                    cur0.setNext(cur1.getNext());
                                    prev.setNext(cur1);
                                    cur1.setNext(cur0);
                                }
                            } finally {
                                cur1.lock.unlock();
                            }
                        } finally {
                            cur0.lock.unlock();
                        }

                    } finally {
                        prev.lock.unlock();
                    }

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        return;
                    }

                    // сдвигаем prev — зависит от того, было ли переставлено
                    if (cur1 != null && cur1.compareTo(cur0) < 0) {
                        prev = cur1;
                    } else {
                        prev = cur0;
                    }
                }
            }

            try {
                Thread.sleep(1000); // пауза между циклами (как в C)
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
