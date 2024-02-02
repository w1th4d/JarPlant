package org.example.target;

public class Main {
    public static void main(String[] args) {
        System.out.println("This is main of the target app.");
        new Lib();

        System.gc();

        System.out.println("Pretending to do some heavy work...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
        }

        System.out.println("Done.");
        System.gc();
    }
}