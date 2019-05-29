package edu.uci.ics.cs221.index.inverted;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * An in-memory representation of the information of a word in dictionary segment.
 *
 */

public class PositionalWordInfo {
    String word; //the keyword
    int offsetB; //the starting position of the posting list in the whole file
    int lenB; // the length of bytes of the posting list
    int offsetD; //the starting position of the offsetPos in the whole file
    int lenD; // the length of bytes of the offsetPos

    public void setWordInfo(String word, int offsetB, int lenB, int offsetD, int lenD) {
        this.word = word;
        this.offsetB = offsetB;
        this.lenB = lenB;
        this.offsetD = offsetD;
        this.lenD = lenD;
    }

    /**
     * write the word info into a ByteBuffer
     *
     * @param bb buffer being written
     */

    public void writeOneWord(ByteBuffer bb) {
        bb.putInt(word.length());
        byte[] tmp = word.getBytes(StandardCharsets.UTF_8);
        bb.put(tmp);
        bb.putInt(offsetB);
        bb.putInt(lenB);
        bb.putInt(offsetD);
        bb.putInt(lenD);
    }

    /**
     * read the word info from a ByteBuffer
     *
     * @param bb buffer being read
     */

    public void readOneWord(ByteBuffer bb) {
        int wordLen = bb.getInt();
        byte[] wordb = new byte[wordLen];
        bb.get(wordb, 0, wordLen);
        this.word = new String(wordb,StandardCharsets.UTF_8);
        this.offsetB = bb.getInt();
        this.lenB = bb.getInt();
        this.offsetD = bb.getInt();
        this.lenD = bb.getInt();
    }
}
