package bgu.spl.net.impl.tftp;

public class OpcodeOperations {
    Opcode opcode;

    public OpcodeOperations(byte code) {
        this.opcode = Opcode.getByOrdinal(code);
    }

    public OpcodeOperations(String name) {
        this.opcode = Opcode.valueOf(name);
    }

    public OpcodeOperations(Opcode code) {
        this.opcode = code;
    }

    public boolean shouldWaitForZeroByte() {
        return opcode.equals(Opcode.RRQ) ||
                opcode.equals(Opcode.WRQ) ||
                opcode.equals(Opcode.ERROR) ||
                opcode.equals(Opcode.LOGRQ) ||
                opcode.equals(Opcode.DELRQ);
    }

    public byte[] getInResponseFormat(){
        byte[] response = new byte[2];
        response[0] = 0;
        response[1] = Opcode.getByte(opcode);
        return response;
    }

    public byte[] getGeneralAck(){
        return getInResponseFormat((byte) 0);
    }

    public byte[] getInResponseFormat(byte b){
        byte[] response = new byte[4];
        byte[] prefix = getInResponseFormat();
        System.arraycopy(prefix, 0, response, 0, prefix.length);
        response[2] = 0;
        response[3] = b;
        return response;
    }

    public boolean shouldAddZero() {
        return opcode.equals(Opcode.ERROR) ||
                opcode.equals(Opcode.BCAST);
    }

    public boolean hasSpecificMsgSize(){
        return opcode.equals(Opcode.ACK) ||
                opcode.equals(Opcode.DIRQ) ||
                opcode.equals(Opcode.DISC);
    }

    public int getExpectedSize() {
        return opcode.equals(Opcode.ACK) ? 4 : 2;
    }
}
