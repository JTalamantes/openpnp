package org.openpnp.machine.reference.driver;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;

import javax.swing.Action;
import javax.swing.Icon;

import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.ReferenceDriver;
import org.openpnp.machine.reference.ReferencePasteDispenser;
import org.openpnp.machine.reference.driver.wizards.AbstractModbusDriverConfigurationWizard;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Location;
import org.openpnp.spi.PropertySheetHolder;

import org.simpleframework.xml.Attribute;

import com.ghgande.j2mod.modbus.ModbusException;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.msg.ReadCoilsRequest;
import com.ghgande.j2mod.modbus.msg.ReadCoilsResponse;
import com.ghgande.j2mod.modbus.msg.ReadInputDiscretesRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputDiscretesResponse;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersResponse;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersResponse;
import com.ghgande.j2mod.modbus.msg.WriteCoilRequest;
import com.ghgande.j2mod.modbus.msg.WriteCoilResponse;
import com.ghgande.j2mod.modbus.msg.WriteMultipleCoilsRequest;
import com.ghgande.j2mod.modbus.msg.WriteMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.WriteSingleRegisterRequest;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.util.BitVector;


//import net.wimpi.modbus.ModbusException;
//import net.wimpi.modbus.facade.ModbusTCPMaster;
//import net.wimpi.modbus.io.ModbusTCPTransaction;
//import net.wimpi.modbus.msg.ReadCoilsRequest;
//import net.wimpi.modbus.msg.ReadCoilsResponse;
//import net.wimpi.modbus.msg.ReadInputDiscretesRequest;
//import net.wimpi.modbus.msg.ReadInputDiscretesResponse;
//import net.wimpi.modbus.msg.ReadInputRegistersRequest;
//import net.wimpi.modbus.msg.ReadInputRegistersResponse;
//import net.wimpi.modbus.msg.ReadMultipleRegistersRequest;
//import net.wimpi.modbus.msg.ReadMultipleRegistersResponse;
//import net.wimpi.modbus.msg.WriteCoilRequest;
//import net.wimpi.modbus.msg.WriteCoilResponse;
//import net.wimpi.modbus.msg.WriteMultipleCoilsRequest;
//import net.wimpi.modbus.msg.WriteMultipleRegistersRequest;
//import net.wimpi.modbus.msg.WriteSingleRegisterRequest;
//import net.wimpi.modbus.net.TCPMasterConnection;
//import net.wimpi.modbus.procimg.InputRegister;
//import net.wimpi.modbus.procimg.Register;
//import net.wimpi.modbus.procimg.SimpleRegister;
//import net.wimpi.modbus.util.BitVector;

/**
 * A base class for basic Modbus based Drivers. Includes functions for connecting,
 * disconnecting, reading and sending data blocks (Discrete Inputs, Coils, Holding Register, Input Register ).
 */
public abstract class AbstractModbusDriver extends AbstractModelObject implements ReferenceDriver, Closeable {

	public Register[] getHoldings() {
		synchronized (writeLock) {
			return holdings;
		}
	}

	public InputRegister[] getInputReg() {
		synchronized (writeLock) {
			return inputReg;
		}
	}

	public BitVector getInputs() {
		synchronized (writeLock) {
			return inputs;
		}
	}

	public BitVector getCoils() {
		synchronized (writeLock) {
			return coils;
		}
	}

	public enum DataType{
		INPUTS,
		COILS,
		HOLDINGS,
		INPUTREGISTER;
	}
	
	public enum ByteOrder{
		LSB,
		MSB;
	}

	@Attribute(required = false)
	protected String modbusIp = "127.0.0.1";

	@Attribute(required = false)
	protected int port = 502;

	@Attribute(required = false)
	protected int timeoutMilliseconds = 500;

	protected ModbusMasterTCP modbusMaster;
	//Modbus Offset
	@Attribute(required = false)
	private int discreteInputsOffset = 0;
	@Attribute(required = false)
	private int coilsOffset = 0;
	@Attribute(required = false)
	private int inputRegOffset = 0;
	@Attribute(required = false)
	private int holdingsOffset = 0;
	//Modbus Sizes
	@Attribute(required = false)
	private int discreteInputsCount = 30;
	@Attribute(required = false)
	private int coilsCount = 30;
	@Attribute(required = false)
	private int inputRegCount = 60;
	@Attribute(required = false)
	private int holdingsCount = 60;
	//Modbus Buffers
	private Register[] holdings;
	private InputRegister[] inputReg;
	private BitVector inputs;
	private BitVector coils;

	private final Object writeLock = new Object();

	protected synchronized void connect() throws Exception {
		disconnect();
		initBuffers();
		modbusMaster = new ModbusMasterTCP(modbusIp);
		modbusMaster.connect();
	}

	protected synchronized void disconnect() throws Exception {
		if (modbusMaster != null) {
			modbusMaster.disconnect();
			modbusMaster = null;
		}
	}

	protected void initBuffers(){
		holdings = new SimpleRegister[holdingsCount];

		for(int i = 0; i< getHoldings().length; i++)
		{
			getHoldings()[i] = new SimpleRegister(0);
		}

		inputReg = new InputRegister[inputRegCount];
		inputs = new BitVector(discreteInputsCount);
		coils = new BitVector(coilsCount);
	}

	@Override
	public void close() throws IOException {
		try {
			disconnect();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	public String getModbusIp(){
		return modbusIp;
	}

	public void setModbusIp(String IP){
		this.modbusIp = IP;
	}

	public int getPort(){
		return port;
	}

	public void setPort(int port){
		this.port = port;
	}

	public int getTimeoutMilliseconds(){
		return timeoutMilliseconds;
	}

	public void setTimeoutMilliseconds(int Timeout){
		this.timeoutMilliseconds = Timeout;
	}

	@Override
	public void dispense(ReferencePasteDispenser dispenser, Location startLocation, Location endLocation,
			long dispenseTimeMilliseconds) throws Exception {
		// Do nothing. This is just stubbed in so that it can be released
		// without breaking every driver in the wild.
	}

	@Override
	public Wizard getConfigurationWizard() {
		try {
			return new AbstractModbusDriverConfigurationWizard(this);
		} catch (ParseException e) {
			return null;
		}
	}

	@Override
	public String getPropertySheetHolderTitle() {
		return getClass().getSimpleName();
	}

	@Override
	public PropertySheetHolder[] getChildPropertySheetHolders() {
		return null;
	}

	@Override
	public PropertySheet[] getPropertySheets() {
		return new PropertySheet[] {new PropertySheetWizardAdapter(getConfigurationWizard())};
	}

	@Override
	public Action[] getPropertySheetHolderActions() {
		return null;
	}

	@Override
	public Icon getPropertySheetHolderIcon() {
		return null;
	}

	protected synchronized void readDiscreteInputs() throws ModbusException{
		synchronized (writeLock) {
			if (discreteInputsCount == 0) {
				return;
			}
			//Read Contacts
			inputs = this.modbusMaster.readInputDiscretes(discreteInputsOffset, discreteInputsCount);
		}
	}

	protected synchronized void readCoils() throws ModbusException{
		synchronized (writeLock) {
			if (coilsCount == 0) {
				return;
			}
			//Read Coils
			coils = this.modbusMaster.readCoils(coilsOffset, coilsCount);
		}
	}

	protected synchronized void writeCoils() throws ModbusException{
		synchronized (writeLock) {
			if (coilsCount == 0) {
				return;
			}
			//Write each coil individually, problems with multiple coils
			this.modbusMaster.writeMultipleCoils(coilsOffset, getCoils());
		}
	}

	protected synchronized void readInputReg() throws ModbusException{ //Limited to 100 registers
		synchronized (writeLock) {
			//		int requests;
			InputRegister[] readInput;

			if (inputRegCount == 0) {
				return;
			}

			readInput = (InputRegister[]) this.modbusMaster.readInputRegisters(inputRegOffset,
					inputRegCount);

			for (int i = 0; i < readInput.length; i++) {
				getInputReg()[i] = readInput[i];
			}

			/* Block to read more than 100 registers (in test) */
			//		//Part the request in 100
			//		requests = inputRegCount/100;
			//		//Read all the times
			//		for(int i=0;i<requests;i++){
			//			InputRegister[] aIreg = (InputRegister[])this.modbusMaster.readInputRegisters(i*100, 100);
			//
			//			for(int j=0;j<aIreg.length;j++){
			//				inputReg[j + (i*100)] = aIreg[j];
			//			}
			//		}
			//
			//		if(inputRegCount - (requests*100) > 0){
			//			InputRegister[] aIreg = (InputRegister[])this.modbusMaster.readInputRegisters(requests*100, inputRegCount - (requests*100));
			//
			//			for(int j=0;j<aIreg.length;j++){
			//				inputReg[j + (requests*100)] = aIreg[j];
			//			}
			//		}
		}
	}

	protected synchronized void readHoldings() throws ModbusException{
		synchronized (writeLock) {
			if (holdingsCount == 0) {
				return;
			}
			//Read Input Register
			Register[] reg = this.modbusMaster.readMultipleRegisters(holdingsOffset, holdingsCount);
			holdings = reg;
		}
	}

	protected synchronized void writeHoldings() throws ModbusException{
		synchronized (writeLock) {
			if (holdingsCount == 0) {
				return;
			}
			//Write Holdings
			this.modbusMaster.writeMultipleRegisters(holdingsOffset, getHoldings());
		}
	}

	public void setByte(Byte data, DataType mbType, int address, ByteOrder order){
		synchronized (writeLock) {
			if (data == null) {
				return;
			}

			switch (mbType) {
				case HOLDINGS:
					Register tempHold = this.getHoldings()[address];
					byte[] values = tempHold.toBytes();

					if (order == ByteOrder.LSB) {
						values[0] = data;
					}
					else {
						values[1] = data;
					}

					tempHold.setValue(values);
					this.getHoldings()[address] = tempHold;
					break;
				default:
					break;
			}
		}
	}
	//16 Bits int

	public void setInt(Integer data, DataType mbType, int address){
		synchronized (writeLock) {
			if (data == null) {
				return;
			}

			switch (mbType) {
				case HOLDINGS:
					this.getHoldings()[address].setValue(data.shortValue());
					break;
				default:
					break;
			}
		}
	}
	//32 Bits Long
	public void setLong(Integer data, DataType mbType, int address){
		synchronized (writeLock) {
			if (data == null) {
				return;
			}

			switch (mbType) {
				case HOLDINGS:
					SimpleRegister dataH = new SimpleRegister();
					SimpleRegister dataL = new SimpleRegister();

					dataL.setValue(data.shortValue());
					dataH.setValue(data >> 16);

					this.getHoldings()[address].setValue(data.shortValue());
					this.getHoldings()[address + 1].setValue(data >> 16);
					break;
				default:
					break;
			}
		}
	}
	//32 Bits Float
	public void setFloat(Float data, DataType mbType, int address){
		synchronized (writeLock) {
			if (data == null) {
				return;
			}

			Integer dataInt = Float.floatToIntBits(data);

			switch (mbType) {
				case HOLDINGS:
					this.getHoldings()[address].setValue(dataInt.shortValue());
					this.getHoldings()[address + 1].setValue(dataInt >> 16);
					break;
				default:
					break;
			}
		}
	}
	//64 Bits Float
	public void setDouble(Double data, DataType mbType, int address){
		synchronized (writeLock) {
			if (data == null) {
				return;
			}

			Long dataLong = Double.doubleToLongBits(data);

			switch (mbType) {
				case HOLDINGS:
					this.getHoldings()[address].setValue((short) dataLong.intValue());
					this.getHoldings()[address + 1].setValue((short) (dataLong >> 16));
					this.getHoldings()[address + 2].setValue((short) (dataLong >> 32));
					this.getHoldings()[address + 3].setValue((short) (dataLong >> 48));
					break;
				default:
					break;
			}
		}
	}
	
	///8 bits INT
    public Byte getByteData(DataType mbType, int address, ByteOrder order){
		synchronized (writeLock) {
			Byte data;

			//Get if it is the MSB or LSB
			int iOrder = order == ByteOrder.LSB ? 0 : 1;

			switch (mbType) {
				case HOLDINGS:
					data = this.getHoldings()[address].toBytes()[iOrder]; //Get the lowest byte of the register
					break;

				case INPUTREGISTER:
					data = this.getInputReg()[address].toBytes()[iOrder]; //Get the lowest byte of the register
					break;

				default:
					return null;
			}

			return data;
		}
    }
    
    //16 Bits int
    public Integer getIntData(DataType mbType, int address){
		synchronized (writeLock) {
			Integer data = 0;

			switch (mbType) {
				case HOLDINGS:
					data = this.getHoldings()[address].toUnsignedShort();
					break;

				case INPUTREGISTER:
					data = this.getInputReg()[address].toUnsignedShort();
					break;

				default:
					break;
			}

			return data;
		}
    }
    
    //32 Bits Long
    public Integer getLongData(DataType mbType, int address){
		synchronized (writeLock) {
			Integer data = 0;
			short dataH;
			short dataL;

			switch (mbType) {
				case HOLDINGS:
					dataL = this.getHoldings()[address].toShort();
					dataH = this.getHoldings()[address + 1].toShort();
					data = (dataH << 16) | (dataL);
					break;

				case INPUTREGISTER:
					dataL = this.getInputReg()[address].toShort();
					dataH = this.getInputReg()[address + 1].toShort();
					data = (dataH << 16) | (dataL);
					break;
				default:
					break;
			}

			return data;
		}
    }
    //32 Bits Float
    public Float getFloatData(DataType mbType, int address){
		synchronized (writeLock) {
			Float data = 0f;
			Integer dataInt = null;
			int dataH;
			int dataL;

			switch (mbType) {
				case HOLDINGS:
					if ((address + 1) > this.getHoldings().length) {
						break;
					}
					dataL = this.getHoldings()[address].toUnsignedShort();
					dataH = this.getHoldings()[address + 1].toUnsignedShort();
					dataInt = (dataH << 16) | (dataL);
					break;

				case INPUTREGISTER:
					if ((address + 1) > this.getInputReg().length) {
						break;
					}
					dataL = this.getInputReg()[address].toUnsignedShort();
					dataH = this.getInputReg()[address + 1].toUnsignedShort();
					dataInt = (dataH << 16) | (dataL);
					break;
				default:
					break;
			}

			if (dataInt != null) {
				data = Float.intBitsToFloat(dataInt);
			}

			return data;
		}
    }
    //64 Bits Float
    public Double getDoubleData(DataType mbType, int address){
		synchronized (writeLock) {
			Double data = 0d;
			Long dataLong = null;
			int dataH;
			int dataL;
			short dataLL;
			short dataHL;

			switch (mbType) {
				case HOLDINGS:
					dataLL = this.getHoldings()[address].toShort();
					dataHL = this.getHoldings()[address + 1].toShort();
					dataL = (dataHL << 16) | (dataLL);

					dataLL = this.getHoldings()[address + 2].toShort();
					dataHL = this.getHoldings()[address + 3].toShort();
					dataH = (dataHL << 16) | (dataLL);

					dataLong = (long) ((long) dataH << 32) | (long) (dataL);
					break;

				case INPUTREGISTER:
					dataLL = this.getInputReg()[address].toShort();
					dataHL = this.getInputReg()[address + 1].toShort();
					dataL = (dataHL << 16) | (dataLL);

					dataLL = this.getInputReg()[address + 2].toShort();
					dataHL = this.getInputReg()[address + 3].toShort();
					dataH = (dataHL << 16) | (dataLL);

					dataLong = (long) ((long) dataH << 32) | (long) (dataL);
					break;
				default:
					break;
			}

			if (dataLong != null) {
				data = Double.longBitsToDouble(dataLong);
			}

			return data;
		}
    }

	/**
	 * Modbus/TCP Master facade, based on the {@link ModbusTCPMaster} with some additional
	 * features.
	 * Deprecated, use instead {@link ModbusTCPMaster}.
	 */
	@Deprecated
	public class ModbusMasterTCP{

		private TCPMasterConnection m_Connection;
		private InetAddress m_SlaveAddress;
		private ModbusTCPTransaction m_Transaction;
		private ReadCoilsRequest m_ReadCoilsRequest;
		private ReadInputDiscretesRequest m_ReadInputDiscretesRequest;
		private WriteCoilRequest m_WriteCoilRequest;
		private WriteMultipleCoilsRequest m_WriteMultipleCoilsRequest;
		private ReadInputRegistersRequest m_ReadInputRegistersRequest;
		private ReadMultipleRegistersRequest m_ReadMultipleRegistersRequest;
		private WriteSingleRegisterRequest m_WriteSingleRegisterRequest;
		private WriteMultipleRegistersRequest m_WriteMultipleRegistersRequest;
		private boolean m_Reconnecting = false;

		/**
		 * Constructs a new master facade instance for communication
		 * with a given slave.
		 *
		 * @param addr an internet address as resolvable IP name or IP number,
		 *             specifying the slave to communicate with.
		 */
		public ModbusMasterTCP(String addr) {
			try {
				m_SlaveAddress = InetAddress.getByName(addr);
				m_Connection = new TCPMasterConnection(m_SlaveAddress);
				m_ReadCoilsRequest = new ReadCoilsRequest();
				m_ReadInputDiscretesRequest = new ReadInputDiscretesRequest();
				m_WriteCoilRequest = new WriteCoilRequest();
				m_WriteMultipleCoilsRequest = new WriteMultipleCoilsRequest();
				m_ReadInputRegistersRequest = new ReadInputRegistersRequest();
				m_ReadMultipleRegistersRequest = new ReadMultipleRegistersRequest();
				m_WriteSingleRegisterRequest = new WriteSingleRegisterRequest();
				m_WriteMultipleRegistersRequest = new WriteMultipleRegistersRequest();

			} catch (UnknownHostException e) {
				throw new RuntimeException(e.getMessage());
			}
		}//constructor

		/**
		 * Constructs a new master facade instance for communication
		 * with a given slave.
		 *
		 * @param addr an internet address as resolvable IP name or IP number,
		 *             specifying the slave to communicate with.
		 * @param port the port the slave is listening to.
		 */
		public ModbusMasterTCP(String addr, int port) {
			this(addr);
			m_Connection.setPort(port);
		}//constructor

		/**
		 * Constructs a new master facade instance for communication
		 * with a given slave.
		 *
		 * @param addr an internet address as resolvable IP name or IP number,
		 *             specifying the slave to communicate with.
		 * @param port the port the slave is listening to.
		 * @param timeout the timeout of the slave
		 */
		public ModbusMasterTCP(String addr, int port, int timeout) {
			this(addr);
			m_Connection.setPort(port);
			m_Connection.setTimeout(timeout);
		}//constructor

		/**
		 * Connects this <tt>ModbusTCPMaster</tt> with the slave.
		 *
		 * @throws Exception if the connection cannot be established.
		 */
		public void connect()
				throws Exception {
			if (m_Connection != null && !m_Connection.isConnected()) {
				m_Connection.connect();
				m_Transaction = new ModbusTCPTransaction(m_Connection);
				m_Transaction.setReconnecting(m_Reconnecting);
			}
		}//connect

		/**
		 * Disconnects this <tt>ModbusTCPMaster</tt> from the slave.
		 */
		public void disconnect() {
			if (m_Connection != null && m_Connection.isConnected()) {
				m_Connection.close();
				m_Transaction = null;
			}
		}//disconnect

		/**
		 * Check the connection of this <tt>ModbusTCPMaster</tt> to the slave.
		 * @return The Connection status, true if connected
		 */
		public boolean isConnected() {
			return m_Connection != null && m_Connection.isConnected();
		}//isConnected

		/**
		 * Sets the flag that specifies whether to maintain a
		 * constant connection or reconnect for every transaction.
		 *
		 * @param b true if a new connection should be established for each
		 *          transaction, false otherwise.
		 */
		public void setReconnecting(boolean b) {
			m_Reconnecting = b;
			if (m_Transaction != null) {
				m_Transaction.setReconnecting(b);
			}
		}//setReconnecting

		/**
		 * Tests if a constant connection is maintained or if a new
		 * connection is established for every transaction.
		 *
		 * @return true if a new connection should be established for each
		 *         transaction, false otherwise.
		 */
		public boolean isReconnecting() {
			return m_Reconnecting;
		}//isReconnecting

		/**
		 * Reads a given number of coil states from the slave.
		 * <p/>
		 * Note that the number of bits in the bit vector will be
		 * forced to the number originally requested.
		 *
		 * @param ref   the offset of the coil to start reading from.
		 * @param count the number of coil states to be read.
		 * @return a <tt>BitVector</tt> instance holding the
		 *         received coil states.
		 * @throws ModbusException if an I/O error, a slave exception or
		 *                         a transaction error occurs.
		 */
		public synchronized BitVector readCoils(int ref, int count)
				throws ModbusException {
			m_ReadCoilsRequest.setUnitID(1);
			m_ReadCoilsRequest.setReference(ref);
			m_ReadCoilsRequest.setBitCount(count);
			m_Transaction.setRequest(m_ReadCoilsRequest);
			m_Transaction.execute();
			BitVector bv = ((ReadCoilsResponse) m_Transaction.getResponse()).getCoils();
			bv.forceSize(count);
			return bv;
		}//readCoils

		/**
		 * Writes a coil state to the slave.
		 *
		 * @param ref    the offset of the coil to be written.
		 * @param state  the coil state to be written.
		 * @return the state of the coil as returned from the slave.
		 * @throws ModbusException if an I/O error, a slave exception or
		 *                         a transaction error occurs.
		 */
		public synchronized boolean writeCoil(int ref, boolean state)
				throws ModbusException {
			m_WriteCoilRequest.setUnitID(1);
			m_WriteCoilRequest.setReference(ref);
			m_WriteCoilRequest.setCoil(state);
			m_Transaction.setRequest(m_WriteCoilRequest);
			m_Transaction.execute();
			return ((WriteCoilResponse) m_Transaction.getResponse()).getCoil();
		}//writeCoil

		/**
		 * Writes a given number of coil states to the slave.
		 * <p/>
		 * Note that the number of coils to be written is given
		 * implicitly, through {@link BitVector#size()}.
		 *
		 * @param ref   the offset of the coil to start writing to.
		 * @param coils a <tt>BitVector</tt> which holds the coil states to be written.
		 * @throws ModbusException if an I/O error, a slave exception or
		 *                         a transaction error occurs.
		 */
		public synchronized void writeMultipleCoils(int ref, BitVector coils)
				throws ModbusException {
			m_WriteMultipleCoilsRequest.setUnitID(1);
			m_WriteMultipleCoilsRequest.setReference(ref);
			m_WriteMultipleCoilsRequest.setCoils(coils);
			m_Transaction.setRequest(m_WriteMultipleCoilsRequest);
			m_Transaction.execute();
		}//writeMultipleCoils

		/**
		 * Reads a given number of input discrete states from the slave.
		 * <p/>
		 * Note that the number of bits in the bit vector will be
		 * forced to the number originally requested.
		 *
		 * @param ref   the offset of the input discrete to start reading from.
		 * @param count the number of input discrete states to be read.
		 * @return a <tt>BitVector</tt> instance holding the received input discrete
		 *         states.
		 * @throws ModbusException if an I/O error, a slave exception or
		 *                         a transaction error occurs.
		 */
		public synchronized BitVector readInputDiscretes(int ref, int count)
				throws ModbusException {
			m_ReadInputDiscretesRequest.setUnitID(1);
			m_ReadInputDiscretesRequest.setReference(ref);
			m_ReadInputDiscretesRequest.setBitCount(count);
			m_Transaction.setRequest(m_ReadInputDiscretesRequest);
			m_Transaction.execute();
			BitVector bv = ((ReadInputDiscretesResponse) m_Transaction.getResponse()).getDiscretes();
			bv.forceSize(count);
			return bv;
		}//readInputDiscretes


		/**
		 * Reads a given number of input registers from the slave.
		 * <p/>
		 * Note that the number of input registers returned (i.e. array length)
		 * will be according to the number received in the slave response.
		 *
		 * @param ref   the offset of the input register to start reading from.
		 * @param count the number of input registers to be read.
		 * @return a <tt>InputRegister[]</tt> with the received input registers.
		 * @throws ModbusException if an I/O error, a slave exception or
		 *                         a transaction error occurs.
		 */
		public synchronized InputRegister[] readInputRegisters(int ref, int count)
				throws ModbusException {
			m_ReadInputRegistersRequest.setUnitID(1);
			m_ReadInputRegistersRequest.setReference(ref);
			m_ReadInputRegistersRequest.setWordCount(count);
			m_Transaction.setRequest(m_ReadInputRegistersRequest);
			m_Transaction.execute();
			return ((ReadInputRegistersResponse) m_Transaction.getResponse()).getRegisters();
		}//readInputRegisters

		/**
		 * Reads a given number of registers from the slave.
		 * <p/>
		 * Note that the number of registers returned (i.e. array length)
		 * will be according to the number received in the slave response.
		 *
		 * @param ref   the offset of the register to start reading from.
		 * @param count the number of registers to be read.
		 * @return a <tt>Register[]</tt> holding the received registers.
		 * @throws ModbusException if an I/O error, a slave exception or
		 *                         a transaction error occurs.
		 */
		public synchronized Register[] readMultipleRegisters(int ref, int count)
				throws ModbusException {
			m_ReadMultipleRegistersRequest.setUnitID(1);
			m_ReadMultipleRegistersRequest.setReference(ref);
			m_ReadMultipleRegistersRequest.setWordCount(count);
			m_Transaction.setRequest(m_ReadMultipleRegistersRequest);
			m_Transaction.execute();
			return ((ReadMultipleRegistersResponse) m_Transaction.getResponse()).getRegisters();
		}//readMultipleRegisters

		/**
		 * Writes a single register to the slave.
		 *
		 * @param ref      the offset of the register to be written.
		 * @param register a <tt>Register</tt> holding the value of the register
		 *                 to be written.
		 * @throws ModbusException if an I/O error, a slave exception or
		 *                         a transaction error occurs.
		 */
		public synchronized void writeSingleRegister(int ref, Register register)
				throws ModbusException {
			m_WriteSingleRegisterRequest.setUnitID(1);
			m_WriteSingleRegisterRequest.setReference(ref);
			m_WriteSingleRegisterRequest.setRegister(register);
			m_Transaction.setRequest(m_WriteSingleRegisterRequest);
			m_Transaction.execute();
		}//writeSingleRegister

		/**
		 * Writes a number of registers to the slave.
		 *
		 * @param ref       the offset of the register to start writing to.
		 * @param registers a <tt>Register[]</tt> holding the values of
		 *                  the registers to be written.
		 * @throws ModbusException if an I/O error, a slave exception or
		 *                         a transaction error occurs.
		 */
		public synchronized void writeMultipleRegisters(int ref, Register[] registers)
				throws ModbusException {
			m_WriteMultipleRegistersRequest.setUnitID(1);
			m_WriteMultipleRegistersRequest.setReference(ref);
			m_WriteMultipleRegistersRequest.setRegisters(registers);
			m_Transaction.setRequest(m_WriteMultipleRegistersRequest);
			m_Transaction.execute();
		}//writeMultipleRegisters

	}//class ModbusMasterTCP
}
