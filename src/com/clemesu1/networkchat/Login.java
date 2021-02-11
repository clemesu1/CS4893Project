package com.clemesu1.networkchat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class Login extends JFrame {
    private JPanel contentPane;
    private JTextField txtName;
    private JPasswordField txtPassword;
    private JTextField txtIPAddress;
    private JTextField txtPort;
    private JButton btnLogin;
    private JLabel lblUsername;
    private JLabel lblPassword;
    private JLabel lblAddress;
    private JLabel lblIPEx;
    private JLabel lblPort;
    private JLabel lblPortEx;
    private JButton btnRegister;

    private static final String sendKey = "thisisthekey";
    private static final String receiveKey = "thisistheotherkey";

    public Login() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        setTitle("Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setBounds(100, 100, 300, 380);
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);

        lblUsername.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));
        lblPassword.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));
        lblAddress.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));
        lblIPEx.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));
        lblPort.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));
        lblPortEx.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));
        btnLogin.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));
        btnRegister.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));
        txtIPAddress.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));
        txtPassword.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));
        txtName.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));
        txtPort.setFont(UIManager.getLookAndFeelDefaults().getFont("Label.font"));

        btnLogin.addActionListener(e -> {
            String username = txtName.getText();
            String password = new String(txtPassword.getPassword());
            String address = txtIPAddress.getText();
            int port;
            if (!txtPort.getText().equals("")) {
                port = Integer.parseInt(txtPort.getText());
                handleLogin(username, password, address, port);
            }
        });

        btnRegister.addActionListener(e -> {
            String username = txtName.getText();
            String password = new String(txtPassword.getPassword());
            String address = txtIPAddress.getText();
            int port;
            if (!txtPort.getText().equals("")) {
                port = Integer.parseInt(txtPort.getText());
                handleRegister(username, password, address, port);
            }
        });
    }

    private void handleLogin(String username, String password, String address, int port) {
        new ClientUI(username, password, address, port, true, this);
    }

    private void handleRegister(String username, String password, String address, int port) {
        new ClientUI(username, password, address, port, false, this);
        JOptionPane.showConfirmDialog(null, "Account has been registered!", "Register Account", JOptionPane.OK_OPTION);
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                Login frame = new Login();
                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
