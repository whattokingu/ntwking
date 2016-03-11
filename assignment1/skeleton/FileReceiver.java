// Zheng Weihan (A0097582N)

import java.net.*;
import java.io.*;

class FileReceiver {

    public DatagramSocket socket;
    public DatagramPacket pkt;

    public static void main(String[] args) throws Exception {

        // check if the number of command line argument is 1
        if (args.length != 1) {
            System.out.println("Usage: java FileReceiver port");
            System.exit(1);
        }

        new FileReceiver(args[0]);
    }

    public FileReceiver(String localPort) throws Exception {
        DatagramSocket rcvSocket = new DatagramSocket(Integer.parseInt(localPort));
        byte[] receiveData = new byte[1000];
        File file = null;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        int size = 0;
        boolean receivedFileName = false;
        while(true){
          DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        	rcvSocket.receive(receivePacket);
        	String sentence = new String(receivePacket.getData(), 0, receivePacket.getLength());
          if(sentence.contains("sending") && !receivedFileName){
            String fileName = sentence.split(": ")[1];
            System.out.printf("filename: \'%s\' \n",fileName);
            file = new File(fileName);
            file.createNewFile();
            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            receivedFileName = true;
          }else if(sentence.contains("sendfileend")){
            Thread.sleep(1);
            System.out.println("streams close");
            rcvSocket.close();
            bos.close();
            fos.close();
            break;
          }else{
            size = size + receivePacket.getLength();
            System.out.printf("received: %d  size: %d\n", receivePacket.getLength(), size);
            bos.write(receivePacket.getData(), 0, receivePacket.getLength());
          }
        }
    }
}
