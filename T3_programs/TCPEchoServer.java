import java.io.*;
import java.net.*;
import java.util.*;

class TCPEchoServer {
    
    public static void main(String[] args) throws IOException {
        
        // Check if the number of command line argument is 1
        if (args.length != 1) {
            System.err.println("Usage: java TCPEchoServer serverPort");
            System.exit(1);
        }
        
        int port = Integer.parseInt(args[0]);
        ServerSocket welcomeSocket = new ServerSocket(port);
        System.out.println("Server listens at port " + port + "...");
        
        while (true) {  // server runs forever
            // Create a separate socket to connect to a client
            Socket connectionSocket = welcomeSocket.accept();
            
            System.out.println("Connection established.\n\tServer port: "
                                   + connectionSocket.getLocalPort()
                                   + "\n\tClient address: "
                                   + connectionSocket.getInetAddress()
                                   + " Client port: "
                                   + connectionSocket.getPort());
            
            Scanner scanner = new Scanner(connectionSocket.getInputStream());
            
            PrintWriter pw = new PrintWriter(connectionSocket.getOutputStream(), true);
            
            // Read data from the connection socket
            String fromClient;
            // Note: Scanner can only read text file, not binary file
            while ( scanner.hasNextLine() ) {
                fromClient = scanner.nextLine();
                pw.println(fromClient);
            }
        }
    }
}