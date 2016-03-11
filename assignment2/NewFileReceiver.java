// Zheng Weihan (A0097582N)

import java.net.*;
import java.net.SocketAddress;
import java.io.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.HashMap;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

class NewFileReceiver {
  public static final int HEADERSIZE = 11;
  int port;
  int sendPort;
  InetAddress sendAddress;
  DatagramSocket rcvSocket;
  DatagramSocket sendSocket;
  String fileName;
  FileOutputStream fos;
  BufferedOutputStream bos;
  int lastAck = 0;
  boolean isLastPacket;
  HashMap<Integer, DatagramPacket> pendingPackets;

  //for performance tracking
  boolean firstTime = true;
  int pendingPacketCount = 0;

  public static void main(String[] args) throws Exception {

    // check if the number of command line argument is 1
    if (args.length != 1) {
      System.out.println("Usage: java FileReceiver port");
      System.exit(1);
    }

    new NewFileReceiver(args[0]);
  }

  public NewFileReceiver(String localPort) throws Exception {
    this.port = Integer.parseInt(localPort);
    this.rcvSocket = new DatagramSocket(this.port);
    this.sendSocket = new DatagramSocket();
    boolean hasReceivedFileName = false;
    this.pendingPackets = new HashMap<Integer, DatagramPacket>();
    this.firstTime= true;
    long starttime = 0;

    while (true){
      byte[] receivePkt = new byte[1000];
      DatagramPacket dataPacket = new DatagramPacket(receivePkt, 1000);
      rcvSocket.receive(dataPacket);
      if(this.firstTime){
        System.out.println("firsttime");
        starttime = System.currentTimeMillis();
        System.out.println(starttime);
        this.firstTime = false;
      }
      // System.out.printf("received Packet: %d, size: %d, seqNum: %d expectedSeqNum: %d\n", calculateChecksum(dataPacket.getData(), dataPacket.getLength()), dataPacket.getLength(), getSeqNum(dataPacket), lastAck);
      this.sendPort = dataPacket.getPort();
      this.sendAddress = dataPacket.getAddress();
      if(calculateChecksum(dataPacket.getData(), dataPacket.getLength()) != 0){
        sendAck(this.lastAck);
        continue;
      }else{
        int seqNum = getSeqNum(dataPacket);
        if(seqNum == 0 && !hasReceivedFileName){
          // System.out.printf ("seqNum: %d", seqNum);
          sendAck(seqNum + dataPacket.getLength() - HEADERSIZE);
          this.lastAck = seqNum + dataPacket.getLength() - HEADERSIZE;
          this.fileName = getFileNameFromPacket(dataPacket);
          File file = new File(fileName);
          hasReceivedFileName = true;
          file.createNewFile();
          System.out.printf("filename: %s", fileName);
          this.fos = new FileOutputStream(file);
          this.bos = new BufferedOutputStream(fos);
        }else if(seqNum > 0 && hasReceivedFileName){
          processPacket(dataPacket);
        }else{
          sendAck(lastAck);
        }
        if(this.isLastPacket){
          System.out.println("LAST PACKET RCVED. CLOSE SHOP");
          System.out.printf("pending packets count: %d\n", this.pendingPacketCount);
          System.out.printf("timetaken: %d\n", (System.currentTimeMillis() - starttime));
          break;

        }
      }
    }
  }
    public void processPacket(DatagramPacket packet) throws Exception{
      int seqNum = getSeqNum(packet);
      if(seqNum == lastAck){
        this.isLastPacket = checkIfLastPacket(packet);
        System.out.printf("packet seq num: %d\n", seqNum);
        sendAck(seqNum + packet.getLength() - HEADERSIZE);
        lastAck = seqNum + packet.getLength() - HEADERSIZE;
        // System.out.printf("data: %s\n", new String(packet.getData(), HEADERSIZE, packet.getLength() - HEADERSIZE));
        // System.out.printf("selected bytes %c, %c\n", packet.getData()[11], packet.getData()[999]);
        bos.write(packet.getData(), HEADERSIZE, packet.getLength() - HEADERSIZE);
        if(isLastPacket){
          for(int i =0; i< 100; i++){
            sendAck(lastAck);
          }
          this.rcvSocket.close();
          this.sendSocket.close();
          this.bos.close();
          this.fos.close();
        }else{
          checkPendingPackets();
        }
      }else{
        addToPendingPackets(packet, seqNum);
        sendAck(lastAck);
      }
    }
    public static String getFileNameFromPacket(DatagramPacket packet){
      return new String(packet.getData(), HEADERSIZE, packet.getLength() - HEADERSIZE);
    }

    public static boolean checkIfLastPacket(DatagramPacket packet){
      byte flag = packet.getData()[10];
      // System.out.printf("lastPacketflag: %d", flag);
      if((int)flag == 0x00000001){
        return true;
      }
      return false;
    }

    public void addToPendingPackets(DatagramPacket packet, int seqNum){
      if(this.pendingPackets.containsKey((new Integer(getSeqNum(packet)))) || seqNum < lastAck){
        return;
      }else{
        // System.out.printf("putting into pending packets: %d, packetseq: %d\n", seqNum, getSeqNum(packet));
        this.pendingPackets.put(getSeqNum(packet), packet);
      }
    }

    public void checkPendingPackets() throws Exception{
      if(this.pendingPackets.containsKey(this.lastAck)){
        this.pendingPacketCount++;
        // System.out.println("taking from pending packets");
        DatagramPacket pkt = this.pendingPackets.remove(this.lastAck);

        this.processPacket(pkt);
      }else{
        sendAck(lastAck);
      }

    }

    public static int getSeqNum(DatagramPacket packet){
      byte[] data = packet.getData();
      return ((data[0] << 24 & 0xFF000000) | (data[1] << 16 & 0x00ff0000) | (data[2] << 8 & 0x0000FF00) | data[3] & 0x000000FF);
    }

    public void sendAck(int ackNum) throws Exception{
        byte[] data = new byte[HEADERSIZE];
        DatagramPacket packet = new DatagramPacket(data, HEADERSIZE, this.sendAddress, this.sendPort);
        data = setHeader(data, 0, ackNum);
        // System.out.printf("send ack: %d\n", ackNum);
        packet.setData(data);
        this.sendSocket.send(packet);
    }
    public static byte[] setHeader(byte[] packet, int seq, int ack){
      byte[] seqByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(seq).array();
      byte[] ackByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(ack).array();

      for(int i=0;i<4;i++){
        packet[i] = seqByte[i];
        packet[i+4] = ackByte[i];
      }
      // System.out.println((header[0] << 24 | header[1] << 16 | header[2]<<8 | header[3]));
      int packetSize = packet.length;
      int checksum = calculateChecksum(packet, packetSize);
      // System.out.printf("checksumCal: %d\n", checksum);
      byte[] checksumByte = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(checksum).array();
      packet[8] = checksumByte[6];
      packet[9] = checksumByte[7];
      return packet;
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
