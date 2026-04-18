/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.ui;

import java.awt.BorderLayout;
import javax.swing.*;

import docking.ActionContext;
import docking.ComponentProvider;
import docking.action.DockingAction;
import docking.action.ToolBarData;
import ghidra.framework.plugintool.Plugin;
import ghidra.util.HelpLocation;
import resources.Icons;

/**
 * UI provider for the Argos plugin.
 * This class provides the graphical user interface for configuring and
 * monitoring the Argos server.
 */
public class ArgosProvider extends ComponentProvider {
    private JPanel panel;
    private JTextArea statusArea;
    private DockingAction configAction;

    /**
     * Constructor
     * @param plugin The parent plugin
     * @param owner The owner name
     */
    public ArgosProvider(Plugin plugin, String owner) {
        super(plugin.getTool(), "Argos Provider", owner);
        buildPanel();
        createActions();
        setStatusText("Argos Model Context Protocol server is running");
    }

    /**
     * Build the UI panel
     */
    private void buildPanel() {
        panel = new JPanel(new BorderLayout());

        // Status area to show server status
        statusArea = new JTextArea(10, 40);
        statusArea.setEditable(false);

        // Add components to panel
        panel.add(new JScrollPane(statusArea), BorderLayout.CENTER);

        setVisible(true);
    }

    /**
     * Create actions for the toolbar
     */
    private void createActions() {
        configAction = new DockingAction("Argos Configuration", getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
                // TODO: Show configuration dialog
                JOptionPane.showMessageDialog(panel,
                    "Argos Configuration (TODO)",
                    "Argos Configuration",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        };

        configAction.setToolBarData(new ToolBarData(Icons.HELP_ICON, null));
        configAction.setEnabled(true);
        configAction.setDescription("Configure Argos");
        configAction.setHelpLocation(new HelpLocation("Argos", "Configuration"));

        addLocalAction(configAction);
    }

    /**
     * Set status text to display
     * @param status The status text to display
     */
    public void setStatusText(String status) {
        statusArea.append(status + "\n");
        statusArea.setCaretPosition(statusArea.getText().length());
    }

    /**
     * Get the UI component
     * @return The JComponent for this provider
     */
    @Override
    public JComponent getComponent() {
        return panel;
    }
}
