/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import input.FailedNodeListReader;
import input.NeighborListReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.ModuleCommunicationBus;
import core.NetworkInterface;
import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.SimScenario;

/**
 * Implementation of Spray and wait router as depicted in 
 * <I>Spray and Wait: An Efficient Routing Scheme for Intermittently
 * Connected Mobile Networks</I> by Thrasyvoulos Spyropoulus et al.
 *
 */
public class BioDRNSnWRouter extends ActiveRouter {
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value})*/ 
	public static final String BINARY_MODE = "binaryMode";
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." +
		"copies";
	
	protected int initialNrofCopies;
	protected boolean isBinary;
	
	//Newly added for Bio-DRN
			/** Energy consumption **/
			public static final String INIT_ENERGY_S = "initialEnergy";
			/** Energy usage per scanning -setting id ({@value}). */
			public static final String SCAN_ENERGY_S = "scanEnergy";
			/** Energy usage per second when sending -setting id ({@value}). */
			public static final String TRANSMIT_ENERGY_S = "transmitEnergy";
			/** Energy update warmup period -setting id ({@value}). Defines the 
			 * simulation time after which the energy level starts to decrease due to 
			 * scanning, transmissions, etc. Default value = 0. If value of "-1" is 
			 * defined, uses the value from the report warmup setting 
			 * {@link report.Report#WARMUP_S} from the namespace 
			 * {@value report.Report#REPORT_NS}. */
			public static final String WARMUP_S = "energyWarmup";

			/** {@link ModuleCommunicationBus} identifier for the "current amount of 
			 * energy left" variable. Value type: double */
			public static final String ENERGY_VALUE_ID = "Energy.value";
			public static final String IS_ENERGY_CONSTRAINED = "isEnergyConstrained";
			
			private final double[] initEnergy;
			private double warmupTime;
			private double currentEnergy;
			/** energy usage per scan */
			private double scanEnergy;
			private double transmitEnergy;
			private double lastScanUpdate;
			private double lastUpdate;
			private double scanInterval;	
			private int isEnergyConstrained = 2;
			private ModuleCommunicationBus comBus;
			private static Random rng = null;
			
			private double initTime;
			private static NeighborListReader neighborListReader;
			private static FailedNodeListReader failedNodeListReader;
			private double samplingInterval = 600;
			private double lastSamplingUpdate = 0;
			private ArrayList<String >currentNodeNeighborList;
			private ArrayList<String> failedNodeList;
			private int lastCCID;

	public BioDRNSnWRouter(Settings s) {
		super(s);
		Settings snwSettings = new Settings(SPRAYANDWAIT_NS);
		
		initialNrofCopies = snwSettings.getInt(NROF_COPIES);
		isBinary = snwSettings.getBoolean( BINARY_MODE);
this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
		
		if (this.initEnergy.length != 1 && this.initEnergy.length != 2) {
			throw new SettingsError(INIT_ENERGY_S + " setting must have " + 
					"either a single value or two comma separated values");
		}
		
		this.scanEnergy = s.getDouble(SCAN_ENERGY_S);
		this.transmitEnergy = s.getDouble(TRANSMIT_ENERGY_S);
		this.scanInterval  = s.getDouble(SimScenario.SCAN_INTERVAL_S);
		this.lastCCID = s.getInt("lastCCID");
		
		if (s.contains(WARMUP_S)) {
			this.warmupTime = s.getInt(WARMUP_S);
			if (this.warmupTime == -1) {
				this.warmupTime = new Settings(report.Report.REPORT_NS).
					getInt(report.Report.WARMUP_S);
			}
		}
		else {
			this.warmupTime = 0;
		}
		this.isEnergyConstrained = s.getInt(IS_ENERGY_CONSTRAINED);
		
		this.samplingInterval = s.getInt("samplingInterval");
		
		if(s.contains("neighborListFile")){
			String filePath = s.getSetting("neighborListFile");
			neighborListReader = new NeighborListReader(filePath);
		}
		
		if(s.contains("failedNodeListFile")){
			String filePath = s.getSetting("failedNodeListFile");
			failedNodeListReader = new FailedNodeListReader(filePath);
		}
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected BioDRNSnWRouter(BioDRNSnWRouter r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
		this.initEnergy = r.initEnergy;
		setEnergy(this.initEnergy);
		this.scanEnergy = r.scanEnergy;
		this.transmitEnergy = r.transmitEnergy;
		this.scanInterval = r.scanInterval;
		this.warmupTime  = r.warmupTime;
		this.comBus = null;
		this.lastScanUpdate = r.lastScanUpdate;
		this.lastUpdate = r.lastUpdate;
		this.isEnergyConstrained = r.isEnergyConstrained;

		this.initTime = r.initTime;
		this.samplingInterval = r.samplingInterval;
		this.lastSamplingUpdate = r.lastSamplingUpdate;
		this.lastCCID = r.lastCCID;
	}
	
	/**
	 * Sets the current energy level into the given range using uniform 
	 * random distribution.
	 * @param range The min and max values of the range, or if only one value
	 * is given, that is used as the energy level
	 */
	protected void setEnergy(double range[]) {
		if (range.length == 1) {
			this.currentEnergy = range[0];
		}
		else {
			if (rng == null) {
				rng = new Random((int)(range[0] + range[1]));
			}
			this.currentEnergy = range[0] + 
				rng.nextDouble() * (range[1] - range[0]);
		}
	}
	
	/**
	 * Updates the current energy so that the given amount is reduced from it.
	 * If the energy level goes below zero, sets the level to zero.
	 * Does nothing if the warmup time has not passed.
	 * @param amount The amount of energy to reduce
	 */
	protected void reduceEnergy(double amount) {
		if (SimClock.getTime() < this.warmupTime) {
			return;
		}
		
		comBus.updateDouble(ENERGY_VALUE_ID, -amount);
		if (this.currentEnergy < 0) {
			comBus.updateProperty(ENERGY_VALUE_ID, 0.0);
		}
	}
	

	/**
	 * Reduces the energy reserve for the amount that is used by sending data
	 * and scanning for the other nodes. 
	 */
	protected void reduceSendingAndScanningEnergy() {
		double simTime = SimClock.getTime();
		if(this.isEnergyConstrained == 2){
			if (this.comBus == null) {
				this.comBus = getHost().getComBus();
				this.comBus.addProperty(ENERGY_VALUE_ID, this.currentEnergy);
				this.comBus.subscribe(ENERGY_VALUE_ID, this);
			}
			
			if (this.currentEnergy <= 0) {
				/* turn radio off */
				this.comBus.updateProperty(NetworkInterface.RANGE_ID, 0.0);
				return; /* no more energy to start new transfers */
			}
			
			//int currentHostId = Integer.parseInt(getHost().toString().substring(1));
			
			//Address failed nodes
			if (failedNodeList!= null && failedNodeList.contains(getHost().toString())){
//				System.out.println("Here: Failed Node List: " + failedNodeList);
				this.comBus.updateProperty(ENERGY_VALUE_ID, 0.0);
				this.comBus.updateProperty(NetworkInterface.RANGE_ID, 0.0);
				return; /* no more energy to start new transfers */
			}
			
			//Transmission energy
			if (getHost().toString().startsWith("n") 
					&& simTime > this.lastUpdate && sendingConnections.size() > 0) {
				// System.out.println("Node: " + getHost()+" Sending " + sendingConnections);
				reduceEnergy((simTime - this.lastUpdate) * this.transmitEnergy);
			}
			
			//Receiving energy
			 if (getHost().toString().startsWith("n") 
					 && simTime > this.lastUpdate && isReceiving() > 0) {
         		//System.out.println("Node: " + getHost()+" Receiving " + sendingConnections);
                	reduceEnergy((simTime - this.lastUpdate) * this.transmitEnergy);
			 }
			this.lastUpdate = simTime;
		
			//Scanning energy
			if (getHost().toString().startsWith("n")  
					&& simTime > this.lastScanUpdate + this.scanInterval) {
				/* scanning at this update round */
				reduceEnergy(this.scanEnergy);
				this.lastScanUpdate = simTime;
			}
		}
	}
	
	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}
	
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		
		assert nrofCopies != null : "Not a SnW message: " + msg;
		
		if (isBinary) {
			/* in binary S'n'W the receiving node gets ceil(n/2) copies */
			nrofCopies = (int)Math.ceil(nrofCopies/2.0);
		}
		else {
			/* in standard S'n'W the receiving node gets only single copy */
			nrofCopies = 1;
		}
		
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		return msg;
	}
	
	@Override 
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());

		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);
		return true;
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
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		/* create a list of SAWMessages that have copies left to distribute */
		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());
		
		if (copiesLeft.size() > 0) {
			/* try to send those messages */
			this.tryMessagesToConnections(copiesLeft, getConnections());
		}
	}
	
	//Update neighbor list based on time slot and failed nodes
	protected void updateNeighborList() {
		if (SimClock.getIntTime() == this.lastSamplingUpdate) {
			currentNodeNeighborList = neighborListReader.getNeighborList(getHost().toString(), SimClock.getIntTime());
			this.lastSamplingUpdate += this.samplingInterval;
			getHost().setNeighborList(currentNodeNeighborList);
			
			failedNodeList = failedNodeListReader.getFailedNodeList(SimClock.getIntTime());
			
			if(currentNodeNeighborList != null && getHost().toString().matches("n10")){
				System.out.println("Current energy; "+ getHost().getComBus().getDouble(ENERGY_VALUE_ID, -1));
				System.out.println("At time: " + SimClock.getIntTime() +" Neighorlist: ");
				System.out.println("Node " + getHost().toString() +" : " + currentNodeNeighborList.toString());
				System.out.println("Failed node list: " + failedNodeList);
			}
		}	
	}
	
	/**
	 * Creates and returns a list of messages this router is currently
	 * carrying and still has copies left to distribute (nrof copies > 1).
	 * @return A list of messages that have copies left
	 */
	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have " + 
				"nrof copies property!";
			if (nrofCopies > 1) {
				list.add(m);
			}
		}
		
		return list;
	}
	
	/**
	 * Called just before a transfer is finalized (by 
	 * {@link ActiveRouter#update()}).
	 * Reduces the number of copies we have left for a message. 
	 * In binary Spray and Wait, sending host is left with floor(n/2) copies,
	 * but in standard mode, nrof copies left is reduced by one. 
	 */
	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		/* reduce the amount of copies left */
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		if (isBinary) { 
			nrofCopies /= 2;
		}
		else {
			nrofCopies--;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	}
	
	@Override
	public BioDRNSnWRouter replicate() {
		return new BioDRNSnWRouter(this);
	}

	@Override
	public void moduleValueChanged(String key, Object newValue) {
		this.currentEnergy = (Double) newValue;	
	}
}
