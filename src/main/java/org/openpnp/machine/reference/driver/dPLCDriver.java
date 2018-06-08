/**
 * 
 */
package org.openpnp.machine.reference.driver;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;

import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.machine.reference.ReferenceActuator;
import org.openpnp.machine.reference.ReferenceDriver;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceHeadMountable;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.machine.reference.ReferencePasteDispenser;
import org.openpnp.machine.reference.driver.wizards.dPLCDriverSettings;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Named;
import org.openpnp.model.Part;
import org.openpnp.spi.Head;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.spi.base.SimplePropertySheetHolder;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.core.Commit;
import org.pmw.tinylog.Logger;

import com.ghgande.j2mod.modbus.ModbusException;
//import net.wimpi.modbus.ModbusException;

/**
 * @author jtalamantes
 *
 */
public class dPLCDriver extends AbstractModbusDriver implements Named, Runnable {

	@Attribute(required = false)
	protected LengthUnit units = LengthUnit.Millimeters;

	@Attribute(required = false)
	protected int maxFeedRate = 1000;

	@Attribute(required = false)
	protected double nonSquarenessFactor = 0;

	@Attribute(required = false)
	protected int moveTimeoutMilliseconds = 5000;

	@Attribute(required = false)
	protected int connectWaitTimeMilliseconds = 3000;

	@Attribute(required = false)
	protected boolean visualHomingEnabled = false;

	@Element(required = false)
	protected Location homingFiducialLocation = new Location(LengthUnit.Millimeters);

	@ElementList(required = false)
	protected List<dPLCDriver> subDrivers = new ArrayList<>();

	@ElementList(required = false)
	protected List<Axis> axes = new ArrayList<>();

	@Attribute(required = false)
	protected String name = "dPLCDriver";

	boolean connected;
	private boolean disconnectRequested;
	private Thread readerThread;
	private Set<Nozzle> pickedNozzles = new HashSet<>();
	private dPLCDriver parent = null;

	@Commit
	public void commit() {
		for (Axis axis : axes) {
			axis.master = this;
		}
		for (dPLCDriver driver : subDrivers) {
			driver.parent = this;
		}
	}

	public void createDefaults() {
		axes = new ArrayList<>();
		// Create the default axis set
		axes.add(new Axis("x", 1, Axis.Type.X, 0, this, "*"));
		axes.add(new Axis("y", 2, Axis.Type.Y, 0, this, "*"));
		axes.add(new Axis("z", 3, Axis.Type.Z, 0, this, "*"));
		axes.add(new Axis("rotation", 4, Axis.Type.Rotation, 0, this, "*"));
	}

	public synchronized void connect() throws Exception {
		super.connect();

		connected = false;
		disconnectRequested = false;

		readerThread = new Thread(this);
		readerThread.setDaemon(true);
		readerThread.start();

		// Wait a bit while the controller starts up
		Thread.sleep(connectWaitTimeMilliseconds);

		// Disable the machine
		setEnabled(false);

		connected = true;
	}

	@Override
	public void setEnabled(boolean enabled) throws Exception {
		if (enabled && !connected) {
			connect();
		}
		if (connected) {
			if (enabled) {
				// enable controller
				this.holdings[0].setValue((short) 0x01);
			} else {
				// disable controller
				this.holdings[0].setValue((short) 0x0);
			}
		}

		for (ReferenceDriver driver : subDrivers) {
			driver.setEnabled(enabled);
		}
	}

	@Override
	public void dispense(ReferencePasteDispenser dispenser, Location startLocation, Location endLocation,
			long dispenseTimeMilliseconds) throws Exception {
		// Logger.debug("dispense({}, {}, {}, {})", new Object[] {dispenser,
		// startLocation, endLocation, dispenseTimeMilliseconds});
		//
		// String command = getCommand(null, CommandType.PRE_DISPENSE_COMMAND);
		// command = substituteVariable(command, "DispenseTime",
		// dispenseTimeMilliseconds);
		//
		// sendGcode(command);
		//
		// for (ReferenceDriver driver: subDrivers )
		// {
		// driver.dispense(dispenser,startLocation,endLocation,dispenseTimeMilliseconds);
		// }
		//
		// command = getCommand(null, CommandType.DISPENSE_COMMAND);
		// command = substituteVariable(command, "DispenseTime",
		// dispenseTimeMilliseconds);
		// sendGcode(command);
		//
		// command = getCommand(null, CommandType.POST_DISPENSE_COMMAND);
		// command = substituteVariable(command, "DispenseTime",
		// dispenseTimeMilliseconds);
		// sendGcode(command);
	}

	@Override
	public void home(ReferenceHead head) throws Exception {

		// First home the Z axis
		head.moveToSafeZ();

		// We need to specially handle X and Y axes to support the non-squareness
		// factor.
		Axis xAxis = null;
		Axis yAxis = null;
		double xHomeCoordinateNonSquare = 0;
		double yHomeCoordinate = 0;
		for (Axis axis : axes) {
			if (axis.getType() == Axis.Type.X) {
				xAxis = axis;
				xHomeCoordinateNonSquare = axis.getHomeCoordinate();
			}
			if (axis.getType() == Axis.Type.Y) {
				yAxis = axis;
				yHomeCoordinate = axis.getHomeCoordinate();
			}
		}

		//TODO: Wait until the controller is inHome position


		// Compensate non-squareness factor:
		// We are homing to the native controller's non-square coordinate system, this
		// does not
		// match OpenPNP's square coordinate system, if the controller's Y home is
		// non-zero.
		// The two coordinate systems coincide at Y0 only, see the non-squareness
		// transformation. It is not a good idea to change the transformation i.e. for
		// the coordinate
		// systems to coincide at Y home, as this would break coordinates captured
		// before this change.
		// In order to home the square internal coordinate system we need to account for
		// the
		// non-squareness X offset here.
		// NOTE this changes nothing in the behavior or the coordinate system of the
		// machine. It just
		// sets the internal X coordinate correctly immediately after homing, so we can
		// capture the
		// home location correctly. Without this compensation the discrepancy between
		// internal and
		// machines coordinates was resolved with the first move, as it is done in
		// absolute mode.
		double xHomeCoordinateSquare = xHomeCoordinateNonSquare - nonSquarenessFactor * yHomeCoordinate;

		for (Axis axis : axes) {
			if (axis == xAxis) {
				// for X use the coordinate adjusted for non-squareness.
				axis.setCoordinate(xHomeCoordinateSquare);
			} else {
				// otherwise just use the standard coordinate.
				axis.setCoordinate(axis.getHomeCoordinate());
			}
		}

		// Write Home Position and wait for command to be done
		for (Axis axis : axes) {
			while (!axis.isInHome());
		}

		for (ReferenceDriver driver : subDrivers) {
			driver.home(head);
		}

		if (visualHomingEnabled) {
			/*
			 * The head camera for nozzle-1 should now be (if everything has homed
			 * correctly) directly above the homing pin in the machine bed, use the head
			 * camera scan for this and make sure this is exactly central - otherwise we
			 * move the camera until it is, and then reset all the axis back to 0,0,0,0 as
			 * this is calibrated home.
			 */
			Part homePart = Configuration.get().getPart("FIDUCIAL-HOME");
			if (homePart != null) {
				Configuration.get().getMachine().getFiducialLocator().getHomeFiducialLocation(homingFiducialLocation,
						homePart);

				// homeOffset contains the offset, but we are not really concerned with that,
				// we just reset X,Y back to the home-coordinate at this point.
				if (xAxis != null) {
					xAxis.setCoordinate(xHomeCoordinateSquare);
				}
				if (yAxis != null) {
					yAxis.setCoordinate(yHomeCoordinate);
				}

				// //Save new home in controller
				// String g92command = getCommand(null, CommandType.POST_VISION_HOME_COMMAND);
				// // make sure to use the native non-square X home coordinate.
				// g92command = substituteVariable(g92command, "X", xHomeCoordinateNonSquare);
				// g92command = substituteVariable(g92command, "Y", yHomeCoordinate);
				// sendGcode(g92command, -1);
			}
		}
	}

	public Axis getAxis(HeadMountable hm, Axis.Type type) {
		for (Axis axis : axes) {
			if (axis.getType() == type
					&& (axis.getHeadMountableIds().contains("*") || axis.getHeadMountableIds().contains(hm.getId()))) {
				return axis;
			}
		}
		return null;
	}

	@Override
	public Location getLocation(ReferenceHeadMountable hm) {
		Axis xAxis = getAxis(hm, Axis.Type.X);
		Axis yAxis = getAxis(hm, Axis.Type.Y);
		Axis zAxis = getAxis(hm, Axis.Type.Z);
		Axis rotationAxis = getAxis(hm, Axis.Type.Rotation);

		// additional info might be on subdrivers (note that subdrivers can only be one
		// level deep)
		for (ReferenceDriver driver : subDrivers) {
			dPLCDriver d = (dPLCDriver) driver;
			if (d.getAxis(hm, Axis.Type.X) != null) {
				xAxis = d.getAxis(hm, Axis.Type.X);
			}
			if (d.getAxis(hm, Axis.Type.Y) != null) {
				yAxis = d.getAxis(hm, Axis.Type.Y);
			}
			if (d.getAxis(hm, Axis.Type.Z) != null) {
				zAxis = d.getAxis(hm, Axis.Type.Z);
			}
			if (d.getAxis(hm, Axis.Type.Rotation) != null) {
				rotationAxis = d.getAxis(hm, Axis.Type.Rotation);
			}
		}

		Location location = new Location(units, xAxis == null ? 0 : xAxis.getTransformedCoordinate(hm),
				yAxis == null ? 0 : yAxis.getTransformedCoordinate(hm),
				zAxis == null ? 0 : zAxis.getTransformedCoordinate(hm),
				rotationAxis == null ? 0 : rotationAxis.getTransformedCoordinate(hm)).add(hm.getHeadOffsets());
		return location;
	}

	@Override
	public void moveTo(ReferenceHeadMountable hm, Location location, double speed) throws Exception {
		// keep copy for calling subdrivers as to not add offset on offset
		Location locationOriginal = location;

		location = location.convertToUnits(units);
		location = location.subtract(hm.getHeadOffsets());

		double x = location.getX();
		double y = location.getY();
		double z = location.getZ();
		double rotation = location.getRotation();

		Axis xAxis = getAxis(hm, Axis.Type.X);
		Axis yAxis = getAxis(hm, Axis.Type.Y);
		Axis zAxis = getAxis(hm, Axis.Type.Z);
		Axis rotationAxis = getAxis(hm, Axis.Type.Rotation);

		// Handle NaNs, which means don't move this axis for this move. We set the
		// appropriate
		// axis reference to null, which we'll check for later.
		if (Double.isNaN(x)) {
			xAxis = null;
		}
		if (Double.isNaN(y)) {
			yAxis = null;
		}
		if (Double.isNaN(z)) {
			zAxis = null;
		}
		if (Double.isNaN(rotation)) {
			rotationAxis = null;
		}

		// Check that the commanded position isn't negative
		if (x < 0) {
			throw new Exception("X Axis position can't be negative");
		}
		if (y < 0) {
			throw new Exception("Y Axis position can't be negative");
		}
		if (z < 0) {
			throw new Exception("Z Axis position can't be negative");
		}

		// If no axes are included in the move, there's nothing to do, so just return.
		if (xAxis == null && yAxis == null && zAxis == null && rotationAxis == null) {
			return;
		}

		if (xAxis != null || yAxis != null || zAxis != null || rotationAxis != null) {
			// //Verify that the position isn't bigger than the length of the actuator
			 if (xAxis != null && xAxis.getLength() != 0 && x > xAxis.getLength()) {
				 throw new Exception("X Axis position out of length");
			 }
			 if (yAxis != null && yAxis.getLength() != 0 && y > yAxis.getLength()) {
				 throw new Exception("Y Axis position out of length");
			 }
			 if (zAxis != null && zAxis.getLength() != 0 && z > zAxis.getLength()) {
				 throw new Exception("Z Axis position out of length");
			 }

			// For each included axis, if the axis has a transform, transform the target
			// coordinate
			// to it's raw value.
			if (xAxis != null && xAxis.getTransform() != null) {
				x = xAxis.getTransform().toRaw(xAxis, hm, x);
			}
			if (yAxis != null && yAxis.getTransform() != null) {
				y = yAxis.getTransform().toRaw(yAxis, hm, y);
			}
			if (zAxis != null && zAxis.getTransform() != null) {
				z = zAxis.getTransform().toRaw(zAxis, hm, z);
			}
			if (rotationAxis != null && rotationAxis.getTransform() != null) {
				rotation = rotationAxis.getTransform().toRaw(rotationAxis, hm, rotation);
			}

			//nonSquarenessFactor gets applied to X and is multiplied by Y

			boolean includeX = false, includeY = false, includeZ = false, includeRotation = false;

			// Primary checks to see if an axis should move
			if (xAxis != null && xAxis.getCoordinate() != x) {
				includeX = true;
			}
			if (yAxis != null && yAxis.getCoordinate() != y) {
				includeY = true;
			}
			if (zAxis != null && zAxis.getCoordinate() != z) {
				includeZ = true;
			}
			if (rotationAxis != null && rotationAxis.getCoordinate() != rotation) {
				includeRotation = true;
			}

			// If Y is moving and there is a non squareness factor we also need to move X,
			// even if
			// no move was intended for X.
			if (includeY && nonSquarenessFactor != 0 && xAxis != null) {
				includeX = true;
			}

			// Only give a command when move is necessary
			if (includeX || includeY || includeZ || includeRotation) {
				//Set speed factor
				float speedValue;
				speedValue = ((maxFeedRate * 100)  / maxFeedRate);
				// Send the position to move
				if (xAxis != null) {
					xAxis.setSpeed(speedValue);
					xAxis.setCoordinate(x);
				}
				if (yAxis != null) {
					yAxis.setSpeed(speedValue);
					yAxis.setCoordinate(y);
				}
				if (zAxis != null) {
					zAxis.setSpeed(speedValue);
					zAxis.setCoordinate(z);
				}
				if (rotationAxis != null) {
					rotationAxis.setSpeed(speedValue);
					rotationAxis.setCoordinate(rotation);
				}

				Logger.debug("moveTo({}, {}, {}, {})...", x, y, z, rotation);

				// Wait for command done
				long t = System.currentTimeMillis();
				boolean done = false;
				while (!done && System.currentTimeMillis() - t < moveTimeoutMilliseconds) {
					// Is it done? Check all involved axis
					if (xAxis != null && !xAxis.movementDone()) {
						continue;
					}

					if (yAxis != null && !yAxis.movementDone()) {
						continue;
					}

					if (zAxis != null && !zAxis.movementDone()) {
						continue;
					}

					if (rotationAxis != null && !rotationAxis.movementDone()) {
						continue;
					}

					done = true;
				}
				if (!done) {
					throw new Exception("Timed out waiting for move to complete.");
				}
			} // there is a move

		} // there were axes involved

		// regardless of any action above the subdriver needs its actions based on
		// original input
		for (ReferenceDriver driver : subDrivers) {
			driver.moveTo(hm, locationOriginal, speed);
		}
	}

	@Override
	public void pick(ReferenceNozzle nozzle) throws Exception {
		// Turn on Pump
		Axis ztheta = getAxis(nozzle, Axis.Type.Z);
		ztheta.setOutput(ztheta.pumpOutput, true);

		// Vacuum limits
		// ReferenceNozzleTip nt = nozzle.getNozzleTip();
		// command = substituteVariable(command, "VacuumLevelPartOn",
		// nt.getVacuumLevelPartOn());
		// command = substituteVariable(command, "VacuumLevelPartOff",
		// nt.getVacuumLevelPartOff());

		for (ReferenceDriver driver : subDrivers) {
			driver.pick(nozzle);
		}
	}

	@Override
	public void place(ReferenceNozzle nozzle) throws Exception {
		// Vacuum limits
		// ReferenceNozzleTip nt = nozzle.getNozzleTip();
		// command = substituteVariable(command, "VacuumLevelPartOn",
		// nt.getVacuumLevelPartOn());
		// command = substituteVariable(command, "VacuumLevelPartOff",
		// nt.getVacuumLevelPartOff());

		// Turn off specific head solenoid
		Axis ztheta = getAxis(nozzle, Axis.Type.Z);
		ztheta.setOutput(ztheta.pumpOutput, false);

		for (ReferenceDriver driver : subDrivers) {
			driver.place(nozzle);
		}
	}

	@Override
	public void actuate(ReferenceActuator actuator, boolean on) throws Exception {
		// String command = getCommand(actuator, CommandType.ACTUATE_BOOLEAN_COMMAND);
		// command = substituteVariable(command, "Id", actuator.getId());
		// command = substituteVariable(command, "Name", actuator.getName());
		// command = substituteVariable(command, "Index", actuator.getIndex());
		// command = substituteVariable(command, "BooleanValue", on);
		// command = substituteVariable(command, "True", on ? on : null);
		// command = substituteVariable(command, "False", on ? null : on);
		// sendGcode(command);
		//
		for (ReferenceDriver driver : subDrivers) {
			driver.actuate(actuator, on);
		}
	}

	@Override
	public void actuate(ReferenceActuator actuator, double value) throws Exception {
		// String command = getCommand(actuator, CommandType.ACTUATE_DOUBLE_COMMAND);
		// command = substituteVariable(command, "Id", actuator.getId());
		// command = substituteVariable(command, "Name", actuator.getName());
		// command = substituteVariable(command, "Index", actuator.getIndex());
		// command = substituteVariable(command, "DoubleValue", value);
		// command = substituteVariable(command, "IntegerValue", (int) value);
		// sendGcode(command);

		for (ReferenceDriver driver : subDrivers) {
			driver.actuate(actuator, value);
		}
	}

	@Override
	public String actuatorRead(ReferenceActuator actuator) throws Exception {
		// String command = getCommand(actuator, CommandType.ACTUATOR_READ_COMMAND);
		// String regex = getCommand(actuator, CommandType.ACTUATOR_READ_REGEX);
		// if (command == null || regex == null) {
		// // If the command or regex is null we'll query the subdrivers. The first
		// // to respond with a non-null value wins.
		// for (ReferenceDriver driver : subDrivers) {
		// String val = driver.actuatorRead(actuator);
		// if (val != null) {
		// return val;
		// }
		// }
		// // If none of the subdrivers returned a value there's nothing left to
		// // do, so return null.
		// return null;
		// }
		//
		// command = substituteVariable(command, "Id", actuator.getId());
		// command = substituteVariable(command, "Name", actuator.getName());
		// command = substituteVariable(command, "Index", actuator.getIndex());
		//
		// List<String> responses = sendGcode(command);
		//
		// for (String line : responses) {
		// if (line.matches(regex)) {
		// Logger.trace("actuatorRead response: {}", line);
		// Matcher matcher = Pattern.compile(regex).matcher(line);
		// matcher.matches();
		//
		// try {
		// String s = matcher.group("Value");
		// return s;
		// }
		// catch (Exception e) {
		// throw new Exception("Failed to read Actuator " + actuator.getName(), e);
		// }
		// }
		// }

		return null;
	}

	public synchronized void disconnect() {
		disconnectRequested = true;
		connected = false;

		try {
			if (readerThread != null && readerThread.isAlive()) {
				readerThread.join(3000);
			}
		} catch (Exception e) {
			Logger.error("disconnect()", e);
		}

		try {
			super.disconnect();
		} catch (Exception e) {
			Logger.error("disconnect()", e);
		}
		disconnectRequested = false;
	}

	@Override
	public void close() throws IOException {
		super.close();

		for (ReferenceDriver driver : subDrivers) {
			driver.close();
		}
	}

	@Override
	public void run() {
		while (!disconnectRequested) {
			// Refresh modbus Data
			if (this.modbusMaster != null) {
				try {
					updateData();
					processPositionReport();
					// Wait time to update data
					Thread.sleep(100);
				} catch (InterruptedException | ModbusException e) {
					Logger.error("modbus update error", e);
					this.disconnect();
				}
			}
		}
	}

	private boolean processPositionReport() {
		Logger.trace("Position report");
		for (Axis axis : axes) {
			try {
				Logger.trace(axis.getName() + ": {}", axis.getCoordinate());
			} catch (Exception e) {
				Logger.warn("Error processing position report for axis {}: {}", axis.getName(), e);
			}
		}

		ReferenceMachine machine = ((ReferenceMachine) Configuration.get().getMachine());
		for (Head head : Configuration.get().getMachine().getHeads()) {
			machine.fireMachineHeadActivity(head);
		}
		return true;
	}

	protected void updateData() throws ModbusException {
		// Read Contacts
		// readDiscreteInputs();
		// Write Coils
		// writeCoils();
		// Read Input Reg
		readInputReg();
		// Write Holdings
		writeHoldings();
	}

	@Override
	public String getPropertySheetHolderTitle() {
		return getName() == null ? "dPLCDriver" : getName();
	}

	@Override
	public PropertySheetHolder[] getChildPropertySheetHolders() {
		ArrayList<PropertySheetHolder> children = new ArrayList<>();
		if (parent == null) {
			children.add(new SimplePropertySheetHolder("Sub-Drivers", subDrivers));
		}
		return children.toArray(new PropertySheetHolder[] {});
	}

	public PropertySheet[] getPropertySheets() {
		return new PropertySheet[] { new PropertySheetWizardAdapter(super.getConfigurationWizard(), "Modbus"),
				new PropertySheetWizardAdapter(new dPLCDriverSettings(this), "dPLC") };
	}

	@Override
	public Action[] getPropertySheetHolderActions() {
		if (parent == null) {
			return new Action[] { addSubDriverAction };
		} else {
			return new Action[] { deleteSubDriverAction };
		}
	}

	public Action addSubDriverAction = new AbstractAction() {
		{
			putValue(SMALL_ICON, Icons.add);
			putValue(NAME, "Add Sub-Driver...");
			putValue(SHORT_DESCRIPTION, "Add a new sub-driver.");
		}

		@Override
		public void actionPerformed(ActionEvent arg0) {
			dPLCDriver driver = new dPLCDriver();
			driver.parent = dPLCDriver.this;
			subDrivers.add(driver);
			fireIndexedPropertyChange("subDrivers", subDrivers.size() - 1, null, driver);
		}
	};

	public Action deleteSubDriverAction = new AbstractAction() {
		{
			putValue(SMALL_ICON, Icons.delete);
			putValue(NAME, "Delete Sub-Driver...");
			putValue(SHORT_DESCRIPTION, "Delete the selected sub-driver.");
		}

		@Override
		public void actionPerformed(ActionEvent arg0) {
			int ret = JOptionPane.showConfirmDialog(MainFrame.get(),
					"Are you sure you want to delete the selected sub-driver?", "Delete Sub-Driver?",
					JOptionPane.YES_NO_OPTION);
			if (ret == JOptionPane.YES_OPTION) {
				parent.subDrivers.remove(dPLCDriver.this);
				parent.fireIndexedPropertyChange("subDrivers", subDrivers.size() - 1, dPLCDriver.this, null);
			}
		}
	};

	public LengthUnit getUnits() {
		return units;
	}

	public void setUnits(LengthUnit units) {
		this.units = units;
	}

	public void setNonSquarenessFactor(double NonSquarenessFactor) {
		this.nonSquarenessFactor = NonSquarenessFactor;
	}

	public double getNonSquarenessFactor() {
		return this.nonSquarenessFactor;
	}

	public int getMaxFeedRate() {
		return maxFeedRate;
	}

	public void setMaxFeedRate(int maxFeedRate) {
		this.maxFeedRate = maxFeedRate;
	}

	public int getMoveTimeoutMilliseconds() {
		return moveTimeoutMilliseconds;
	}

	public void setMoveTimeoutMilliseconds(int moveTimeoutMilliseconds) {
		this.moveTimeoutMilliseconds = moveTimeoutMilliseconds;
	}

	public int getConnectWaitTimeMilliseconds() {
		return connectWaitTimeMilliseconds;
	}

	public void setConnectWaitTimeMilliseconds(int connectWaitTimeMilliseconds) {
		this.connectWaitTimeMilliseconds = connectWaitTimeMilliseconds;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
		firePropertyChange("name", null, getName());
	}

	public boolean isVisualHomingEnabled() {
		return visualHomingEnabled;
	}

	public void setVisualHomingEnabled(boolean visualHomingEnabled) {
		this.visualHomingEnabled = visualHomingEnabled;
	}

	protected static class Axis {
		public enum Type {
			X, Y, Z, Rotation
		}

		@Attribute
		private String name;

		@Attribute
		private int slotNum;

		@Attribute
		private Type type;

		@Attribute(required = false)
		private double homeCoordinate = 0;

		@ElementList(required = false)
		private Set<String> headMountableIds = new HashSet<String>();

		@Element(required = false)
		private AxisTransform transform;

		/*
		 * Axis Configuration
		 */
		@Attribute(required = false)
		private int pumpOutput = 0;

		@Attribute(required = false)
		private float speed = 100; // mm/s

		@Attribute(required = false)
		private float tolerance = 0.1f; // mm

		@Attribute(required = false)
		private byte acceleration = 20; // % of max

		@Attribute(required = false)
		private byte deceleration = 20; // % of max

		@Attribute(required = false)
		private byte torque = 0;

		@Attribute(required = false)
		private int length = 0; // Size of the actuator

		/**
		 * Stores the current value for this axis.
		 */
		private double coordinate = 0;
		private byte output = 0;
		private dPLCDriver master;

		public Axis() {
			// TODO Auto-generated constructor stub
		}

		public Axis(String name, int slotNum, Type type, double homeCoordinate, dPLCDriver master,
				String... headMountableIds) {
			this.master = master;
			this.name = name;
			this.type = type;
			this.slotNum = slotNum;
			this.homeCoordinate = homeCoordinate;
			this.headMountableIds.addAll(Arrays.asList(headMountableIds));
			updateParameters();
		}

		public dPLCDriver getMaster() {
			return master;
		}

		public void setMaster(dPLCDriver master) {
			this.master = master;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getSlotNum() {
			return slotNum;
		}

		public void setSlotNum(int slotNum) {
			this.slotNum = slotNum;
		}

		public Type getType() {
			return type;
		}

		public void setType(Type type) {
			this.type = type;
		}

		public float getCoordinate() {
			// Read coordinate from modbus
			float readCoordinate = 0;
			if (master != null && master.connected) {
				// Read Input Reg
				try {
					readCoordinate = master.getFloatData(DataType.INPUTREGISTER, 2 + ((slotNum - 1) * 8));
				} catch (NullPointerException ex) {
					readCoordinate = 0;
				}
			}

			return readCoordinate;
		}

		public void setCoordinate(double coordinate) {
			// Write coordinate to modbus
			this.coordinate = coordinate;
			updateParameters();
		}

		public double getCommandCoordinate() {
			return coordinate;
		}

		public double getHomeCoordinate() {
			return homeCoordinate;
		}

		public void setHomeCoordinate(double homeCoordinate) {
			this.homeCoordinate = homeCoordinate;
		}

		public boolean isInHome() {
			return compareCoordinate((float) this.homeCoordinate);
		}

		public double getTransformedCoordinate(HeadMountable hm) {
			if (this.transform != null) {
				return transform.toTransformed(this, hm, this.getCoordinate());
			}
			return this.getCoordinate();
		}

		public Set<String> getHeadMountableIds() {
			return headMountableIds;
		}

		public void setHeadMountableIds(Set<String> headMountableIds) {
			this.headMountableIds = headMountableIds;
		}

		public AxisTransform getTransform() {
			return transform;
		}

		public void setTransform(AxisTransform transform) {
			this.transform = transform;
		}

		public float getSpeed() {
			return speed;
		}

		public void setSpeed(float speed) {
			this.speed = speed;
			updateParameters();
		}

		public void setSpeed(double speed) {
			this.setSpeed(new Double(speed).floatValue());
		}

		public double getTolerance() {
			return tolerance;
		}

		public void setTolerance(float tolerance) {
			this.tolerance = tolerance;
			updateParameters();
		}

		public byte getAcceleration() {
			return acceleration;
		}

		public void setAcceleration(byte acceleration) {
			if (acceleration <= 255 && acceleration >= 0) {
				this.acceleration = (byte) acceleration;
			}
			updateParameters();
		}

		public byte getDeceleration() {
			return deceleration;
		}

		public void setDeceleration(byte decceleration) {
			if (decceleration <= 255 && decceleration >= 0) {
				this.deceleration = (byte) decceleration;
			}
			updateParameters();
		}

		public byte getTorque() {
			return torque;
		}

		public void setTorque(int torque) {
			if (torque <= 255 && torque >= 0) {
				this.torque = (byte) torque;
			}
			updateParameters();
		}

		public boolean getOutput(int output) {
			if (output >= 0 && output < 2) {
				return (this.output >> output) == 1;
			}

			return false;
		}

		public void setOutput(int output, boolean state) {
			if (output >= 0 && output < 2) {
				if (state) { // turn on output
					this.output |= (1 << output);
				} else { // turn off output
					int mask = 0xffff ^ (1 << output);
					this.output &= mask;
				}
			}
			updateParameters();
		}

		public boolean movementDone() {
			return compareCoordinate((float) coordinate);
		}

		public int getLength() {
			return length;
		}

		public void setLength(int length) {
			this.length = length;
		}

		protected void updateParameters() {
			if (this.master != null && master.connected) {
				int offset;
				// Update all parameters to modbus registers
				offset = ((slotNum - 1) * 8);
				master.setFloat((float) coordinate, DataType.HOLDINGS, 2 + offset);
				master.setFloat(speed, DataType.HOLDINGS, 4 + offset);
				master.setFloat(tolerance, DataType.HOLDINGS, 6 + offset);
				master.setByte(acceleration, DataType.HOLDINGS, 8 + offset, ByteOrder.MSB);
				master.setByte(deceleration, DataType.HOLDINGS, 8 + offset, ByteOrder.LSB);
				master.setByte(torque, DataType.HOLDINGS, 9 + offset, ByteOrder.MSB);
				master.setByte(output, DataType.HOLDINGS, 9 + offset, ByteOrder.LSB);
			}
		}

		private boolean compareCoordinate(float coordinate) {
			float diff;

			diff = Math.abs(coordinate - getCoordinate());

			if (diff < this.tolerance) {
				return true;
			}

			return false;
		}
	}

	public interface AxisTransform {
		/**
		 * Transform the specified raw coordinate into it's corresponding transformed
		 * coordinate. The transformed coordinate is what the user sees, while the raw
		 * coordinate is what the motion controller sees.
		 * 
		 * @param hm
		 * @param rawCoordinate
		 * @return
		 */
		public double toTransformed(Axis axis, HeadMountable hm, double rawCoordinate);

		/**
		 * Transform the specified transformed coordinate into it's corresponding raw
		 * coordinate. The transformed coordinate is what the user sees, while the raw
		 * coordinate is what the motion controller sees.
		 * 
		 * @param hm
		 * @param transformedCoordinate
		 * @return
		 */
		public double toRaw(Axis axis, HeadMountable hm, double transformedCoordinate);
	}

	/**
	 * An AxisTransform for heads with dual linear Z axes powered by one motor. The
	 * two Z axes are defined as normal and negated. Normal gets the raw coordinate
	 * value and negated gets the same value negated. So, as normal moves up,
	 * negated moves down.
	 */
	public static class NegatingTransform implements AxisTransform {
		@Element
		private String negatedHeadMountableId;

		@Override
		public double toTransformed(Axis axis, HeadMountable hm, double rawCoordinate) {
			if (hm.getId().equals(negatedHeadMountableId)) {
				return -rawCoordinate;
			}
			return rawCoordinate;
		}

		@Override
		public double toRaw(Axis axis, HeadMountable hm, double transformedCoordinate) {
			// Since we're just negating the value of the coordinate we can just
			// use the same function.
			return toTransformed(axis, hm, transformedCoordinate);
		}
	}
}
