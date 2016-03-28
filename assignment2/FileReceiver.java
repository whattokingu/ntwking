// Zheng Weihan (A0097582N)

import java.net.*;
import java.net.SocketAddress;
import java.io.*;
import java.util.HashMap;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

class FileReceiver {
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
  int pendingPacketCount = 0;
  int totalPacketsNum = 0;

  public static void main(String[] args) throws Exception {

    // check if the number of command line argument is 1
    if (args.length != 1) {
      System.out.println("Usage: java FileReceiver port");
      System.exit(1);
    }

    new FileReceiver(args[0]);
  }

  public FileReceiver(String localPort) throws Exception {
    this.port = Integer.parseInt(localPort);
    this.rcvSocket = new DatagramSocket(this.port);
    this.sendSocket = new DatagramSocket();
    boolean hasReceivedFileName = false;
    this.pendingPackets = new HashMap<Integer, DatagramPacket>();

    while (true){
      byte[] receivePkt = new byte[1000];
      DatagramPacket dataPacket = new DatagramPacket(receivePkt, 1000);
      rcvSocket.receive(dataPacket);
      // System.out.printf("received Packet: %d, size: %d, seqNum: %d expectedSeqNum: %d\n", calculateChecksum(dataPacket.getData(), dataPacket.getLength()), dataPacket.getLength(), getSeqNum(dataPacket), lastAck);
      this.sendPort = dataPacket.getPort();
      this.sendAddress = dataPacket.getAddress();
      //if packet is corrupted, then just send last ack.
      if(calculateChecksum(dataPacket.getData(), dataPacket.getLength()) != 0){
        sendAck(this.lastAck);
        continue;
      }else{
        int seqNum = getSeqNum(dataPacket);
        //process normal packet with a bool to control for when first packet has not arrived.
        if(hasReceivedFileName){
          processPacket(dataPacket);
          //receive first packet.
        }else if(seqNum == 0 && !hasReceivedFileName){
          // System.out.printf ("seqNum: %d", seqNum);
          sendAck(seqNum + dataPacket.getLength() - HEADERSIZE);
          this.lastAck = seqNum + dataPacket.getLength() - HEADERSIZE;
          this.fileName = getFileNameFromPacket(dataPacket);
          File file = new File(fileName);
          file.createNewFile();
          System.out.printf("filename: %s\n", fileName);
          this.fos = new FileOutputStream(file);
          this.bos = new BufferedOutputStream(fos);
          hasReceivedFileName = true;
          //seqnum wraparound
        }else{
          //should not come here. But either way, just sendAck.
          sendAck(lastAck);
        }
        if(this.isLastPacket){
          System.out.printf("total number of packets in file: %d\n", this.totalPacketsNum);
          break;

        }
      }
    }
  }
  //packet that got here is legit(no corruption)
    public void processPacket(DatagramPacket packet) throws Exception{
      int seqNum = getSeqNum(packet);
      //process the packet's content only if seqnum matches the next seqnum we are expecting.
      //if not we add it to pendingPackets, which we can then retrieve with `checkPendingPackets`.
      if(seqNum == lastAck){
        this.isLastPacket = checkIfLastPacket(packet);
        this.totalPacketsNum++;
        sendAck(seqNum + packet.getLength() - HEADERSIZE);
        lastAck = seqNum + packet.getLength() - HEADERSIZE;
        // System.out.printf("data: %s\n", new String(packet.getData(), HEADERSIZE, packet.getLength() - HEADERSIZE));
        // System.out.printf("selected bytes %c, %c\n", packet.getData()[11], packet.getData()[999]);
        bos.write(packet.getData(), HEADERSIZE, packet.getLength() - HEADERSIZE);
        if(isLastPacket){
          //repeat ack many times to give a high probability that the ack is received. Either way, sender
          //should self-terminate after some time.
          for(int i =0; i< 200; i++){
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
        //pending packets packets that arrived 'too early'. Store them and retrieve them with `checkPendingPackets`
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
      //add packet only if packet is 'too early' and has not been added.
      if(this.pendingPackets.containsKey((new Integer(getSeqNum(packet)))) || seqNum < lastAck){
        return;
      }else{
        // System.out.printf("putting into pending packets: %d, packetseq: %d\n", seqNum, getSeqNum(packet));
        this.pendingPackets.put(getSeqNum(packet), packet);
      }
    }

    public void checkPendingPackets() throws Exception{
      //if we have already received the current expected packet, then we can just retrieve from here, and process it.
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
    // first 4 bytes is sequence num, then 4 bytes of ack num,
    // then next 2 bytes of checksum. last byte is used to indicate last packet (non-zero).
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
