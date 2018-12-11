
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

public class FailedNodeListReader {
	/* Prefix for comment lines (lines starting with this are ignored) */
	public static final String COMMENT_PREFIX = "#";
	private Scanner scanner;
	private double currTime;
	private String survivorId = "n";
	private ArrayList<String> allLines = new ArrayList<String>();
		
	/**
	 * Constructor. Creates a new reader that reads the data from a file.
	 * @param inFilePath Path to the file where the data is read
	 * @throws SettingsError if the file wasn't found
	 */
	public FailedNodeListReader(String inFilePath) {
		File inFile = new File(inFilePath);
		try {
				scanner = new Scanner(inFile);
			} catch (FileNotFoundException e) {
				System.out.println("Couldn't find external movement input " +
						"file " + inFile);
			}
			
			String currLine = scanner.nextLine();
			try {
				Scanner lineScan = new Scanner(currLine);
//				currTime = lineScan.nextDouble();
//				System.out.println("Current time " + currTime);
			} catch (Exception e) {
				System.out.println("Invalid line '" + currLine + "'");
			}	
			
	 //read all lines
		while(scanner.hasNextLine()){
			String currentLine = scanner.nextLine();
//			System.out.println("Here " + currentLine );
			allLines.add(currentLine);
		}
	}

	
	public ArrayList<String> getFailedNodeList(int simTime){
		ArrayList<String> failedNodeList = new ArrayList<String>();
		for(int i =0; i < allLines.size(); i++){
			try {
				Scanner lineScan = new Scanner(allLines.get(i));
				int time = lineScan.nextInt();
				if(Math.abs(simTime - time) == 0){
					while(lineScan.hasNext()){
						int value = lineScan.nextInt();
						//System.out.print(value +" ");
						failedNodeList.add(survivorId+value);
					}
				}
			}catch (Exception e) {
				System.out.println("Invalid line '" + "'");
			}		
		}
		return failedNodeList;
	}
	
}
