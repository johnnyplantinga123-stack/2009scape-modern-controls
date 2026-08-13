package com.editor;

import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;

public class Console extends JFrame {
    private static final long serialVersionUID = -4693540915136770583L;
    public static JTextArea output;

    public Console() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.setTitle("Console");
        this.setResizable(false);
        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.setLocationRelativeTo(null);
        this.initComponents();
        Main.log("Console", "Console Started.");
    }

    private void initComponents() {
        JScrollPane jScrollPane1 = new JScrollPane();
        output = new JTextArea();
        JMenuBar jMenuBar1 = new JMenuBar();
        JMenu jMenu1 = new JMenu();
        JMenuItem jMenuItem1 = new JMenuItem();
        JMenuItem jMenuItem2 = new JMenuItem();
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        output.setEditable(false);
        output.setColumns(20);
        output.setLineWrap(true);
        output.setRows(5);
        jScrollPane1.setViewportView(output);
        jMenu1.setText("File");
        jMenuItem1.setText("Close Console");
        jMenuItem1.addActionListener(Console.this::jMenuItem1ActionPerformed);
        jMenu1.add(jMenuItem1);
        jMenuItem2.setText("Exit Program");
        jMenuItem2.addActionListener(Console.this::jMenuItem2ActionPerformed);
        jMenu1.add(jMenuItem2);
        jMenuBar1.add(jMenu1);
        this.setJMenuBar(jMenuBar1);
        GroupLayout layout = new GroupLayout(this.getContentPane());
        this.getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING).addComponent(jScrollPane1, -1, 618, 32767));
        layout.setVerticalGroup(layout.createParallelGroup(Alignment.LEADING).addComponent(jScrollPane1, -1, 240, 32767));
        this.pack();
    }

    private void jMenuItem2ActionPerformed(ActionEvent evt) {
        System.exit(0);
    }

    private void jMenuItem1ActionPerformed(ActionEvent evt) {
        this.dispose();
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> (new Console()).setVisible(true));
    }

    private static void updateTextArea(final String text) {
        SwingUtilities.invokeLater(() -> Console.output.append(text));
    }

    public static void redirectSystemStreams() {
        OutputStream out = new OutputStream() {
            public void write(int b) {
                Console.updateTextArea(String.valueOf((char)b));
            }

            public void write(byte[] b, int off, int len) {
                Console.updateTextArea(new String(b, off, len));
            }

            public void write(byte[] b) {
                this.write(b, 0, b.length);
            }
        };
        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));
    }
}
