package com.clemesu1.networkchat.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Server implements Runnable {

    private final String FILE_LOCATION = "C:\\Users\\coliw\\Documents\\FileServer\\";

    private List<ServerClient> clients = new ArrayList<>();
    private List<File> files = new ArrayList<File>(Arrays.asList((new File(FILE_LOCATION).listFiles())));
    private List<Integer> clientResponse = new ArrayList<>();

    private DatagramSocket socket;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private final int port;
    private boolean running = false;
    private Thread run, manage, send, receive, update;
    private boolean raw = false;
    private final int MAX_ATTEMPTS = 10;
    private FileOutputStream fileOut;
    private ObjectInputStream input;
    private ObjectOutputStream output;

    public Server(int port) {
        this.port = port;
        try {
            socket = new DatagramSocket(port);
            serverSocket = new ServerSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        run = new Thread(this, "Server");
        run.start();
    }

    public void run() {
        running = true;
        System.out.println("Server started on port " + port + "...");
        manageClients();
        if (clientSocket == null) {
            try {
                clientSocket = serverSocket.accept();
                output = new ObjectOutputStream(clientSocket.getOutputStream());
                input = new ObjectInputStream(clientSocket.getInputStream());
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
        receive();

        Scanner scan = new Scanner(System.in);
        while (running) {
            String text = scan.nextLine();
            if (!text.startsWith("/")) {
                sendToAll("/m/Server: " + text + "/e/");
                continue;
            } else {
                text = text.substring(1);
                if (text.equalsIgnoreCase("raw")) {
                    if (raw) System.out.println("Raw mode disabled.");
                    else System.out.println("Raw mode enabled.");
                    raw = !raw;
                } else if (text.equalsIgnoreCase("clients")) {
                    System.out.println("Clients:\n=========================================================");
                    for (ServerClient c : clients) {
                        System.out.println(c.name + "(" + c.getID() + "): " + c.address.toString() + ":" + c.port);
                    }
                    System.out.println("=========================================================");
                } else if (text.startsWith("kick")) {
                    String name = text.split(" ")[1];
                    int id = -1;
                    boolean number = true;
                    try {
                        id = Integer.parseInt(name);
                    } catch (NumberFormatException e) {
                        number = false;
                    }
                    if (number) {
                        boolean exists = false;
                        for (ServerClient client : clients) {
                            if (client.getID() == id) {
                                exists = true;
                                break;
                            }
                        }
                        if (exists) disconnect(id, true);
                        else System.out.println("User " + id + "doesn't exist! Check ID number.");
                    } else {
                        for (ServerClient client : clients) {
                            if (name.equals(client.name)) {
                                disconnect(id, true);
                                break;
                            }
                        }
                    }
                } else if (text.equals("help")) {
                    printHelp();

                } else if (text.equals("quit")) {
                    quit();
                } else {
                    System.out.println("Unknown Command.");
                    printHelp();
                }
            }
        }
        scan.close();
    }

    private void printHelp() {
        System.out.println("Here is a list of all available commands:");
        System.out.println("=========================================");
        System.out.println("/raw - Enables raw mode.");
        System.out.println("/clients - Shows all connected clients.");
        System.out.println("/kick [user ID or username] - Kicks a user.");
        System.out.println("/help - Shows all available commands.");
        System.out.println("/quit - Shuts down the server.");
        System.out.println("=========================================");
    }

    private void manageClients() {
        manage = new Thread("Manage") {
            public void run() {
                while (running) {
                    sendToAll("/i/server");
                    sendStatus();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    for (ServerClient client : clients) {
                        ServerClient c = client;
                        if (!clientResponse.contains(client.getID())) {
                            if (c.attempt > MAX_ATTEMPTS) {
                                disconnect(c.getID(), false);
                            } else {
                                c.attempt++;
                            }
                        } else {
                            clientResponse.remove(new Integer(c.getID()));
                            c.attempt = 0;
                        }
                    }
                }
            }
        };
        manage.start();
    }
    private void sendStatus() {
        if (clients.size() <= 0) return;
        String users = "/u/";
        for (int i=0; i<clients.size()-1; i++) {
            users += clients.get(i).name + "/n/";
        }
        users += clients.get(clients.size() - 1).name + "/e/";
        sendToAll(users);
    }

    private void receive() {
        receive = new Thread("Receive") {
            public void run() {
                while (running) {
                    byte[] data = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(data, data.length);
                    try {
                        socket.receive(packet);
                    } catch (SocketException e) {
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    process(packet);
                }
            }
        };
        receive.start();
    }

    private void sendToAll(String message) {
        if (message.startsWith("/m/")) {
            String text = message.substring(3);
            text = text.split("/e/")[0];
            System.out.println(text);
        }
        for (ServerClient client : clients) {
            send(message.getBytes(StandardCharsets.UTF_8), client.address, client.port);
        }
    }

    private void send(String message, InetAddress address, int port) {
        message += "/e/";
        send(message.getBytes(StandardCharsets.UTF_8), address, port);
    }

    private void send(final byte[] data, final InetAddress address, final int port) {
        send = new Thread("Send") {
            public void run() {
                DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                try {
                    socket.send(packet);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        };
        send.start();
    }

    private void process(DatagramPacket packet) {
        byte[] data = packet.getData();
        String string = new String(data);
        if (raw) System.out.println(string);
        if (string.startsWith("/c/")) {
            String name = string.split("/c/|/e/")[1];
            UUID uuid = UUID.randomUUID();
            int ID = Math.abs((int) uuid.getLeastSignificantBits());
            System.out.println("User " + name + " (" + ID + ") @ " + packet.getAddress() + ":" + packet.getPort() + " connected.");
            String online = "/m/User " + name + " has connected./e/";
            sendToAll(online);
            clients.add(new ServerClient(name, packet.getAddress(), packet.getPort(), ID));
            String id = "/c/" + ID;
            send(id, packet.getAddress(), packet.getPort());
            updateFiles();
        } else if (string.startsWith("/m/")) {
            sendToAll(string);
        } else if (string.startsWith("/d/")) {
            String id = string.split("/d/|/e/")[1];
            disconnect(Integer.parseInt(id), true);
        } else if (string.startsWith("/i/")) {
            clientResponse.add(Integer.valueOf(string.split("/i/|/e/")[1]));
        } else if (string.startsWith("/up/")) {
            String fileName = string.split("/up/|/e/")[1];
            receiveFile(fileName);
        } else if (string.startsWith("/down/")) {
            String fileName = string.split("/down/|/e/")[1];
            sendFile(fileName);
        } else {
            System.out.println(string);
        }
    }

    private void updateFiles() {
        update = new Thread(() -> {
            if (files.size() <= 0) return;

            File directory = new File(FILE_LOCATION);
            File[] directoryFiles = directory.listFiles();
            String fileString = "/f/";

            for (int i=0; i<files.size()-1; i++) {
                fileString += files.get(i).getName() + "/n/";
            }
            fileString += files.get(files.size() - 1).getName() + "/e/";
            sendToAll(fileString);
        });
        update.start();
    }

    private void sendFile(String fileName) {
        try {
            FileInputStream fileIn = new FileInputStream(FILE_LOCATION + fileName);
            // Get the length of the requested file.
            int fileSize = (int) (new File(FILE_LOCATION + fileName)).length();

            System.out.println("File Requested: " + fileName + " (" + fileSize + " bytes)");

            // Create a byte array for the requested file.
            byte[] byteArray = new byte[fileSize];

            // Read the file into the byte array.
            fileIn.read(byteArray);
            fileIn.close();

            // Write the byte array to the socket output stream.
            output.writeObject(byteArray);
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void receiveFile(String fileName) {
        try {
            byte[] fileData = (byte[]) input.readObject();
            FileOutputStream fileOut = new FileOutputStream(FILE_LOCATION+fileName);
            fileOut.write(fileData);
            fileOut.close();
            File receivedFile = new File(FILE_LOCATION+fileName);
            System.out.println("File Received: " + receivedFile.getName() + " (" + receivedFile.length() + " bytes)");
            files.add(receivedFile);
            updateFiles();
        } catch (IOException | ClassNotFoundException ioException) {
            ioException.printStackTrace();
        }
    }

    private void quit() {
        for (ServerClient client : clients) {
            disconnect(client.getID(), true);
        }
        running = false;
    }

    private void disconnect(int id, boolean status) {
        ServerClient c = null;
        boolean existed = false;
        for (ServerClient client : clients) {
            if (client.getID() == id) {
                c = client;
                clients.remove(client);
                existed = true;
                break;
            }
        }

        if (!existed) return;

        String message = "";
        if (status) {
            message = "User " + c.name + " (" + c.getID() + ") @ " + c.address.toString() + ":" + c.port + " disconnected.";
        } else {
            message = "User " + c.name + " (" + c.getID() + ") @ " + c.address.toString() + ":" + c.port + " timed out.";
        }
        System.out.println(message);
    }
}
