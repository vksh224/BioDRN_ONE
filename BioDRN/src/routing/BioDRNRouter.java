/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;
import java.util.List;

import core.*;

/**
 * Energy level-aware variant of Epidemic router.
 */
public class BioDRNRouter extends ActiveRouter 
		implements ModuleCommunicationListener{
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public BioDRNRouter(Settings s) {
		super(s);
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected BioDRNRouter(BioDRNRouter r) {
		super(r);
	}
	
	protected boolean shouldMessageBeSent(Connection con) {
		boolean canMsgBeSent= false;
		DTNHost host, otherHost;
		host = getHost();
		otherHost = con.getOtherNode(getHost());
		
		String sOtherHost;
		sOtherHost = otherHost.toString(); 
		
		if(host.getNeighborList()!= null && host.getNeighborList().contains(sOtherHost)){
				//|| (otherHost.getNeighborList()!= null && otherHost.getNeighborList().contains(host.toString()))){
			canMsgBeSent = true;
		}
		else
			canMsgBeSent = false;
		return canMsgBeSent;	
	}
	
	protected Connection tryMessagesToConnections(List<Message> messages,
			List<Connection> connections) {
		for (int i=0, n=connections.size(); i<n; i++) {
			Connection con = connections.get(i);
			boolean canMsgBeSent = shouldMessageBeSent(con);
			//canMsgBeSent = true;
			if(canMsgBeSent == false)
				continue;
			else{
				Message started = tryAllMessages(con, messages); 
				if (started != null) { 
					return con;
				}
			}
		}
		return null;
	}
	
	@Override
	public void update() {
		super.update();
		updateNeighborList();
		reduceSendingAndScanningEnergy();
		
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		this.tryAllMessagesToAllConnections();
	}
		
	@Override
	public BioDRNRouter replicate() {
		return new BioDRNRouter(this);
	}

	@Override
	public void moduleValueChanged(String key, Object newValue) {
		// TODO Auto-generated method stub
		
	}

}