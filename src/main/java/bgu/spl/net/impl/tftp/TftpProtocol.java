package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    TftpServerUsers loggedInUsers;
    Queue<byte[]> responseToUserQueue;
    Queue<byte[]> incomingDataQueue;
    String userName;
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
                    processAck();
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
            // TODO prepare the data in packets for the user
        }
    }

    private void prepareToReadFromUser(byte[] message) {
        String fileName = extractStringFromMessage(message);
        if (fileWithThisNameExist(fileName)){
            generateError(5, "File already exists");
        } else {
//        create a new file in the files folder
            //TODO create data packets for user
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
        //TODO look for the file
        return false;
    }

    private String extractStringFromMessage(byte[] message) {
        byte[] subArr = new byte[message.length - 2]; // Ignore first two bytes of Opcode
        System.arraycopy(message, 2, subArr, 0, subArr.length);
        return convertUtf8ToString(subArr);
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

    private void processAck() {
        response = responseToUserQueue.remove();
    }

    private void collectDataFromUser(byte[] message) {
        incomingDataQueue.add(message);
        generateAckReceived();
    }

    private void generateAckReceived() {
        OpcodeOperations opcodeOperationsResponse = new OpcodeOperations(Opcode.ACK);
        response = opcodeOperationsResponse.getInResponseFormat((byte) 0);
    }

    private void getDir() {
        //TODO get a snapshot list of all files fully uploaded
        List<String> listOfFiles = null;
        byte[] dir = generateDirDataForString(listOfFiles);
        createDataPackets(dir);
    }

    private void createDataPackets(byte[] data) {
        int numberOfPackets;
        numberOfPackets = data.length % 512;
        if (data.length % 512 == 0){
            numberOfPackets++;
        }
        for (int i = 0; i < numberOfPackets; i++){

        }
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
        sizeOfDir += listOfFilesAsByte.size(); //calculating the added zeros
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
            dirData[pointerForEmptySpace] = (byte) 0;
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
        return response;
    }
}
