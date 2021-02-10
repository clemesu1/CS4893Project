package com.clemesu1.networkchat;

import java.io.*;
import java.net.*;

public class Client {

    private String name, password, address;
    private int port;
    private DatagramSocket socket;
    private Socket clientSocket;
    private InetAddress ip;
    private Thread connect, send;
    private int ID = -1;

    private ObjectInputStream input;
    private ObjectOutputStream output;

    public Client(String name, String password, String address, int port) {
        this.name = name;
        this.password = password;
        this.address = address;
        this.port = port;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }


    public boolean openConnection(String address, int port) {
        try {
            socket = new DatagramSocket();
            ip = InetAddress.getByName(address);
            connect = new Thread(() -> {
                try {
                    clientSocket = new Socket(address, port);
                    output = new ObjectOutputStream(clientSocket.getOutputStream());
                    input = new ObjectInputStream(clientSocket.getInputStream());
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            });
            connect.start();
        } catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void send(final byte[] data) {
        send = new Thread("Send") {
            @Override
            public void run() {
                DatagramPacket packet = new DatagramPacket(data, data.length, ip, port);
                try {
                    socket.send(packet);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        };
        send.start();
    }

    public String receive() {
        byte[] data = new byte[1024];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        try {
            socket.receive(packet);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        String message = new String(packet.getData());
        return message;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public int getID() {
        return ID;
    }

    public void close() {
        new Thread(() -> {
            synchronized (socket) {
                socket.close();
                try {
                    clientSocket.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }).start();
    }

    public void uploadFile(File file) {
        try {
            FileInputStream fileIn = new FileInputStream(file);
            int fileSize = (int) file.length();
            byte[] fileData = new byte[fileSize];
            fileIn.read(fileData);
            fileIn.close();
            output.writeObject(fileData);
            output.flush();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void downloadFile(String fileLocation) {
        try {
            // Read file into byte array.
            byte[] byteArray = (byte[]) input.readObject();
            // Create file output stream for received audio file.
            FileOutputStream fileStream = new FileOutputStream(fileLocation);

            // Write received data into file.
            fileStream.write(byteArray);

            fileStream.close();
        } catch (IOException | ClassNotFoundException ioException) {
            ioException.printStackTrace();
        }
    }
}
