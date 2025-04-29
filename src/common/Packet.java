package common;

import java.io.Serializable;

public class Packet implements Serializable {
    private static final long serialVersionUID = 1L;

    public int sequenceNumber;  // Used to identify the packet (0 to 9)
    public byte[] data;         // A chunk of the image+description data

    public Packet(int sequenceNumber, byte[] data) {
        this.sequenceNumber = sequenceNumber;
        this.data = data;
    }
}