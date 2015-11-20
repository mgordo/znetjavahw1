package hw1opt2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Broadcast {
	/**
	 * Class representing the broadcasting functionality of the Game, both in broadcasting a game the local peer is in, and in receiving the broadcasts of other Peers
	 */
	private static final int port = 10807;
	private static final int delay = 5000;
	private static DatagramSocket listeningSocket;
	private static DatagramSocket broadcastingSocket;
	
	private static final BroadcastListener listener = new BroadcastListener();
	private static final Thread listenerThread = new Thread(listener);
	
	private static final ScheduledExecutorService broadcastScheduler = Executors.newScheduledThreadPool(1);
	private static ScheduledFuture<?> futureBroadcast = null;
	private static final Object broadcastingLock = new Object();
	
	private static final ConcurrentHashMap<String,GameInfo> gameList = new ConcurrentHashMap<String,GameInfo>();
	
	/**
	 * Static method to initialize the Sockets used in broadcasting and listening to broadcasts
	 */
	public static void init(){
		try{
			broadcastingSocket = new DatagramSocket();	
			listeningSocket = new DatagramSocket(null);		
			listeningSocket.setReuseAddress(true);
			listeningSocket.setBroadcast(true);
			listeningSocket.bind(new InetSocketAddress(port));
			listenerThread.setDaemon(true);
			listenerThread.start();
		}
		catch (IOException e){
			e.printStackTrace();
		}
	}
	

	/**
	 * Static class representing a Broadcast Listener, receiving and storing the broadcasts of other Peers currently in running games
	 */
	private static class BroadcastListener implements Runnable{

		@Override
		public void run() {
			while (true){
				try {
					byte[] buffer = new byte[100];
			        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			        listeningSocket.receive(packet);
					String message = new String(packet.getData()).trim();
					if (message.startsWith("P2PRPS")){
						String messageParts[] = message.split(" ");
						GameInfo info = new GameInfo(messageParts[1], packet.getAddress(), Integer.parseInt(messageParts[2]));
						gameList.put(info.name, info);
						//System.out.println("DBG: Broadcast:  Received message from "+messageParts[1]+", at "+packet.getAddress()+":"+Integer.parseInt(messageParts[2]));
					}
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	

	/**
	 * Static method to receive the Broadcasts collected by the BroadcastListener
	 */
	public static Collection<GameInfo> getGames(){
		return gameList.values();
	}
	

	/**
	 * Static method to switch the Broadcasting of the local Peer's Game on and off
	 * @param state the state to toggle the Broadcasting to
	 */
	public static void setBroadcasting(boolean state){
		synchronized (broadcastingLock) {
			
			if (!state && (futureBroadcast != null)){
				futureBroadcast.cancel(false);
				futureBroadcast = null;
				System.out.println("DBG: Broadcast: broadcasting off");
			}
			if (state && (futureBroadcast == null || futureBroadcast.isDone())){
				futureBroadcast = broadcastScheduler.schedule(broadcastTask, delay, TimeUnit.MILLISECONDS);
				System.out.println("DBG: Broadcast: broadcasting on");
			}
		}
	}

	/**
	 * Static Runnable representing one Broadcast of the local peer's Game
	 */
	private final static Runnable broadcastTask = new Runnable() {
		@Override
		public void run() {
			

			byte[] broadcastMessage = ("P2PRPS "+PRSGame.getInstance().getName()+" "+PRSGame.getInstance().getPort()).getBytes();
			
			try {
				Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
				while (interfaces.hasMoreElements()) {
					NetworkInterface netInterface = interfaces.nextElement();
					//don't advertise on loopback interfaces, since a peer connecting through loopback will be later advertised with the loopback address, limiting connectivity
					if (netInterface.isLoopback())
						continue;
					for (InterfaceAddress address : netInterface.getInterfaceAddresses()) {
						if (address.getBroadcast() == null)
							continue;
						
						try {
							DatagramPacket broadcastPacket = new DatagramPacket(broadcastMessage, broadcastMessage.length, address.getBroadcast(), port);
							broadcastingSocket.send(broadcastPacket);
							//System.out.println("DBG: Broadcast:  Sent message");
						}
						catch (Exception e1) {
							e1.printStackTrace();
						}
					}
				}
			}
			catch (SocketException e) {
				e.printStackTrace();
			}

			synchronized (broadcastingLock) {
				futureBroadcast = broadcastScheduler.schedule(broadcastTask, delay, TimeUnit.MILLISECONDS);
			}
		}
	};

	/**
	 * Static class representing a Game found through BroadcastListener
	 */
	public static class GameInfo{
		public final String name;
		public final InetAddress address;
		public final int port;
		
		public GameInfo(String name, InetAddress address, int port){
			this.name = name;
			this.address = address;
			this.port = port;
		}
		
	}
	
}
