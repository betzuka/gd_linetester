package greendragons.linetester;

public class LineTestResult {
	private boolean success;
	private MachineState state;
	private int targetForce, forceAchieved, lastForceAchieved, length;
	private boolean lineBreak;
	public  LineTestResult(boolean success, MachineState state, int targetForce, int forceAchieved,
			int lastForceAchieved, int length, boolean lineBreak) {
		this.success = success;
		this.state = state;
		this.targetForce = targetForce;
		this.forceAchieved = forceAchieved;
		this.lastForceAchieved = lastForceAchieved;
		this.length = length;
		this.lineBreak = lineBreak;
	}
	public boolean isSuccess() {
		return success;
	}
	public MachineState getState() {
		return state;
	}
	public int getTargetForce() {
		return targetForce;
	}
	public int getForceAchieved() {
		return forceAchieved;
	}
	public int getLastForceAchieved() {
		return lastForceAchieved;
	}
	public int getLength() {
		return length;
	}
	public boolean isLineBreak() {
		return lineBreak;
	}
	
	
	
}
