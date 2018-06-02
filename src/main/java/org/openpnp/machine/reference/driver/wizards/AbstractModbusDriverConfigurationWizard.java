package org.openpnp.machine.reference.driver.wizards;

import java.awt.Frame;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BoxLayout;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;

import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.machine.reference.driver.AbstractModbusDriver;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

public class AbstractModbusDriverConfigurationWizard extends AbstractConfigurationWizard {
	private final AbstractModbusDriver driver;
	private JTextField textModbusIp;
	private JSpinner spinnerPort;
	private JSpinner spinnerTimeout;

	public AbstractModbusDriverConfigurationWizard(AbstractModbusDriver driver) throws ParseException {
		this.driver = driver;

		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));


		JPanel panel = new JPanel();
		panel.setBorder(new TitledBorder(null, "Modbus", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		contentPanel.add(panel);
		panel.setLayout(new FormLayout(new ColumnSpec[] {
				FormSpecs.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("right:default"),
				FormSpecs.RELATED_GAP_COLSPEC,
				FormSpecs.DEFAULT_COLSPEC,},
				new RowSpec[] {
						FormSpecs.RELATED_GAP_ROWSPEC,
						FormSpecs.DEFAULT_ROWSPEC,
						FormSpecs.RELATED_GAP_ROWSPEC,
						FormSpecs.DEFAULT_ROWSPEC,
						FormSpecs.RELATED_GAP_ROWSPEC,
						FormSpecs.DEFAULT_ROWSPEC,
						FormSpecs.RELATED_GAP_ROWSPEC,
						FormSpecs.DEFAULT_ROWSPEC,
						FormSpecs.RELATED_GAP_ROWSPEC,
						FormSpecs.DEFAULT_ROWSPEC,
						FormSpecs.RELATED_GAP_ROWSPEC,
						FormSpecs.DEFAULT_ROWSPEC,
						FormSpecs.RELATED_GAP_ROWSPEC,
						FormSpecs.DEFAULT_ROWSPEC,
						FormSpecs.RELATED_GAP_ROWSPEC,
						FormSpecs.DEFAULT_ROWSPEC,}));

		JLabel lblIPAddress = new JLabel("IP address");
		panel.add(lblIPAddress, "2, 2, right, default");


		textModbusIp = new JTextField();
		textModbusIp.setInputVerifier(new IPTextFieldVerifier());
		textModbusIp.setText(driver.getModbusIp());
		panel.add(textModbusIp, "4, 2, fill, default");
		textModbusIp.setColumns(10);

		JLabel lblPort = new JLabel("Port");
		panel.add(lblPort, "2, 4, right, default");

		spinnerPort = new JSpinner(new SpinnerNumberModel(driver.getPort(), 
				1, 65535, 1));
		panel.add(spinnerPort, "4, 4, fill, default");

		JLabel lblTimeout = new JLabel("Timeout (ms)");
		panel.add(lblTimeout, "2, 6, right, default");

		spinnerTimeout = new JSpinner(new SpinnerNumberModel(driver.getTimeoutMilliseconds(), 
				1, 10000, 1));
		panel.add(spinnerTimeout, "4, 6, fill, default");
	}

	@Override
	public void createBindings() {
		addWrappedBinding(driver, "modbusIp", textModbusIp, "text");
		addWrappedBinding(driver, "port", spinnerPort, "value");
		addWrappedBinding(driver, "timeoutMilliseconds", spinnerTimeout, "value");
	}

	class IPTextFieldVerifier extends InputVerifier {

		Pattern pat = Pattern.compile("\\b(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."+
				"(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
				"(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
				"(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");

		public boolean verify(JComponent input) {
			JTextField field = (JTextField) input;
			Matcher m = pat.matcher(field.getText());


			if(!m.matches()){
				input.setInputVerifier(null);
				JOptionPane.showMessageDialog(new Frame(), "Malformed IP Address!", "Error",
						JOptionPane.ERROR_MESSAGE);
				input.setInputVerifier(this); 
			}

			return m.matches();
		}

		public boolean shouldYieldFocus(JComponent input) {
			return verify(input);
		}
	}


}
