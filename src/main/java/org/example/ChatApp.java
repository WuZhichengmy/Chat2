package org.example;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

enum MsgType{
    TEXT((byte) 0), FILE((byte) 1), STATE((byte) 2);
    final byte val;
    MsgType(byte i) {
        val=i;
    }
}

enum ChatType{
    QUEUE((byte) 0), TOPIC((byte) 1);
    final byte val;
    ChatType(byte i) {
        val=i;
    }
}

enum ClientState{
    ONLINE((byte) 0), OFFLINE((byte) 1);
    final byte val;
    ClientState(byte i){val=i;}
}

public class ChatApp {

    private static final String patternQueue = "queue:%s";
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private String serverAddr;
    private String id;
    private Destination destination;
    private String lastMsg; //上一条收到的消息
    private String lastFilePath; //上一次接收到的文件路径
    private MsgType msgType; //信息的类型
    private ChatType mode; //0为单发
    private MsgType lastMsgType; //上一条收到的消息类型
    private ClientState counterPartState = ClientState.ONLINE; //点对点通信对方的状态,默认为offline

    private List<Message> messages = new ArrayList<>(); //用来记录聊天信息

    public void addMessgeRecord(Message message){
        messages.add(message);
    }

    public void removeMessageRecord(Message message){
        messages.remove(message);
    }
    public ClientState getCounterPartState(){return counterPartState;};

    public void setCounterPartState(ClientState state){this.counterPartState = counterPartState;};

    public MsgType getLastMsgType() {
        return lastMsgType;
    }

    public void setLastMsgType(MsgType lastMsgType) {
        this.lastMsgType = lastMsgType;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLastMsg() {
        return lastMsg;
    }

    public void setLastMsg(String lastMsg) {
        this.lastMsg = lastMsg;
    }

    public String getLastFilePath() {
        return lastFilePath;
    }

    public void setLastFilePath(String lastFilePath) {
        this.lastFilePath = lastFilePath;
    }

    public MsgType getMsgType() {
        return msgType;
    }

    public void setMsgType(MsgType msgType) {
        this.msgType = msgType;
    }

    public ChatType getMode() {
        return mode;
    }

    public void setMode(ChatType mode) {
        this.mode = mode;
    }

    public String getId() {
        return id;
    }

    public ChatApp(String addr, String id) throws JMSException {
        this.id = id;
        this.serverAddr=addr;
        this.connectionFactory = new ActiveMQConnectionFactory("tcp://" + serverAddr);
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
    }

    public void start() throws JMSException, IOException {
        try {
            Scanner scanner = new Scanner(System.in);
            String type = scanner.nextLine();
            MessageConsumer consumer;

            String destName;
            System.out.println("输入对方ID：");
            destName = scanner.nextLine();

            destination = session.createQueue(String.format(patternQueue, destName));
            consumer = session.createConsumer(session.createQueue(String.format(patternQueue, id)));
            session.commit();
            consumer.setMessageListener(new TextListener(this));

            //send online state to counterpart
            sendClientState(ClientState.ONLINE, destination);

            System.out.println("1/发送消息，2/发送文件：");
            String c = scanner.nextLine();
            if(c.equals("1"))
                msgType=MsgType.TEXT;
            else
                msgType=MsgType.FILE;

            System.out.println("输入消息内容/文件路径（输入exit退出，“forward:对方id” 转发）：");
            String sendMsg = scanner.nextLine();

            while(!sendMsg.equalsIgnoreCase("exit")){

                if(sendMsg.startsWith("forward:")){
                    showLastRecords();
                    if( !this.messages.isEmpty()){
                        System.out.println("输入你要转发的消息序号: ");
                        String choice = scanner.nextLine();
                        int chosenMsgIndex = judgeAndGetDigitChoice(choice, scanner);

                        String forwardDest = sendMsg.substring(8);
                        forwardQueueMessage(chosenMsgIndex, forwardDest);

                        System.out.println("choice: " + chosenMsgIndex +" ok!");
                    }
                } else if(msgType == MsgType.TEXT){
                    sendQueueMsg(sendMsg, destination);
                } else if(msgType == MsgType.FILE){
                    sendQueueFile(sendMsg, destination);
                }
                sendMsg = scanner.nextLine();
            }
        }
        finally {
            //send offline state to counterpart
            sendClientState(ClientState.OFFLINE, destination);
            session.close();
            connection.close();
        }

    }

    /**
     * @param
     * @return: void
     * 显示之前的聊天记录（最多十条，可变）
     */
    public void showLastRecords() throws JMSException {
        int num = 10;
        if(this.messages.isEmpty()){
            System.out.println("records empty!");
        }
        if(this.messages.size() > num){
            for(int i = 0; i < num; i++ ){
                int k = messages.size() - num - i;
                TextMessage textMessage = (TextMessage) messages.get(k);
                System.out.println(k + ": " + textMessage.getText());
            }
        }else{
            for(int i = 0; i < messages.size(); i++){
                TextMessage textMessage = (TextMessage) messages.get(i);
                System.out.println(i + ": " + textMessage.getText());
            }
        }
    }

    /**
     * 判断输入数字是否符合
     * @param choice
     * @return
     */
    public int judgeAndGetDigitChoice(String choice, Scanner scanner){
        String pattern = "\\d+";
        if(choice.matches(pattern) && Integer.parseInt(choice) < messages.size()-1){
            return Integer.parseInt(choice);
        }else{
            String input;
            do{
                System.out.println("输入不合法，请重新输入数字序号:");
                input = scanner.nextLine();
            }while (!input.matches("\\d+") || Integer.parseInt(input) > messages.size()-1);
            return Integer.parseInt(input);
        }
    }

    /**
     * 区分消息类型，分别调用方法发送给新的通讯对象
     * @param chosenMsgIndex
     * @param forwardDest
     * @return: void
     */
    public void forwardQueueMessage(int chosenMsgIndex, String forwardDest) throws JMSException, IOException {
        Destination forwardDestQueue = session.createQueue(String.format(patternQueue, forwardDest));
        MessageProducer producer = session.createProducer(forwardDestQueue);
        TextMessage textMessage = (TextMessage)messages.get(chosenMsgIndex);
        String text = textMessage.getText();

        if(text.startsWith("file::")){  //如果是文件消息
            String fileName=text.substring(6);
            Path path = Paths.get(".//" + fileName);
            sendQueueFile(path.toAbsolutePath().toString(),forwardDestQueue);
        }else{                         //是普通消息
            sendQueueMsg(text,forwardDestQueue);
        }
    }

    /**
     * 向通信对方发送自己上线/下线的信息
     * @param state
     * @param msgDest
     * @throws JMSException
     */
    public void sendClientState(ClientState state ,Destination msgDest) throws JMSException{
        MessageProducer producer = session.createProducer(msgDest);
        String msg = getId();

        TextMessage textMessage = session.createTextMessage(msg);
        if(state == ClientState.ONLINE){
            textMessage.setStringProperty("counterPart",ClientState.ONLINE.toString());
        }else if(state == ClientState.OFFLINE){
            //offline is a default state
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            textMessage.setStringProperty("counterPart",ClientState.OFFLINE.toString());
        }

        producer.send(textMessage);
        session.commit();
    }

    /**
     * 向Queue发送文字信息
     * @param msg
     * @throws JMSException
     */
    public void sendQueueMsg(String msg, Destination msgDest) throws JMSException {
        MessageProducer producer = session.createProducer(msgDest);
        TextMessage textMessage = session.createTextMessage(msg);
        sendOfflineState(textMessage);
        producer.send(textMessage);
        session.commit();
    }

    /**
     * 向Queue以Byte形式发送文件
     * @param filePath
     * @throws IOException
     * @throws JMSException
     */
    public void sendQueueFile(String filePath, Destination msgDest) throws IOException, JMSException {
        File file = new File(filePath);
        String fileName = file.getName();
        byte[] bytes = Files.readAllBytes(file.toPath());
        MessageProducer producer = session.createProducer(msgDest);
        BytesMessage bytesMessage = session.createBytesMessage();
        bytesMessage.writeBytes(bytes);
        sendOfflineState(bytesMessage);
        sendQueueMsg("file::"+fileName, msgDest);
        producer.send(bytesMessage);
        session.commit();
    }

    /**
     * 若当前通信对象已经下线，为消息加上下线属性
     * @param message
     * @throws JMSException
     */
    public void sendOfflineState(Message message) throws JMSException {
        if(counterPartState == ClientState.OFFLINE){
            message.setStringProperty("offline","yes");
        }
    }

}

/**
 * 监听消息
 */
class TextListener implements MessageListener {
    private String fileName;

    private ChatApp client;

    public TextListener(ChatApp client){
        this.client = client;
    }

    @Override
    public void onMessage(Message message) {
        //上下线信息
        try {
            if(message.getStringProperty("counterPart")!=null){
                TextMessage textMessage = (TextMessage) message;
                if(message.getStringProperty("counterPart").equalsIgnoreCase(ClientState.ONLINE.toString())){
                    System.out.println("client "+textMessage.getText()+" online!");
                    client.setCounterPartState(ClientState.ONLINE);
                }else{
                    System.out.println("client "+textMessage.getText()+" offline! But you can still send msg!");
                    client.setCounterPartState(ClientState.OFFLINE);
                }
                return;
            }
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        //提示下线时收到消息
        try {
            if(message.getStringProperty("offline") != null){
                System.out.println("offline messages: ");
            }
        }catch (JMSException e){
            throw new RuntimeException(e);
        }

        if(message instanceof TextMessage) {
            // 普通消息
            TextMessage textMessage = (TextMessage) message;
            try {
                String text = textMessage.getText();
                if(text.startsWith("file::")){
                    fileName=text.substring(6);
                    System.out.println("接收到文件：" + fileName);
                } else{
                    System.out.println(LocalDateTime.now() + " " + text);
                    this.client.setLastMsgType(MsgType.TEXT);
                    this.client.setLastMsg(text);
                }
                this.client.addMessgeRecord(textMessage);
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
        } else if(message instanceof BytesMessage) {
            // 文件
            BytesMessage bytesMessage = (BytesMessage) message;
            byte[] bytes = new byte[1024];
            try {
                bytesMessage.readBytes(bytes);
                Path path = Paths.get(".//" + fileName);
                Files.write(path, bytes);
                this.client.setLastMsgType(MsgType.FILE);
                this.client.setLastFilePath(".//" + fileName);
            } catch (JMSException | IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("无法处理的消息类型");
        }
    }
}