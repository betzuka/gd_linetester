package greendragons.linetester;

public class MachineStatus {
	
	private MachineState state;
	private int currentForce, currentRawForce;
	private boolean extendLimit, retractLimit, forceLimit;
	public MachineStatus(MachineState state, int currentForce, int currentRawForce, boolean extendLimit,
			boolean retractLimit, boolean forceLimit) {
		this.state = state;
		this.currentForce = currentForce;
		this.currentRawForce = currentRawForce;
		this.extendLimit = extendLimit;
		this.retractLimit = retractLimit;
		this.forceLimit = forceLimit;
	}
	public MachineState getState() {
		return state;
	}
	public int getCurrentForce() {
		return currentForce;
	}
	public int getCurrentRawForce() {
		return currentRawForce;
	}
	public boolean isExtendLimit() {
		return extendLimit;
	}
	public boolean isRetractLimit() {
		return retractLimit;
	}
	public boolean isForceLimit() {
		return forceLimit;
	}
	
}