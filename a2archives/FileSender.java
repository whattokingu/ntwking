// Zheng Weihan (A0097582N)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
// import System.arraycopy;

class FileSender {
    int HEADERSIZE=16;
    int port;
    InetAddress address;

    public static void main(String[] args) throws Exception{
        // check if the number of command line argument is 4
        if (args.length != 4) {
            System.out.println("Usage: java FileSender <path/filename> "
                                   + "<rcvHostName> <rcvPort> <rcvFileName>");
            System.exit(1);
        }

        new FileSender(args[0], args[1], args[2], args[3]);
        // new FileSender();
    }
    public FileSender() throws Exception{
     byte[] sendData =  ("sendingfilename: abc").getBytes();
     byte[] sendArray = new byte[sendData.length + HEADERSIZE];
     this.address = InetAddress.getByName("localhost");
     this.port = 9000;
     System.arraycopy(sendData, 0, sendArray, HEADERSIZE, sendData.length);
     sendArray = setHeader(sendArray, this.address.getAddress(), this.port, 10, 10);
     long checksum = calculateChecksum(sendArray, sendArray.length);
     System.out.printf("checksum: %d\n", checksum);
    }
    public FileSender(String fileToOpen, String host, String port, String rcvFileName) throws Exception {

        // Refer to Assignment 0 Ex #4 on how to open a file with BufferedInputStream


        // UDP transmission is unreliable. Sender may overrun
        // receiver if sending too fast, giving packet lost as a result.
        // In that case, sender may need to pause sending once in a while.
        // E.g., Thread.sleep(1); // pause for 1 millisecond

       InetAddress serverAddress = InetAddress.getByName(host);
       int serverPort = Integer.parseInt(port);
       this.port = serverPort;
       this.address = serverAddress;
       DatagramSocket clientSocket = new DatagramSocket();
       int seqNum = 0;
       int fileSize = 0;
       int lastAck = 0;
        //first packet is filename;
       byte[] sendData =  ("sendingfilename: " + rcvFileName).getBytes();
       byte[] sendArray = new byte[sendData.length + HEADERSIZE];
       System.arraycopy(sendData, 0, sendArray, HEADERSIZE, sendData.length);
       sendArray = setHeader(sendArray, this.address.getAddress(), this.port, 0, 0);
       DatagramPacket sendPacket = new DatagramPacket(sendArray, sendArray.length, serverAddress, serverPort);
       clientSocket.send(sendPacket);
        System.out.printf("firstpacket length: %d, seqNum: %d, checksum: %d\n", sendArray.length, seqNum, calculateChecksum(sendArray, sendArray.length));
       seqNum += sendArray.length - HEADERSIZE;

       FileInputStream fis = new FileInputStream(fileToOpen);
       BufferedInputStream bis = new BufferedInputStream(fis, 1000 - HEADERSIZE);

       while(bis.available() > 0){
         if(bis.available() < 1000 - HEADERSIZE){
           int bytesLeft = bis.available();
           byte[] data = new byte[bytesLeft + HEADERSIZE];
           fileSize += data.length - HEADERSIZE;
           bis.read(data, HEADERSIZE, data.length - HEADERSIZE);
           data = setHeader(data, this.address.getAddress(), this.port, seqNum, 123);
           System.out.printf("sendpacket: length: %d, seqNum: %d, checkSum: %d\n", data.length - HEADERSIZE, ((data[6] << 24 & 0xFF000000) | (data[7] << 16 & 0x00ff0000) | (data[8] << 8 & 0x0000FF00) | data[9] & 0x000000FF), calculateChecksum(data, data.length));
           sendPacket = new DatagramPacket(data, data.length, serverAddress, serverPort);
           clientSocket.send(sendPacket);
           seqNum += data.length - HEADERSIZE;
         }else{
           sendData = new byte[1000];
           bis.read(sendData, HEADERSIZE, sendData.length - HEADERSIZE);
           fileSize += sendData.length - HEADERSIZE;
           sendData = setHeader(sendData, this.address.getAddress(), this.port, seqNum, 0);
           System.out.printf("sendpacket: length: %d seqNum: %d, checkSum: %d\n", sendData.length - HEADERSIZE, ((sendData[6] << 24 & 0xFF000000) | (sendData[7] << 16 & 0x00ff0000) | (sendData[8] << 8 & 0x0000FF00) | sendData[9] & 0x000000FF), calculateChecksum(sendData, sendData.length));
           sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
           clientSocket.send(sendPacket);
           seqNum += sendData.length - HEADERSIZE;
         }
       }
       clientSocket.close();
       bis.close();
       fis.close();
    }

    public static byte[] setHeader(byte[] header, byte[] ip, int port, int seq, int ack){
      byte[] seqByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(seq).array();
      byte[] ackByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(ack).array();
      header[4] =(byte) (port & 0x0000FF00);
      header[5] =(byte) (port & 0x000000FF);
      for(int i=0;i<4;i++){
        header[i] =ip[i];
        header[i+6] = seqByte[i];
        header[i+10] = ackByte[i];
      }
      // System.out.println((header[0] << 24 | header[1] << 16 | header[2]<<8 | header[3]));
      int packetSize = header.length;
      int checksum = calculateChecksum(header, header.length);
      byte[] checksumByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(checksum).array();
      header[14] = checksumByte[2];
      header[15] = checksumByte[3];
      return header;
    }

    public static int calculateChecksum(byte[] packet, int length) {
      int currentByte = 0;
      int checksum = 0;
      int data;
      while(length >= 2){
        //combine 2 bytes into 1 int(long).
        data = (((packet[currentByte] << 8) & 0xFF00 ) | (packet[currentByte + 1] & 0xFF));
        checksum+= data;
        //check for overflow(16bit)
        if((checksum & 0xFFFF0000) > 0){
          checksum = checksum & 0xFFFF;
          checksum += 1;
        }
        currentByte += 2;
        length -= 2;
      }
      if(length > 0){
        checksum += ((packet[currentByte] << 8) & 0xFF00);
        if((checksum & 0xFFFF0000) > 0){
          checksum = checksum & 0xFFFF;
          checksum += 1;
        }
      }
      checksum = ~checksum;
      checksum = checksum & 0xFFFF;
      return checksum;
  }

}
