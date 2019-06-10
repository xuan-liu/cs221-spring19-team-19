package edu.uci.ics.cs221.index.inverted;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * An in-memory representation of the information of a word in dictionary segment.
 *
 */

public class WordInfo {
    String word; //the keyword
    int pageID; //the page of the posting list
    int offset; //the starting position of the posting list in the page
    int len; // the length of the posting list

    public void setWordInfo(String word, int pageID, int offset, int len) {
        this.word = word;
        this.pageID = pageID;
        this.offset = offset;
        this.len = len;
    }

    /**
     * write the word info into a ByteBuffer
     *
     * @param bb buffer being written
     */

    public void writeOneWord(ByteBuffer bb) {
        byte[] tmp = word.getBytes(StandardCharsets.UTF_8);
        bb.putInt(tmp.length);
        bb.put(tmp);
        bb.putInt(pageID);
        bb.putInt(offset);
        bb.putInt(len);
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
        this.pageID = bb.getInt();
        this.offset = bb.getInt();
        this.len = bb.getInt();
    }
}