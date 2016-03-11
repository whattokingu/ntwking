// Zheng Weihan (A0097582N)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
// import System.arraycopy;

public class NewFileSender {
    public static final int HEADERSIZE=11;
    HashMap<Integer, DatagramPacket> sentPackets;
    int port;
    InetAddress address;
    int seqNum;
    int lastSeqNum = -1;
    int lastAck;
    DatagramSocket socket;
    SocketReceiver receiver;
    SocketSender sender;
    boolean hasFirstPktAcked = false;
    int firstAckNum = 0;
    ScheduledThreadPoolExecutor scheduler;
    boolean stopSignal = false;

    public class SocketReceiver extends Thread{
      boolean stopSignal = false;
      public void run(){
        try{
          byte[] rcvData = new byte[1000];
          DatagramPacket rcvPacket = new DatagramPacket(rcvData, 1000);
          while(!stopSignal){
            socket.receive(rcvPacket);
            if(calculateChecksum(rcvPacket.getData(), rcvPacket.getLength()) == 0){
              int ack = getAck(rcvPacket.getData());
              // System.out.printf("receive ack: %d, lastAck: %d\n", ack, lastAck );
              if(!hasFirstPktAcked && ack == firstAckNum){
                lastAck = firstAckNum;
                hasFirstPktAcked = true;
              }else if(hasFirstPktAcked && ack > firstAckNum && ack > lastAck){
                lastAck = ack;
              }else if(hasFirstPktAcked && ack > firstAckNum){
                // System.out.println("respond to ack.");
                DatagramPacket pkt = sentPackets.get(ack);
                if(pkt != null){
                  sender.addPacket(sentPackets.get(ack));
                }
              }
            }
          }
        }catch(SocketException e){
          this.stopSignal = true;

        }catch(Exception e){
          e.printStackTrace();

        }finally{
        }
        System.out.println("SocketReceiver exits");
      }
      public void stopWork(){
        this.stopSignal = true;
      }
    }

    public class HeartBeatTask implements Runnable{
      public HeartBeatTask(){

      }
      public void run(){
        System.out.println("pingping");
      }
    }
    public class SendPacketTask implements Runnable{
      DatagramPacket packet;
      int packetSeqNum;
      int repeatCount;
      public SendPacketTask(DatagramPacket packet, int seqNum, int repeat){
        this.packet = packet;
        this.packetSeqNum = seqNum;
        this.repeatCount = repeat;
      }
      public void run(){
        if(lastAck > packetSeqNum){
          // System.out.printf("cancellingg: %d\n", packetSeqNum);
          if(this.packetSeqNum == lastSeqNum){
            stopWork();
          }
          return;
        }
        sender.addPacket(packet);
        if(this.repeatCount == 0){
          return;
        }else if(this.repeatCount == -1){
          scheduler.schedule(new SendPacketTask(this.packet, this.packetSeqNum, -1), 200L, TimeUnit.MILLISECONDS);
        }else{
          scheduler.schedule(new SendPacketTask(this.packet, this.packetSeqNum, this.repeatCount - 1), 200L, TimeUnit.MILLISECONDS);
        }
    }
    public long calculateDelay(int packetSeqNum, int lastAck){
      if(packetSeqNum - lastAck > 30000 || lastAck > packetSeqNum){
        return 2000L;
      }else{
        return 100L;
      }
    }
  }
    public class SocketSender extends Thread{
      private ConcurrentLinkedQueue<DatagramPacket> buffer;
      private boolean stopSignal = false;
      public void run(){
        // System.out.println("running");
        buffer = new ConcurrentLinkedQueue<DatagramPacket>();
        try{
          while(!stopSignal){
            if(!this.buffer.isEmpty()){
              DatagramPacket pkt = buffer.poll();
              // System.out.printf("sending packet: %d\n", getSeqNum(pkt));
              socket.send(pkt);
            }
            // Thread.sleep(1);
          }
        }catch(Exception e){
          e.printStackTrace();
        }
        System.out.println("SocketSender exits");
      }
      public void stopWork(){
        this.stopSignal = true;
      }
      public void addPacket(DatagramPacket packet){
        this.buffer.add(packet);
      }
    }
    public static void main(String[] args) throws Exception{
        // check if the number of command line argument is 4
        if (args.length != 4) {
            System.out.println("Usage: java FileSender <path/filename> "
                                   + "<rcvHostName> <rcvPort> <rcvFileName>");
            System.exit(1);
        }

        new NewFileSender(args[0], args[1], args[2], args[3]);
        // new FileSender();
    }

    public NewFileSender(String fileToOpen, String host, String port, String rcvFileName) throws Exception {
      InetAddress serverAddress = InetAddress.getByName(host);
      int serverPort = Integer.parseInt(port);
      this.port = serverPort;
      this.address = serverAddress;
      this.socket = new DatagramSocket();
      this.seqNum = 0;
      this.lastAck = 0;
      this.sender = new SocketSender();
      this.sentPackets = new HashMap<Integer, DatagramPacket>();
      this.scheduler = new ScheduledThreadPoolExecutor(10);
      // scheduler.scheduleAtFixedRate(new HeartBeatTask(), 0L, 3000L);
      sender.start();
      receiver = new SocketReceiver();
      receiver.start();
      FileInputStream fis = new FileInputStream(fileToOpen);
      BufferedInputStream bis = new BufferedInputStream(fis, 1000 - HEADERSIZE);
      while(!hasFirstPktAcked){
        byte[] firstPkt = createFirstPacket(rcvFileName);
        this.firstAckNum = firstPkt.length - HEADERSIZE;
        DatagramPacket dataPacket = new DatagramPacket(firstPkt, firstPkt.length, this.address, this.port);
        System.out.println("sending first packet");
        this.sender.addPacket(dataPacket);
        Thread.sleep(10);
        this.seqNum = this.firstAckNum;
      }

      while(bis.available() > 0){
        boolean lastPacket = false;
        int size = bis.available();
        if(size > 1000 - HEADERSIZE){
          size = 1000 - HEADERSIZE;
        }else{
          lastPacket = true;
        }
        byte[] data = new byte[size + HEADERSIZE];
        bis.read(data, HEADERSIZE, size);
        if(lastPacket){
          System.out.printf("LAST PACKET:%d\n", this.seqNum);
          // System.out.printf("data: %s", new String(data, HEADERSIZE, data.length - HEADERSIZE));
          data = setHeader(data, this.seqNum, 0, true);
          this.lastSeqNum = this.seqNum;
          DatagramPacket dataPacket = new DatagramPacket(data, data.length, this.address, this.port);
          scheduler.schedule(new SendPacketTask(dataPacket, this.seqNum, 100), 1L, TimeUnit.MILLISECONDS);
          break;
        }else{
          // System.out.printf("sending packet:%d size: %d\n", this.seqNum, data.length);
          data = setHeader(data, this.seqNum ,0, false);
          // System.out.printf("data: %s", new String(data, HEADERSIZE, data.length - HEADERSIZE));
          // System.out.printf("selected bytes: %c, %c\n", data[11], data[999]);
          DatagramPacket dataPacket = new DatagramPacket(data, data.length, this.address, this.port);
          if(!sentPackets.containsKey(seqNum)){
            sentPackets.put(seqNum, dataPacket);
          }
          // System.out.println("queuepacket");
          scheduler.schedule(new SendPacketTask(dataPacket, this.seqNum, -1), 1L, TimeUnit.MILLISECONDS);
          seqNum += data.length - HEADERSIZE;
        }
        // Thread.sleep(10);
      }
      bis.close();
      fis.close();
      System.out.println("main thread exits");
    }

    public void stopWork(){
      System.out.println("STOPWORK");
      this.scheduler.shutdownNow();
      this.receiver.stopWork();
      this.sender.stopWork();
      this.socket.close();
      System.exit(0);
    }

    public static byte[] createFirstPacket(String rcvFileName){
      byte[] pkt = new byte[rcvFileName.length() + HEADERSIZE];
      byte[] fileNameByte = rcvFileName.getBytes();
      for(int i = 0; i < rcvFileName.length(); i++){
        pkt[i + HEADERSIZE] = fileNameByte[i];
      }
      pkt = setHeader(pkt, 0, 0, false);
      return pkt;
    }

    public static int getAck(byte[] data){
      return ((data[4] << 24 & 0xFF000000 | (data[5] << 16  & 0x00FF0000) | (data[6] << 8  & 0x0000FF00) | data[7] & 0x000000FF));
    }

    public static byte[] setHeader(byte[] header, int seq, int ack, boolean isLastPacket){
      byte[] seqByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(seq).array();
      byte[] ackByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(ack).array();
      for(int i=0;i<4;i++){
        header[i] = seqByte[i];
        header[i+4] = ackByte[i];
      }
      int packetSize = header.length;
      if( isLastPacket ){
        header[10] = (byte) 0x00000001;
      }
      int checksum = calculateChecksum(header, header.length);
      byte[] checksumByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(checksum).array();
      header[8] = checksumByte[2];
      header[9] = checksumByte[3];
      return header;
    }

    public static int getSeqNum(DatagramPacket packet){
      byte[] data = packet.getData();
      return ((data[0] << 24 & 0xFF000000) | (data[1] << 16 & 0x00ff0000) | (data[2] << 8 & 0x0000FF00) | data[3] & 0x000000FF);
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
