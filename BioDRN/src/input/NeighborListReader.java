/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package input;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import core.Coord;
import core.SettingsError;
import core.Tuple;


/**
 * Reader for ExternalMovement movement model's time-location tuples.
 * <P>
 * First line of the file should be the offset header. Syntax of the header
 * should be:<BR>
 * <CODE>minTime maxTime minX maxX minY maxY minZ maxZ</CODE>
 * <BR>
 * Last two values (Z-axis) are ignored at the moment but can be present 
 * in the file.
 * <P>
 * Following lines' syntax should be:<BR>
 * <CODE>time id xPos yPos</CODE><BR>
 * where <CODE>time</CODE> is the time when a node with <CODE>id</CODE> should
 * be at location <CODE>(xPos, yPos)</CODE>.
 * </P>
 * <P>
 * All lines must be sorted by time. Sampling interval (time difference between
 * two time instances) must be same for the whole file.
 * </P>
 */
public class NeighborListReader {
	/* Prefix for comment lines (lines starting with this are ignored) */
	public static final String COMMENT_PREFIX = "#";
	private Scanner scanner;
	private double lastTimeStamp = -1;
	private String lastLine;
	private double minTime;
	private double maxTime;
	private boolean normalize;
	private String survivorId = "n";
	private ArrayList<String> allLines = new ArrayList<String>();
		
	/**
	 * Constructor. Creates a new reader that reads the data from a file.
	 * @param inFilePath Path to the file where the data is read
	 * @throws SettingsError if the file wasn't found
	 */
	public NeighborListReader(String inFilePath) {
		this.normalize = true;
		File inFile = new File(inFilePath);
		try {
				scanner = new Scanner(inFile);
			} catch (FileNotFoundException e) {
				System.out.println("Couldn't find external movement input " +
						"file " + inFile);
			}
			
			String offsets = scanner.nextLine();
		
			try {
				Scanner lineScan = new Scanner(offsets);
				minTime = lineScan.nextDouble();
				maxTime = lineScan.nextDouble();
				System.out.println("Min and Max time " + minTime + " " + maxTime);
			} catch (Exception e) {
				System.out.println("Invalid offset line '" + offsets + "'");
			}
			
//			lastLine = scanner.nextLine();
//			System.out.println("Last line: " + lastLine);
//			
			
	 //read all lines
		while(scanner.hasNextLine()){
			String currentLine = scanner.nextLine();
			//System.out.println("Here " + currentLine );
			allLines.add(currentLine);
		}
	}
	
	/**
	 * Sets normalizing of read values on/off. If on, values returned by 
	 * {@link #readNextMovements()} are decremented by minimum values of the
	 * offsets. Default is on (normalize).
	 * @param normalize If true, normalizing is on (false -> off).
	 */
	public void setNormalize(boolean normalize) {
		this.normalize = normalize;
	}
	
	public ArrayList<String> getNeighborListEdgeRewiring(String nodeId){
		ArrayList<String> neighborList = new ArrayList<String>();
		for(int i =0; i < allLines.size(); i++){
			Scanner lineScan = new Scanner(allLines.get(i));
			int time = lineScan.nextInt();
			String id = lineScan.next();
			if((survivorId+id).matches(nodeId)){
				while(lineScan.hasNext()){
					int value = lineScan.nextInt();
//					System.out.println(value +" ");
					neighborList.add(survivorId+value);
				}
			}
				
		}
		return neighborList;
	}
	
	public ArrayList<String> getNeighborList(String nodeId, int simTime){
		ArrayList<String> neighborList = new ArrayList<String>();
		for(int i =0; i < allLines.size(); i++){
			Scanner lineScan = new Scanner(allLines.get(i));
			int time = lineScan.nextInt();
			String id = lineScan.next();
			if(Math.abs(simTime - time) == 0 && (survivorId+id).matches(nodeId)){
//				System.out.println("Simtime: " + simTime + " Time: " + time + " ");
				
				while(lineScan.hasNext()){
					int value = lineScan.nextInt();
					//System.out.print(value +" ");
					neighborList.add(survivorId+value);
				}
			}
				
		}
		
//		if(simTime == 0  && nodeId.matches("n0")){
//			System.out.println("At time" + simTime + " Neighborlist: " + neighborList);
//		}
		return neighborList;
	}
	
	
	/**
	 * Reads all new id-coordinate tuples that belong to the same time instance
	 * @return A list of tuples or empty list if there were no more moves
	 * @throws SettingError if an invalid line was read
	 */
	public List<Tuple<String, List>> readNextNeighbors() {
		
	//System.out.println("Hello");
	ArrayList<Tuple<String, List>> neighbors = 
		new ArrayList<Tuple<String, List>>();
	List<String> eachNeighborList = new ArrayList<String>();
	
	if (!scanner.hasNextLine()) {
		System.out.println("Does not have next line");
		return neighbors;
	}
	
	Scanner lineScan = new Scanner(lastLine);
	//System.out.println("Current Line: " + lastLine);
	double time = lineScan.nextDouble();
	String id = lineScan.next();
	System.out.println("Time : " + time + " Id: "  + id);
	
	//Store the neighbors in a list
	while(lineScan.hasNext()){
		Double value = lineScan.nextDouble();
		//System.out.print(value +" ");
		eachNeighborList.add(survivorId+value);
	}
	
	if (normalize) {
		time -= minTime;
//		x -= minX;
//		y -= minY;
	}
	
	lastTimeStamp = time;
	
	while (scanner.hasNextLine() && lastTimeStamp == time) {
		lastLine = scanner.nextLine();
		//System.out.println("Line: " + lastLine);
		
		if (lastLine.trim().length() == 0 || 
				lastLine.startsWith(COMMENT_PREFIX)) {
			continue; /* skip empty and comment lines */
		}
		int idVal = Integer.valueOf(id);
		// add previous line's tuple
		neighbors.add(new Tuple<String, List>(survivorId + id, eachNeighborList));
		//System.out.println("Added: " + id + " - " + eachNeighborList + " <=> " + neighbors.get(idVal).getKey() +" => "+neighbors.get(idVal).getValue());
		
		//moves.add(new Tuple<String, Coord>(id, new Coord(x,y)));		
		
		lineScan = new Scanner(lastLine);
		
		try {
			eachNeighborList = new ArrayList<String>();
			
			time = lineScan.nextDouble();
			id = lineScan.next();
			
			while(lineScan.hasNext()){
				Double value = lineScan.nextDouble();
				//System.out.print(value +" ");
				eachNeighborList.add(survivorId+ value);
			}
			System.out.println("\n");
		} catch (Exception e) {
			System.out.println("Invalid line '" + lastLine + "'");
		}
		
		if (normalize) {
			time -= minTime;
//			x -= minX;
//			y -= minY;
		}
	}
	
	if (!scanner.hasNextLine()) {	// add the last tuple of the file
		neighbors.add(new Tuple<String, List>(survivorId + id, eachNeighborList));
	}
	//System.out.println("Neighbors List: " + neighbors);
	return neighbors;
}
	
	
	
	public HashMap<String, List> readNextNeighborsMap() {
		
	//System.out.println("Hello");
	HashMap<String, List> neighbors = 
		new HashMap<String, List>();
	List<String> eachNeighborList = new ArrayList<String>();
	
	if (!scanner.hasNextLine()) {
		System.out.println("Does not have next line");
		return neighbors;
	}
	
	Scanner lineScan = new Scanner(lastLine);
	//System.out.println("Current Line: " + lastLine);
	double time = lineScan.nextDouble();
	String id = lineScan.next();
	System.out.println("Time : " + time + " Id: "  + id);
	
	//Store the neighbors in a list
	while(lineScan.hasNext()){
		Double value = lineScan.nextDouble();
		//System.out.print(value +" ");
		eachNeighborList.add(survivorId+value);
	}
	
	if (normalize) {
		time -= minTime;
//		x -= minX;
//		y -= minY;
	}
	
	lastTimeStamp = time;
	
	while (scanner.hasNextLine() && lastTimeStamp == time) {
		lastLine = scanner.nextLine();
		//System.out.println("Line: " + lastLine);
		
		if (lastLine.trim().length() == 0 || 
				lastLine.startsWith(COMMENT_PREFIX)) {
			continue; /* skip empty and comment lines */
		}
		
		// add previous line's tuple
		neighbors.put(survivorId + id , eachNeighborList);
	
		//System.out.println("Added: " + id + " - " + eachNeighborList + " <=> " + neighbors.get(idVal).getKey() +" => "+neighbors.get(idVal).getValue());
		
		//moves.add(new Tuple<String, Coord>(id, new Coord(x,y)));		
		
		lineScan = new Scanner(lastLine);
		
		try {
			eachNeighborList = new ArrayList<String>();
			
			time = lineScan.nextDouble();
			id = lineScan.next();
			
			while(lineScan.hasNext()){
				Double value = lineScan.nextDouble();
				//System.out.print(value +" ");
				eachNeighborList.add(survivorId+ value);
			}
			System.out.println("\n");
		} catch (Exception e) {
			System.out.println("Invalid line '" + lastLine + "'");
		}
		
		if (normalize) {
			time -= minTime;
//			x -= minX;
//			y -= minY;
		}
	}
	
	if (!scanner.hasNextLine()) {	// add the last tuple of the file
		neighbors.put(survivorId + id, eachNeighborList);
	}
	//System.out.println("Neighbors List: " + neighbors);
	return neighbors;
}


	
	/**
	 * Returns the time stamp where the last moves read with 
	 * {@link #readNextMovements()} belong to.
	 * @return The time stamp
	 */
	public double getLastTimeStamp() {
		return lastTimeStamp;
	}

	/**
	 * Returns offset maxTime
	 * @return the maxTime
	 */
	public double getMaxTime() {
		return maxTime;
	}

	/**
	 * Returns offset maxX
	 * @return the maxX
	 */
//	public double getMaxX() {
//		return maxX;
//	}

	/**
	 * Returns offset maxY
	 * @return the maxY
	 */
//	public double getMaxY() {
//		return maxY;
//	}

	/**
	 * Returns offset minTime
	 * @return the minTime
	 */
	public double getMinTime() {
		return minTime;
	}

	/**
	 * Returns offset minX
	 * @return the minX
	 */
//	public double getMinX() {
//		return minX;
//	}

	/**
	 * Returns offset minY
	 * @return the minY
	 */
//	public double getMinY() {
//		return minY;
//	}
	
}
