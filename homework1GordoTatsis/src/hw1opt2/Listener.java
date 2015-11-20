package hw1opt2;
import java.io.IOException;
import java.net.*;
public class Listener extends Thread {

	/**
	 * Class representing a TCP Listener for Game Messages from other Peers
	 */

	InetAddress localIP;
	private int myport;
	private ServerSocket serversocket;
	private PRSGame gameListening;


	/**
	 * Static method to initialize the Sockets used in broadcasting and listening to broadcasts
	 * @param port the port to listen to
	 * @param game the GUI instance
	 */
	public Listener(int port, PRSGame game){
		
		gameListening = game;
		try {
			localIP = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			System.out.println("Error getting local IP address in listener thread");
			PRSGame.getInstance().TCPListenerError(e.getMessage());
		}
		myport=port;
		game.setIpAddress(localIP);
		try {
			serversocket = new ServerSocket(myport);
		} catch (IOException e) {
			System.out.println("Error generating listening socket");
			PRSGame.getInstance().TCPListenerError(e.getMessage());
		} 
	}
	
	/**
	 * The run method of the Thread extending Listener, accepting connections from other peers and sppawning a MessageParser to handle the sent Message 
	 */
	@Override
	public void run(){
		while(true){
			Socket socket=null;
			try {
				socket = serversocket.accept();
				MessageParser newaction = new MessageParser(socket, gameListening);
				newaction.setPriority(newaction.getPriority()+1);
				newaction.start();
			} catch (IOException e) {
				System.out.println("Failed to establish connection while listening");
				e.printStackTrace();
			}
		}
	}
}
