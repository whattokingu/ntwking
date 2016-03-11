import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.Thread;
import java.util.LinkedList;
import java.util.Set;
import java.util.Timer;

class OldNewFileSender {
  public static final int HEADERSIZE = 16;
  public Sender sender;
  public String port;
  public String address;
  public Runnable socketListener;
  public int lastAckNum;
  public ConcurrentHashMap<Integer, byte[]> packets;
  public ConcurrentHashMap<Integer, Timer> timers;
  public int lastSeqNum;

  public static void main(String[] args) throws Exception {
    // check if the number of command line argument is 4
    if (args.length != 4) {
      System.out.println("Usage: java FileSender <path/filename> "
      + "<rcvHostName> <rcvPort> <rcvFileName>");
      System.exit(1);
    }

    new FileSender(args[0], args[1], args[2], args[3]);
    //      new NewFileSender();
  }

  public OldNewFileSender(String fileToOpen, String host, String port, String rcvFileName) throws Exception {
    this.port = port;
    this.address = host;
    this.socketListener = new SocketListener(host, port, this);
    this.sender = new Sender(fileToOpen, host, port, rcvFileName, this);
    this.packets = new ConcurrentHashMap<Integer, byte[]>();
    this.lastSeqNum = -1;
    Thread sendThread = new Thread(this.sender);
    Thread rcvThread = new Thread(this.socketListener);
    rcvThread.start();
    sendThread.start();
  }

  public void removePacket(int ackNum){
    this.packets.remove(ackNum);
    this.timers.remove(ackNum);
  };

  public void removePreviousAckNum(){
    Set<Integer> keys = this.packets.keySet();
    for(int key : keys){
      if(key < lastAckNum){
        this.removePacket(key);

      }
    }
  }


  public class Sender implements Runnable {
    public static final int HEADERSIZE = 16;
    String fileToOpen;
    byte[] host;
    int port;
    String rcvFileName;
    SocketSender socket;
    FileInputStream fis;
    BufferedInputStream bis;
    NewFileSender parent;

    public Sender(String fileToOpen, String host, String port, String rcvFileName, OldNewFileSender parent) throws Exception{
      this.fileToOpen = fileToOpen;
      this.host = InetAddress.getByName(host).getAddress();
      this.port = Integer.parseInt(port);
      this.rcvFileName = rcvFileName;
      this.socket = new SocketSender(InetAddress.getByName(host), this.port, parent);
      this.fis = new FileInputStream(fileToOpen);
      this.bis = new BufferedInputStream(fis, 1000 - HEADERSIZE);
      this.parent = parent;


    }

    public void run() {
      try{
        int seqNum = 0;
        Thread SocketThread = new Thread(this.socket);
        SocketThread.start();
        while(this.bis.available() > 0){
          if(this.bis.available() < 1000 - HEADERSIZE){
            int bytesLeft = bis.available();
            byte[] data = new byte[bytesLeft + HEADERSIZE];
            bis.read(data, HEADERSIZE, data.length - HEADERSIZE);
            data = packetHandler.setHeader(data, this.host, this.port, seqNum, seqNum);
            this.parent.packets.put(seqNum + data.length, data);
            this.socket.send(new DatagramPacket(data, data.length));

          }else{
            byte[] data = new byte[1000];
            bis.read(data, HEADERSIZE, 1000 - HEADERSIZE);
            data = packetHandler.setHeader(data, this.host, this.port, seqNum, 0);
            this.parent.packets.put(seqNum+1000 - HEADERSIZE, data);
            this.socket.send(new DatagramPacket(data, data.length));
          }
        }
      }catch(Exception e){
        e.printStackTrace();
      }
    }
  }

  public class SocketSender implements Runnable {
    InetAddress host;
    int port;
    NewFileSender parent;
    DatagramSocket socket;
    LinkedList<DatagramPacket> queue;
    public SocketSender(InetAddress host, int port, OldNewFileSender parent) throws Exception{
      this.host = host;
      this.port = port;
      this.parent = parent;
      this.socket = new DatagramSocket(port, host);
      this.queue = new LinkedList<DatagramPacket>();
    }

    public void send(DatagramPacket pkt){
      this.queue.add(pkt);
    }
    public void run(){
      try{
        while(true){
          if(this.queue.size() > 0) {
            socket.send(this.queue.remove());
          }else{
            Thread.sleep(1);
          }
        }
      }catch(Exception e){
        e.printStackTrace();
      }
    }
  }
  private class SocketListener implements Runnable {
    public static final int HEADERSIZE = 16;
    String host;
    String port;
    DatagramSocket socket;
    NewFileSender parent;

    public SocketListener(String host, String port, NewFileSender parent) throws Exception{
      this.host = host;
      this.port = port;
      this.socket = new DatagramSocket(Integer.parseInt(port), InetAddress.getByName(host));
      this.parent = parent;
    }

    public void run(){
      byte[] data = new byte[1000];
      DatagramPacket pkt = new DatagramPacket(data, 1000);
      while (true) {
        try{
          socket.receive(pkt);
          if(packetHandler.validatePacket(pkt)) {
            int ackNum = packetHandler.getAckNum(pkt);
            this.parent.removePacket(ackNum);

            if(ackNum == this.parent.lastSeqNum){
              break;
            }
          }
        }catch(Exception e){
          e.printStackTrace();
        }
      }
    }

  }

}
class packetHandler {
  public static final int HEADERSIZE = 16;

  public static byte[] setHeader(byte[] packet, byte[] ip, int port, int seq, int ack) {
    byte[] seqByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(seq).array();
    byte[] ackByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(ack).array();
    byte[] portByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(port).array();

    for (int i = 0; i < 4; i++) {
      packet[i] = ip[i];
      packet[i + 6] = seqByte[i];
      packet[i + 10] = ackByte[i];
    }
    packet[4] = portByte[2];
    packet[5] = portByte[3];

    int checksum = calcChecksum(packet);
    byte[] checksumByte = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(checksum).array();
    packet[14] = checksumByte[2];
    packet[15] = checksumByte[3];
    return packet;
  }

  public static int calcChecksum(byte[] packet) {
    int length = packet.length;
    int currentByte = 0;
    int checksum = 0;
    int data;
    while (length >= 2) {
      //combine 2 bytes into 1 int(long).
      data = (((packet[currentByte] << 8) & 0xFF00) | (packet[currentByte + 1] & 0xFF));
      checksum += data;
      //check for overflow(16bit)
      if ((checksum & 0xFFFF0000) > 0) {
        checksum = checksum & 0xFFFF;
        checksum += 1;
      }
      currentByte += 2;
      length -= 2;
    }
    if (length > 0) {
      checksum += ((packet[currentByte] << 8) & 0xFF00);
      if ((checksum & 0xFFFF0000) > 0) {
        checksum = checksum & 0xFFFF;
        checksum += 1;
      }
    }
    checksum = ~checksum;
    checksum = checksum & 0xFFFF;
    return checksum;
  }
  public static int getAckNum(DatagramPacket pkt) {
    byte[] data = pkt.getData();
    return (int) ((data[4] << 24 & 0xFF000000 | (data[5] << 16  & 0x00FF0000) | (data[6] << 8  & 0x0000FF00) | data[7] & 0x000000FF));
  }
  public static boolean validatePacket(DatagramPacket pkt) {
    return (calcChecksum(pkt.getData()) == 0);
  }
}
