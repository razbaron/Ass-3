package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'start'");
    }

    @Override
    public void process(byte[] message) {
        OpcodeOperations opcodeOp = new OpcodeOperations(message[1]);
        switch (opcodeOp.opcode) {
            case LOGRQ:
                userLogin(message);
                break;
        }
//        switch (message.command) {
//            case CONNECT:
//                processConnect(message);
//                break;
//            case SEND:
//                processSend(message);
//                break;
//            case SUBSCRIBE:
//                processSubscribe(message);
//                break;
//            case UNSUBSCRIBE:
//                processUnsubscribe(message);
//                break;
//            case DISCONNECT:
//                processDisconnect(message);
//                break;
//            case UNKNOWN:
//                processUnknown(message);
//                break;
//        }

        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    private void userLogin(byte[] message) {
    }

    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    } 


    
}
