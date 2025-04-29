package server;

import java.io.Serializable;

public class Message implements Serializable {
    public static final int LOGIN = 1;
    public static final int TEXT = 2;

    public int type;
    public String sender;
    public String content;

    public Message(int type, String sender, String content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
    }

    @Override
    public String toString() {
        return "[" + sender + "] " + content;
    }
}//
