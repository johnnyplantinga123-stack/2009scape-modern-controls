package com.editor;

import com.editor.item.ItemSelection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class ToolSelection extends JFrame {
    private static final long serialVersionUID = 2024943190858205332L;
    private String cache = "";
    private JButton loadCacheButton;
    private JComboBox selectionBox;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> (new ToolSelection()).setVisible(true));
    }

    public ToolSelection() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.setIconImage(Main.iconImage);
        this.setTitle("Tool Selection");
        this.setResizable(false);
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        this.setLocationRelativeTo(null);
        this.initComponents();
        Main.log("Main", "ToolSelection Started");
    }

    private void initComponents() {
        JPanel alignmentPanel1 = new JPanel(new FlowLayout());
        JPanel alignmentPanel2 = new JPanel(new FlowLayout());
        JPanel alignmentPanel3 = new JPanel(new FlowLayout());

        this.setPreferredSize(new Dimension(250, 200));
        JLabel selectYourEditorLabel = new JLabel("Select your editor:");
        this.selectionBox = new JComboBox();
        JButton submitButton = new JButton();
        JMenuBar jMenuBar1 = new JMenuBar();
        JMenu jMenu1 = new JMenu();
        this.loadCacheButton = new JButton();
        JMenuItem exitButton = new JMenuItem();
        this.setDefaultCloseOperation(3);
        this.loadCacheButton.setText("Load Cache");
        this.loadCacheButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadCacheButtonHandler(e);
            }
        });
        this.loadCacheButton.setPreferredSize(new Dimension(125, 30));
        alignmentPanel1.add(loadCacheButton, BorderLayout.CENTER);

        alignmentPanel2.add(selectYourEditorLabel);

        this.selectionBox.setModel(new DefaultComboBoxModel(new String[]{"Items"}));
        submitButton.setText("Submit");
        submitButton.addActionListener(ToolSelection.this::submitButtonActionPerformed);

        alignmentPanel3.add(this.selectionBox);
        alignmentPanel3.add(submitButton);

        jMenu1.setText("File");
        exitButton.setText("Exit Program");
        exitButton.addActionListener(ToolSelection.this::exitButtonActionPerformed);
        jMenu1.add(exitButton);
        jMenuBar1.add(jMenu1);
        this.setJMenuBar(jMenuBar1);
        GridLayout layout = new GridLayout(3, 1, 5, 10);
        this.getContentPane().setLayout(layout);
        this.add(alignmentPanel1);
        this.add(alignmentPanel2);
        this.add(alignmentPanel3);
        this.pack();
    }

    private void submitButtonActionPerformed(ActionEvent evt) {
        if (this.cache.isEmpty()) {
            try {
                Main.log("ToolSelection", "No Cache Set!");
            } catch (ArrayIndexOutOfBoundsException e) {
                Main.log("ToolSelection", "No Cache Set!");
            }
        } else if (this.selectionBox.getSelectedIndex() == 0) {
            try {
                (new ItemSelection(this.cache)).setVisible(true);
                Main.log("ToolSelection", "ItemSelection Started");
            } catch (IOException var4) {
                Main.log("ToolSelection", "No Cache Set!");
            }
        } else {
            Main.log("ToolSelection", "No Tool Selected!");
        }
    }

    private void loadCacheButtonHandler(ActionEvent evt) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(1);
        if (evt.getSource() == this.loadCacheButton) {
            int returnVal = fc.showOpenDialog(this);
            if (returnVal == 0) {
                File file = fc.getSelectedFile();
                this.cache = file.getPath() + "/";
            }
        }
    }

    private void exitButtonActionPerformed(ActionEvent evt) {
        JDialog.setDefaultLookAndFeelDecorated(true);
        int response = JOptionPane.showConfirmDialog(null, "Do you want to continue?", "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (response == 0) {
            System.exit(0);
        }
    }
}
