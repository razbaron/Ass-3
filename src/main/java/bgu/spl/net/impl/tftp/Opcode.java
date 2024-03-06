package bgu.spl.net.impl.tftp;

public enum Opcode {
    RRQ,
    WRQ,
    DATA,
    ACK,
    ERROR,
    DIRQ,
    LOGRQ,
    DELRQ,
    BCAST,
    UNDEFINED;

    public static Opcode getByOrdinal(byte value) {
        int index = Byte.toUnsignedInt(value);
        if (index >= 0 && index < values().length) {
            int unsignedByte = value & 0xFF; //TODO
            return values()[index];

        }
        return UNDEFINED;
    }
}
