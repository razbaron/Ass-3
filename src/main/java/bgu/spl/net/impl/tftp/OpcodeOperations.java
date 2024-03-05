package bgu.spl.net.impl.tftp;

public class OpcodeOperations {
    Opcode opcode;

    public OpcodeOperations(byte code) {
        this.opcode = Opcode.getByOrdinal(code);
    }

    public boolean shouldWaitForZeroByte() {
        return opcode.equals(Opcode.RRQ) ||
                opcode.equals(Opcode.WRQ) ||
                opcode.equals(Opcode.ERROR) ||
                opcode.equals(Opcode.LOGRQ) ||
                opcode.equals(Opcode.DELRQ) ||
                opcode.equals(Opcode.BCAST);
    }

//    switch (bytes[1]){
//                case (1): {
////                    RRQ - read request
//                }
//                case (2): {
////                    WRQ - write request
//                }
//                case (3): {
////                    DATA - data packet
//                }
//                case (4): {
////                    ACK - acknowledgment
//                }
//                case (5): {
////                    ERROR - Error
//                }
//                case (6): {
////                    DIRQ - directory listing request
//                }
//                case (7): {
////                    LOGRQ - Login request
//                }
//                case (8): {
////                    DELRQ - delete file request
//                }
//                case (9): {
////                    BCAST - broadcast file added/deleted
//                }
//                case (10): {
////                    DISC - disconnect
//                }
//            }
}
