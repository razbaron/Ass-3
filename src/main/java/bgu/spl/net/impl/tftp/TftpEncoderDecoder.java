package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder

    private int size = 1 << 9;
    private byte[] bytes = new byte[size]; //start with 512 allegedly
    private int opCode;
    private int len = 0;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
//        TODO: check which opcode we have. it has to be the first byte of the message.(Parser class)
        if (len < 2) {
            pushByte(nextByte);
        } else {
            switch (bytes[1]){
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

        }
//        if (nextByte == '\n') {
//            return popBytes();
//        }
//
//        pushByte(nextByte);
        return null; //not a line yet
        // TODO: implement this
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }
    private byte[] popBytes() {
        byte[] result = bytes;
        len = 0;
        bytes = new byte[size];
        return result;
    }

    @Override
    public byte[] encode(byte[] message) {
        //TODO: implement this
        return null;
    }
}