package org.example;

import javax.jms.JMSException;
import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws JMSException, IOException {
        String id;
        Scanner scanner = new Scanner(System.in);
        id = scanner.nextLine();
        ChatApp chatClient = new ChatApp("127.0.0.1:61616", id);
        //TopicChatClient chatClient = new TopicChatClient("127.0.0.1:61616", id);
        chatClient.start();
    }
}