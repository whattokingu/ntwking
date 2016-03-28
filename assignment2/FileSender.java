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

public class FileSender {
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

    //Thread to listen on socket.
    public class SocketReceiver extends Thread{
      boolean stopSignal = false;
      public void run(){
        try{
          byte[] rcvData = new byte[1000];
          DatagramPacket rcvPacket = new DatagramPacket(rcvData, 1000);
          while(!stopSignal){
            socket.receive(rcvPacket);
            //ignore corrupted packets.
            if(calculateChecksum(rcvPacket.getData(), rcvPacket.getLength()) == 0){
              int ack = getAck(rcvPacket.getData());
              // System.out.printf("receive ack: %d, lastAck: %d\n", ack, lastAck );
              if(!hasFirstPktAcked && ack == firstAckNum){
                lastAck = firstAckNum;
                hasFirstPktAcked = true;
              }else if(hasFirstPktAcked && ack > firstAckNum && ack > lastAck){
                //update acks if incoming ack is bigger.
                lastAck = ack;
              }else if(hasFirstPktAcked && ack > firstAckNum){
                // System.out.println("respond to ack.");
                //send packets that are 'acked'.
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
    //class representing a producer of packets to be sent and added to a queue.
    //This abstraction allows a scheduler(ScheduledThreadPoolExecutor) to schedule the packets easily.
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
        //drops task if ack number has surpass the packet's sequence number.
        if(lastAck > packetSeqNum){
          // System.out.printf("cancellingg: %d\n", packetSeqNum);
          if(this.packetSeqNum == lastSeqNum){
            stopWork();
          }
          return;
        }
        sender.addPacket(packet);
        //repeatCount keeps track of how many times a packet is to be resent
        if(this.repeatCount == 0){
          return;
        }else if(this.repeatCount == -1){
          scheduler.schedule(new SendPacketTask(this.packet, this.packetSeqNum, -1), calculateDelay(this.packetSeqNum, lastAck), TimeUnit.MILLISECONDS);
          // scheduler.schedule(new SendPacketTask(this.packet, this.packetSeqNum, -1), 200L, TimeUnit.MILLISECONDS);
        }else{
          scheduler.schedule(new SendPacketTask(this.packet, this.packetSeqNum, this.repeatCount - 1), calculateDelay(this.packetSeqNum, lastAck), TimeUnit.MILLISECONDS);
          // scheduler.schedule(new SendPacketTask(this.packet, this.packetSeqNum, this.repeatCount - 1), 200L, TimeUnit.MILLISECONDS);
        }
      }
    }
    //a class that sends packets that arrives in a queue.
    //functions as the consumer of the queue.
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

        new FileSender(args[0], args[1], args[2], args[3]);
        // new FileSender();
    }
    //Main class that init the other classes/Threads.
    //also reads the file to be sent.
    public FileSender(String fileToOpen, String host, String port, String rcvFileName) throws Exception {
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
      sender.start();
      receiver = new SocketReceiver();
      receiver.start();
      FileInputStream fis = new FileInputStream(fileToOpen);
      BufferedInputStream bis = new BufferedInputStream(fis, 1000 - HEADERSIZE);
      //first packet contains the filename and is therefore special. Only when the first packet is acked, would
      //the rest of the file be sent.
      while(!hasFirstPktAcked){
        byte[] firstPkt = createFirstPacket(rcvFileName);
        this.firstAckNum = firstPkt.length - HEADERSIZE;
        DatagramPacket dataPacket = new DatagramPacket(firstPkt, firstPkt.length, this.address, this.port);
        System.out.println("sending first packet");
        this.sender.addPacket(dataPacket);
        Thread.sleep(10);
        this.seqNum = this.firstAckNum;
      }

      // At this point, first packet is sent and acked. The rest of the file is then sent.
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

        //last packet is special. This can be better designed to combine the last packet/ordinary packet branch.
        if(lastPacket){
          // System.out.printf("data: %s", new String(data, HEADERSIZE, data.length - HEADERSIZE));
          data = setHeader(data, this.seqNum, 0, true);
          this.lastSeqNum = this.seqNum;
          DatagramPacket dataPacket = new DatagramPacket(data, data.length, this.address, this.port);

          // packets are not sent directly.
          //Instead, a scheduler would schedule when the packet is sent based on `calculateDelay`.
          scheduler.schedule(new SendPacketTask(dataPacket, this.seqNum, 1000), calculateDelay(this.seqNum, this.lastAck), TimeUnit.MILLISECONDS);
          break;
        }else{
          // System.out.printf("sending packet:%d size: %d\n", this.seqNum, data.length);
          data = setHeader(data, this.seqNum ,0, false);

          DatagramPacket dataPacket = new DatagramPacket(data, data.length, this.address, this.port);
          //'archive' the packets. When ack comes in, we can then send the packet immediately.
          if(!sentPackets.containsKey(seqNum)){
            sentPackets.put(seqNum, dataPacket);
          }
          // System.out.println("queuepacket");
          scheduler.schedule(new SendPacketTask(dataPacket, this.seqNum, -1), calculateDelay(this.seqNum, this.lastAck), TimeUnit.MILLISECONDS);
          seqNum += data.length - HEADERSIZE;
        }
        // Thread.sleep(1);
      }
      bis.close();
      fis.close();
      System.out.println("main thread exits");
    }

    public void stopWork(){
      this.scheduler.shutdownNow();
      this.receiver.stopWork();
      this.sender.stopWork();
      this.socket.close();
      System.exit(0);
    }
    //calculates delay based on how far the 'ack' number is from the packet's seqnum.
    //if lastack > packetSeqNum, then the task would actually stop scheduling itself.
    public long calculateDelay(int packetSeqNum, int lastAck){
      if(packetSeqNum - lastAck < 10000){
        return 2L;
      }else if(packetSeqNum - lastAck < 100000){
        return 25L;
      }else if(packetSeqNum - lastAck < 500000){
        return 50L;
      }else if(packetSeqNum - lastAck < 1000000){
        return 200L;
      }else{
        return 800L;
      }
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

    // first 4 bytes is sequence num, then 4 bytes of ack num,
    // then next 2 bytes of checksum. last byte is used to indicate last packet.
    // header size is 11 bytes.
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
