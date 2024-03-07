package bgu.spl.net.impl.tftp;

public class OpcodeOperations {
    Opcode opcode;

    public OpcodeOperations(byte code) {
        this.opcode = Opcode.getByOrdinal(code);
    }

    public OpcodeOperations(Opcode code) {
        this.opcode = code;
    }

    public boolean shouldWaitForZeroByte() {
        return opcode.equals(Opcode.RRQ) ||
                opcode.equals(Opcode.WRQ) ||
                opcode.equals(Opcode.ERROR) ||
                opcode.equals(Opcode.LOGRQ) ||
                opcode.equals(Opcode.DELRQ) ||
                opcode.equals(Opcode.BCAST);
    }

    public byte[] getInResponseFormat(){
        byte[] response = new byte[2];
        if (opcode.equals(Opcode.ACK)){
            response = new byte[4];
            response[2] = 0;
            response[3] = 0;
        }
        response[0] = 0;
        response[1] = Opcode.getByte(opcode);
        return response;
    }
}
