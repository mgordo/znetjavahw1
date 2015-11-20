package constants;

public class MessageTypes {
	/**
	 * Class containing all the possible Message Types
	 */
	//Message telling the other Peer of a Move made by the local Peer
	public static final String MOVE = "move";
	//Message to announce a new Peer to the Game, giving contact information
	public static final String HELLO = "hello";
	//Message to announce a Peer leaving the Game
	public static final String BYE = "bye";
	//Message prompting the remote Peer to choose a Move
	public static final String ACT_FAST = "act_fast";
	//Message making sure a connection to the remote Peer is establishable, and the remote Peer's Process is still running
	public static final String ALIVE = "alive";
	//Message requesting the Game and all Peer information from a Peer upon joining a Game
	public static final String NEED_INFO = "need_info";
	//Message giving the Game and all Peer information to a Peer newly joined to the Game
	public static final String INFO = "info";
	

}
