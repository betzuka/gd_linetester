package greendragons.linetester;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import com.fazecast.jSerialComm.SerialPort;


public class Machine implements Runnable {

	public static double maxForce = 200;
	private MachineObserver observer;
	
	private class PortWrapper {
		private SerialPort port;
		private BufferedReader in;
		private BufferedWriter out;
		
		private PortWrapper(SerialPort port) {
			port.openPort();
			port.setComPortParameters(115200, 8, 1, 0);
			port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
			in = new BufferedReader(new InputStreamReader(port.getInputStream()));
			out = new BufferedWriter(new OutputStreamWriter(port.getOutputStream()));
		}
		
		public synchronized void write(String s) throws IOException {
			out.write(s);
			out.flush();
		}
		
		public String readLine() throws IOException {
			return in.readLine();
		}
		
	}
	
	private PortWrapper portWrapper;
	
	public Machine(SerialPort port, MachineObserver observer) {
		this.observer = observer;
		this.portWrapper = new PortWrapper(port);
	}

	private MachineState parseMachineState(String s) {
		switch (parseInt(s)) {
		case 1: return MachineState.ESTOP;
		case 2: return MachineState.READY;
		case 3: return MachineState.HOMING;
		case 4: return MachineState.TESTING;
		}
		throw new IllegalStateException();
	}
	
	private int parseInt(String s) {
		return Integer.parseInt(s);
	}
	
	
	private boolean parseBoolean(String s) {
		return parseInt(s)==1;
	}
	
	
	public void run() {
		try {
			String s = null;
			while ((s = portWrapper.readLine())!=null) {
				String [] parts = s.split(",");
				if (parts[0].equals("M")) {
					//test result
					MachineState state = parseMachineState(parts[1]);
					boolean success = parseBoolean(parts[2]);
					int targetForce = parseInt(parts[3]);
					int forceAchieved = parseInt(parts[4]);
					int lastForceAchieved = parseInt(parts[5]);
					int length = parseInt(parts[6]);
					boolean lineBreak = parseBoolean(parts[7]);
					
					LineTestResult result = new LineTestResult(success, state, targetForce, forceAchieved, lastForceAchieved, length, lineBreak);
					observer.onResult(result);
					
				} else if (parts[0].equals("Z")) {
					//response
					boolean success = parseBoolean(parts[1]);
					MachineState state = parseMachineState(parts[2]);
					int currentForce = parseInt(parts[3]);
					boolean extendLimitHit = parseBoolean(parts[4]);
					boolean retractLimitHit = parseBoolean(parts[5]);
					boolean forceLimitHit = parseBoolean(parts[6]);
					int rawForce = parseInt(parts[7]);
					
					MachineResponse resp = new MachineResponse(success, new MachineStatus(state, currentForce, rawForce, extendLimitHit, retractLimitHit, forceLimitHit));
					
					observer.onResponse(resp);
				}
			}
		} catch (IOException e) {
			observer.onError(e);
		}
	}
	
	private  void send(String s) {
		try {
			portWrapper.write(s + "\r");
		} catch (IOException e) {
			observer.onError(e);
		}
	}
			
	public void getStatus() {
		send("S");
	}
	
	public void home() {
		send("H");
	}
	
	public void reset() {
		send("R");
	}
	
	public void estop() {
		send("E");
	}
	
	public void turnLaserOn() {
		send("L");
	}
	
	public void turnLaserOff() {
		send("l");
	}
	
	public void streamStatus() {
		send("D");
	}
	
	public void startTest(int force, int timeToHold, boolean returnHome) {
		send(String.format("T,%d,%d,%d", force, timeToHold, returnHome ? 1 : 0));
	}
	
		
}
