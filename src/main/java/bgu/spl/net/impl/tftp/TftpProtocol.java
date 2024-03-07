package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.FileHandler;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    TftpServerUsers loggedInUsers;
    Queue<byte[]> responseToUserQueue;
    Queue<byte[]> incomingDataQueue;
    String userName;
    String fileNameInWriting;
    int connectionId;
    Connections<byte[]> connections;
    byte[] response;

    public TftpProtocol(TftpServerUsers users){
        loggedInUsers = users;
    }
    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
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
                    disconnect(message);
                    break;
            }
        }
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    private void generateError(int errorCode, String message) {
        OpcodeOperations opcodeOperations = new OpcodeOperations(Opcode.ERROR);
        byte[] errorPrefix = opcodeOperations.getInResponseFormat((byte) errorCode);
        byte[] errorMessage = convertStringToUtf8(message);
        response = new byte[errorMessage.length + errorPrefix.length];
        System.arraycopy(errorPrefix, 0, response, 0, errorPrefix.length);
        System.arraycopy(errorMessage, 0, response, errorPrefix.length, errorMessage.length);
    }

    private void processReadRequest(byte[] message) {
        String fileToRead = extractStringFromMessage(message);
        if (!lookForFileWithError(fileToRead)){
            // TODO read data of file
            byte[] fileData = null;
            createDataPackets(fileData);
        }
    }

    private void prepareToReadFromUser(byte[] message) {
        fileNameInWriting = extractStringFromMessage(message);
        if (fileWithThisNameExist(fileNameInWriting)){
            generateError(5, "File already exists");
        } else {
            File file = new File(" ", fileNameInWriting);
            try {
                file.createNewFile();
            } catch (IOException e) {
            }
            incomingDataQueue = new LinkedList<>();
        }
    }

    private boolean lookForFileWithError(String fileName) {
        boolean ans = fileWithThisNameExist(fileName);
        if (!ans){
            generateError(1, "File not found");
        }
        return ans;
    }

    private boolean fileWithThisNameExist(String fileName) {
        File file = new File(fileName); //TODO should be path
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

    private void disconnect(byte[] message) {

    }

    private void processError(byte[] message) {
        System.out.println(extractStringFromMessage(message)); //For human use
        //TODO should generate a response
    }

    private void processAck(byte[] message) {
        if (ackForPacket(message)){
            if (ackPacketSuccesses(message)){
                response = responseToUserQueue.remove();
            } else {
                //TODO what should be done if the user did not receive the last packet????
            }
        }
    }

    private boolean ackPacketSuccesses(byte[] message) {
        byte[] blockNum = Arrays.copyOfRange(message, 2, 4);
        return didUserReceiveLastPacket(blockNum);
    }

    private boolean didUserReceiveLastPacket(byte[] receivedByUserBlockNum) {
        byte[] sentBlockNum = Arrays.copyOfRange(responseToUserQueue.peek(), 4,6);
        return sentBlockNum == receivedByUserBlockNum;
    }

    private boolean ackForPacket(byte[] message) {
        //TODO should I find out if it is a packet Of data Or others?
        return false;
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
            FileOutputStream fileOutputStream = new FileOutputStream(fileNameInWriting);
            fileOutputStream.write(readyToWrite);
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    private boolean isLastPacket(byte[] message) {
        return message.length != 518;
    }

    private void generateAckReceived(byte[] message) {
        OpcodeOperations opcodeOperationsResponse = new OpcodeOperations(Opcode.ACK);
        response = new byte[4];
        System.arraycopy(opcodeOperationsResponse.getInResponseFormat(), 0, response, 0, 2);
        System.arraycopy(Arrays.copyOfRange(message, 2, 4), 2, response,2, 2);
    }

    private void getDir() {
//        FileHandler fileHandler = new FileHandler();
        //TODO get a snapshot list of all files fully uploaded
        List<String> listOfFiles = null;
        byte[] dir = generateDirDataForString(listOfFiles);
        createDataPackets(dir);
    }

    private void createDataPackets(byte[] data) {
        //Todo should lock the queue to avoid BCAST?
        int numberOfPackets;
        numberOfPackets = data.length % 512;
        if (data.length % 512 == 0){
            numberOfPackets++;
        }
        for (int i = 1; i <= numberOfPackets; i++){
            int sizeOfData = Math.min(512, data.length - ((i -1) * 512));
            byte[] dataPacket = new byte[6 + sizeOfData];
            byte[] dataPrefix = generateDataPrefix(sizeOfData, i);
            System.arraycopy(dataPrefix, 0, dataPacket, 0, dataPrefix.length);
            System.arraycopy(data, (i - 1) * 512, dataPacket, 6, dataPacket.length);
            responseToUserQueue.add(dataPacket);
        }
        response = responseToUserQueue.remove(); //first Packet is ready to be sent
    }

    private byte[] generateDataPrefix(int sizeOfData, int packetNum) {
        byte[] prefix = new byte[6];
        OpcodeOperations operations = new OpcodeOperations("DATA");
        System.arraycopy(operations.getInResponseFormat(), 0, prefix, 0, operations.getInResponseFormat().length);
        System.arraycopy(convertIntToByte(sizeOfData), 0, prefix, 2, 2);
        System.arraycopy(convertIntToByte(packetNum), 0, prefix, 4, 2);
        return prefix;

    }

    private byte[] convertIntToByte(int number) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((number >> 8) & 0xFF);
        bytes[1] = (byte) (number & 0xFF);
        return bytes;
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
        sizeOfDir += listOfFilesAsByte.size(); //calculating the limiter between files
        return generateDirDataForByte(listOfFilesAsByte, sizeOfDir);
    }

    private byte[] generateDirDataForByte(List<byte[]> listOfFilesAsByte, int sizeOfDir) {
        byte[] dirData = new byte[sizeOfDir];
        int pointerForEmptySpace = 0;
        //Todo should check for limits on size?
        Iterator<byte[]> itr = listOfFilesAsByte.iterator();
        while (itr.hasNext()){
            byte[] fileName = itr.next();
            System.arraycopy(fileName, 0, dirData, pointerForEmptySpace, fileName.length);
            pointerForEmptySpace += fileName.length;
            dirData[pointerForEmptySpace] = (byte) 0; //Separating file names
            pointerForEmptySpace++;
        }
        if (pointerForEmptySpace != sizeOfDir){
            System.out.println("Huston we have a problem with generateDirDataForByte");
            //TODO remove this if
        }
        return dirData;
    }

    private void deleteFile(byte[] message) {
        String fileToDelete = extractStringFromMessage(message);
        if (!lookForFileWithError(fileToDelete)){
            //TODO delete file
//        ToDo notify all users that was deleted
        }
    }

    private void userLogin(byte[] message) {
        String name = extractStringFromMessage(message);
        if (loggedInUsers.isUserLoggedIn(name)){
            generateError(7, "User already logged in");
        } else {
            userName = name;
            loggedInUsers.logInUser(userName, connectionId);
        }
    }

    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    }

    public byte[] getResponseToUser(){
        if (response == null){
            System.out.println("We got a problem, sending null response to user");
            //TODO remove that print
        }
        byte[] toSend = response;
        response = null;
        return toSend;
    }
}
