package com.clemesu1.networkchat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Login extends JFrame {
    private JPanel contentPane;
    private JTextField txtName;
    private JTextField txtIPAddress;
    private JTextField txtPort;
    private JButton btnLogin;


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


        btnLogin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String name = txtName.getText();
                String address = txtIPAddress.getText();
                int port = Integer.parseInt(txtPort.getText());
                handleLogin(name, address, port);
            }
        });
    }

    private void handleLogin(String name, String address, int port) {
        dispose();
        new ClientUI(name, address, port);
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
