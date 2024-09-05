package org.example.target;

public class Lib {
    public Lib() {
        System.out.println("This is an instance of the target library.");
    }

    private static void something() {
        System.out.println("This is a static method in the target library.");
    }

    static {
        System.out.println("This is a static block in the target library.");
        something();
    }
}
