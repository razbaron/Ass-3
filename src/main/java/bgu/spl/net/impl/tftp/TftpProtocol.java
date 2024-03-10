package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    TftpServerUsers loggedInUsers;
    Queue<byte[]> responseToUserQueue;
    Queue<byte[]> incomingDataQueue;
    String userName;
    String pathToDir;
    String fileNameInProcess;
    boolean needToBcast;
    int connectionId;
    Connections<byte[]> connections;
    byte[] response;
    boolean shouldTerminate;

    public TftpProtocol(TftpServerUsers users){
        loggedInUsers = users;
    }
    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.shouldTerminate = false;
        this.needToBcast = false;
        this.connectionId = connectionId;
        this.connections = connections;
        this.pathToDir = "Files";
    }

    @Override
    public void process(byte[] message) {
        OpcodeOperations opcodeOp = new OpcodeOperations(message[1]);
        if (Opcode.UNDEFINED.equals(opcodeOp.opcode) || Opcode.BCAST.equals(opcodeOp.opcode)){
            generateError(4, "Illegal TFTP operation");
        }
        if (!(opcodeOp.opcode.equals(Opcode.LOGRQ) || loggedInUsers.isUserLoggedIn(userName))){
            generateError(6, "User not logged in");
        } else {
            switch (opcodeOp.opcode) {
                case LOGRQ:
                    userLogin(message);
                    break;
                case DELRQ:
                    deleteFile(message);
                    break;
                case RRQ:
                    processReadRequest(message);
                    break;
                case WRQ:
                    prepareToReadFromUser(message);
                    break;
                case DIRQ:
                    getDir();
                    break;
                case DATA:
                    collectDataFromUser(message);
                    break;
                case ACK:
                    processAck(message);
                    break;
                case ERROR:
                    processError(message);
                    break;
                case DISC:
                    disconnect();
                    return;
            }
        }
        forwardResponseToUser();
        bcastUsersIfNeeded(message);
    }

    private void forwardResponseToUser() {
        byte[] responseToUser = getResponseToUser();
        if (responseToUser != null) {
            connections.send(connectionId, responseToUser);
        }
    }

    private void userLogin(byte[] message) {
        String name = extractStringFromMessage(message);
        if (loggedInUsers.isUserLoggedIn(name)){
            generateError(7, "User already logged in");
        } else {
            userName = name;
            loggedInUsers.logInUser(userName, connectionId);
            generateGeneralAck();
        }
    }

    private void deleteFile(byte[] message) {
        String fileToDelete = extractStringFromMessage(message);
        if (lookForFileWithError(fileToDelete)){
            File file = getMeThisFile(fileToDelete);
            if (file.delete()) {
                needToBcast = true;
                fileNameInProcess = fileToDelete;
//                bcastUsers(0, fileToDelete);
                generateGeneralAck();
            } else {
                generateError(2, "Access violation – File cannot be written, read or deleted.");
            }
        }
    }

    private void processReadRequest(byte[] message) {
        String fileToRead = extractStringFromMessage(message);
        if (lookForFileWithError(fileToRead)){
//            connections.send(connectionId, response);
            byte[] fileData = getDataOfFile(fileToRead);
            createDataPackets(fileData);
        }
    }

    private void generateError(int errorCode, String message) {
        OpcodeOperations opcodeOperations = new OpcodeOperations(Opcode.ERROR);
        byte[] errorPrefix = opcodeOperations.getInResponseFormat((byte) errorCode);
        byte[] errorMessage = convertStringToUtf8(message);
        response = new byte[errorMessage.length + errorPrefix.length];
        System.arraycopy(errorPrefix, 0, response, 0, errorPrefix.length);
        System.arraycopy(errorMessage, 0, response, errorPrefix.length, errorMessage.length);
    }

    private File getMeThisFile(String fileName) {
        return new File(pathToDir + File.separator + fileName);
    }

    private byte[] getDataOfFile(String name) {
        File file = getMeThisFile(name);
        byte[] data = new byte[(int) file.length()];
        try {
            FileInputStream fis = new FileInputStream(file);
            fis.read(data);
            fis.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return data;
    }

    private void prepareToReadFromUser(byte[] message) {
        fileNameInProcess = extractStringFromMessage(message);
        if (fileWithThisNameExist(fileNameInProcess)){
            generateError(5, "File already exists");
        } else {
            incomingDataQueue = new LinkedList<>();
            generateGeneralAck();
        }
    }

    private void generateGeneralAck() {
        OpcodeOperations op = new OpcodeOperations(Opcode.ACK);
        response = op.getGeneralAck();
    }

    private boolean lookForFileWithError(String fileName) {
        boolean ans = fileWithThisNameExist(fileName);
        if (!ans){
            generateError(1, "File not found");
        }
        return ans;
    }

    private boolean fileWithThisNameExist(String fileName) {
        File file = getMeThisFile(fileName);
        return file.exists();
    }

    private String extractStringFromMessage(byte[] message) {
        return convertUtf8ToString(Arrays.copyOfRange(message,2, message.length)); // Ignore first two bytes of Opcode
    }

    private String convertUtf8ToString(byte[] subArr) {
        return new String(subArr, StandardCharsets.UTF_8);
    }

    private byte[] convertStringToUtf8(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    private void disconnect() {
        generateGeneralAck();
        forwardResponseToUser();
        loggedInUsers.logOutUser(connectionId);
        connections.disconnect(connectionId);
        shouldTerminate = true;
    }

    private void processError(byte[] message) {
        System.out.println(extractStringFromMessage(message)); //For human use
    }

    private void processAck(byte[] message) {
        if (ackForPacket(message)){
            if (ackPacketSuccesses(message)){
                responseToUserQueue.remove(); //Packet was sent and received
                response = responseToUserQueue.peek();
            } else {
                throw new RuntimeException("The ACK packet that was received does not match the last packet that was send, received ACK for the packet number " + extractDataPacketNumber(message));
            }
        }
    }

    private int extractAckPacketNumber(byte[] message) {
        return ((message[2] & 0xFF) << 8) | (message[3] & 0xFF);
    }

    private boolean ackPacketSuccesses(byte[] message) {
        return extractAckPacketNumber(message) == extractDataPacketNumber(responseToUserQueue.peek());
    }

    private boolean ackForPacket(byte[] message) {
        return extractAckPacketNumber(message) != 0;
    }

    private void collectDataFromUser(byte[] message) {
        incomingDataQueue.add(message);
        generateAckReceived(message);
        if (isLastPacket(message)){
            completeIncomingFile();
            incomingDataQueue = null;
        }
    }

    private void completeIncomingFile() {
        byte[] readyToWrite = generateBytesFromData();
        try {
            File file = getMeThisFile(fileNameInProcess);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(readyToWrite);
            fileOutputStream.close();
            needToBcast = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void bcastUsersIfNeeded(byte[] message) {
        if (needToBcast){
            byte[] fileNameBytes = convertStringToUtf8(fileNameInProcess);
            byte[] prefix = new byte[3];
            OpcodeOperations opToSend = new OpcodeOperations(Opcode.BCAST);
            System.arraycopy(opToSend.getInResponseFormat(), 0, prefix, 0, 2);
            OpcodeOperations opFromMessage = extractOpFromMessage(message);
            int i = opFromMessage.opcode.ordinal();
            prefix[2] = (i == 8) ? (byte) 0 : (byte) 1;
            byte[] toBroadcast = new byte[fileNameBytes.length + prefix.length];
            System.arraycopy(prefix, 0, toBroadcast, 0, prefix.length);
            System.arraycopy(fileNameBytes, 0, toBroadcast, prefix.length, fileNameBytes.length);
            Set<Integer> activeConnections = loggedInUsers.getLoggedInUsersId();
            toBroadcast = encode(toBroadcast);
            for (Integer connectedUser :
                    activeConnections) {
                connections.send(connectedUser, toBroadcast);
            }
            fileNameInProcess = "";
            needToBcast = false;
        }
    }

    private byte[] generateBytesFromData() {
        byte[] inProcess = incomingDataQueue.remove();
        byte[] pureData = Arrays.copyOfRange(inProcess, 6, inProcess.length);
        while (!incomingDataQueue.isEmpty()){
            inProcess = incomingDataQueue.remove();
            pureData = appendBytes(pureData, Arrays.copyOfRange(inProcess, 6, inProcess.length));
        }
        return pureData;
    }

    private byte[] appendBytes(byte[] pureData, byte[] dataToAdd) {
        byte[] appended = new byte[pureData.length + dataToAdd.length];
        System.arraycopy(pureData, 0, appended, 0, pureData.length);
        System.arraycopy(dataToAdd, 0, appended, pureData.length, dataToAdd.length);
        return appended;
    }

    private void generateAckReceived(byte[] message) {
        OpcodeOperations opcodeOperationsResponse = new OpcodeOperations(Opcode.ACK);
        response = new byte[4];
        System.arraycopy(opcodeOperationsResponse.getInResponseFormat(), 0, response, 0, 2);
        byte[] packetNumber = new byte[]{message[4], message[5]};
        System.arraycopy(packetNumber, 0, response,2, packetNumber.length);
    }

    private void getDir() {
        List<String> listOfFiles = new LinkedList<>();
        try {
            for (Path path :
                    listFilesInDirectory(pathToDir)) {
                listOfFiles.add(path.toString().substring(6));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        byte[] dir = generateDirDataForString(listOfFiles);
        createDataPackets(dir);
    }


    public static List<Path> listFilesInDirectory(String directoryPath) throws IOException {
        Path dir = Paths.get(directoryPath);
        return Files.list(dir)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());
    }

    private void createDataPackets(byte[] data) {
        generateGeneralAck();
        connections.send(connectionId, response);
        response = new byte[]{};
        int numberOfPackets;
        numberOfPackets = (data.length / 512) + 1;
        responseToUserQueue = new LinkedList<>();
        for (int i = 1; i <= numberOfPackets; i++){
            int sizeOfData = Math.min(512, data.length - ((i -1) * 512));
            byte[] dataPacket = new byte[6 + sizeOfData];
            byte[] dataPrefix = generateDataPrefix(sizeOfData, i);
            System.arraycopy(dataPrefix, 0, dataPacket, 0, dataPrefix.length);
            if (sizeOfData != 0){
                System.arraycopy(data, (i - 1) * 512, dataPacket, 6, sizeOfData);
            }
            responseToUserQueue.add(dataPacket);
        }
        response = responseToUserQueue.peek(); //first Packet is ready to be sent
    }

    private int extractDataPacketNumber(byte[] dataPacket) {
        return ((dataPacket[4] & 0xFF) << 8) | (dataPacket[5] & 0xFF);
    }

    private boolean isLastPacket(byte[] message) {
        return message.length != 518;
    }
    private byte[] generateDataPrefix(int sizeOfData, int packetNum) {
        byte[] prefix = new byte[6];
        OpcodeOperations operations = new OpcodeOperations("DATA");
        System.arraycopy(operations.getInResponseFormat(), 0, prefix, 0, operations.getInResponseFormat().length);
        System.arraycopy(convertIntToByte(sizeOfData), 0, prefix, 2, 2);
        System.arraycopy(convertIntToByte(packetNum), 0, prefix, 4, 2);
        return prefix;

    }

    private byte[] generateDirDataForString(List<String> listOfFilesAsString) {
        List<byte[]> listOfFilesAsByte = new LinkedList<>();
        int sizeOfDir = 0;
        for (String fileName:
             listOfFilesAsString) {
            byte[] filenameAsByte = convertStringToUtf8(fileName);
            sizeOfDir += filenameAsByte.length;
            listOfFilesAsByte.add(filenameAsByte);
        }
        sizeOfDir += listOfFilesAsByte.size() - 1; //calculating delimiter between files
        return generateDirDataForByte(listOfFilesAsByte, sizeOfDir);
    }

    private byte[] generateDirDataForByte(List<byte[]> listOfFilesAsByte, int sizeOfDir) {
        byte[] dirData = new byte[sizeOfDir];
        int pointerForEmptySpace = 0;
        Iterator<byte[]> itr = listOfFilesAsByte.iterator();
        while (itr.hasNext()){
            byte[] fileName = itr.next();
            System.arraycopy(fileName, 0, dirData, pointerForEmptySpace, fileName.length);
            pointerForEmptySpace += fileName.length;
            if (itr.hasNext()){
                dirData[pointerForEmptySpace] = (byte) 0; //Separating file names
                pointerForEmptySpace++;
            }
        }
        return dirData;
    }

    private byte[] convertIntToByte(int number) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((number >> 8) & 0xFF);
        bytes[1] = (byte) (number & 0xFF);
        return bytes;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    public byte[] getResponseToUser(){
        byte[] toSend = response;
        response = null;
        return encode(toSend);
    }

    public byte[] encode(byte[] message) {
        if ((message != null) && (hasToAddZeroByte(message))){
            byte[] zero = {(byte) 0};
            byte[] modified = new byte[message.length + zero.length];
            System.arraycopy(message, 0, modified, 0, message.length);
            System.arraycopy(zero, 0, modified, message.length, zero.length);
            message = modified;
        }
        return message;
    }

    private boolean hasToAddZeroByte(byte[] message) {
        OpcodeOperations opcodeOperations = extractOpFromMessage(message);
        return opcodeOperations.shouldAddZero();
    }

    private OpcodeOperations extractOpFromMessage(byte[] message) {
        return new OpcodeOperations(message[1]);
    }
}
