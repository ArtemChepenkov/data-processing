package ru.chepenkov;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

public class Main {
    static int BUFFER_SIZE = 80;
    static int THREAD_AMOUNT = 2;
    static int DELAY_MILLS = 500;
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        char[] buffer = new char[80];
        MyLinkedList myLinkedList = new MyLinkedList();

        for (int i = 0; i < THREAD_AMOUNT; i++) {
            new Thread(new SortingThread(myLinkedList, DELAY_MILLS)).start();
        }

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
                //System.out.println("Steps so far: " + StepCounter.steps.get());
            }
        }).start();

        while (true) {
            int charsRead = in.read(buffer, 0, BUFFER_SIZE);

            if (charsRead == -1) {
                System.out.println("end");
                break;
            } else if (charsRead == 1) {
                myLinkedList.printList();
                continue;
            }
            String str = new String(buffer, 0, charsRead);
            str = str.replace("\n", "").replace("\r", "");
            myLinkedList.addFirst(str);
        }
    }
}