package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder

    private final int size = 1 << 9;
    private byte[] bytes = new byte[size]; //start with 512 allegedly
    private OpcodeOperations opcodeOp;
    private int len = 0;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
//        TODO: check which opcode we have. it has to be the first byte of the message.(Parser class)
        if (len <= 1) {
            if (len == 1){
               opcodeOp = new OpcodeOperations(nextByte);
            }
        } else {
            if (opcodeOp.shouldWaitForZeroByte()){
                if (nextByte == 0){
                    return popBytes();
                }
            } else {
                return popBytes();
            }
        }
        pushByte(nextByte);
        return null;

        // TODO: implement this - Done
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
        //TODO should we add 0 as an the limiter
        return message;
    }
}