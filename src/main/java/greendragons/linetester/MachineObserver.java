package greendragons.linetester;

public interface MachineObserver {
	public void onResult(LineTestResult result) ;
	public void onResponse(MachineResponse response);
	public void onError(Throwable t);
}
