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

        switch (message[1]){
            case (1): {
//                    RRQ - read request
            }
            case (2): {
//                    WRQ - write request
            }
            case (3): {
//                    DATA - data packet
            }
            case (4): {
//                    ACK - acknowledgment
            }
            case (5): {
//                    ERROR - Error
            }
            case (6): {
//                    DIRQ - directory listing request
            }
            case (7): {
//                    LOGRQ - Login request
            }
            case (8): {
//                    DELRQ - delete file request
            }
            case (9): {
//                    BCAST - broadcast file added/deleted
            }
            case (10): {
//                    DISC - disconnect
            }
        }

        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    } 


    
}
