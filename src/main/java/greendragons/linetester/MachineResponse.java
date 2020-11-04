package greendragons.linetester;

public class MachineResponse {
	private boolean success;
	private MachineStatus status;
	public MachineResponse(boolean success, MachineStatus status) {
		this.success = success;
		this.status = status;
	}
	public boolean isSuccess() {
		return success;
	}
	public MachineStatus getStatus() {
		return status;
	}
	
}
