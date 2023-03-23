package org.example;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Scanner;

enum MsgType{
    TEXT((byte) 0), FILE((byte) 1);
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
    private String FTPHost;
    private String FTPPort;
    private String FTPUser;
    private String FTPPwd;
    private String fileSavePath;

    public String getFileSavePath() {
        return fileSavePath;
    }

    public void setFileSavePath(String fileSavePath) {
        this.fileSavePath = fileSavePath;
    }

    public String getFTPHost() {
        return FTPHost;
    }

    public void setFTPHost(String FTPHost) {
        this.FTPHost = FTPHost;
    }

    public String getFTPPort() {
        return FTPPort;
    }

    public void setFTPPort(String FTPPort) {
        this.FTPPort = FTPPort;
    }

    public String getFTPUser() {
        return FTPUser;
    }

    public void setFTPUser(String FTPUser) {
        this.FTPUser = FTPUser;
    }

    public String getFTPPwd() {
        return FTPPwd;
    }

    public void setFTPPwd(String FTPPwd) {
        this.FTPPwd = FTPPwd;
    }

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

    public ChatApp(String addr, String id, String FTPHost, String FTPPort, String FTPUser, String FTPPwd) throws JMSException {
        this.id = id;
        this.serverAddr=addr;
        this.connectionFactory = new ActiveMQConnectionFactory("tcp://" + serverAddr);
        this.FTPHost=FTPHost;
        this.FTPUser=FTPUser;
        this.FTPPort=FTPPort;
        this.FTPPwd=FTPPwd;
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
    }

    public void start() throws JMSException, IOException {
        Scanner scanner = new Scanner(System.in);
        MessageConsumer consumer;

        String destName;
        System.out.println("输入对方ID：");
        destName = scanner.nextLine();


        System.out.println("1/发送消息，2/发送文件：");
        String c = scanner.nextLine();
        if(c.equals("1")) {
            msgType= MsgType.TEXT;
        }
        else {
            msgType= MsgType.FILE;
        }

        System.out.println("输入文件存储位置：");
        fileSavePath= scanner.nextLine();

        destination = session.createQueue(String.format(patternQueue, destName));
        consumer = session.createConsumer(session.createQueue(String.format(patternQueue, id)));

        consumer.setMessageListener(new TextListener(this));

        System.out.println("输入消息内容/文件路径（输入exit退出，“forward:对方id” 转发）：");
        String sendMsg = scanner.nextLine();

        while(!sendMsg.equalsIgnoreCase("exit")){

            if(sendMsg.startsWith("forward:")){
                String forwardDest = sendMsg.substring(8);
                Destination forwardDestQueue = session.createQueue(String.format(patternQueue, forwardDest));
                if(lastMsgType == MsgType.TEXT){
                    sendQueueMsg(lastMsg, forwardDestQueue);
                } else{
                    sendQueueFile(lastFilePath, forwardDestQueue);
                }
            } else if(msgType == MsgType.TEXT){
                sendQueueMsg(sendMsg, destination);
            } else{
                sendQueueFile(sendMsg, destination);
            }
            sendMsg = scanner.nextLine();
        }
        session.close();
        connection.close();
    }

    /**
     * 向Queue发送文字信息
     * @param msg
     * @throws JMSException
     */
    public void sendQueueMsg(String msg, Destination msgDest) throws JMSException {
        MessageProducer producer = session.createProducer(msgDest);
        TextMessage textMessage = session.createTextMessage(msg);
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
        FileInputStream fileInputStream = new FileInputStream(file);
        String fileName = file.getName();
        String fileMD5 = FTPUtils.getMD5(filePath);
        FTPUtils.uploadFile(FTPHost, FTPUser, FTPPwd, Integer.valueOf(FTPPort), "/", fileMD5, fileInputStream);
        sendQueueMsg("file::"+fileName+","+fileMD5, msgDest);
        session.commit();
        fileInputStream.close();
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
        try {
            if(message.getStringProperty("sender")!=null){
                if(message.getStringProperty("sender").equals(this.client.getId())){
                    return;
                }
            }
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }

        if(message instanceof TextMessage) {
            // 普通消息
            TextMessage textMessage = (TextMessage) message;
            try {
                String text = textMessage.getText();
                if(text.startsWith("file::")){
                    String[] fileInfo=text.substring(6).split(",");
                    fileName=fileInfo[0];
                    FTPUtils.downloadFile(client.getFTPHost(), client.getFTPUser(), client.getFTPPwd(), Integer.parseInt(client.getFTPPort()),
                            "//", fileInfo[1], client.getFileSavePath(), fileName);

                    System.out.println("接收到文件：" + fileName);
                    this.client.setLastMsgType(MsgType.FILE);
                    this.client.setLastFilePath(client.getFileSavePath()+"/"+fileName);
                } else{
                    System.out.println(LocalDateTime.now() + " " + text);
                    this.client.setLastMsgType(MsgType.TEXT);
                    this.client.setLastMsg(text);
                }
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("无法处理的消息类型");
        }
    }

}