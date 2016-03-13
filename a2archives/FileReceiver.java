// Zheng Weihan (A0097582N)

import java.net.*;
import java.net.SocketAddress;
import java.io.*;
import java.util.HashMap;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

class FileReceiver {
    int HEADERSIZE = 10;

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
        int seqNum = 0;
        int ack = 0;
        int fileSize = 0;
        int lastAck = 0;
        boolean receivedFileName = false;
        HashMap<Integer, byte[]> packets = new HashMap<Integer, byte[]>();
        HashMap<Integer, Integer> packetsLength = new HashMap<Integer, Integer>();


        while(true){
          DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
          rcvSocket.receive(receivePacket);
          byte[] data = receivePacket.getData();
          if(calculateChecksum(data, receivePacket.getLength()) != 0){
            SocketAddress address = receivePacket.getSocketAddress();
            byte[] ackMsg = new byte[10];
            ackMsg = setHeader(ackMsg, 0, lastAck);
            // DatagramPacket ackPkt = new DatagramPacket(ackMsg, ackMsg.length, address);
            // rcvSocket.send(ackPkt);
            //acklastAck;
            continue;
          }else{
            seqNum = ((data[0] << 24 & 0xFF000000) | (data[1] << 16 & 0x00ff0000) | (data[2] << 8 & 0x0000FF00) | data[3] & 0x000000FF);
            ack = ((data[4] << 24 & 0xFF000000) | (data[5] << 16 & 0x00ff0000) | (data[6] << 8 & 0x0000FF00) | data[7] & 0x000000FF);
            System.out.printf("seqNum: %d, ", seqNum);
            if(!receivedFileName && seqNum == 0){
              System.out.printf("firstpacket, size: %d\n", receivePacket.getLength());
              String sentence = new String(receivePacket.getData(), HEADERSIZE, receivePacket.getLength() - HEADERSIZE);
              System.out.println(sentence);
              if(sentence.contains("sendingfilename")){
                String fileName = sentence.split(": ")[1];
                System.out.printf("filename: \'%s\' \n",fileName);
                file = new File(fileName);
                file.createNewFile();
                fos = new FileOutputStream(file);
                bos = new BufferedOutputStream(fos);
                lastAck += receivePacket.getLength() - HEADERSIZE;
                receivedFileName = true;
                continue;
              }
            }else if(!receivedFileName){
              //ack0;
              continue;
            }
            //its not first packet.
            System.out.printf("received: %d, seqNum: %d, lastack: %d, ack: %d, nextACK: %d\n", receivePacket.getLength(), seqNum, lastAck, ack, seqNum + data.length - HEADERSIZE);
            if(seqNum > lastAck){
              packets.put(seqNum, data);
              packetsLength.put(seqNum, receivePacket.getLength());
              //ackcurrent
            }else if(seqNum == lastAck){
              bos.write(data, HEADERSIZE, receivePacket.getLength() - HEADERSIZE);
              int nextAck = seqNum + receivePacket.getLength() - HEADERSIZE;
              boolean hasNext = packets.containsKey(nextAck);
              while(hasNext){
                bos.write(packets.get(nextAck), HEADERSIZE, packetsLength.get(nextAck));
                nextAck += packets.get(nextAck).length;
                hasNext = packets.containsKey(nextAck);
              }
              lastAck = nextAck;
              System.out.println("about to close");
              if(ack > 0){
                System.out.println("close");
                rcvSocket.close();
                bos.close();
                fos.close();
                break;
              }
            }
          }
        }
    }

    public static byte[] setHeader(byte[] header, int seq, int ack){
      byte[] seqByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(seq).array();
      byte[] ackByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(ack).array();

      for(int i=0;i<4;i++){
        header[i] = seqByte[i];
        header[i+4] = ackByte[i];
      }
      // System.out.println((header[0] << 24 | header[1] << 16 | header[2]<<8 | header[3]));
      int packetSize = header.length;
      int checksum = calculateChecksum(header, header.length);
      System.out.printf("checksumCal: %d\n", checksum);
      byte[] checksumByte = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(checksum).array();
      header[8] = checksumByte[6];
      header[9] = checksumByte[7];
      return header;
    }

    public static int calculateChecksum(byte[] packet, int length) {
      int currentByte = 0;
      int checksum = 0;
      int data;
      while(length >= 2){
        //combine 2 bytes into 1 int(long).
        data = (((packet[currentByte] << 8) & 0x0000FF00 ) | (packet[currentByte + 1] & 0x000000FF));
        checksum+= data;
        //check for overflow(16bit)
        if((checksum & 0xFFFF0000) > 0){
          checksum = checksum & 0x0000FFFF;
          checksum += 1;
        }
        currentByte += 2;
        length -= 2;
      }
      if(length == 1){
        checksum += ((packet[currentByte] << 8) & 0x0000FF00);
        if((checksum & 0xFFFF0000) > 0){
          checksum = checksum & 0x0000FFFF;
          checksum += 1;
        }
      }
      if((checksum & 0xFFFF0000) > 0){
        checksum = checksum & 0x0000FFFF;
        checksum += 1;
      }
      checksum = ~checksum;
      checksum = checksum & 0xFFFF;
      return checksum;
  }
}
