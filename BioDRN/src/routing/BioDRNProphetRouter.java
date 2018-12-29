/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import input.FailedNodeListReader;
import input.NeighborListReader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import core.Tuple;

/**
 * Implementation of PRoPHET router as described in 
 * <I>Probabilistic routing in intermittently connected networks</I> by
 * Anders Lindgren et al.
 */
public class BioDRNProphetRouter extends ActiveRouter {
	/** delivery predictability initialization constant*/
	public static final double P_INIT = 0.75;
	/** delivery predictability transitivity scaling constant default value */
	public static final double DEFAULT_BETA = 0.25;
	/** delivery predictability aging constant */
	public static final double GAMMA = 0.98;
	
	/** Prophet router's setting namespace ({@value})*/ 
	public static final String PROPHET_NS = "ProphetRouter";
	/**
	 * Number of seconds in time unit -setting id ({@value}).
	 * How many seconds one time unit is when calculating aging of 
	 * delivery predictions. Should be tweaked for the scenario.*/
	public static final String SECONDS_IN_UNIT_S ="secondsInTimeUnit";
	
	/**
	 * Transitivity scaling constant (beta) -setting id ({@value}).
	 * Default value for setting is {@link #DEFAULT_BETA}.
	 */
	public static final String BETA_S = "beta";

	/** the value of nrof seconds in time unit -setting */
	private int secondsInTimeUnit;
	/** value of beta setting */
	private double beta;

	/** delivery predictabilities */
	private Map<DTNHost, Double> preds;
	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;
	
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
	private int firstCD;
	private int lastFailedNodesSamplingUpdate = 0;
	private int failedNodesSamplingInterval = 300;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public BioDRNProphetRouter(Settings s) {
		super(s);
		Settings prophetSettings = new Settings(PROPHET_NS);
		secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
		if (prophetSettings.contains(BETA_S)) {
			beta = prophetSettings.getDouble(BETA_S);
		}
		else {
			beta = DEFAULT_BETA;
		}

		initPreds();
		
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
		
		if(s.contains("failedNodesSamplingInterval")) {
			this.failedNodesSamplingInterval = s.getInt("failedNodesSamplingInterval");
		}
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected BioDRNProphetRouter(BioDRNProphetRouter r) {
		super(r);
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		this.beta = r.beta;
		initPreds();
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
		this.firstCD = r.firstCD;
		this.failedNodesSamplingInterval = r.failedNodesSamplingInterval;
	}

	
	/**
	 * Initializes predictability hash
	 */
	private void initPreds() {
		this.preds = new HashMap<DTNHost, Double>();
	}

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
		}
	}
	
	/**
	 * Updates delivery predictions for a host.
	 * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
	 * @param host The host we just met
	 */
	private void updateDeliveryPredFor(DTNHost host) {
		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * P_INIT;
		preds.put(host, newValue);
	}
	
	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param host The host to look the P for
	 * @return the current P value
	 */
	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (preds.containsKey(host)) {
			return preds.get(host);
		}
		else {
			return 0;
		}
	}
	
	/**
	 * Updates transitive (A->B->C) delivery predictions.
	 * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
	 * @param host The B host who we just met
	 */
	private void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof BioDRNProphetRouter : "PRoPHET only works " + 
			" with other routers of same type";
		
		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds = 
			((BioDRNProphetRouter)otherRouter).getDeliveryPreds();
		
		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}
			
			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pOld + ( 1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);
		}
	}

	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 * @see #SECONDS_IN_UNIT_S
	 */
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / 
			secondsInTimeUnit;
		
		if (timeDiff == 0) {
			return;
		}
		
		double mult = Math.pow(GAMMA, timeDiff);
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue()*mult);
		}
		
		this.lastAgeUpdate = SimClock.getTime();
	}
	
	/**
	 * Returns a map of this router's delivery predictions
	 * @return a map of this router's delivery predictions
	 */
	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.preds;
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
	public void update() {
		super.update();
		updateNeighborList();
		failedNodeList();
		reduceSendingAndScanningEnergy();
		if (!canStartTransfer() ||isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
		
		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		tryOtherMessages();		
	}
	
	//Update neighbor list based on time slot and failed nodes
		protected void updateNeighborList() {
			if (SimClock.getIntTime() >= this.lastSamplingUpdate) {
				currentNodeNeighborList = neighborListReader.getNeighborList(getHost().toString(), SimClock.getIntTime());
				this.lastSamplingUpdate += this.samplingInterval;
				getHost().setNeighborList(currentNodeNeighborList);
				
				if (currentNodeNeighborList!= null && currentNodeNeighborList.size() > 0) {
					getHost().setNeighborList(currentNodeNeighborList);
				}
				
//				failedNodeList = failedNodeListReader.getFailedNodeList(SimClock.getIntTime());
				
				if(currentNodeNeighborList != null && getHost().toString().matches("n30")){
					System.out.println("Current energy; "+ getHost().getComBus().getDouble(ENERGY_VALUE_ID, -1));
					System.out.println("At time: " + SimClock.getIntTime() +" Neighorlist: ");
					System.out.println("Node " + getHost().toString() +" : " + currentNodeNeighborList.toString());
					//System.out.println("Failed node list: " + failedNodeList);
				}
			}	
		}
	
	
	protected void failedNodeList() {
		if (SimClock.getIntTime() >= this.lastFailedNodesSamplingUpdate) {
			this.lastFailedNodesSamplingUpdate += this.failedNodesSamplingInterval;
			failedNodeList = failedNodeListReader.getFailedNodeList(SimClock.getIntTime());
			if(failedNodeList != null && getHost().toString().matches("n30")){
				System.out.println("Failed node list: " + failedNodeList);
			}
		}
	}
	
	protected boolean shouldMessageBeSent(Connection con) {
		boolean canMsgBeSent= false;
		DTNHost host, otherHost;
		host = getHost();
		otherHost = con.getOtherNode(getHost());
		
		String sOtherHost;
		sOtherHost = otherHost.toString(); 
		
		int hostId = Integer.parseInt(host.toString().substring(1));
		int oHostId =Integer.parseInt(sOtherHost.substring(1));
		
		if(hostId >= this.firstCD && oHostId >= this.firstCD){
//			if (hostId == this.firstCD)
//				System.out.println("host " + hostId + " - " + oHostId);
			canMsgBeSent = true;
		}
		
		if(host.getNeighborList()!= null && host.getNeighborList().contains(sOtherHost)){
				//|| (otherHost.getNeighborList()!= null && otherHost.getNeighborList().contains(host.toString()))){
			canMsgBeSent = true;
		}
		else
			canMsgBeSent = false;
		return canMsgBeSent;	
	}
	
	
	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			BioDRNProphetRouter othRouter = (BioDRNProphetRouter)other.getRouter();
			
			if(shouldMessageBeSent(con) == false) {
				continue;
			}
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				if (othRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
					// the other node has higher probability of delivery
					messages.add(new Tuple<Message, Connection>(m,con));
				}
			}			
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		// sort the message-connection tuples
		Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	/**
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their delivery probability by the host on the other side of the 
	 * connection (GRTRMax)
	 */
	private class TupleComparator implements Comparator 
		<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((BioDRNProphetRouter)tuple1.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((BioDRNProphetRouter)tuple2.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple2.getKey().getTo());

			// bigger probability should come first
			if (p2-p1 == 0) {
				/* equal probabilities -> let queue mode decide */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			}
			else if (p2-p1 < 0) {
				return -1;
			}
			else {
				return 1;
			}
		}
	}
	
	@Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryPreds();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(preds.size() + 
				" delivery prediction(s)");
		
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();
			
			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", 
					host, value)));
		}
		
		top.addMoreInfo(ri);
		return top;
	}
	
	@Override
	public MessageRouter replicate() {
		BioDRNProphetRouter r = new BioDRNProphetRouter(this);
		return r;
	}

	@Override
	public void moduleValueChanged(String key, Object newValue) {
		this.currentEnergy = (Double) newValue;
	}

}
