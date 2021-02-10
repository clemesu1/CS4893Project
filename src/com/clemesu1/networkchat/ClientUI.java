package com.clemesu1.networkchat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ClientUI extends JFrame implements Runnable {
    private JPanel contentPane;
    private JTextArea txtHistory;
    private JButton btnSend;
    private JTextField txtMessage;
    private JList<String> userList;
    private JTabbedPane tabbedPane1;
    private JPanel tabChatroom;
    private JButton btnUpload;
    private JButton btnDownload;
    private JButton btnBrowse;
    private JList<String> fileList;
    private JPanel interfacePanel;
    private JPanel mediaPanel;
    private JLabel lblFileSelected;
    private JPopupMenu popupMenu;
    private JMenuItem directMessage;
    private MessagePane messagePane;

    private DefaultCaret caret;
    private Thread run, listen;
    private Client client;

    private boolean running = false;

    private File selectedFile;

    public ClientUI(String name, String address, int port) {
        setTitle("Chat Client");
        client = new Client(name, address, port);

        boolean connect = client.openConnection(address, port);
        if (!connect) {
            System.err.println("Connection failed!");
            console("Connection failed!");
        }
        createWindow();
        console("Attempting to connect to " + address + ":" + port + ", user: " + name);
        String connection = "/c/" + name + "/e/";
        client.send(connection.getBytes(StandardCharsets.UTF_8));
        running = true;
        run = new Thread(this, "Running");
        run.start();
    }

    private void createWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 480);
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);

        txtHistory.setMargin(new Insets(0, 5, 5, 5));
        txtHistory.setEditable(false);

        btnUpload.setEnabled(false);

        caret = (DefaultCaret) txtHistory.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        popupMenu = new JPopupMenu("Message");
        directMessage = new JMenuItem("Direct Message");
        popupMenu.add(directMessage);

        txtMessage.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    send(txtMessage.getText(), true);
                }
            }
        });

        btnSend.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                send(txtMessage.getText(), true);
            }
        });

        btnBrowse.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleBrowse();
            }
        });

        btnUpload.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleUpload();
            }
        });

        btnDownload.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleDownload();
            }
        });

        fileList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                lblFileSelected.setText(fileList.getSelectedValue());
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                String disconnect = "/d/" + client.getID() + "/e/";
                send(disconnect, false);
                running = false;
                client.close();
            }
        });
        
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    userList.setSelectedIndex(userList.locationToIndex(e.getPoint())); // Highlight right-clicked value in list.
                    popupMenu.show(e.getComponent(), e.getX(), e.getY()); // Show popup menu
                }
            }
        });

        directMessage.addActionListener(e -> {
            String username = userList.getSelectedValue();
            if (!client.getName().equals(username)) {
                messagePane = new MessagePane(client, username);
                JFrame f = new JFrame("Message: " + username);
                f.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                f.setSize(500, 500);
                f.getContentPane().add(messagePane, BorderLayout.CENTER);
                f.setVisible(true);
            }
        });

        setVisible(true);
        txtMessage.requestFocusInWindow();
    }

    public void updateUsers(String[] users) {
        userList.setListData(users);
    }

    public void updateFiles(String[] files) {
        fileList.setListData(files);
    }

    public void handleUpload() {
        String fileName = this.selectedFile.getName();
        int choice = JOptionPane.showConfirmDialog(null, "Upload " + fileName + "?", "Upload File", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            String command = "/up/" + this.selectedFile.getName() + "/e/";
            client.send(command.getBytes(StandardCharsets.UTF_8));
            client.uploadFile(this.selectedFile);
        }
    }

    public void handleDownload() {
        if (fileList.getSelectedValue() != null) {
            String fileName = "/down/" + lblFileSelected.getText() + "/e/";
            client.send(fileName.getBytes(StandardCharsets.UTF_8));
            String fileLocation = "";

            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File(lblFileSelected.getText()));
            int choice = fc.showSaveDialog(this);
            if (choice == JFileChooser.APPROVE_OPTION) {
                fileLocation = fc.getSelectedFile().getAbsolutePath();
                client.downloadFile(fileLocation);
                JOptionPane.showConfirmDialog(this, "File Successfully downloaded.", "Success!", JOptionPane.OK_OPTION);
            }
        }
    }

    public void handleBrowse() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Media Files", "wav", "mp3", "png", "jpg"));
        fc.setCurrentDirectory(new File(System.getProperty("user.name")));
        fc.setAcceptAllFileFilterUsed(false);
        int choice = fc.showOpenDialog(this);
        if (choice == JFileChooser.APPROVE_OPTION) {
            this.selectedFile = fc.getSelectedFile();
            lblFileSelected.setText(this.selectedFile.getName());
            btnUpload.setEnabled(true);
        }
    }

    public void send(String message, boolean text) {
        if (message.equals("")) return;
        if (text) {
            message = "/m/" + client.getName() + ": " + message + "/e/";
            txtMessage.setText("");
        }
        client.send(message.getBytes(StandardCharsets.UTF_8));
    }

    public void listen() {
        listen = new Thread("Listen") {
            @Override
            public void run() {
                while (running) {
                    String message = client.receive();
                    if (message.startsWith("/c/")) {
                        client.setID(Integer.parseInt(message.split("/c/|/e/")[1]));
                        console("Successfully connected to server! ID: " + client.getID());
                    } else if (message.startsWith("/m/")) {
                        String text = message.substring(3);
                        text = text.split("/e/")[0];
                        console(text);
                    } else if (message.startsWith("/i/")) {
                        String text = "/i/" + client.getID() + "/e/";
                        send(text, false);
                    } else if (message.startsWith("/u/")) {
                        String[] u = message.split("/u/|/n/|/e/");
                        updateUsers(Arrays.copyOfRange(u, 1, u.length - 1));
                    } else if (message.startsWith("/f/")) {
                        String[] f = message.split("/f/|/n/|/e/");
                        updateFiles(Arrays.copyOfRange(f, 1, f.length - 1));
                    } else if (message.startsWith("/mu/")) {
                        String messageReceived = message.split("/mu/|/n/|/e/")[2];
                        messagePane.receiveMessage(messageReceived);
                    } else {
                        System.out.println(message.substring(3));
                    }
                }
            }
        };
        listen.start();
    }

    public void console(String message) {
        txtHistory.append(message + "\n\r");
    }

    @Override
    public void run() {
        listen();
    }
}
