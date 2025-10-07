package ru.chepenkov;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class Main1 {
    public static AtomicLong steps = new AtomicLong(); // глобальный счётчик шагов

    public static void main(String[] args) {
        List<String> list = Collections.synchronizedList(new ArrayList<>());

        int sorterThreads = 2;
        for (int i = 0; i < sorterThreads; i++) {
            new Thread(new ArraySorter(list, 20), "Sorter-" + i).start();
        }

        // отдельный поток будет выводить количество шагов каждые 2 секунды
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
                System.out.println("Steps so far: " + steps.get());
            }
        }).start();

        Scanner sc = new Scanner(System.in);
        while (true) {
            String line = sc.nextLine();
            if (line.isEmpty()) {
                synchronized (list) {
                    for (String s : list) {
                        System.out.println(s);
                    }
                    System.out.println("-----");
                }
                continue;
            }

            int idx = 0;
            while (idx < line.length()) {
                String part = line.substring(idx, Math.min(idx + 80, line.length()));
                list.add(0, part);
                idx += 80;
            }
        }
    }
}

class ArraySorter implements Runnable {
    private final List<String> list;
    private final long delayMillis;

    ArraySorter(List<String> list, long delayMillis) {
        this.list = list;
        this.delayMillis = delayMillis;
    }

    @Override
    public void run() {
        while (true) {
            synchronized (list) {
                for (int i = 0; i < list.size() - 1; i++) {
                    // шаг сортировки = попытка сравнения пары
                    Main1.steps.incrementAndGet();

                    if (list.get(i).compareTo(list.get(i + 1)) > 0) {
                        Collections.swap(list, i, i + 1);
                    }

                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }
}
