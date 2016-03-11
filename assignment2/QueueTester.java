import java.util.concurrent.ConcurrentLinkedQueue;

public class QueueTester{

  public static void main(String args[]) throws Exception{
    ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<Integer>();
    int i=0;
    while(true){
      if(!queue.isEmpty()){
        System.out.println(queue.poll());
      }
      queue.offer(i);
      i++;
      Thread.sleep(1000);
    }
  }
  public QueueTester(){

  }
}
