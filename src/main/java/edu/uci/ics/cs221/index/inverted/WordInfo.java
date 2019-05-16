package edu.uci.ics.cs221.index.inverted;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class WordInfo {
    String word;
    int pageID;
    int offset;
    int len;

    public void setWordInfo(String word, int pageID, int offset, int len) {
        this.word = word;
        this.pageID = pageID;
        this.offset = offset;
        this.len = len;
    }

    public void writeOneWord(ByteBuffer bb) {
        bb.putInt(word.length());
        byte[] tmp = word.getBytes(StandardCharsets.UTF_8);
        bb.put(tmp);
        bb.putInt(pageID);
        bb.putInt(offset);
        bb.putInt(len);
    }

    public void readOneWord(ByteBuffer bb) {
        int wordLen = bb.getInt();
        byte[] wordb = new byte[wordLen];
        bb.get(wordb, 0, wordLen);
        this.word = new String(wordb,StandardCharsets.UTF_8);
        this.pageID = bb.getInt();
        this.offset = bb.getInt();
        this.len = bb.getInt();
    }
}
