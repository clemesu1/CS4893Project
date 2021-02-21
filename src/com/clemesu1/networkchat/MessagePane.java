package com.clemesu1.networkchat;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;

public class MessagePane extends JPanel {

    private Client client;
    private String username;

    private DefaultListModel<String> listModel = new DefaultListModel<>();
    private JList<String> messageList = new JList<>(listModel);
    private JTextField inputField = new JTextField();

    public MessagePane(Client client, String username) {
        this.client = client;
        this.username = username;

        setLayout(new BorderLayout());
        add(new JScrollPane(messageList), BorderLayout.CENTER);
        add(inputField, BorderLayout.SOUTH);

        inputField.addActionListener(e -> {
            String message = inputField.getText();
            listModel.addElement(client.getName() + ": " + message);
            message = "/mu/" + username + "/n/" + client.getName() + ": " + message + "/e/";
            inputField.setText("");
            message = AES.encrypt(message, ClientUI.sendKey);
            client.send(message.getBytes(StandardCharsets.UTF_8));
        });
    }

    public void receiveMessage(String messageReceived) {
        listModel.addElement(messageReceived);
    }

}
