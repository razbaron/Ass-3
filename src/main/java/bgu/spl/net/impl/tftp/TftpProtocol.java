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
    String fileNameInWriting;
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
        this.connectionId = connectionId;
        this.connections = connections;
//        this.pathToDir = "server" + File.separator + "Files";
        this.pathToDir = "Files";
    }

    @Override
    public void process(byte[] message) {
        System.out.println("##Processing a message in length " + message.length);
        OpcodeOperations opcodeOp = new OpcodeOperations(message[1]);
        if (Opcode.UNDEFINED.equals(opcodeOp.opcode) || Opcode.BCAST.equals(opcodeOp.opcode)){
            generateError(4, "Illegal TFTP operation");
            System.out.println("Undefined");
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
                    break;
            }
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
                bcastUsers(0, fileToDelete);
                generateGeneralAck();
            } else {
//                TODO should I do something if file was not deleted?
            }
        }
    }

    private void processReadRequest(byte[] message) {
        String fileToRead = extractStringFromMessage(message);
        if (lookForFileWithError(fileToRead)){
            byte[] fileData = getDataOfFile(fileToRead);
            createDataPackets(fileData);
        }
    }

    private void generateError(int errorCode, String message) {
//        System.out.println("generating Error " + message);
        OpcodeOperations opcodeOperations = new OpcodeOperations(Opcode.ERROR);
        byte[] errorPrefix = opcodeOperations.getInResponseFormat((byte) errorCode);
        byte[] errorMessage = convertStringToUtf8(message);
//        System.out.println("#Error is the length of " + (errorMessage.length + errorPrefix.length));
        response = new byte[errorMessage.length + errorPrefix.length];
//        response = new byte[errorMessage.length + errorPrefix.length + 1];
        System.arraycopy(errorPrefix, 0, response, 0, errorPrefix.length);
        System.arraycopy(errorMessage, 0, response, errorPrefix.length, errorMessage.length);
//        response[response.length - 1] = 0;
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
        fileNameInWriting = extractStringFromMessage(message);
        if (fileWithThisNameExist(fileNameInWriting)){
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
                //TODO what should be done if the user did not receive the last packet????
            }
        } else {
            //TODO what should be done if the user sent that?
        }
    }

    private boolean ackPacketSuccesses(byte[] message) {
        byte[] blockNum = Arrays.copyOfRange(message, 2, 4);
        return didUserReceiveLastPacket(blockNum);
    }

    private boolean didUserReceiveLastPacket(byte[] receivedByUserBlockNum) {
        byte[] sentBlockNum = Arrays.copyOfRange(responseToUserQueue.peek(), 4,6);
        return sentBlockNum.equals(receivedByUserBlockNum);
    }

    private boolean ackForPacket(byte[] message) {
        byte[] blockNum = Arrays.copyOfRange(message, 2, 4);
        byte[] zeros = {(byte) 0, (byte) 0};
        return !blockNum.equals(zeros);
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
            File file = getMeThisFile(fileNameInWriting);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(readyToWrite);
            fileOutputStream.close();
            bcastUsers(1, fileNameInWriting);
            fileNameInWriting = "";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void bcastUsers(int i, String fileName) {
        byte[] fileNameBytes = convertStringToUtf8(fileName);
        byte[] prefix = new byte[3];
        OpcodeOperations opToSend = new OpcodeOperations(Opcode.BCAST);
        System.arraycopy(opToSend.getInResponseFormat(), 0, prefix, 0, 2);
        prefix[2] = (byte) i;
        byte[] toBroadcast = new byte[fileNameBytes.length + prefix.length];
        System.arraycopy(prefix, 0, toBroadcast, 0, prefix.length);
        System.arraycopy(fileNameBytes, 0, toBroadcast, prefix.length, fileNameBytes.length);
        Set<Integer> activeConnections = loggedInUsers.getLoggedInUsersId();
        for (Integer connectedUser :
                activeConnections) {
            if (connectedUser != connectionId){
                connections.send(connectedUser, toBroadcast);
            }
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
                listOfFiles.add(path.toString());
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
        //Todo should lock the queue to avoid BCAST?
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
        if (pointerForEmptySpace != sizeOfDir){
            System.out.println("Huston we have a problem with generateDirDataForByte");
            //TODO remove this if
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
//        if (response == null){
//            System.out.println("We got a problem, sending null response to user");
//            //TODO remove that print
//        } else {
//            System.out.println("#That my response " + response[1] + " length is " + response.length);
//            System.out.println(convertUtf8ToString(response));
//        }
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
        System.out.println("#Got this opcode" + opcodeOperations.opcode.name());
        return opcodeOperations.shouldAddZero();
    }

    private OpcodeOperations extractOpFromMessage(byte[] message) {
        return new OpcodeOperations(message[1]);
    }
}
