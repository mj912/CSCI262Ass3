import java.util.*;
import java.io.*;

class InconsistentException extends Exception {
	String s;
	InconsistentException (String s) {
		this.s=s;
	}
	
	public String toString() {
		return("InconsistentException occurred: "+ s);
	}
}

class VehicleType {
	String name;
	boolean canPark;
	String regFormat;
	int volumeWeight;
	int speedWeight;
	
	public VehicleType(String name, boolean canPark, String regFormat, int volumeWeight, int speedWeight) {
		this.name=name;
		this.canPark=canPark;
		this.regFormat=regFormat;
		this.volumeWeight=volumeWeight;
		this.speedWeight=speedWeight;
	}
}

class Vehicle {
	String type;
	boolean isParked;
	String regPlate; //according to regFormat
	double curSpeed; //km/h
	double distance; //distance traveled on the road. Starts from 0. Unit: km
	
	public Vehicle(VehicleType vehicleType, double startSpeed, String regPlate) {
		this.type=vehicleType.name;
		this.regPlate=regPlate;
		isParked=false;
		curSpeed=startSpeed;
		distance=0;
	}
}

class Stat {
	String name;
	double numMean;
	double numStdDev;
	double speedMean;
	double speedStdDev;
	Random rand;
	
	public Stat(String name, double numMean, double numStdDev, double speedMean, double speedStdDev) {
		rand=new Random();
		this.name=name;
		this.numMean=numMean;
		this.numStdDev=numStdDev;
		this.speedMean=speedMean;
		this.speedStdDev=speedStdDev;
	}
	
	public double getGaussianNumber() {
		return numMean + rand.nextGaussian()*numStdDev;
	}
	
	public double getGaussianSpeed() {
		return speedMean + rand.nextGaussian()*speedStdDev;
	}
}

enum EventType {
	ARRIVAL, DEPART_SIDE, DEPART_END,PARK, MOVE
}

class Event {
	String vehicleType;
	String plate;
	EventType eType;
	int d,m; //day,hour,minute of the event
	
	public Event(Vehicle v, EventType eType, int d, int m) {
		this.vehicleType=v.type;
		plate=v.regPlate;
		this.eType=eType;
		this.d=d;
		this.m=m;
	}
	
	public String toString() {
		return vehicleType+","+plate+","+eType.toString()+","+d+","+m;
	}
}

class ArrivalEvent extends Event {
	double arrivalSpeed;
	
	public ArrivalEvent(Vehicle v, int d, int m) {
		super(v,EventType.ARRIVAL,d,m);
		this.arrivalSpeed=v.curSpeed;
	}
	
	public String toString() {
		return vehicleType+","+plate+","+eType.toString()+","+arrivalSpeed+","+d+","+m;
	}
}

class ParkEvent extends Event {
	boolean park; //if true, the vehicle starts parking, if false, the vehicle stop parking
	
	public ParkEvent(Vehicle v, int d, int m, boolean park) {
		super(v,EventType.PARK,d,m);
		this.park=park;
	}
	
	public String toString() {
		return vehicleType+","+plate+","+eType.toString()+","+(park? "StartPark" : "StopPark")+","+d+","+m;
	}
}

public class Traffic { 
	private int monitoredTypes; 
	private int days; //number of days to log
	private int length;
	private int maxSpeed;
	private int parkingSpaces;
	private int remainingVehicles; //number of all remainingVehicles in remains
	private boolean baseline;
	private HashMap<String, VehicleType> vehicleTypes;
	private HashMap<String, Stat> stats;
	private ArrayList<Vehicle> vehicles; //all vehicles currently on the road
	private ArrayList<Vehicle> newArrivals;
	private HashMap<String, LinkedList<String>> remains; //number of vehicles per type remained for each day to be generated (linked with name of the type)
	private ArrayList<Event> events; //collection of all events in all days
	private Random r;
	private HashSet<String> plateSet; //a set of unique plates for all vehicles
	
	public Traffic(String vehicleFile,String statFile,int days) throws IOException,InconsistentException,NumberFormatException,IllegalArgumentException {
		readVehicleFile(vehicleFile);
		readStatFile(statFile);
		this.days=days;
		
		if (vehicleTypes.size()!=stats.size()) {
			throw new InconsistentException("Number of vehicle types is not consistent between 2 files");
		}
		
		remains = new HashMap<String, LinkedList<String>>();
		remainingVehicles=0;
		vehicles = new ArrayList<Vehicle>();
		newArrivals = new ArrayList<Vehicle>();
		events=new ArrayList<Event>();
		r = new Random();
		plateSet = new HashSet<>();
	}
	
	private void readVehicleFile(String vehicleFile) throws IOException,InconsistentException, IllegalArgumentException, NumberFormatException
	{
		BufferedReader r = new BufferedReader(new FileReader(vehicleFile));
		String line= r.readLine(); //read the first line - the number of monitored vehicle types
		monitoredTypes = Integer.parseInt(line);
		if(monitoredTypes < 1) {
			throw new IllegalArgumentException("Monitored vehicles must be greater than 0.");
		}
		
		int readCounter = 0;
		vehicleTypes = new HashMap<String,VehicleType>();
		while ((line=r.readLine())!=null) { //read in subsequent lines
			String[] fields = line.split(":");
			String name=fields[0];
			boolean canPark = (fields[1].equals("0")) ? false : true;
			String regFormat = fields[2];
			int volumeWeight = Integer.parseInt(fields[3]);
			int speedWeight = Integer.parseInt(fields[4]);
			if(volumeWeight < 0 || speedWeight < 0) {
				throw new IllegalArgumentException("Invalid input in vehicles file. Ensure weights are equal to or greater then 0.");
			}
			VehicleType v = new VehicleType(name,canPark,regFormat,volumeWeight,speedWeight);
			vehicleTypes.put(name,v); //associate each vehicle with the name
			readCounter++;
		}
		r.close();
		if(readCounter != monitoredTypes)
		{
			throw new InconsistentException("Monitored count did not match the amount of vehicles read.");
		}
	}
	
	private void readStatFile(String statFile) throws IOException,InconsistentException, IllegalArgumentException, NumberFormatException
	{
		BufferedReader r = new BufferedReader(new FileReader(statFile));
		String line = r.readLine();
		String[] roadStats = line.split(" ");
		
		if (Integer.parseInt(roadStats[0])!= monitoredTypes) {
			throw new InconsistentException("Number of vehicle types is not consistent between 2 files");
		}
		
		length = Integer.parseInt(roadStats[1]);
		maxSpeed = Integer.parseInt(roadStats[2]);
		parkingSpaces = Integer.parseInt(roadStats[3]);
		if(length < 0 || maxSpeed < 0 || parkingSpaces < 0) {
			throw new IllegalArgumentException("Invalid input in stats file. Ensure your length, speed and parking spaces are correct.");
		}
		
		int readCounter = 0;
		stats = new HashMap<String,Stat>();
		while ((line=r.readLine())!=null) {
			String[] fields = line.split(":");
			String name = fields[0];
			if(!vehicleTypes.containsKey(name)) {
				throw new InconsistentException("Vehicle types were not consistent.");
			}
			double numMean = Double.parseDouble(fields[1]);
			double numStdDev = Double.parseDouble(fields[2]);
			double speedMean = Double.parseDouble(fields[3]);
			double speedStdDev = Double.parseDouble(fields[4]);
			if(numMean < 0 || numStdDev < 0 || speedMean < 0 || speedStdDev < 0) {
				throw new IllegalArgumentException("Invalid input in stats file. Ensure statistical data is correct.");
			}
			Stat s = new Stat(name,numMean,numStdDev,speedMean,speedStdDev);
			stats.put(name,s);
			readCounter++;
		}
		r.close();
		if(readCounter != monitoredTypes)
		{
			throw new InconsistentException("Monitored count did not match the amount of vehicles read.");
		}
	}
	
	public void generateAndLog() throws IOException {
		System.out.println("Started generating events");
		for (int d=1; d<=days; d++) { //for each day
			System.out.println("Current day: "+ d);
			remains.clear(); //clear all remaining at the beginning of each day
			remainingVehicles=0;
			for (String name : vehicleTypes.keySet()) {
				int vehicleNum = (int) stats.get(name).getGaussianNumber();
				remainingVehicles+=vehicleNum;
				LinkedList<String> plates = new LinkedList<>();
				System.out.println(name);
				for (int i=0; i< vehicleNum; i++) {
					plates.add(generateUniquePlate(vehicleTypes.get(name)));
					//System.out.println(plates.get(i));
				}
				remains.put(name, plates);
			}
			for (int m=1; m<=1440;m++) { //for each minute
				if (vehicles.size()==0) { //if there's no current vehicle in the system, we have to generate more vehicles. Generate one more randomly
					if (m>1380) { //if it's over 23:00 already, it mean we are done for the day because empty street, no new vehicle arrive
						break;
					}
					else if (remainingVehicles<=0) {
						break;
					}
					else {
						Vehicle v=vehicleArrive(); //becuase remainingVehicles >0, we guarantee to generate a new vehicle v
						System.out.println("Empty and added vehicle: "+v.regPlate);
						events.add(new ArrivalEvent(v,d,m));
					}
				}
				Iterator<Vehicle> it = vehicles.iterator();
				while (it.hasNext()) {
					Vehicle v = it.next();
					if (!v.isParked) { //if v is not parked, then every v has to move and possibly change speed
						double speedDelta=r.nextDouble()*2-1; //to range from -1.0 to 1.0 km/h
						v.curSpeed+=speedDelta;
						v.distance+=v.curSpeed/60; //this is km/minute because v.curSpeed is km/h
						events.add(new Event(v,EventType.MOVE,d,m));
						
						if (v.distance>=length) { //the vehicle goes to the end of the road
							events.add(new Event(v,EventType.DEPART_END,d,m));
							it.remove();
							continue;
						}
					}
					EventType t = randomizeEventType(); //generate a random index between [0,99] and see which eventType correspond to that.
					if (t==EventType.DEPART_SIDE && !v.isParked) {
						events.add(new Event(v,t,d,m));
						it.remove();
					}
					else if (t==EventType.PARK) {
						if (v.isParked) { //if v is currently parked, we want it to stop parking
							v.isParked=false;
							parkingSpaces++;
							v.curSpeed=stats.get(v.type).getGaussianSpeed();
							events.add(new ParkEvent(v,d,m,false));
						}
						else { //if v is currently not parked
							if (vehicleTypes.get(v.type).canPark && parkingSpaces>0) { //if v is of a type that can park and there're enough parking spaces
								v.isParked=true;
								parkingSpaces--;
								events.add(new ParkEvent(v,d,m,true));
							}
						}
					}
					else { //this must be EventType.ARRIVAL
						if (m<=1380 && remainingVehicles>0) { //if it is still <= 23:00 and still remaining some vehicles
							Vehicle newV=vehicleArrive();
							System.out.println("Not Empty and added vehicle: "+newV.regPlate);
							events.add(new ArrivalEvent(newV,d,m));
						}
					}
				}
				vehicles.addAll(newArrivals);
				newArrivals.clear();
			}
		}
		
		BufferedWriter w = new BufferedWriter(new FileWriter("log.txt"));
		w.write(Integer.toString(days));
		w.newLine();
		for (Event e : events) {
			w.write(e.toString());
			w.newLine();
		}
		w.close();
	}
	
	private String generateUniquePlate(VehicleType vType) {
		String newPlate = generatePlate(vType);
		while (plateSet.contains(newPlate)) {
			newPlate=generatePlate(vType);
		}
		return newPlate;
	}
	
	private String generatePlate(VehicleType vType) {
		StringBuilder b = new StringBuilder();
		for (int i=0; i< vType.regFormat.length(); i++) {
			if (vType.regFormat.charAt(i)=='D') {
				b.append(r.nextInt(10)); //append one digit from 0-9
			}
			else {
				char c = (char)('A'+r.nextInt(26));
				b.append(c); //r.nextInt(26) returns 0 to 25, plus 'A' return A to Z
			}
		}
		return b.toString();
	}
	
	private Vehicle vehicleArrive() { //whether it's possible to add another vehicle of this type to the road
		if (remainingVehicles>0) {
			VehicleType vehicleType = randomizeVehicleType();
			String regPlate=remains.get(vehicleType.name).removeFirst();
			remainingVehicles--;
			double startSpeed= stats.get(vehicleType.name).getGaussianSpeed();
			Vehicle v = new Vehicle(vehicleType,startSpeed,regPlate);
			newArrivals.add(v);
			return v;
		}
		else {
			return null;
		}
	}
	
	private VehicleType randomizeVehicleType() {
		List<String> types = new ArrayList<>();
		for (String type: remains.keySet()) {
			if (remains.get(type).size()>0) { //only add to the to be randomized array elements that are of size >0
				types.add(type);
			}
		}
		int index = r.nextInt(types.size());
		return vehicleTypes.get(types.get(index));
	}
	
	private EventType randomizeEventType() {
		int n = r.nextInt(100);
		if (n<5) {
			return EventType.DEPART_SIDE;
		}
		else if (n<95) {
			return EventType.ARRIVAL;
		}
		else {
			return EventType.PARK;
		}
	}
	
	private void analyze(String outputFile) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader("log.txt"));
		String line=reader.readLine();
		int days=Integer.parseInt(line);
		
		HashMap<String,int[]> numberMap = new HashMap<String, int[]>(); //ArrayList contain totals for each day of a type
		HashMap<String,double[]> dailySpeedMap = new HashMap<String, double[]>(); //contain the total arrivalSpeed for each type in each day
		HashMap<String,ArrayList<Double>> speedMap = new HashMap<String,ArrayList<Double>>(); //array list contains all speed of a type
		HashMap<String,Integer> vehicleMap = new HashMap<>(); //map regPlate with arrivalTime, to be able to test speed breaches
		HashMap<Integer,ArrayList<String>> breachedVehicles = new HashMap<Integer,ArrayList<String>>();
		
		System.out.println("Start analyzing log file...");
		while ((line=reader.readLine())!=null) {
			String[] fields = line.split(",");
			String vType = fields[0];
			String eventType = fields[2];
			int d = Integer.parseInt(fields[fields.length-2]);
			if (eventType.equals("ARRIVAL")) {
				String regPlate = fields[1];
				int arrivalMinute = Integer.parseInt(fields[fields.length-1]);
				vehicleMap.put(regPlate,arrivalMinute);
				double arrivalSpeed = Double.parseDouble(fields[3]);
				if (dailySpeedMap.containsKey(vType)) {
					dailySpeedMap.get(vType)[d-1]+=arrivalSpeed;
				}
				else {
					double[] dailyTotalSpeeds = new double[days];
					dailyTotalSpeeds[d-1]=arrivalSpeed;
					dailySpeedMap.put(vType,dailyTotalSpeeds);
				}
				if (speedMap.containsKey(vType)) {
					speedMap.get(vType).add(arrivalSpeed);
				}
				else {
					ArrayList<Double> speedList = new ArrayList<>();
					speedList.add(arrivalSpeed);
					speedMap.put(vType,speedList);
				}
				if (numberMap.containsKey(vType)) {
					numberMap.get(vType)[d-1]++;
				}
				else {
					int[] dailyTotalNums = new int[days];
					dailyTotalNums[d-1]=1;
					numberMap.put(vType,dailyTotalNums);
				}
			}
			else if (eventType.equals("DEPART_END")) {
				String regPlate = fields[1];
				int departMinute = Integer.parseInt(fields[fields.length-1]);
				double averageSpeed = (double)(length)/(departMinute - vehicleMap.get(regPlate))*60; //to convert km/minute -> km/h
				if (averageSpeed > maxSpeed) {
					if (breachedVehicles.containsKey(d)) {
						breachedVehicles.get(d).add(regPlate);
					}
					else {
						ArrayList<String> breachOfToday = new ArrayList<>();
						breachOfToday.add(regPlate);
						breachedVehicles.put(d,breachOfToday);
					}
				}
			}
		}
		
		//calculate statistics, numberMap, speedMap, breachedVehicles
		System.out.println("Calculating vehicle statistics...");
		//if run in baseline mode, generate baseline stats. If run in live traffic, generate another file liveStats.txt
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
		writer.write(monitoredTypes+" "+length+" "+maxSpeed+" "+parkingSpaces);
		writer.newLine();
		for (String type: vehicleTypes.keySet()) {
			double numberMean=0.0, numberStdDev=0.0, speedMean=0.0, speedStdDev=0.0;
			if (numberMap.containsKey(type)) {
				int[] dailyTotalNums = numberMap.get(type);
				
				//calculate volume mean
				int total=0;
				for (int i : dailyTotalNums) { //each dailyTotalNums[i] is the total number of vehicles for this type, day i+1
					total+=i;
				}
				numberMean = (double)(total)/dailyTotalNums.length;
				
				//calculate volume standard deviation
				double squaredTotal=0;
				for (int i : dailyTotalNums) {
					squaredTotal+=(i-numberMean)*(i-numberMean);
				}
				if (dailyTotalNums.length>1) {
					numberStdDev = Math.sqrt(squaredTotal/(dailyTotalNums.length-1));
				}
			}
			if (speedMap.containsKey(type)) {
				ArrayList<Double> speedList = speedMap.get(type);
				
				//calculate speed mean
				double total=0;
				for (double speed : speedList) {
					total+=speed;
				}
				speedMean = total/speedList.size();
				
				//calculate speed standard deviation
				double squaredTotal=0;
				for (double speed: speedList) {
					squaredTotal+=(speed-speedMean)*(speed-speedMean);
				}
				if (speedList.size()>1) {
					speedStdDev=Math.sqrt(squaredTotal/(speedList.size()-1));
				}
			}
			writer.write(type+":"+numberMean+":"+numberStdDev+":"+speedMean+":"+speedStdDev+":");
			writer.newLine();
		}
		writer.close();
		
		//list all vehicle plates that breaches the speed limit
		System.out.println("Listing breached vehicles...");
		writer = new BufferedWriter(new FileWriter("breachedVehicles.txt"));
		for (int d : breachedVehicles.keySet()) {
			//System.out.println("On day: "+d);
			writer.write("On day: "+d);
			writer.newLine();
			for (String plate : breachedVehicles.get(d)) {
				//System.out.println(plate);
				writer.write(plate);
				writer.newLine();
			}
		}
		writer.close();
		
		//produce daily totals, which contain day:vType:totalNumPerDay:averageSpeedPerDay
		writer = new BufferedWriter(new FileWriter("dailyTotals.txt"));
		for (int d=1; d<=days;d++) {
			for (String type: vehicleTypes.keySet()) {
				writer.write(d+":"+type+":");
				int totalNum=0;
				double averageSpeed=0.0;
				if (numberMap.containsKey(type)) {
					totalNum=numberMap.get(type)[d-1];
					writer.write(totalNum+":");
				}
				if (dailySpeedMap.containsKey(type)) {
					if (totalNum>0) {
						averageSpeed=dailySpeedMap.get(type)[d-1]/totalNum;
					}
					writer.write(averageSpeed+":");
				}
				writer.newLine();
			}
		}
		writer.close();
	}
	
	public static void main(String[] args) throws IOException,NumberFormatException,InconsistentException,IllegalArgumentException {
		String vehicleFile = args[0];
		String statFile = args[1];
		int days = Integer.parseInt(args[2]);
		if(days < 1)
		{
			System.out.println("Days argument invalid. Enter an integer greater then 0: ");
			Scanner in = new Scanner(System.in);
			days = in.nextInt();
			while(days < 1) {
				System.out.println("Days argument invalid. Enter an integer greater then 0: ");
				days = in.nextInt();
			}
			in.close();
		}
		Traffic baselineTraffic = new Traffic (vehicleFile, statFile,days);
		
		//generate events and log file
		baselineTraffic.generateAndLog();
		
		//analyze the log file, produce baselineStats.txt, breachedVehicles.txt, dailyTotals.txt
		baselineTraffic.analyze("baselineStats.txt");
		
		//prompt user for a new file similar to Stats.txt, create a new Traffic instance
		/*
		Traffic liveTraffic = new Traffic(vehicleFile, liveStatFile,newDays);
		liveTraffic.generateAndLog() => create log.txt
		liveTraffic.analyze("liveStats.txt"); // by passing a different output file here, we generate a different stats to compare with baseline
		*/
	}

}
