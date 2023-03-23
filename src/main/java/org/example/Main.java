package org.example;

import javax.jms.JMSException;
import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws JMSException, IOException {
        String id;
        Scanner scanner = new Scanner(System.in);
        id = scanner.nextLine();
        ChatApp chatClient = new ChatApp("1.12.51.41:61616", id, "1.12.51.41", "21", "user", "userpwd");
        chatClient.start();
    }
}