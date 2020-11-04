package greendragons.linetester;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.io.SerializablePermission;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.markers.SeriesMarkers;

import com.fazecast.jSerialComm.SerialPort;



public class UI extends JFrame {

	private final boolean useLaser = System.getProperty("noLaser")==null;
	private final int baseForce = 5000;
	private final int holdTimeSeconds = 5;
	private final int stepSize = 5000;
	
	private final Integer [] steps = {5,10,15,20,25,30,35,40,45,50,55,60};
	
	private Controller controller;
	
	void setPanelEnabled(JPanel panel, Boolean isEnabled) {
	    panel.setEnabled(isEnabled);

	    Component[] components = panel.getComponents();

	    for (Component component : components) {
	        if (component instanceof JPanel) {
	            setPanelEnabled((JPanel) component, isEnabled);
	        }
	        component.setEnabled(isEnabled);
	    }
	}
	
	
	
	private class Controller implements MachineObserver {
		private Machine machine;
		private InputPanel inputPanel;
		private GaugePanel gaugePanel;
		private MachinePanel machinePanel;
		private ResultsPanel resultsPanel;
		private List<Integer> forcesToTest;
		
		
		private Controller(MachinePanel machinePanel, InputPanel inputPanel, GaugePanel gaugePanel, ResultsPanel resultsPanel) {
			this.machinePanel = machinePanel;
			this.inputPanel = inputPanel;
			this.gaugePanel = gaugePanel;
			this.resultsPanel = resultsPanel;
		}

		
		private void start() {
				
			SerialPort [] ports = null;
			while ((ports = SerialPort.getCommPorts()).length==0) {
				JOptionPane.showMessageDialog(UI.this, "Please attach the machine", "No machine found", JOptionPane.WARNING_MESSAGE);
			}
			
			String [] portNames = new String [ports.length];
			for (int i=0;i<ports.length;i++) {
				portNames[i] = ports[i].getSystemPortName();
			}
			
			String chosenPort = null;
			while ((chosenPort = (String)JOptionPane.showInputDialog(UI.this, "Select a port", "Port", JOptionPane.PLAIN_MESSAGE, null, portNames, portNames[0]))==null) {
				System.exit(0);
			}
			
			SerialPort port = SerialPort.getCommPort(chosenPort);
			
			machine = new Machine(port, this);
				
			Thread machineReader = new Thread(machine);
			machineReader.start();

		}
		
		private Machine getMachine() {
			return machine;
		}
		
		private void startTest(int force, boolean stepped) {
			List<Integer> forces = new ArrayList<>();
			if (stepped) {
				for (int i=baseForce;i<force;i+=stepSize) {
					forces.add(i);
				}
			}
			forces.add(force);
			this.forcesToTest = forces;
			resultsPanel.reset();
			nextTest();
			
		}
		
		private void nextTest() {
			if (forcesToTest!=null && !forcesToTest.isEmpty()) {
				int force = forcesToTest.remove(0);
				machine.startTest(force, holdTimeSeconds, forcesToTest.isEmpty());
				System.out.println("Testing with force " + force);
			}
		}
		
		@Override
		public void onResult(LineTestResult result) {
			SwingUtilities.invokeLater(()->{
				handleMachineState(result.getState());
				resultsPanel.onResult(result);
				nextTest();
			});
		}

		@Override
		public void onResponse(MachineResponse response) {
			SwingUtilities.invokeLater(()->{
				handleMachineState(response.getStatus().getState());
				gaugePanel.updateForce(response.getStatus().getCurrentForce());
			});
			System.out.println(response.getStatus().getCurrentRawForce());
		}
		private void handleMachineState(MachineState state) {
			inputPanel.handleMachineState(state);
			machinePanel.handleMachineState(state);
		}
		@Override
		public void onError(Throwable t) {
		}
		
		
	}
	
	
	private class MachinePanel extends JPanel {
		private JButton stopBtn, homeBtn, resetBtn, laserOnBtn, laserOffBtn;
		private TitledBorder border;
		private JLabel stateLabel;
		
		private MachinePanel () {
			
			setLayout(new BorderLayout());
			
			JPanel north = new JPanel();
			JPanel centre = new JPanel();
			JPanel south = new JPanel();
			
			stateLabel = new JLabel();
			stateLabel.setFont(stateLabel.getFont().deriveFont(24f));
			
			stopBtn = new JButton("Stop");
			resetBtn = new JButton("Reset");
			homeBtn = new JButton("Home");
			laserOnBtn = new JButton("Laser ON");
			laserOffBtn = new JButton("Laser OFF");
			
			north.add(stateLabel);

			centre.add(homeBtn);
			centre.add(stopBtn);
			centre.add(resetBtn);
			south.add(laserOnBtn);
			south.add(laserOffBtn);
			
			add(north, BorderLayout.NORTH);
			add(centre, BorderLayout.CENTER);
			add(south, BorderLayout.EAST);
			
			border = BorderFactory.createTitledBorder("Machine");
			setBorder(border);
			
			homeBtn.addActionListener((e)->{
				controller.getMachine().home();
			});
			stopBtn.addActionListener((e)->{
				controller.getMachine().estop();
			});
			resetBtn.addActionListener((e)->{
				controller.getMachine().reset();
			});
			laserOnBtn.addActionListener((e)->{
				controller.getMachine().turnLaserOn();
			});
			laserOffBtn.addActionListener((e)->{
				controller.getMachine().turnLaserOff();
			});
			
		}
		
		private void handleMachineState(MachineState state) {
			homeBtn.setEnabled(state==MachineState.READY);
			resetBtn.setEnabled(state==MachineState.ESTOP);
			stopBtn.setEnabled(state!=MachineState.ESTOP);
			stateLabel.setText(state.name());
		}
	}
	
	private class InputPanel extends JPanel {
		private JButton runBtn;
		private JComboBox<Integer> forceInput;
		private JCheckBox stepInput;
		
		private InputPanel() {
			runBtn = new JButton("Test");
			
			stepInput = new JCheckBox("Step");
			
			
			forceInput = new JComboBox<>(steps);
			add(new JLabel("Force (kg):"));
			add(forceInput);
			add(stepInput);
			add(runBtn);
			
			handleMachineState(MachineState.READY);
			setBorder(BorderFactory.createTitledBorder("Test setup"));
			
			
			runBtn.addActionListener((e)->{
				controller.startTest((int)forceInput.getSelectedItem()*1000, stepInput.isSelected());
			});
			
		}
		
		private void handleMachineState(MachineState state) {
			setInputsEnabled(state==MachineState.READY);
		}
		
		private void setInputsEnabled(boolean enabled) {
			runBtn.setEnabled(enabled);
			stepInput.setEnabled(enabled);
			forceInput.setEnabled(enabled);
		}
	}
	
	private class GaugePanel extends JPanel {
		private JLabel label;
		
		
		private GaugePanel () {
			label = new JLabel();
			label.setFont(label.getFont().deriveFont(48f));
			updateForce(0);
			add(label);
			setBorder(BorderFactory.createTitledBorder("Force gauge"));
		}
		
		public void updateForce(int force) {
			label.setText(String.format("%.1f Kg", force/1000f));
		}
		
		
	}
	
	private class ResultsPanel extends JPanel {
		
		private DefaultTableModel model;
		private ChartPanel chart ;
		
		private ResultsPanel() {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			JTable table  = new JTable();
			model = (DefaultTableModel)table.getModel();
			model.addColumn("Target (Kg)");
			model.addColumn("Actual (Kg)");
			model.addColumn("Laser (mm)");
			model.addColumn("Result");
			
			model.setRowCount(0);
			
			JScrollPane scroll = new JScrollPane(table);
			scroll.setPreferredSize(new Dimension(getWidth(), 200));
			add(scroll);
			
			chart = new ChartPanel();
			add(chart);
			
			setBorder(BorderFactory.createTitledBorder("Results"));
		}
		
		
		public void reset() {
			model.setRowCount(0);
			chart.reset();
		}
		
		public void onResult(LineTestResult result) {
			model.addRow(new Object [] {
				result.getTargetForce()/1000f,
				(result.isLineBreak() ? result.getLastForceAchieved() : result.getForceAchieved())/1000f,
				result.getLength()/10f,
				result.isSuccess() ? "Pass" : "Fail" + (result.isLineBreak() ? " (line broke)" : "")
			});
			chart.onResult(result);
		}
	}
	
	private class ChartPanel extends JPanel {
		private XYChart chart;
		private XChartPanel<XYChart> chartPanel;
		private List<LineTestResult> results = new ArrayList<>();
		
		private ChartPanel() {
			setLayout(new BorderLayout());
			chart = new XYChartBuilder().width(400).height(300).build();
			chart.setXAxisTitle("Force (Kg)");
			chart.setYAxisTitle("Stretch (mm)");
			chart.getStyler().setLegendVisible(false);
			chartPanel = new XChartPanel<>(chart);
			add(chartPanel, BorderLayout.CENTER);
		}
		
		public void reset() {
			results.clear();
		}
		
		public void onResult(LineTestResult result) {
			results.add(result);
			chart.removeSeries("Measurements");
			if (results.size()>1) {
				double [] x = new double[results.size()];
				double [] y = new double[results.size()];
				for (int i=0;i<results.size();i++) {
					result = results.get(i);
					y[i] = useLaser ? (result.getLength()-results.get(0).getLength())/10f : i;
					x[i] = (result.isLineBreak() ? result.getLastForceAchieved() : result.getForceAchieved())/1000f;
				}
				chart.addSeries("Measurements", x, y).setMarker(SeriesMarkers.DIAMOND).setLineColor(Color.red);

				chartPanel.repaint();
			}
		}
		
	}
	
	
	public UI() {
		
		setTitle("Green Dragons Line Tester v1.0");
		
		Container content = this.getContentPane();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		
		MachinePanel machinePanel = new MachinePanel();
		content.add(machinePanel);
		
		InputPanel inputPanel = new InputPanel();
		content.add(inputPanel);
		
		GaugePanel gaugePanel = new GaugePanel();
		
		content.add(gaugePanel);
		
		ResultsPanel resultsPanel = new ResultsPanel();
		
		content.add(resultsPanel);
		
		controller = new Controller(machinePanel, inputPanel, gaugePanel, resultsPanel);
		
		controller.start();
		
	}
	
	
	
	public static void main(String [] args) throws Exception {
		
		UI ui = new UI();
		ui.setSize(800, 600);;
		ui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		ui.setVisible(true);
		ui.pack();		
		
	}
	
}
