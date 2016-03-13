import java.io.*;
import java.net.*;
import java.lang.Thread;

public class SocketSendTester {

  public static void main(String args[]) throws Exception{
    new SocketSendTester();
  }

  public SocketSendTester() throws Exception{
    InetAddress serverAddress = InetAddress.getByName("localhost");
    SocketReceiver rcv = new SocketReceiver();
    Thread send = new Thread(rcv);
    send.start();
    DatagramSocket socket = new DatagramSocket();
    Thread.sleep(1);
    while(true){
      System.out.println("sending packet");
      byte[] data = "abcabccdecde".getBytes();
      System.out.println(new String(data));
      DatagramPacket pkt = new DatagramPacket(new byte[data.length], data.length, serverAddress, 9000);
      pkt.setData(data);
      socket.send(pkt);
      System.out.println("sending");
      Thread.sleep(500);
    }
  }

  private class SocketReceiver implements Runnable{
    DatagramSocket socket;
    public SocketReceiver() throws Exception{
      this.socket = new DatagramSocket(9000);
    }

    public void run(){
      try{
        while(true){
          byte[] receiveData = new byte[1000];
          DatagramPacket receivePacket = new DatagramPacket(receiveData, 1000);
          this.socket.receive(receivePacket);
          byte[] data = receivePacket.getData();
          String string = new String(data);
          System.out.println("receive: " + string);
        }
      }catch(Exception e){
        e.printStackTrace();
      }
    }
  }
}
