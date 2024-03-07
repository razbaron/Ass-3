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
        if (Opcode.UNDEFINED.equals(opcodeOp)){ //TODO modify this
            generateError(4, "Illegal TFTP operation");
        }
        if (!opcodeOp.opcode.equals(Opcode.LOGRQ) || loggedInUsers.isUserLoggedIn(userName)){
            generateError(6, "User not logged in");
        }
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
                getDir(message);
                break;
            case DATA:
                collectDataFromUser(message);
                break;
            case ACK:
                processAck();
                break;
            case BCAST:
//                Should it be here?? it is form the server to the all users.
//                processDisconnect(message);
                break;
            case ERROR:
//                For humans only
                processError(message);
                break;
            case DISC:
                disconnect(message);
                break;
            case UNDEFINED:
//                NoIdea if this status should exist or if it is relevant here
//                processUnknown(message);
                break;
        }

        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    private void generateError(int errorCode, String message) {
        OpcodeOperations opcodeOperations = new OpcodeOperations(Opcode.ERROR);
        byte[] errorPrefix = opcodeOperations.getInResponseFormat();
        byte[] errorMessage = convertStringToUtf8(message);
        //TODO use errorcode
        response = new byte[errorMessage.length + errorPrefix.length];
        response = opcodeOperations.getInResponseFormat();
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
        System.out.println(extractStringFromMessage(message));

    }

    private void processAck() {
        response = responseToUserQueue.remove();
    }

    private void collectDataFromUser(byte[] message) {
        incomingDataQueue.add(message);
        AckReceived();
    }

    private void AckReceived() {
        OpcodeOperations opcodeOperationsResponse = new OpcodeOperations(Opcode.ACK);
        response = opcodeOperationsResponse.getInResponseFormat();
    }

    private void getDir(byte[] message) {

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
            loggedInUsers.logInUser(name, connectionId);
            userName = name;
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
