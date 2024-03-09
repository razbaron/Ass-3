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
        if (len <= 1) {
            if (len == 1){
               opcodeOp = new OpcodeOperations(nextByte);
               System.out.println("received " + opcodeOp.opcode.name());
               if (opcodeOp.opcode.equals(Opcode.DISC)){
                   pushByte(nextByte);
                   return popBytes();
               }
            }
        } else {
            if (opcodeOp.hasSpecificMsgSize()){
                int expectedLength = opcodeOp.getExpectedSize();
                if (expectedLength == len){
                    return popBytes();
                }
            } else if (opcodeOp.opcode.equals(Opcode.DATA)){
                if (len >= 4){
                    int expectedLength = getTotalPacketLength();
                    if (len == expectedLength - 1){
                        pushByte(nextByte);
                        return popBytes();
                    }
                }
            } else {
                if (!opcodeOp.shouldWaitForZeroByte()){
                    System.out.println("we got a problem, message is not decoded correctly in decodeNextByte");
                }
                if (nextByte == 0) {
                    return popBytes();
                }
            }
        }
        pushByte(nextByte);
        return null;
    }

    private int getTotalPacketLength() {
        return ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF) + 6;
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }
        bytes[len++] = nextByte;
    }
    private byte[] popBytes() {
        byte[] result = Arrays.copyOfRange(bytes, 0, len);
        len = 0;
        opcodeOp = new OpcodeOperations(Opcode.UNDEFINED);
        bytes = new byte[size];
        return result;
    }

    @Override
    public byte[] encode(byte[] message) {
        if (hasToAddZeroByte(message)){
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
        return opcodeOperations.shouldAddZero();
    }

    private OpcodeOperations extractOpFromMessage(byte[] message) {
        return new OpcodeOperations(message[2]);
    }
}