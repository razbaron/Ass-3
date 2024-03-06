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
    byte[] response;

    public TftpProtocol(TftpServerUsers users){
        loggedInUsers = users;
    }
    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'start'");
    }

    @Override
    public void process(byte[] message) {
        OpcodeOperations opcodeOp = new OpcodeOperations(message[1]);
        if (!opcodeOp.opcode.equals(Opcode.LOGRQ) || loggedInUsers.isUserLoggedIn(userName)){
//            ToDo throw an error to user
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

    private void processReadRequest(byte[] message) {
        String fileToRead = extractStringFromMessage(message);
        // prepare the data in packets
    }

    private void prepareToReadFromUser(byte[] message) {
        String fileName = extractStringFromMessage(message);
//        create a new file in the files folder
        incomingDataQueue = new LinkedList<>();
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
//        Build a response that data was received
    }

    private void getDir(byte[] message) {

    }

    private void deleteFile(byte[] message) {
        String fileToDelete = extractStringFromMessage(message);
//        ToDo notify all users that was deleted
    }

    private void userLogin(byte[] message) {
    }

    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    }

    public byte[] getResponse(){
        return null;
    }


    
}
