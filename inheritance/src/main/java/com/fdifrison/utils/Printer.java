package com.fdifrison.utils;

public class Printer {

    public static void focus() {
        System.out.println("\nFocus from here ↓↓↓\n\n");
    }

    public static void focus(String text) {
        System.out.println("\nFocus from here ↓↓↓\n\n" + text + "\n\n");
    }

    public static void entity(Object entity) {
        System.out.println("\nEntity state \n" + entity.toString() + "\n\n");
    }
}
