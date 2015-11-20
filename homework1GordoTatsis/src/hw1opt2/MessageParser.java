/**
 * 
 */
package hw1opt2;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

import constants.MessageTypes;


public class MessageParser extends Thread {

	/**
	 * Class representing a Thread handling a connection initiated by a remote Peer, receiving their Message, and calling the appropriate PRSGame Methods to handle it
	 */
	
	private Socket socket;
	private PRSGame gameListening;
	

	/**
	 * Constructor to create a new MessageParser
	 * @param socket the socket to receive a Message from
	 * @param game the GUI instance
	 */
	public MessageParser(Socket socket,PRSGame game) {
		this.socket=socket;
		this.gameListening = game;
	}

	/**
	 * This Thread's run method
	 */
	@Override
	public void run() {
		
		Object message;
		try {
			ObjectInputStream buffrd = new ObjectInputStream(socket.getInputStream());
			message = buffrd.readObject();
			
		} catch (IOException e1) {
			System.out.println("Error while reading message object from socket");
			try {
				socket.close();
			} catch (IOException e) {
				System.out.println("Could not close socket");
				e.printStackTrace();
			}
			e1.printStackTrace();
			return;
		} catch (ClassNotFoundException e) {
			System.out.println("Error while reading message, class not found");
			e.printStackTrace();
			try {
				socket.close();
			} catch (IOException e1) {
				System.out.println("Could not close socket");
				e1.printStackTrace();
			}
			return;
		}
		
		if(message instanceof Message){
			Message msg = (Message) message;
			System.out.println("DBG: MessageParser:  Received "+msg.getMsgtype()+" from "+msg.getFrom());
			if(msg.getMsgtype().equals(MessageTypes.MOVE)){
				gameListening.moveMade(msg.getFrom(), (String)msg.getData());
			
			}else if(msg.getMsgtype().equals(MessageTypes.BYE)){
				gameListening.removePeer(msg.getFrom());
			}else if(msg.getMsgtype().equals(MessageTypes.HELLO)){
				gameListening.putNewPeer(msg.getFrom(), socket.getInetAddress(), (Integer)msg.getData());
			}else if(msg.getMsgtype().equals(MessageTypes.ACT_FAST)){
				gameListening.actFast();
			}else if(msg.getMsgtype().equals(MessageTypes.NEED_INFO)){
				gameListening.sendInfo(msg.getFrom());
			}else if(msg.getMsgtype().equals(MessageTypes.ALIVE)){
				gameListening.hostAlive(msg.getFrom());
			}else if(msg.getMsgtype().equals(MessageTypes.INFO)){
				gameListening.arrivedInfo((ConcurrentHashMap<String,PeerInformation>)msg.getData(), msg.getFrom());
			}
		}
		
		try {
			socket.close();
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}

}
