package com.clemesu1.networkchat.server;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Server implements Runnable {

    private final String FILE_LOCATION = "C:\\Users\\coliw\\OneDrive\\Documents\\FileServer\\";
    private final String USER_DATA_LOCATION = "C:\\Users\\coliw\\OneDrive\\Documents\\GitHub\\CS4893Project\\src\\com\\clemesu1\\networkchat\\server\\user_data.csv";

    private List<ServerClient> clients = new ArrayList<>();
    private List<File> files = new ArrayList<>(Arrays.asList((new File(FILE_LOCATION).listFiles())));
    private List<Integer> clientResponse = new ArrayList<>();

    private Map<String, String> userPasswordMap = new HashMap<>();
    private Map<String, Integer> userSaltMap = new HashMap<>();
    private Map<String, Integer> userIDMap = new HashMap<>();

    private File userData = new File(USER_DATA_LOCATION);

    private DatagramSocket socket;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private final int port;
    private boolean running = false;
    private Thread run, manage, send, receive, update;
    private boolean raw = false;
    private final int MAX_ATTEMPTS = 10;
    private ObjectInputStream input;
    private ObjectOutputStream output;

    private static final String receiveKey = "thisisthekey";
    private static final String sendKey = "thisistheotherkey";
    private static SecretKeySpec secretKey;
    private static byte[] key;

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
        readUserData();
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

    private void readUserData() {
        if (userData.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(userData))) {

                // Read CSV header
                reader.readLine();

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] data = line.split(",");

                    userPasswordMap.put(data[0], data[1]);
                    userSaltMap.put(data[0], Integer.parseInt(data[2]));
                    userIDMap.put(data[0], Integer.parseInt(data[3]));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try (PrintWriter writer = new PrintWriter(new FileWriter(userData, true), true)) {
                writer.append("Username");
                writer.append(",");
                writer.append("Password");
                writer.append(",");
                writer.append("Salt");
                writer.append(",");
                writer.append("ID");
                writer.append("\n");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
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
                    try {
                        Thread.sleep(20000);
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
    private void updateUsers() {
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
                        // Ignore.
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
            send(message, client.address, client.port);
        }
    }

    private void sendToUser(String message) {
        String toUser = message.split("/mu/|/n/|/e/")[1];
        String text = message.split("/mu/|/n/|/e/")[2];
        System.out.println(text + " -> " + toUser);

        for (ServerClient client : clients) {
            if (client.getName().equals(toUser)) {
                send(message, client.address, client.port);
                break;
            }
        }
    }

    private void sendMessage(String message, InetAddress address, int port) {
        message += "/e/";
        send(message, address, port);
    }

    private void send(final String message, final InetAddress address, final int port) {
        send = new Thread("Send") {
            public void run() {
                String text = encrypt(message, sendKey);
                final byte[] data = text.getBytes(StandardCharsets.UTF_8);
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

    private byte[] fromHexString(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i=0; i<len; i+=2) {
            data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private void process(DatagramPacket packet) {
        byte[] data = packet.getData();
        String string = new String(data);

        // Decrypt received packet.
        string = decrypt(string, receiveKey);

        if (raw) System.out.println(string);
        if (string.startsWith("/c/")) {
            /* Connection Packet */

            // Get username, password and user command.
            String name = string.split("/c/|/p/|/b/|/e/")[1];
            String password = string.split("/c/|/p/|/b/|/e/")[2];
            String command =  string.split("/c/|/p/|/b/|/e/")[3];

            int ID = 0;
            if (command.equals("register")) {
                if (!isUsernameTaken(name)) {
                    ID = Math.abs((int) UUID.randomUUID().getLeastSignificantBits());
                    registerAccount(name, password, ID);
                }
            } else if (command.equals("login")) {
                if (!isLoginCorrect(name, password)) {
                    // Invalid username.
                    String error = "/error/Login";
                    sendMessage(error, packet.getAddress(), packet.getPort());
                } else if (!isPasswordCorrect(name, password)) {
                    // Invalid password.
                    String error = "/error/Password";
                    sendMessage(error, packet.getAddress(), packet.getPort());
                } else {
                    // No errors detected. Attempting login.

                    // Get ID from ID hashmap.
                    ID = userIDMap.get(name);

                    // Send user connection alert.
                    System.out.println("User " + name + " (" + ID + ") @ " + packet.getAddress() + ":" + packet.getPort() + " connected.");
                    String online = "/m/User " + name + " has connected./e/";
                    sendToAll(online);

                    // Add client to list of clients.
                    clients.add(new ServerClient(name, packet.getAddress(), packet.getPort(), ID));

                    // send ID to user
                    String id = "/c/" + ID;
                    sendMessage(id, packet.getAddress(), packet.getPort());

                    // Update users and files.
                    updateUsers();
                    updateFiles();
                }
            }
        } else if (string.startsWith("/m/")) {
            sendToAll(string);
        } else if (string.startsWith("/d/")) {
            String id = string.split("/d/|/e/")[1];
            disconnect(Integer.parseInt(id), true);
            updateUsers();
        } else if (string.startsWith("/i/")) {
            clientResponse.add(Integer.valueOf(string.split("/i/|/e/")[1]));
        } else if (string.startsWith("/up/")) {
            String fileName = string.split("/up/|/e/")[1];
            receiveFile(fileName);
        } else if (string.startsWith("/down/")) {
            String fileName = string.split("/down/|/e/")[1];
            sendFile(fileName);
        } else if (string.startsWith("/mu/")) {
            // /mu/<Username>/n/<Message>/e/
            sendToUser(string);
        } else {
            System.out.println(string);
        }
    }

    public boolean isUsernameTaken(String username) {
        return userPasswordMap.containsKey(username);
    }

    private void registerAccount(String username, String password, int ID) {
        int salt = getRandomSalt();
        String saltedPassword = password + salt;
        String passwordHash = getSimpleHash(saltedPassword);
        userPasswordMap.put(username, passwordHash);
        userSaltMap.put(username, salt);
        userIDMap.put(username, ID);

        writeUserData(username, passwordHash, salt, ID);
    }

    private void writeUserData(String username, String passwordHash, int salt, int ID) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(userData, true), true)) {
            List<String> rowData = Arrays.asList(username, passwordHash, String.valueOf(salt), String.valueOf(ID));
            writer.append(String.join(",", rowData));
            writer.append("\n");
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private boolean isPasswordCorrect(String username, String password) {
        int salt = userSaltMap.get(username);
        String saltedPassword = password + salt;
        String passwordHash = getSimpleHash(saltedPassword);
        String storedPasswordHash = userPasswordMap.get(username);

        return passwordHash.equals(storedPasswordHash);
    }

    public boolean isLoginCorrect(String username, String password) {
        // Username is not registered.
        if (!userPasswordMap.containsKey(username) || !userSaltMap.containsKey(username)) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Returns a random number between 0 and 1000.
     */
    private int getRandomSalt() {
        return (int) (Math.random() * 1000);
    }

    /**
     * https://www.geeksforgeeks.org/sha-512-hash-in-java/
     * Returns a hash for the given password.
     */
    private String getSimpleHash(String password) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-512");
            byte[] messageDigest = sha.digest(password.getBytes(StandardCharsets.UTF_8));
            BigInteger no = new BigInteger(1, messageDigest);
            String hashText = no.toString(16);
            while (hashText.length() < 32) {
                hashText = "0" + hashText;
            }
            return hashText;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
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

        String message = "", send = "";
        if (status) {
            message = "User " + c.name + " (" + c.getID() + ") @ " + c.address.toString() + ":" + c.port + " disconnected.";
            send = "User " + c.name + " has disconnected.";
        } else {
            message = "User " + c.name + " (" + c.getID() + ") @ " + c.address.toString() + ":" + c.port + " timed out.";
            send = "User " + c.name + " has timed out.";

        }
        System.out.println(message);
        sendToAll("/m/" + send + "/e/");
    }

    private static void setKey(String keyString) {
        try {
            key = keyString.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            secretKey = new SecretKeySpec(key, "AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private static String encrypt(String message, String secret) {
        try {
            setKey(secret);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getMimeEncoder().encodeToString(cipher.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | BadPaddingException | IllegalBlockSizeException e) {
            System.err.println("Error while encrypting: " + e);
        }
        return null;
    }

    private static String decrypt(String message, String secret) {
        try {
            setKey(secret);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getMimeDecoder().decode(message)));
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            System.err.println("Error while decrypting: " + e);
        }
        return null;
    }
}
