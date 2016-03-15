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
  boolean stopSignal;
  int port;
  int sendPort;
  long starttime;
  InetAddress sendAddress;
  DatagramSocket rcvSocket;
  DatagramSocket sendSocket;
  String fileName;
  FileOutputStream fos;
  BufferedOutputStream bos;
  int lastAck = 0;
  boolean isLastPacket;
  HashMap<Integer, DatagramPacket> pendingPackets;
  ConcurrentLinkedQueue<DatagramPacket> processingQueue;

  SocketListener listener;

  //for performance tracking
  boolean firstTime = true;
  int pendingPacketCount = 0;
  int totalPacketsNum = 0;

  public static void main(String[] args) throws Exception {

    // check if the number of command line argument is 1
    if (args.length != 1) {
      System.out.println("Usage: java FileReceiver port");
      System.exit(1);
    }

    new NewFileReceiver(args[0]);
  }
  public class SocketListener extends Thread{
    boolean stopSignal = false;
    boolean firstTime= true;
    public void run(){
      try{
        while(!this.stopSignal){
          byte[] receivePkt = new byte[1000];
          DatagramPacket dataPacket = new DatagramPacket(receivePkt, 1000);
          rcvSocket.receive(dataPacket);
          if(this.firstTime){
            starttime = System.currentTimeMillis();
            System.out.println(starttime);
            sendPort = dataPacket.getPort();
            sendAddress = dataPacket.getAddress();
            this.firstTime = false;
          }
          if(calculateChecksum(dataPacket.getData(), dataPacket.getLength()) != 0){
            sendAck(lastAck);
            continue;
          }else{
            addPacketToQueue(dataPacket);
          }
        }
      }catch(SocketException e){

      }catch (Exception e){
        e.printStackTrace();
      }
    }

    public void addPacketToQueue(DatagramPacket packet){
      int seqNum = getSeqNum(packet);
      if(!pendingPackets.containsKey(seqNum)){
        processingQueue.add(packet);
      }
    }

    public void stopWork(){
      this.stopSignal = true;
    }
  }

  public NewFileReceiver(String localPort) throws Exception {
    this.stopSignal = false;
    this.port = Integer.parseInt(localPort);
    this.rcvSocket = new DatagramSocket(this.port);
    this.sendSocket = new DatagramSocket();
    boolean hasReceivedFileName = false;
    this.pendingPackets = new HashMap<Integer, DatagramPacket>();
    this.processingQueue = new ConcurrentLinkedQueue<DatagramPacket>();
    this.starttime = 0;
    this.listener = new SocketListener();
    listener.start();

    while (!this.stopSignal){
      if(!processingQueue.isEmpty()){
        DatagramPacket packet = processingQueue.poll();
        int seqNum = getSeqNum(packet);
        if(seqNum == 0 && !hasReceivedFileName){
          this.lastAck = seqNum + packet.getLength() - HEADERSIZE;
          sendAck(this.lastAck);
          this.fileName = getFileNameFromPacket(packet);
          File file = new File(fileName);
          hasReceivedFileName = true;
          file.createNewFile();
          System.out.printf("filename: %s", fileName);
          this.fos = new FileOutputStream(file);
          this.bos = new BufferedOutputStream(fos);
        }else if(seqNum > 0 && hasReceivedFileName){
          processPacket(packet);
        }else{
          sendAck(lastAck);
        }
        if(this.isLastPacket){
          this.stopSignal = true;
          stopWork();
          this.rcvSocket.close();
          this.sendSocket.close();
          this.bos.close();
          this.fos.close();
          System.out.println("LAST PACKET RCVED. CLOSE SHOP");
          System.out.printf("pending packets count: %d\n", this.pendingPacketCount);
          System.out.printf("total number of packets in file: %d\n", this.totalPacketsNum);
          System.out.printf("timetaken: %d\n", (System.currentTimeMillis() - starttime));
          break;
        }
      }

    }
  }
  public void stopWork(){
    this.listener.stopWork();
  }

    public void processPacket(DatagramPacket packet) throws Exception{
      int seqNum = getSeqNum(packet);
      if(seqNum == lastAck){
        this.isLastPacket = checkIfLastPacket(packet);
        System.out.printf("packet seq num: %d\n", seqNum);
        this.totalPacketsNum++;
        sendAck(seqNum + packet.getLength() - HEADERSIZE);
        lastAck = seqNum + packet.getLength() - HEADERSIZE;
        // System.out.printf("data: %s\n", new String(packet.getData(), HEADERSIZE, packet.getLength() - HEADERSIZE));
        // System.out.printf("selected bytes %c, %c\n", packet.getData()[11], packet.getData()[999]);
        bos.write(packet.getData(), HEADERSIZE, packet.getLength() - HEADERSIZE);
        if(isLastPacket){
          for(int i =0; i< 100; i++){
            sendAck(lastAck);
          }
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
        System.out.println("taken from pendingpackets");
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
    // first 4 bytes is sequence num, then 4 bytes of ack num,
    // then next 2 bytes of checksum. last byte is used to indicate last packet.
    // header size is 11 bytes.
    public static byte[] setHeader(byte[] packet, int seq, int ack){
      byte[] seqByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(seq).array();
      byte[] ackByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(ack).array();
      for(int i=0;i<4;i++){
        packet[i] = seqByte[i];
        packet[i+4] = ackByte[i];
      }

      int packetSize = packet.length;
      int checksum = calculateChecksum(packet, packetSize);
      byte[] checksumByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(checksum).array();
      packet[8] = checksumByte[2];
      packet[9] = checksumByte[3];
      return packet;
    }

    public static int calculateChecksum(byte[] packet, int length) {
      int currentByte = 0;
      int checksum = 0;
      int data;
      while(length - currentByte >= 2){
        //combine 2 bytes into 1 int(16bit).
        data = (((packet[currentByte] << 8) & 0x0000FF00 ) | (packet[currentByte + 1] & 0x000000FF));
        checksum+= data;
        //check for overflow(16bit)
        if((checksum & 0xFFFF0000) > 0){
          checksum = checksum & 0x0000FFFF;
          checksum += 1;
        }
        currentByte += 2;
      }
      //handle if odd num of bytes
      if(length - currentByte == 1){
        checksum += ((packet[currentByte] << 8) & 0x0000FF00);
        //handle overflow
        if((checksum & 0xFFFF0000) > 0){
          checksum = checksum & 0x0000FFFF;
          checksum += 1;
        }
      }
      // handle overflow
      if((checksum & 0xFFFF0000) > 0){
        checksum = checksum & 0x0000FFFF;
        checksum += 1;
      }
      //1's complement
      checksum = ~checksum;
      //take lower 16 bit
      checksum = checksum & 0x0000FFFF;
      return checksum;
    }
}
