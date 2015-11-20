/**
 * 
 */
package hw1opt2;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

import constants.Move;
import hw1opt2.Broadcast.GameInfo;

public class PRSGame extends JPanel{
	/**
	 * Main UI Class, containing all the UI information, as well as most of the Game's logic
	 */
	private static final long serialVersionUID = 7503589667461115906L;

	private static PRSGame instance;

	private String playerName = "";
	private int portTCP;

	private static JFrame frame;
	
	private final JPanel initPanel = new JPanel();
    private final JButton createButton = new JButton("Create a Game");
    private final JButton joinButton = new JButton("Join a Game");
    private final JLabel namePromptLabel = new JLabel("Name:");
    private final JTextField nameTextField = new JTextField(playerName);
    private final JLabel portPromptLabel = new JLabel("Port:");
    private final JFormattedTextField portTextField = new JFormattedTextField();
    

    private final JPanel joinPanel = new JPanel();
    private final JPanel directConnectPanel = new JPanel();
    private final JTextField remoteIpTextField = new JTextField();
    private final JFormattedTextField remotePortTextField = new JFormattedTextField();
    private final JButton connectButton = new JButton("Connect");
    private final JLabel availableGamesInfoLabel = new JLabel("Available Games:");
    private final JPanel availableGamesPanel = new JPanel();
    private final JButton refreshButton = new JButton("Refresh Games");
    
    
    private final JPanel gamePanel = new JPanel();
    private final JPanel infoPanel = new JPanel();
    private final JLabel playerLabel = new JLabel();
    private final JLabel scoreLabel = new JLabel("Score:");
    private final JPanel movePanel = new JPanel();
    private final JLabel moveLabel = new JLabel();
    private final JButton rockButton = new JButton("Rock");
    private final JButton paperButton = new JButton("Paper");
    private final JButton scissorsButton = new JButton("Scissors");
    private final JPanel peersPanel = new JPanel();
    private final JButton nextButton = new JButton("Next Round");

    private volatile boolean actFastReceived = false;
    private volatile int playersMoved = 0;
    
    private Map<String, PeerInformation> peerMap = new ConcurrentHashMap<String, PeerInformation>();
    private Map<String, Timer> peerActFastTimers = new ConcurrentHashMap<String, Timer>();
	private Set<String> peerRequestedInfo = Collections.synchronizedSet(new HashSet<String>());
	
	private Listener listener;
    
    private static DefaultFormatterFactory portFormatterFactory;
    static{
	    NumberFormatter integerFormatter = new NumberFormatter(new DecimalFormat("###"));
	    integerFormatter.setValueClass(Integer.class);
	    integerFormatter.setMinimum(1024);
	    integerFormatter.setMaximum(65535);
	    portFormatterFactory = new DefaultFormatterFactory(integerFormatter);
    }
    
	/**
	 * GUI main method, starts the GUI
	 * @param args
	 */
	public static void main(String[] args) {
		frame = new JFrame("P2P Rock-Paper-Scissors");
        frame.setContentPane(new PRSGame());
        frame.pack();
        frame.setVisible(true);
		Broadcast.init();
	}
	
	/**
	 * PRSGame constructor, initialises the GUI 
	 */
	private PRSGame(){
		createInitPanel();
		createJoinPanel();
		add(initPanel);
		instance = this;
		frame.addWindowListener(new WindowAdapter() {
		   public void windowClosing(WindowEvent we) {
		      MessageSender.sendMessageToAllPeers(Message.makeByeMessage(playerName));
		      System.exit(0);
		   }
		});
	}
	
	/**
	 * Creates the initPanel, containing the player's name field and the buttons to create or join a game
	 */
	private void createInitPanel(){

		joinButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!checkInitFields())
					return;
				//myPeer = new Peer(playerName, portTCP, instance);
				initNetworking(false);
			    updateAvailableGames();
				switchPanel(joinPanel);
			}
		});
		
		createButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!checkInitFields())
					return;
				//myPeer = new Peer(playerName, portTCP, instance);
				initNetworking(true);
				switchPanel(gamePanel);
			}
		});
	
		nameTextField.setToolTipText("Enter your player name, without any whitespaces");

	    portTextField.setFormatterFactory(portFormatterFactory);
	    portTextField.setToolTipText("Enter a port to listen to (1024-65535)");
		
		initPanel.setLayout(new GridLayout(3, 2));

		initPanel.add(namePromptLabel);
		initPanel.add(nameTextField);
		initPanel.add(portPromptLabel);
		initPanel.add(portTextField);
		initPanel.add(joinButton);
		initPanel.add(createButton);
	}
	
	/**
	 * Checks the join panel's field, create Dialog if a problem exists
	 * @return false if any field is invalid, true if all are valid  
	 */
	
	private boolean checkInitFields(){
		if (nameTextField.getText().isEmpty()){
			JOptionPane.showMessageDialog(frame, "Name cannot be empty.", "Error!", JOptionPane.WARNING_MESSAGE);
			return false;
		}
		else if (nameTextField.getText().matches("\\s")){
			JOptionPane.showMessageDialog(frame, "Name cannot contain whitespaces.", "Error!", JOptionPane.WARNING_MESSAGE);
			return false;
		}
		else if (portTextField.getText().isEmpty()){
			JOptionPane.showMessageDialog(frame, "Port cannot be empty.", "Error!", JOptionPane.WARNING_MESSAGE);
			return false;
		}
		return true;
	}
	
	/**
	 * Method to start the TCP Listening Thread, as well as start broadcasting the game information if this peer has started a new game 
	 * @param host boolean flag signifying whether this peer has hosted a game or is joining another's
	 */	
	public void initNetworking(boolean host){
		portTCP = Integer.parseInt(portTextField.getText());
		playerName = nameTextField.getText();
		if(host)
			Broadcast.setBroadcasting(true);
		peerMap.put(playerName, new PeerInformation(0, null, portTCP, true, Move.NONE));
		createGamePanel(true);
		
		listener = new Listener(portTCP, instance);
		listener.start();
	}
	
	/**
	 * Creates the joinPanel, containing the available games, and buttons to join them
	 */
	private void createJoinPanel(){
		
		connectButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!checkJoinFields())
					return;
				MessageSender.sendMessage(Message.makeHelloMessage(playerName, portTCP), null, remoteIpTextField.getText(), remotePortTextField.getText());
				MessageSender.sendMessage(Message.makeNeedInfoMessage(playerName), null, remoteIpTextField.getText(), remotePortTextField.getText());
			}
		});
		
		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateAvailableGames();
			}
		});

		joinPanel.setLayout(new BoxLayout(joinPanel, BoxLayout.PAGE_AXIS));
		
		directConnectPanel.setLayout(new GridLayout(0, 3));

	    remoteIpTextField.setToolTipText("Enter remote peer's IP address");
	    remotePortTextField.setFormatterFactory(portFormatterFactory);
	    remotePortTextField.setToolTipText("Enter remote peer's port");
	    
	    directConnectPanel.add(remoteIpTextField);
	    directConnectPanel.add(remotePortTextField);
	    directConnectPanel.add(connectButton);
	    
		availableGamesPanel.setLayout(new GridLayout(0, 2));
		availableGamesPanel.add(availableGamesInfoLabel);
		availableGamesPanel.add(refreshButton);
		
	    joinPanel.add(directConnectPanel);
	    joinPanel.add(availableGamesPanel);
	}
	
	/**
	 * Checks the join panel's fields, create Dialog if a problem exists
	 * @return false if any field is invalid, true if all are valid  
	 */
	
	private boolean checkJoinFields(){
		if (remoteIpTextField.getText().isEmpty()){
			JOptionPane.showMessageDialog(frame, "Remote ip cannot be empty.", "Error!", JOptionPane.WARNING_MESSAGE);
			return false;
		}
		if (remotePortTextField.getText().isEmpty()){
			JOptionPane.showMessageDialog(frame, "Remote port cannot be empty.", "Error!", JOptionPane.WARNING_MESSAGE);
			return false;
		}
		return true;
	}
	
	/**
	 * Updates the availableGamesPanel, containing all peers broadcasting games
	 */
	private void updateAvailableGames(){
		availableGamesPanel.removeAll();
		availableGamesPanel.add(availableGamesInfoLabel);
		availableGamesPanel.add(refreshButton);
		for (final GameInfo info : Broadcast.getGames()){
		    JLabel gameNameLabel = new JLabel(info.name);
		    JButton connectButton = new JButton("Connect");
		    
		    connectButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					MessageSender.sendMessage(Message.makeHelloMessage(playerName, portTCP), info.name, info.address, info.port);
					MessageSender.sendMessage(Message.makeNeedInfoMessage(playerName), info.name, info.address, info.port);
				}
			});

		    if (info.name.equals(playerName)){
		    	gameNameLabel.setForeground(Color.RED);
		    	connectButton.setEnabled(false);
		    }
			availableGamesPanel.add(gameNameLabel);
			availableGamesPanel.add(connectButton);
		}
		frame.revalidate();
		frame.pack();
	}
	
	/**
	 * Creates the gamePanel, containing the game information, and move actions
	 * @param first: signifies if this is the initial call
	 */
	private void createGamePanel(boolean first){

		if (first){
			rockButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					executeMove(Move.ROCK);
				}
			});
			
			paperButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					executeMove(Move.PAPER);
				}
			});
			
			scissorsButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					executeMove(Move.SCISSORS);
				}
			});
			
			nextButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					createGamePanel(false);
					revalidate();
					frame.pack();
					revalidate();
				}
			});
			PeerPanelControl.addPeer(peersPanel, playerName, 0);
		}
		
		actFastReceived = false;
		
		infoPanel.setLayout(new GridLayout(0, 2));
		playerLabel.setText(playerName);
	    scoreLabel.setText("Score: "+peerMap.get(playerName).getScore());
	   
		movePanel.add(rockButton);
		movePanel.add(paperButton);
		movePanel.add(scissorsButton);
		
		moveLabel.setText(playerName+": Choose a move!");

		if (nextButton.getParent() != null && nextButton.getParent() == gamePanel)
			gamePanel.remove(nextButton);
		
		if (first){
			infoPanel.add(playerLabel);
			infoPanel.add(scoreLabel);

			gamePanel.setLayout(new GridLayout(0, 1));
			movePanel.setLayout(new GridLayout(0, 3));
			peersPanel.setLayout(new GridLayout(0, 3));
		
			gamePanel.add(moveLabel);
			gamePanel.add(movePanel);
			gamePanel.add(peersPanel);
		}
		else
			movePanel.setVisible(true);
		
		initPeerPanel();
	}
	
	/**
	 * Initialises the Peer panel with the values contained in the Peer instance
	 */
	private void initPeerPanel(){
		
		List<String> peers = new ArrayList<String>(peerMap.keySet());
		
		Collections.sort(peers, new Comparator<String>() {
			public int compare(String left, String right) {
				return peerMap.get(right).getScore()-(peerMap.get(left).getScore());
			}
		});
		
		PeerPanelControl.clear(peersPanel);
		
		for (String peer : peers){
			PeerInformation peerInfo = peerMap.get(peer);
			PeerPanelControl.addPeer(peersPanel, peer, peerInfo.getScore());
			PeerPanelControl.setPeerMove(peersPanel, peer, peerMap.get(peer).getMove(), false);
			if (!peer.equals(playerName))
				addActFastTimer(peer);
		}
		revalidate();
		frame.pack();
	}
	
	/**
	 * Returns to initscreen and produces a custom message, call in case of error in TCPListener
	 * @param errorMessage false if problem in empty, true if filled  
	 */
	
	public void TCPListenerError(final String errorMessage){
		peerMap.remove(playerName);
		SwingUtilities.invokeLater(new Runnable() {
		    public void run() {
				JOptionPane.showMessageDialog(frame, "TCP Socket produced an error:\n"+errorMessage, "Error!", JOptionPane.WARNING_MESSAGE);
		    	switchPanel(initPanel);
		    }
	    });
	}
	

	/**
	 * Send your move choice to the other peers
	 * @param move: This peer's move
	 */
	private void executeMove(String move){
		
		peerMap.get(playerName).setMove(move);
		movePanel.setVisible(false);
		moveLabel.setText(playerName+": You played "+move+".");
		
		revalidate();
		frame.pack();
		
		Message m = Message.makeMoveMessage(playerName, move);
		MessageSender.sendMessageToAllPeers(m);
		PeerPanelControl.setPeerMove(peersPanel, playerName, move, false);

		playersMoved ++;
		if(peerMap.size() == playersMoved){
			finishGame();
		}
	}
	

	/**
	 * Method signaling the end of the current round, calculating scores and displaying everyone's moves before cleanup
	 */	
	private void finishGame() {
		for (String peer: peerMap.keySet()){
			String move = peerMap.get(peer).getMove();
			int scoreChange = 0;
			for (String otherPeer: peerMap.keySet()){
				String otherMove = peerMap.get(otherPeer).getMove();
				if ((move.equals(Move.ROCK) && otherMove.equals(Move.SCISSORS)) ||
						(move.equals(Move.SCISSORS) && otherMove.equals(Move.PAPER)) ||
						(move.equals(Move.PAPER) && otherMove.equals(Move.ROCK)))
					scoreChange++;					
			}
			peerMap.get(peer).setScore(peerMap.get(peer).getScore()+scoreChange);
		}

		playersMoved = 0;
		
    	for (String peer: peerMap.keySet()){
    		PeerPanelControl.setPeerMove(peersPanel, peer, peerMap.get(peer).getMove(), true);
    		PeerPanelControl.setPeerScore(peersPanel, peer, peerMap.get(peer).getScore());
			peerMap.get(peer).setMove(null);
			if (!peer.equals(playerName))
				peerActFastTimers.get(peer).stop();
    	}
    	gamePanel.add(nextButton);
    	
		revalidate();
		frame.pack();
		
		ActionListener autoStartNextGame = new ActionListener() {
		    public void actionPerformed(ActionEvent evt) {
		    	if (nextButton.getParent() == null)
		    		return;
				createGamePanel(false);
				revalidate();
				frame.pack();
				revalidate();
		    }
		};
		Timer timer = new Timer(5000 ,autoStartNextGame);
		timer.setRepeats(false);
		timer.start();
	}
	
	/**
	 * Method to start a Timer, that upon finishing will send an ActFast Message to the specified peer
	 * @param name The name of the peer to start the timer for
	 */	
	private void addActFastTimer(final String name){
		ActionListener sendActFastMessage = new ActionListener() {
		    public void actionPerformed(ActionEvent evt) {
		    	if (peerMap.containsKey(name))
		    		MessageSender.sendMessage(Message.makeActFastMessage(playerName), name);
		    }
		};
		Timer timer = new Timer(10000,sendActFastMessage);
		timer.setRepeats(false);
		timer.start();
		peerActFastTimers.put(name, timer);
	}
	
	
	/**
	 * Switches visibility to another panel
	 * @param panel: the panel to switch to
	 */
	private void switchPanel(JPanel panel){
		if (panel.getParent() != null && panel.getParent() == this)
			return;
		removeAll();
		add(panel);
		revalidate();
		frame.pack();
	}
	
	/** PUBLIC METHODS FOR UPDATING THE GAME AFTER AN EVENT - RUNNING ON OTHER THREADS**/
	/**
	 * Method called when a peer sends its move
	 * @param name Name of the host sending the move
	 * @param move Move made
	 */
	public void moveMade(final String name, final String move) {
		String previousMove = peerMap.get(name).getMove();
		if (previousMove == null)
			playersMoved ++;
		peerMap.get(name).setMove(move);
			
		SwingUtilities.invokeLater(new Runnable() {
		    public void run() {
		    	PeerPanelControl.setPeerMove(peersPanel, name, move, false);
				revalidate();
		    }
	    });
		
		if(peerMap.size() == playersMoved){
			SwingUtilities.invokeLater(new Runnable() {
			    public void run() {
					finishGame();
			    }
		    });
		}
	}
	
	/**
	 * Method called when a peer is to be removed
	 * @param name Name of the peer to be removed
	 */
	public void removePeer(final String name) {
		if (name == null)
			return;
		if (peerMap.get(name).getMove() != null)
			playersMoved --;
		peerMap.remove(name);
		if (peerActFastTimers.containsKey(name))
			peerActFastTimers.get(name).stop();
		
		SwingUtilities.invokeLater(new Runnable() {
		    public void run() {
				PeerPanelControl.removePeer(peersPanel, name);
				revalidate();
				frame.pack();
				if(peerMap.size() == playersMoved){
					finishGame();
			    }
		    }
	    });
	}
	
	/**
	 * Method called when a peer is added to the game
	 * @param name Name of the peer being added
	 * @param ip InetAddress of the peer being added
	 * @param port the port number that the remote peer is listening at 
	 */
	public void putNewPeer(final String name, final InetAddress ip, final int port) {
		if (peerMap.containsKey(name))
			return;
		PeerInformation peerInfo = new PeerInformation(0, ip, port, false, Move.NONE);
		peerMap.put(name, peerInfo);
		MessageSender.startHeartbeat(name, ip, port);
		if (peerRequestedInfo.contains(name))
			sendInfo(name);
		
		SwingUtilities.invokeLater(new Runnable() {
		    public void run() {
				addActFastTimer(name);
				PeerPanelControl.addPeer(peersPanel, name, 0);
				revalidate();
				frame.pack();
				if (peerMap.get(playerName).getMove() != null){
					Message m = Message.makeMoveMessage(playerName, 
							peerMap.get(playerName).getMove());
					MessageSender.sendMessage(m, name);
				}
		    }
	    });
	}
	
	/**
	 * Method called when a peer has sent an ActFast Message
	 */
	public void actFast() {
		if (peerMap.get(playerName).getMove() == null && !actFastReceived){
    		actFastReceived = true;
			SwingUtilities.invokeLater(new Runnable() {
			    public void run() {
		    		JOptionPane.showMessageDialog(frame, "Please pick a move, the other players are waiting.", "Hey you!", JOptionPane.INFORMATION_MESSAGE);
		    	}
		    });
		}
	}
	
	/**
	 * Placeholder method to be called when a peer sends a hostAlive Message
	 * @param name Name of the peer sending the Message
	 */
	public void hostAlive(String hostname) {
		// Do Nothing!
	}
	
	/**
	 * Method called when the game's information has been received by a remote peer
	 * @param data the game information, stored in a Map
	 * @param name Name of the peer sending the Message
	 */
	public void arrivedInfo(Map<String, PeerInformation> data, final String from) {
		peerMap.putAll(data);
		Broadcast.setBroadcasting(true);
		MessageSender.sendMessageToAllPeers(Message.makeHelloMessage(playerName, portTCP));
		for(String peer:peerMap.keySet()){
			if (peer.equals(playerName))
				continue;
			MessageSender.startHeartbeat(peer, peerMap.get(peer).getIpAddress(), peerMap.get(peer).getPort());
		}
		
		SwingUtilities.invokeLater(new Runnable() {
		    public void run() {
				initPeerPanel();
				switchPanel(gamePanel);
		    }
	    });
	}
	
	/**
	 * Method called to send the Game's Information to a remote peer
	 * @param to name of the peer the information is to be sent to
	 */
	public void sendInfo(String to) {
		if (!peerMap.containsKey(to)){
			peerRequestedInfo.add(to);
			return;
		}
		Message infomsg = Message.makeInfo(playerName, new ConcurrentHashMap<String,PeerInformation>(peerMap));
		MessageSender.sendMessage(infomsg, to);
	}


	/**
	 * Returns the name set by the player
	 */
	public String getName(){
		return playerName;
	}
	
	/**
	 * Returns the port the server is listening to
	 */
	public int getPort(){
		return portTCP;
	}
	
	/**
	 * Returns the current instance of the PRSGame Class
	 */
	public static PRSGame getInstance(){
		return instance;	
	}

	/**
	 * Returns the current peers participating in the Game
	 */
	public Set<String> getPeerSet() {
		return Collections.unmodifiableSet(peerMap.keySet());
	}
	
	/**
	 * Returns the information of the specified Peer
	 * @param peerName the peer whose information is requested
	 */
	public PeerInformation getPeerInformation(String peerName){
		return peerMap.get(peerName);
	}
	
	
	/**
	 * Sets the local peer's InetAddress to the specified one
	 * @param localIP the InetAddress this peer is listening at
	 */
	public void setIpAddress(InetAddress localIP) {
		peerMap.get(playerName).setIpAddress(localIP);
	}
	
	/**
	 * Custom class to hold information about the peersPanel in gamePanel, and allow fast changes
	 * Should only be accessed by the gui Thread
	 */
	private static class PeerPanelControl{
		private static Map<String,PeerPanelControl> peers = new HashMap<String,PeerPanelControl>();
		private JLabel peerNameLabel;
		private JLabel peerScoreLabel;
		private JLabel peerMoveLabel;
		
		/**
		 * Private constructor for the JLabels comprising the information being presented in the UI for one peer
		 * @param panel the Panel containing the Peer Information JLabels
		 * @param name the name of the peer these JLabels represent 
		 * @param score the initial score to be shown for this peer
		 */
		private PeerPanelControl(JPanel panel, String name, int score){
		    peerNameLabel = new JLabel(name);
		    peerScoreLabel = new JLabel(Integer.toString(score));
		    peerMoveLabel = new JLabel("");
		}
		
		/**
		 * Static method to show a new peer to the UI	 
		 * @param panel the Panel containing the Peer Information JLabels
		 * @param name the name of the peer these JLabels represent 
		 * @param score the initial score to be shown for this peer
		 */
		public static void addPeer(JPanel panel, String name, int score){
			PeerPanelControl peerInfo = new PeerPanelControl(panel, name, score);
			
		    panel.add(peerInfo.peerNameLabel);
		    panel.add(peerInfo.peerScoreLabel);
		    panel.add(peerInfo.peerMoveLabel);
		    
		    peers.put(name, peerInfo);
		}
		
		/**
		 * Static method to Set the peer's Move JLabel according to whether he has made a move or not in this round, and whether it's the end of the round or not	 
		 * @param panel the Panel containing the Peer Information JLabels
		 * @param name the name of the peer these JLabels represent 
		 * @param move the move this peer has performed, null if the peer has not moved yet this round
		 * @param show boolean flag whether to show the peer's move or not
		 */
		public static void setPeerMove(JPanel panel, String name, String move, boolean show){
			if (show){
				peers.get(name).peerMoveLabel.setText(move);
			}
			else{
				if (move == null)
					peers.get(name).peerMoveLabel.setText("");
				else
					peers.get(name).peerMoveLabel.setText("Moved");
			}
		}
		
		/**
		 * Static method to modify the score shown for the specified peer
		 * @param panel the Panel containing the Peer Information JLabels
		 * @param name the name of the peer these JLabels represent 
		 * @param score the score to be shown for this peer
		 */
		public static void setPeerScore(JPanel panel, String name, int score){
			peers.get(name).peerScoreLabel.setText(""+score);
		}
		
		/**
		 * Static method to remove a peer's JLabels from the UI
		 * @param panel the Panel containing the Peer Information JLabels
		 * @param name the name of the peer these JLabels represent
		 */
		public static void removePeer(JPanel panel, String name){
			PeerPanelControl peerInfo = peers.get(name);
					
			panel.remove(peerInfo.peerNameLabel);
		    panel.remove(peerInfo.peerScoreLabel);
		    panel.remove(peerInfo.peerMoveLabel);
		    
		    peers.remove(name);
			MessageSender.stopHeartbeat(name);
		}
		
		/**
		 * Static method to clear the JPanel from all the peer information JLabels
		 * @param panel the Panel containing the Peer Information JLabels
		 */
		public static void clear(JPanel panel){
			Set<String> peerSet = new HashSet<String>(peers.keySet());
			for (String name: peerSet)
				removePeer(panel, name);
		}
	}
}

