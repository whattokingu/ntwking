// Zheng Weihan (A0097582N)

import java.net.*;
import java.io.*;
class FileSender {

    public DatagramSocket socket;
    public DatagramPacket pkt;

    public static void main(String[] args) throws Exception{

        // check if the number of command line argument is 4
        if (args.length != 4) {
            System.out.println("Usage: java FileSender <path/filename> "
                                   + "<rcvHostName> <rcvPort> <rcvFileName>");
            System.exit(1);
        }

        new FileSender(args[0], args[1], args[2], args[3]);
    }

    public FileSender(String fileToOpen, String host, String port, String rcvFileName) throws Exception {

        // Refer to Assignment 0 Ex #4 on how to open a file with BufferedInputStream

        // UDP transmission is unreliable. Sender may overrun
        // receiver if sending too fast, giving packet lost as a result.
        // In that case, sender may need to pause sending once in a while.
        // E.g., Thread.sleep(1); // pause for 1 millisecond

       InetAddress serverAddress = InetAddress.getByName(host);
       int serverPort = Integer.parseInt(port);
       DatagramSocket clientSocket = new DatagramSocket();
        //first packet is filename;
       byte[] sendData =  ("sending file: " + rcvFileName).getBytes();
       DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
       clientSocket.send(sendPacket);

       FileInputStream fis = new FileInputStream(fileToOpen);
       BufferedInputStream bis = new BufferedInputStream(fis, 1000);
       while(bis.available() > 0){
         if(bis.available() < 1000){
           int bytesLeft = bis.available();
           byte[] data = new byte[bytesLeft];
           bis.read(data, 0, data.length);
           sendPacket = new DatagramPacket(data, data.length, serverAddress, serverPort);
           Thread.sleep(1);
           clientSocket.send(sendPacket);
         }else{
           sendData = new byte[1000];
           Thread.sleep(1);
           bis.read(sendData, 0, sendData.length);
           sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
           clientSocket.send(sendPacket);
         }
       }
       Thread.sleep(1);
       sendData = ("sendfileend").getBytes();
       sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
       clientSocket.send(sendPacket);
       System.out.printf("send file end. closing streams.\n");
       clientSocket.close();
       bis.close();
       fis.close();
    }
}
