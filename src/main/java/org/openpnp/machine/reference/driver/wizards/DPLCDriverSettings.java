package org.openpnp.machine.reference.driver.wizards;

import java.awt.FileDialog;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.StringReader;
import java.io.StringWriter;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.ComponentDecorators;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.machine.reference.driver.DPLCDriver;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.PasteDispenser;
import org.simpleframework.xml.Serializer;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;
import javax.swing.JCheckBox;

public class DPLCDriverSettings extends AbstractConfigurationWizard {
    private final DPLCDriver driver;

    public DPLCDriverSettings(DPLCDriver driver) {
        this.driver = driver;
        
        JPanel settingsPanel = new JPanel();
        settingsPanel.setBorder(new TitledBorder(null, "Settings", TitledBorder.LEADING, TitledBorder.TOP, null, null));
        contentPanel.add(settingsPanel);
        settingsPanel.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
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
                FormSpecs.DEFAULT_ROWSPEC,}));

        //Right side
        JLabel lblUnits = new JLabel("Units");
        settingsPanel.add(lblUnits, "6, 2, right, default");
        
        unitsCb = new JComboBox(LengthUnit.values());
        settingsPanel.add(unitsCb, "8, 2, fill, default");

        JLabel lblNewLabel = new JLabel("Driver Name");
        settingsPanel.add(lblNewLabel, "6, 4, right, default");

        driverName = new JTextField();
        driverName.setColumns(5);
        settingsPanel.add(driverName, "8, 4");

        JLabel lblVisualHoming = new JLabel("Visual Homing");
        settingsPanel.add(lblVisualHoming, "6, 6, right, default");

        visualHoming = new JCheckBox("");
        settingsPanel.add(visualHoming, "8, 6");

        //Left Side
        
        JLabel lblCommandTimeoutms = new JLabel("Command Timeout [ms]");
        settingsPanel.add(lblCommandTimeoutms, "2, 2, right, default");
        
        commandTimeoutTf = new JTextField();
        settingsPanel.add(commandTimeoutTf, "4, 2, fill, default");
        commandTimeoutTf.setColumns(5);
        
        JLabel lblConnectWaitTime = new JLabel("Connect Wait Time [ms]");
        settingsPanel.add(lblConnectWaitTime, "2, 4, right, default");
        
        moveWaitTimeTf = new JTextField();
        settingsPanel.add(moveWaitTimeTf, "4, 4, fill, default");
        moveWaitTimeTf.setColumns(5);

        JLabel lblNonSquarenessFactor = new JLabel("Non-Squareness Factor");
        settingsPanel.add(lblNonSquarenessFactor, "2, 6, right, default");
        
        nonSquarenessFactorTf = new JTextField();
        settingsPanel.add(nonSquarenessFactorTf, "4, 6, fill, default");
        nonSquarenessFactorTf.setColumns(5);

        JLabel lblMaxFeedRateX = new JLabel("Max Speed X [Units/Seg]");
        settingsPanel.add(lblMaxFeedRateX, "2, 8, right, default");

        maxFeedRateXTf = new JTextField();
        settingsPanel.add(maxFeedRateXTf, "4, 8, fill, default");
        maxFeedRateXTf.setColumns(5);

        JLabel lblMaxFeedRateY = new JLabel("Max Speed Y [Units/Seg]");
        settingsPanel.add(lblMaxFeedRateY, "6, 8, right, default");

        maxFeedRateYTf = new JTextField();
        settingsPanel.add(maxFeedRateYTf, "8, 8, fill, default");
        maxFeedRateYTf.setColumns(5);

        JLabel lblMaxFeedRateZ = new JLabel("Max Speed Z [Units/Seg]");
        settingsPanel.add(lblMaxFeedRateZ, "2, 10, right, default");

        maxFeedRateZTf = new JTextField();
        settingsPanel.add(maxFeedRateZTf, "4, 10, fill, default");
        maxFeedRateZTf.setColumns(5);

        JLabel lblMaxFeedRateTheta = new JLabel("Max Speed Θ [Units/Seg]");
        settingsPanel.add(lblMaxFeedRateTheta, "6, 10, right, default");

        maxFeedRateThetaTf = new JTextField();
        settingsPanel.add(maxFeedRateThetaTf, "8, 10, fill, default");
        maxFeedRateThetaTf.setColumns(5);
    }

    @Override
    public void createBindings() {
        IntegerConverter intConverter = new IntegerConverter();
        DoubleConverter doubleConverter =
                new DoubleConverter(Configuration.get().getLengthDisplayFormat());
        DoubleConverter doubleConverterFine = new DoubleConverter("%f");
        
        addWrappedBinding(driver, "units", unitsCb, "selectedItem");
        addWrappedBinding(driver, "maxFeedRateX", maxFeedRateXTf, "text", intConverter);
        addWrappedBinding(driver, "maxFeedRateY", maxFeedRateYTf, "text", intConverter);
        addWrappedBinding(driver, "maxFeedRateZ", maxFeedRateZTf, "text", intConverter);
        addWrappedBinding(driver, "maxFeedRateTheta", maxFeedRateThetaTf, "text", intConverter);
        addWrappedBinding(driver, "nonSquarenessFactor", nonSquarenessFactorTf, "text", doubleConverterFine);
        addWrappedBinding(driver, "moveTimeoutMilliseconds", moveWaitTimeTf, "text", intConverter);
        addWrappedBinding(driver, "connectWaitTimeMilliseconds", commandTimeoutTf, "text", intConverter);
        addWrappedBinding(driver, "name", driverName, "text");
        addWrappedBinding(driver, "visualHomingEnabled", visualHoming, "selected");
        
        ComponentDecorators.decorateWithAutoSelect(maxFeedRateXTf);
        ComponentDecorators.decorateWithAutoSelect(maxFeedRateYTf);
        ComponentDecorators.decorateWithAutoSelect(maxFeedRateZTf);
        ComponentDecorators.decorateWithAutoSelect(maxFeedRateThetaTf);
        ComponentDecorators.decorateWithAutoSelect(nonSquarenessFactorTf);
        ComponentDecorators.decorateWithAutoSelect(commandTimeoutTf);
        ComponentDecorators.decorateWithAutoSelect(moveWaitTimeTf);
        ComponentDecorators.decorateWithAutoSelect(driverName);
    }

    public final Action exportProfileAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.export);
            putValue(NAME, "Export dPLC File");
            putValue(SHORT_DESCRIPTION, "Export the dPLC profile to a file.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            try {
                FileDialog fileDialog = new FileDialog(MainFrame.get(), "Save dPLC Profile As...",
                        FileDialog.SAVE);
                fileDialog.setFilenameFilter(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.toLowerCase().endsWith(".xml");
                    }
                });
                fileDialog.setVisible(true);
                String filename = fileDialog.getFile();
                if (filename == null) {
                    return;
                }
                if (!filename.toLowerCase().endsWith(".xml")) {
                    filename = filename + ".xml";
                }
                File file = new File(new File(fileDialog.getDirectory()), filename);
                if (file.exists()) {
                    int ret = JOptionPane.showConfirmDialog(getTopLevelAncestor(),
                            file.getName() + " already exists. Do you want to replace it?",
                            "Replace file?", JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    if (ret != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                Serializer s = Configuration.createSerializer();
                FileWriter w = new FileWriter(file);
                s.write(driver, w);
                w.close();
            }
            catch (Exception e) {
                MessageBoxes.errorBox(MainFrame.get(), "Export Failed", e);
            }
        }
    };

    public final Action importProfileAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.importt);
            putValue(NAME, "Load dPLC File");
            putValue(SHORT_DESCRIPTION, "Import the dPLC profile from a file.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            try {
                FileDialog fileDialog = new FileDialog(MainFrame.get(),
                        "Load dPLC Profile From...", FileDialog.LOAD);
                fileDialog.setFilenameFilter(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.toLowerCase().endsWith(".xml");
                    }
                });
                fileDialog.setVisible(true);
                String filename = fileDialog.getFile();
                File file = new File(new File(fileDialog.getDirectory()), filename);
                Serializer ser = Configuration.createSerializer();
                FileReader r = new FileReader(file);
                DPLCDriver d = ser.read(DPLCDriver.class, r);
                // copySettings(d, driver);
            }
            catch (Exception e) {
                MessageBoxes.errorBox(MainFrame.get(), "Import Failed", e);
            }
        }
    };

    public final Action copyProfileToClipboardAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.copy);
            putValue(NAME, "Copy dPLC to Clipboard");
            putValue(SHORT_DESCRIPTION, "Copy the dPLC profile to the clipboard.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            try {
                Serializer s = Configuration.createSerializer();
                StringWriter w = new StringWriter();
                s.write(driver, w);
                StringSelection stringSelection = new StringSelection(w.toString());
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
                MessageBoxes.infoBox("Copied dPLC", "Copied dPLC to Clipboard");
            }
            catch (Exception e) {
                MessageBoxes.errorBox(MainFrame.get(), "Copy Failed", e);
            }
        }
    };

    public final Action pasteProfileFromClipboardAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.paste);
            putValue(NAME, "Paste dPLC from Clipboard");
            putValue(SHORT_DESCRIPTION, "Import the dPLC profile from the clipboard.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            try {
                Serializer ser = Configuration.createSerializer();
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                String s = (String) clipboard.getData(DataFlavor.stringFlavor);
                StringReader r = new StringReader(s);
                DPLCDriver d = ser.read(DPLCDriver.class, s);
                // copySettings(d, driver);
                MessageBoxes.infoBox("Pasted dPLC", "Pasted dPLC from Clipboard");
            }
            catch (Exception e) {
                MessageBoxes.errorBox(MainFrame.get(), "Paste Failed", e);
            }
        }
    };
    private JTextField maxFeedRateXTf;
    private JTextField maxFeedRateYTf;
    private JTextField maxFeedRateZTf;
    private JTextField maxFeedRateThetaTf;
    private JTextField nonSquarenessFactorTf;
    private JTextField commandTimeoutTf;
    private JTextField moveWaitTimeTf;
    private JComboBox unitsCb;
    private JTextField driverName;
    private JCheckBox visualHoming;

    static class HeadMountableItem {
        private HeadMountable hm;

        public HeadMountableItem(HeadMountable hm) {
            this.hm = hm;
        }

        public HeadMountable getHeadMountable() {
            return hm;
        }

        @Override
        public String toString() {
            if (hm == null) {
                return "Default";
            }
            String type = null;
            if (hm instanceof Nozzle) {
                type = "Nozzle";
            }
            else if (hm instanceof PasteDispenser) {
                type = "Paste Dispenser";
            }
            else if (hm instanceof Camera) {
                type = "Camera";
            }
            else if (hm instanceof Actuator) {
                type = "Actuator";
            }
            return String.format("%s: %s %s", type, hm.getHead() == null ? "[No Head]" : hm.getHead().getName(), hm.getName());
        }
    }
}
