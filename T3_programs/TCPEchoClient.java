import java.io.*;
import java.net.*;
import java.util.*;

class TCPEchoClient {
    
    public static void main(String[] args) throws IOException {
        
        // Check if the number of command line argument is 2
        if (args.length != 2) {
            System.err.println("Usage: java TCPEchoClient serverIP, serverPort");
            System.exit(1);
        }
        
        String serverIP = args[0];
        int serverPort = Integer.parseInt(args[1]);
        
        // Create a client socket and connect to the server
        Socket clientSocket = new Socket(serverIP, serverPort);
        System.out.println("Connected to " + serverIP + " at " + serverPort + "...");
        
        // Read user input from keyboard
        Scanner scanner = new Scanner(System.in);
        String fromKeyboard = scanner.nextLine();
        
        PrintWriter pw = new PrintWriter(clientSocket.getOutputStream(), true);
        Scanner sc = new Scanner(clientSocket.getInputStream());
        
        while ( !fromKeyboard.equalsIgnoreCase("bye") ) {
            
            // Write user input to the socket
            pw.println(fromKeyboard);
            
            //Read server reply from the socket
            String fromServer = sc.nextLine();
            
            // Show reply on screen
            System.out.println("Echo from server: " + fromServer);
            
            fromKeyboard = scanner.nextLine();
        }
        
        scanner.close();
        sc.close();
        clientSocket.close();
    }
}