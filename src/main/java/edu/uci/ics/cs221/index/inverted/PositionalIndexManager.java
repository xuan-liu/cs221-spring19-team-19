package edu.uci.ics.cs221.index.inverted;

import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import edu.uci.ics.cs221.analysis.*;
import edu.uci.ics.cs221.storage.Document;
import edu.uci.ics.cs221.storage.DocumentStore;
import edu.uci.ics.cs221.storage.MapdbDocStore;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.nio.BufferUnderflowException;
import java.io.ByteArrayOutputStream;

/**
 * This class manages an disk-based positional index and all the documents in the positional index.
 *
 * On disk, there are 4 files per segment: docStore, dictionary, and invertedLists, positionLists, offsetOfPositionList.
 * PositionLists and offsetOfPositionList will be compressed.
 *
 * Dictionary is in “segmentXXa”. The first page has one integer, which represents the total number of bytes the remaining
 * pages will use. The remaining pages store word information for each keyword — length(keyword), keyword, offset(posting list),
 * length(posting list), offset(offsetOfPositionList), lengthOfByte(offsetOfPositionList).
 *
 * InvertedLists is in “segmentXXb”. For each keyword, it stores — docID1, length(position list of docID1), docID2,
 * length(position list of docID2) ……
 *
 * OffsetOfPositionList is in “segmentXXd”. For each keyword, it stores — offset(position list of docID1), offset(position list of docID2)
 * … offset(position list of docIDn), endOffset(position list of docIDn).
 *
 * PositionLists is in “segmentXXc”. For each keyword, it stores — position list of docID1, position list of docID2 ……
 *
 * DocStore is in “segmentXX.db”.
 * 
 * Please refer to the project 3 wiki page for implementation guidelines.
 */

public class PositionalIndexManager extends InvertedIndexManager {
    private Compressor compressor;

    public PositionalIndexManager(String indexFolder, Analyzer analyzer, Compressor compressor) {
        super(indexFolder, analyzer);
        this.compressor = compressor;
    }

    /**
     * Flushes all the documents in the in-memory segment buffer to disk. If the buffer is empty, it should not do anything.
     * flush() writes the segment to disk containing the posting list and the corresponding document store.
     */

    @Override
    public void flush() {
        // If the buffer is empty, return
        if (invertedLists.size() == 0 || documents.size() == 0) {
            return;
        }
        docID = 0;

        ByteBuffer wordsBuffer = ByteBuffer.allocate(STORE_PARAMETER * invertedLists.size());
        ByteBuffer listBuffer = ByteBuffer.allocate(STORE_PARAMETER * invertedLists.size());
        ByteBuffer offPosBuffer = ByteBuffer.allocate(STORE_PARAMETER * invertedLists.size());
        ByteBuffer positionBuffer = ByteBuffer.allocate(STORE_PARAMETER * invertedLists.size());

        int offsetB = 0; // in dic, represent the offset of posting list
        int offsetD = 0; // in dic, represent the offset of offsetPos "offset (position list) + end offset"
        int offsetPos = 0; // the num stored in offsetPos


        for (String word: invertedLists.keySet()) {

            // store all the position lists in segmentXXc

            List<Integer> postingList = invertedLists.get(word);
            List<Integer> offPos = new ArrayList<>();

            for (int docID: postingList) {
                // store the posting lists and length(position list) in segmentXXb
                List<Integer> positionList = positions.get(word, docID);
                listBuffer.putInt(docID);
                listBuffer.putInt(positionList.size());

                byte[] positionListByte = compressor.encode(positionList);
                positionBuffer.put(positionListByte);

                offPos.add(offsetPos); // the start offset is coincident with the end offset of previous keyword
                offsetPos += positionListByte.length;
            }

            offPos.add(offsetPos); // add the end offset

            //  store the according "offset (position list) + end offset" in segmentXXd
            byte[] offPosByte = compressor.encode(offPos);
            offPosBuffer.put(offPosByte);

            // store the len(keywords), keywords, offset(list), length(list), offset(offsetPos), lenOfByte(offsetPos)
            // in segmentXXa, with the first page have the total number of bytes the remaining pages will use

            PositionalWordInfo wi = new PositionalWordInfo();
            wi.setWordInfo(word, offsetB, postingList.size(), offsetD, offPosByte.length);
            wi.writeOneWord(wordsBuffer);

            offsetB += postingList.size() * 2 * 4;
            offsetD += offPosByte.length;
        }

        byte[] offPosByte = compressor.encode(Arrays.asList(offsetPos));
        offPosBuffer.put(offPosByte);

        // write the dictionary
        Path wordsPath = Paths.get(indexFolder + "/segment" + segmentID + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);

        // write the first page
        writeFirstPageOfWord(wordsFileChannel, wordsBuffer.position());

        // write the remaining page
        wordsFileChannel.appendAllBytes(fitBuffer(wordsBuffer));
        wordsFileChannel.close();

        // write the posting list
        Path listPath = Paths.get(indexFolder+"/segment" + segmentID + "b");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(listPath);
        listFileChannel.appendAllBytes(fitBuffer(listBuffer));
        listFileChannel.close();

        // write the offsetPos list
        Path offsetPath = Paths.get(indexFolder+"/segment" + segmentID + "d");
        PageFileChannel offsetFileChannel = PageFileChannel.createOrOpen(offsetPath);
        offsetFileChannel.appendAllBytes(fitBuffer(offPosBuffer));
        offsetFileChannel.close();

        // write the position list
        Path positionPath = Paths.get(indexFolder+"/segment" + segmentID + "c");
        PageFileChannel positionFileChannel = PageFileChannel.createOrOpen(positionPath);
        positionFileChannel.appendAllBytes(fitBuffer(positionBuffer));
        positionFileChannel.close();

        // store all the documents in segmentXX.db
        DocumentStore ds = MapdbDocStore.createWithBulkLoad(indexFolder + "/segment" + segmentID + ".db",documents.entrySet().iterator());
        ds.close();

        // clear the invertedLists and documents
        invertedLists = new TreeMap<>();
        documents = new TreeMap<>();
        this.positions = TreeBasedTable.create();
        segmentID += 1;

        // if the num of segment reach DEFAULT_MERGE_THRESHOLD, call merge()
        if (segmentID >= DEFAULT_MERGE_THRESHOLD) {
            mergeAllSegments();
        }
    }

    /**
     * fit a suitable ByteBuffer for a byte array.
     *
     * @param bb the previous ByteBuffer
     * @return the outcome ByteBuffer
     */

    private ByteBuffer fitBuffer(ByteBuffer bb) {
        int tmp = bb.position();
        bb.position(0);
        bb.limit(tmp);
        ByteBuffer bb_new = ByteBuffer.allocate(tmp);
        bb_new.put(bb);
        bb_new.rewind();
        return bb_new;
    }

    /**
     * A help class for method readListBufferByPage
     */

    private class BufferAndByte{
        ByteBuffer bb;
        byte[] b;
        int pageIDRead;
        int offStart;
        int offEnd;

        public BufferAndByte(ByteBuffer bb, byte[] b, int pageIDRead, int offStart, int offEnd) {
            this.bb = bb;
            this.b = b;
            this.pageIDRead = pageIDRead;
            this.offStart = offStart;
            this.offEnd = offEnd;
        }
    }

    /**
     * write a ByteBuffer containing position list into buffer by page, if the list length is larger than the page size,
     * append the page and open another buffer
     *
     * @param segID the segment ID
     * @param bb the BybeBuffer being written with capacity = PAGE_SIZE
     * @param pageIDRead the page of the file being read
     * @param len the length of byte of the list
     * @param x the part of segment (a represent dictionary, b represent posting list, c represent position list, d represent offset list
     * @return the BybeBuffer being read, the outcome byte array, the page of the file being read
     */

    private BufferAndByte readListBufferByPage(int segID, ByteBuffer bb, int pageIDRead, int len, String x) {
        int remain = bb.limit() - bb.position();
        int lSize = len;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // if the array is longer than the remaining buffer, first read the buffer,
        // then open the next page and read

        while (lSize / remain >= 1) {
            byte[] result = new byte[remain];
            bb.get(result, 0, remain);
            output.write(result, 0, result.length);

            pageIDRead += 1;
            bb = readSegPage(segID, x, pageIDRead);
            lSize -= remain;
            remain = PageFileChannel.PAGE_SIZE;
        }

        // if the array is no longer than the remaining buffer, just read the buffer
        byte[] result = new byte[lSize];
        bb.get(result, 0, lSize);
        output.write(result, 0, result.length);
        byte[] out = output.toByteArray();

        // if the list is offset list, also return the start offset and end offset
        if (x == "d") {
            List<Integer> list = compressor.decode(out);
            return new BufferAndByte(bb, out, pageIDRead, list.get(0), list.get(list.size() - 1));
        }

        return new BufferAndByte(bb, out, pageIDRead, 0, 0);
    }


    /**
     * Write the byte array into buffer by page, if the list length is larger than the page size,
     * append the page and open another buffer
     *
     * @param pfc the file being written
     * @param bb the BybeBuffer being written with capacity = PAGE_SIZE
     * @param b the byte array
     */

    private void writeListBufferByPage(PageFileChannel pfc, ByteBuffer bb, byte[] b) {
        int lSize = b.length;
        int remain = bb.limit() - bb.position();

        // if the array is longer than the remaining buffer, first write the buffer,
        // then append the page and open another buffer to write
        while (lSize / remain >= 1) {
            byte[] tmp = new byte[remain];
            System.arraycopy(b, 0, tmp, 0, remain);
            byte[] bnew = new byte[lSize - remain];
            System.arraycopy(b, remain, bnew, 0, lSize - remain);

            b = bnew;
            bb.put(tmp);
            pfc.appendPage(bb);
            bb.clear();
            lSize -= remain;
            remain = PageFileChannel.PAGE_SIZE;
        }

        // if the array is no longer than the remaining buffer, just write the buffer
        bb.put(b);
    }

    /**
     * for a byte array, decode it to list, add n to every list elements, and encode it to a list and return it
     *
     * @param bl the byte array being added
     * @param n the number being added
     * @return the outcome byte array
     */

    private byte[] addNumList(byte[] bl, int n) {
        List<Integer> list = compressor.decode(bl, 0, bl.length);
        List<Integer> listNew = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            listNew.add(list.get(i) + n);
        }
        return compressor.encode(listNew);
    }

    /**
     * add the second byte array behind the first byte array
     *
     * @param b1 the first byte array
     * @param b2 the second byte array
     * @return the outcome byte array
     */

    private byte[] joinTwoByte(byte[] b1, byte[] b2) {
        byte[] lsNew = new byte[b1.length + b2.length];
        System.arraycopy(b1, 0, lsNew, 0, b1.length);
        System.arraycopy(b2, 0, lsNew, b1.length, b2.length);
        return lsNew;
    }

    /**
     * when merge, change the offset list. If there are two byte arrays, join it to be a new byte array.
     * If there are only one byte array, change it to be a new byte array.
     *
     * @param a the first byte array
     * @param b the second byte array
     * @param offStart the start offset in the writing buffer
     * @return the outcome byte array
     */

    private byte[] joinTwoOffPosList(byte[] a, byte[] b, int offStart) {
        // if there are only one byte array, change it to be a new byte array
        if (b == null) {
            List<Integer> la = compressor.decode(a, 0, a.length);
            List<Integer> lnew = new ArrayList<>();
            int change = offStart - la.get(0);
            for (int i = 0; i < la.size(); i++) {
                lnew.add(la.get(i) + change);
            }
            return compressor.encode(lnew);
        }

        // if there are two byte arrays, join it to be a new byte array
        List<Integer> la = compressor.decode(a, 0, a.length);
        List<Integer> lb = compressor.decode(b, 0, b.length);
        List<Integer> lnew = new ArrayList<>();

        int change = offStart - la.get(0);
        for (int i = 0; i < la.size(); i++) {
            lnew.add(la.get(i) + change);
        }
        change = lnew.get(la.size() - 1) - lb.get(0);
        for (int i = 1; i < lb.size(); i++) {
            lnew.add(lb.get(i) + change);
        }
        return compressor.encode(lnew);
    }

    /**
     * Merges the invertedLists of two disk segments
     *
     * @param segID1 the first segment ID
     * @param segID2 the second segment ID
     * @param numDoc1 the number of documents in the first segment
     */

    @Override
    void mergeInvertedLists(int segID1, int segID2, int numDoc1) {
//        System.out.println("merge:"+segID1+" and "+segID2);
        // read two segmentXXa into two buffer and delete these two segmentXXa
        Path path = Paths.get(indexFolder + "/segment" + segID1 + "a");
        PageFileChannel pfc = PageFileChannel.createOrOpen(path);
        ByteBuffer wb1 = pfc.readAllPages();
        pfc.close();
        deleteFile(indexFolder + "/segment" + segID1 + "a");
        int cap1 = readFirstPageOfWord(wb1);

        path = Paths.get(indexFolder + "/segment" + segID2 + "a");
        pfc = PageFileChannel.createOrOpen(path);
        ByteBuffer wb2 = pfc.readAllPages();
        pfc.close();
        deleteFile(indexFolder + "/segment" + segID2 + "a");
        int cap2 = readFirstPageOfWord(wb2);

        // merge the inverted lists of the two segments
        ByteBuffer wordsBuffer = ByteBuffer.allocate(10 * (cap1 + cap2));
        ByteBuffer listBuffer = ByteBuffer.allocate(PageFileChannel.PAGE_SIZE);
        ByteBuffer offPosBuffer = ByteBuffer.allocate(PageFileChannel.PAGE_SIZE);
        ByteBuffer positionBuffer = ByteBuffer.allocate(PageFileChannel.PAGE_SIZE);

        PositionalWordInfo wi1 = new PositionalWordInfo();
        wi1.readOneWord(wb1);
        PositionalWordInfo wi2 = new PositionalWordInfo();
        wi2.readOneWord(wb2);

        int offsetB = 0;
        int offsetD = 0;
        int offsetPos = 0;
        int pageIDRead1 = 0;
        int pageIDRead2 = 0;
        int pageIDReadOff1 = 0;
        int pageIDReadOff2 = 0;
        int pageIDReadPos1 = 0;
        int pageIDReadPos2 = 0;
        ByteBuffer lb1 = readSegPage(segID1, "b", pageIDRead1);
        ByteBuffer lb2 = readSegPage(segID2, "b", pageIDRead2);
        ByteBuffer ob1 = readSegPage(segID1, "d", pageIDReadOff1);
        ByteBuffer ob2 = readSegPage(segID2, "d", pageIDReadOff2);
        ByteBuffer pb1 = readSegPage(segID1, "c", pageIDReadPos1);
        ByteBuffer pb2 = readSegPage(segID2, "c", pageIDReadPos2);

        path = Paths.get(indexFolder + "/segment b tmp");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(path);

        path = Paths.get(indexFolder + "/segment d tmp");
        PageFileChannel offPosFileChannel = PageFileChannel.createOrOpen(path);

        path = Paths.get(indexFolder + "/segment c tmp");
        PageFileChannel positionFileChannel = PageFileChannel.createOrOpen(path);

        while (true) {
//            System.out.println("word1: "+wi1.word+" ,word2: "+wi2.word);
            if (wi1.word.equals(wi2.word)) {
                // add them to the dictionary, find their posting lists and add them to the disk
                //find their position list and add them to the disk, move both bb1 and bb2 to the next words

                //get the list according to the word, for list 2, all docID add numDoc1
                BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.lenB, false, numDoc1);
                lb1 = bl1.bb;
                List<Integer> ls1 = bl1.list;
                Map<Integer, Integer> map1 = bl1.map;
                pageIDRead1 = bl1.pageIDRead;

                BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.lenB, true, numDoc1);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                Map<Integer, Integer> map2 = bl2.map;
                pageIDRead2 = bl2.pageIDRead;

                ls1.addAll(ls2);
                map1.putAll(map2);

                //write the list info into buffer, if buffer full, append it into disk
                writeListBufferByPage(listFileChannel, listBuffer, ls1, map1);

                // read the offPos buffer, get the offset. write the offPos into buffer
                BufferAndByte ol1 = readListBufferByPage(segID1, ob1, pageIDReadOff1, wi1.lenD, "d");
                ob1 = ol1.bb;
                byte[] os1 = ol1.b;
                pageIDReadOff1 = ol1.pageIDRead;

                BufferAndByte ol2 = readListBufferByPage(segID2, ob2, pageIDReadOff2, wi2.lenD, "d");
                ob2 = ol2.bb;
                byte[] os2 = ol2.b;
                pageIDReadOff2 = ol2.pageIDRead;

                byte[] osNew = joinTwoOffPosList(os1, os2, offsetPos);
                writeListBufferByPage(offPosFileChannel, offPosBuffer, osNew);

                //read and write the position list into buffer, if buffer full, append it into disk
                BufferAndByte rp1 = readListBufferByPage(segID1, pb1, pageIDReadPos1, ol1.offEnd - ol1.offStart, "c");
                pb1 = rp1.bb;
                pageIDReadPos1 = rp1.pageIDRead;
                writeListBufferByPage(positionFileChannel, positionBuffer, rp1.b);

                BufferAndByte rp2 = readListBufferByPage(segID2, pb2, pageIDReadPos2, ol2.offEnd - ol2.offStart, "c");
                pb2 = rp2.bb;
                pageIDReadPos2 = rp2.pageIDRead;
                writeListBufferByPage(positionFileChannel, positionBuffer, rp2.b);

                offsetPos += rp1.b.length + rp2.b.length;

                //write the word info into buffer
                PositionalWordInfo wi = new PositionalWordInfo();
                wi.setWordInfo(wi1.word, offsetB, ls1.size(), offsetD, osNew.length);
                wi.writeOneWord(wordsBuffer);
                offsetB += ls1.size()* (4 * 2);
                offsetD += osNew.length;

                //check whether bb1 and bb2 can move to the next words
                if (!wb1.hasRemaining() || !wb2.hasRemaining()) {
                    break;
                }

                //move bb1 and bb2 to the next words
                wi1 = new PositionalWordInfo();
                wi1.readOneWord(wb1);
                wi2 = new PositionalWordInfo();
                wi2.readOneWord(wb2);
            }
            else if (wi1.word.compareTo(wi2.word) > 0) {
                // add key2 and its list to the disk, move bb2 to the next word
                //get the list according to the word, for list 2, all docID add numDoc1
                BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.lenB, true, numDoc1);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                Map<Integer, Integer> map2 = bl2.map;
                pageIDRead2 = bl2.pageIDRead;

                //write the list info into buffer, if buffer full, append it into disk
                writeListBufferByPage(listFileChannel, listBuffer, ls2, map2);

                // read the offPos buffer, get the offset. write the offPos into buffer
                BufferAndByte ol2 = readListBufferByPage(segID2, ob2, pageIDReadOff2, wi2.lenD, "d");
                ob2 = ol2.bb;
                byte[] os2 = ol2.b;
                pageIDReadOff2 = ol2.pageIDRead;

                byte[] osNew = joinTwoOffPosList(os2, null, offsetPos);
                writeListBufferByPage(offPosFileChannel, offPosBuffer, osNew);

                //read and write the position list into buffer, if buffer full, append it into disk
                BufferAndByte rp2 = readListBufferByPage(segID2, pb2, pageIDReadPos2, ol2.offEnd - ol2.offStart, "c");
                pb2 = rp2.bb;
                pageIDReadPos2 = rp2.pageIDRead;
                writeListBufferByPage(positionFileChannel, positionBuffer, rp2.b);

                offsetPos += rp2.b.length;

                //write the word info into buffer
                PositionalWordInfo wi = new PositionalWordInfo();
                wi.setWordInfo(wi2.word, offsetB, ls2.size(), offsetD, osNew.length);
                wi.writeOneWord(wordsBuffer);
                offsetB += ls2.size() * (4 * 2);
                offsetD += osNew.length;

                if (!wb2.hasRemaining()) {
                    BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.lenB, false, numDoc1);
                    lb1 = bl1.bb;
                    List<Integer> ls1 = bl1.list;
                    Map<Integer, Integer> map1 = bl1.map;
                    pageIDRead1 = bl1.pageIDRead;

                    //write the list info into buffer, if buffer full, append it into disk
                    writeListBufferByPage(listFileChannel, listBuffer, ls1, map1);

                    // read the offPos buffer, get the offset. write the offPos into buffer
                    BufferAndByte ol1 = readListBufferByPage(segID1, ob1, pageIDReadOff1, wi1.lenD, "d");
                    ob1 = ol1.bb;
                    byte[] os1 = ol1.b;
                    pageIDReadOff1 = ol1.pageIDRead;

                    osNew = joinTwoOffPosList(os1, null, offsetPos);
                    writeListBufferByPage(offPosFileChannel, offPosBuffer, osNew);

                    //read and write the position list into buffer, if buffer full, append it into disk
                    BufferAndByte rp1 = readListBufferByPage(segID1, pb1, pageIDReadPos1, ol1.offEnd - ol1.offStart, "c");
                    pb1 = rp1.bb;
                    pageIDReadPos1 = rp1.pageIDRead;
                    writeListBufferByPage(positionFileChannel, positionBuffer, rp1.b);

                    offsetPos += rp1.b.length;

                    //write the word info into buffer
                    wi = new PositionalWordInfo();
                    wi.setWordInfo(wi1.word, offsetB, ls1.size(), offsetD, osNew.length);
                    wi.writeOneWord(wordsBuffer);
                    offsetB += ls1.size() * (4 * 2);
                    offsetD += osNew.length;

                    break;
                }
                wi2 = new PositionalWordInfo();
                wi2.readOneWord(wb2);
            }
            else {
                //add key1 and its list to the disk, move bb1 to the next word
                BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.lenB, false, numDoc1);
                lb1 = bl1.bb;
                List<Integer> ls1 = bl1.list;
                Map<Integer, Integer> map1 = bl1.map;
                pageIDRead1 = bl1.pageIDRead;

                //write the list info into buffer, if buffer full, append it into disk
                writeListBufferByPage(listFileChannel, listBuffer, ls1, map1);

                // read the offPos buffer, get the offset. write the offPos into buffer
                BufferAndByte ol1 = readListBufferByPage(segID1, ob1, pageIDReadOff1, wi1.lenD, "d");
                ob1 = ol1.bb;
                byte[] os1 = ol1.b;
                pageIDReadOff1 = ol1.pageIDRead;

                byte[] osNew = joinTwoOffPosList(os1, null, offsetPos);
                writeListBufferByPage(offPosFileChannel, offPosBuffer, osNew);

                //read and write the position list into buffer, if buffer full, append it into disk
                BufferAndByte rp1 = readListBufferByPage(segID1, pb1, pageIDReadPos1, ol1.offEnd - ol1.offStart, "c");
                pb1 = rp1.bb;
                pageIDReadPos1 = rp1.pageIDRead;
                writeListBufferByPage(positionFileChannel, positionBuffer, rp1.b);

                offsetPos += rp1.b.length;

                //write the word info into buffer
                PositionalWordInfo wi = new PositionalWordInfo();
                wi.setWordInfo(wi1.word, offsetB, ls1.size(), offsetD, osNew.length);
                wi.writeOneWord(wordsBuffer);
                offsetB += ls1.size() * (4 * 2);
                offsetD += osNew.length;

                if (!wb1.hasRemaining()) {
                    BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.lenB, true, numDoc1);
                    lb2 = bl2.bb;
                    List<Integer> ls2 = bl2.list;
                    Map<Integer, Integer> map2 = bl2.map;
                    pageIDRead2 = bl2.pageIDRead;

                    //write the list info into buffer, if buffer full, append it into disk
                    writeListBufferByPage(listFileChannel, listBuffer, ls2, map2);

                    // read the offPos buffer, get the offset. write the offPos into buffer
                    BufferAndByte ol2 = readListBufferByPage(segID2, ob2, pageIDReadOff2, wi2.lenD, "d");
                    ob2 = ol2.bb;
                    byte[] os2 = ol2.b;
                    pageIDReadOff2 = ol2.pageIDRead;

                    osNew = joinTwoOffPosList(os2, null, offsetPos);
                    writeListBufferByPage(offPosFileChannel, offPosBuffer, osNew);

                    //read and write the position list into buffer, if buffer full, append it into disk
                    BufferAndByte rp2 = readListBufferByPage(segID2, pb2, pageIDReadPos2, ol2.offEnd - ol2.offStart, "c");
                    pb2 = rp2.bb;
                    pageIDReadPos2 = rp2.pageIDRead;
                    writeListBufferByPage(positionFileChannel, positionBuffer, rp2.b);

                    offsetPos += rp2.b.length;

                    //write the word info into buffer
                    wi = new PositionalWordInfo();
                    wi.setWordInfo(wi2.word, offsetB, ls2.size(), offsetD, osNew.length);
                    wi.writeOneWord(wordsBuffer);
                    offsetB += ls2.size() * (4 * 2);
                    offsetD += osNew.length;

                    break;
                }
                wi1 = new PositionalWordInfo();
                wi1.readOneWord(wb1);
            }
        }

        if (!wb1.hasRemaining() && wb2.hasRemaining()) {
            while (wb2.hasRemaining()) {
                wi2 = new PositionalWordInfo();
                wi2.readOneWord(wb2);

                //get the list according to the word, for list 2, all docID add numDoc1
                BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.lenB, true, numDoc1);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                Map<Integer, Integer> map2 = bl2.map;
                pageIDRead2 = bl2.pageIDRead;

                //write the list info into buffer, if buffer full, append it into disk
                writeListBufferByPage(listFileChannel, listBuffer, ls2, map2);

                // read the offPos buffer, get the offset. write the offPos into buffer
                BufferAndByte ol2 = readListBufferByPage(segID2, ob2, pageIDReadOff2, wi2.lenD, "d");
                ob2 = ol2.bb;
                byte[] os2 = ol2.b;
                pageIDReadOff2 = ol2.pageIDRead;

                byte[] osNew = joinTwoOffPosList(os2, null, offsetPos);
                writeListBufferByPage(offPosFileChannel, offPosBuffer, osNew);

                //read and write the position list into buffer, if buffer full, append it into disk
                BufferAndByte rp2 = readListBufferByPage(segID2, pb2, pageIDReadPos2, ol2.offEnd - ol2.offStart, "c");
                pb2 = rp2.bb;
                pageIDReadPos2 = rp2.pageIDRead;
                writeListBufferByPage(positionFileChannel, positionBuffer, rp2.b);

                offsetPos += rp2.b.length;

                //write the word info into buffer
                PositionalWordInfo wi = new PositionalWordInfo();
                wi.setWordInfo(wi2.word, offsetB, ls2.size(), offsetD, osNew.length);
                wi.writeOneWord(wordsBuffer);
                offsetB += ls2.size() * (4 * 2);
                offsetD += osNew.length;
            }
        }

        if (wb1.hasRemaining() && !wb2.hasRemaining()) {
            while (wb1.hasRemaining()) {
                wi1 = new PositionalWordInfo();
                wi1.readOneWord(wb1);

                BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.lenB, false, numDoc1);
                lb1 = bl1.bb;
                List<Integer> ls1 = bl1.list;
                Map<Integer, Integer> map1 = bl1.map;
                pageIDRead1 = bl1.pageIDRead;

                //write the list info into buffer, if buffer full, append it into disk
                writeListBufferByPage(listFileChannel, listBuffer, ls1, map1);

                // read the offPos buffer, get the offset. write the offPos into buffer
                BufferAndByte ol1 = readListBufferByPage(segID1, ob1, pageIDReadOff1, wi1.lenD, "d");
                ob1 = ol1.bb;
                byte[] os1 = ol1.b;
                pageIDReadOff1 = ol1.pageIDRead;

                byte[] osNew = joinTwoOffPosList(os1, null, offsetPos);
                writeListBufferByPage(offPosFileChannel, offPosBuffer, osNew);

                //read and write the position list into buffer, if buffer full, append it into disk
                BufferAndByte rp1 = readListBufferByPage(segID1, pb1, pageIDReadPos1, ol1.offEnd - ol1.offStart, "c");
                pb1 = rp1.bb;
                pageIDReadPos1 = rp1.pageIDRead;
                writeListBufferByPage(positionFileChannel, positionBuffer, rp1.b);

                offsetPos += rp1.b.length;

                //write the word info into buffer
                PositionalWordInfo wi = new PositionalWordInfo();
                wi.setWordInfo(wi1.word, offsetB, ls1.size(), offsetD, osNew.length);
                wi.writeOneWord(wordsBuffer);
                offsetB += ls1.size() * (4 * 2);
                offsetD += osNew.length;
            }
        }

        // set position file
        positionFileChannel.appendAllBytes(fitBuffer(positionBuffer));
        positionFileChannel.close();
        deleteFile(indexFolder + "/segment" + segID1 + "c");
        deleteFile(indexFolder + "/segment" + segID2 + "c");

        File f1 = new File(indexFolder + "/segment c tmp");
        File f2 = new File(indexFolder + "/segment" + segID1/2 + "c");
        f1.renameTo(f2);

        // set list file
        listFileChannel.appendAllBytes(fitBuffer(listBuffer));
        listFileChannel.close();
        deleteFile(indexFolder + "/segment" + segID1 + "b");
        deleteFile(indexFolder + "/segment" + segID2 + "b");

        f1 = new File(indexFolder + "/segment b tmp");
        f2 = new File(indexFolder + "/segment" + segID1/2 + "b");
        f1.renameTo(f2);

        // set offPos file
        offPosFileChannel.appendAllBytes(fitBuffer(offPosBuffer));
        offPosFileChannel.close();
        deleteFile(indexFolder + "/segment" + segID1 + "d");
        deleteFile(indexFolder + "/segment" + segID2 + "d");

        f1 = new File(indexFolder + "/segment d tmp");
        f2 = new File(indexFolder + "/segment" + segID1/2 + "d");
        f1.renameTo(f2);

        // set word file
        path = Paths.get(indexFolder + "/segment" + segID1/2 + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(path);

        writeFirstPageOfWord(wordsFileChannel, wordsBuffer.position());

        wordsFileChannel.appendAllBytes(fitBuffer(wordsBuffer));
        wordsFileChannel.close();
    }

    /**
     * Performs a phrase search on a positional index.
     * Phrase search means the document must contain the consecutive sequence of keywords in exact order.
     *
     * You could assume the analyzer won't convert each keyword into multiple tokens.
     * Throws UnsupportedOperationException if the inverted index is not a positional index.
     *
     * @param phrase, a consecutive sequence of keywords
     * @return a iterator of documents matching the query
     */

        @Override
    public Iterator<Document> searchPhraseQuery(List<String> phrase) {
        Preconditions.checkNotNull(phrase);
        List<Document> docs = new ArrayList<>();
        int totalSegments = getNumSegments();

        // searching each segment
        for (int seg = 0; seg < totalSegments; seg++) {
            Path dictSeg = Paths.get(indexFolder + "/segment" + seg + "a");
            PageFileChannel pfc = PageFileChannel.createOrOpen(dictSeg);

            // the previous state of the phrase lists
            Map<Integer, List<Integer>> prev = new HashMap<>();

            // the position of the keyword in the phrase
            int pos = 0;

            // searching for each individual keyword
            for (String keyword : phrase) {
                List<String> word = analyzer.analyze(keyword);
                if (word.size() == 0 || word.get(0).length() == 0) {
                    continue;
                }
                keyword = word.get(0);
                Map<Integer, List<Integer>> curr = findWord(pfc, keyword, seg);
                if (curr.isEmpty()) {
                    break;
                }
                if (pos == 0) {
                    prev = mapMerge(curr, curr, 0);
                }
                else {
                    prev = mapMerge(prev, curr, pos);
                }
                
                // increment the position
                pos++;
            }
            if (prev.isEmpty()) {
                continue;
            }
            
            // find the documents matching the IDs
            List<Integer> idList = new ArrayList<>(prev.keySet());
            List<Document> docList = getDocs(seg, idList);
            if (docList.isEmpty()) {
                continue;
            }
            docs.addAll(docList);
        }
        return docs.iterator();
    }

    /**
     * Finds the word in the loaded PageChannelFile of the dictionary.
     *
     * @param pfc loaded segment from the disk
     * @param target the keyword to look for
     * @param segID the segment number to look for the target in
     * @return a list of integers containing the ID of documents matching the search
     */

        private Map<Integer, List<Integer>> findWord(PageFileChannel pfc, String target, int segID) {
        Map<Integer, List<Integer>> wordList = new HashMap<>();
        
        // get number of pages
        int cap = pfc.getNumPages();
        
        // start from the dictionary
        int pageNumber = 1;
        if (pageNumber >= cap) {
            System.err.println("dictionary too short!");
            System.exit(1);
        }
        ByteBuffer bb = pfc.readPage(pageNumber);
        bb.limit(PageFileChannel.PAGE_SIZE);
        bb.position(0);
        
        // reading the dictionary word info
        while (true) {
            int wordLength;
            byte[] word;
            int pageID;
            int offset;
            int length;
            try {
                wordLength = bb.getInt();
            }
            catch (BufferUnderflowException e) {
                pageNumber++;
                if (pageNumber >= cap) {
                    break;
                }
                bb = pfc.readPage(pageNumber);
                bb.position(0);
                wordLength = bb.getInt();
            }
            if (wordLength == 0) {
                return wordList;
            }
            word = new byte[wordLength];
            for (int i = 0; i < wordLength; i++) {
                try {
                    word[i] = bb.get();
                }
                catch (BufferUnderflowException e) {
                    pageNumber++;
                    if (pageNumber >= cap) {
                        break;
                    }
                    bb = pfc.readPage(pageNumber);
                    bb.position(0);
                    word[i] = bb.get();
                }
            }
            
            // dictionary word
            String dictWord = new String(word);
            try {
                pageID = bb.getInt();
            }
            catch (BufferUnderflowException e) {
                pageNumber++;
                if (pageNumber >= cap) {
                    break;
                }
                bb = pfc.readPage(pageNumber);
                bb.position(0);
                pageID = bb.getInt();
            }
            try {
                offset = bb.getInt();
            }
            catch (BufferUnderflowException e) {
                pageNumber++;
                if (pageNumber >= cap) {
                    break;
                }
                bb = pfc.readPage(pageNumber);
                bb.position(0);
                offset = bb.getInt();
            }
            try {
                length = bb.getInt();
            }
            catch (BufferUnderflowException e) {
                pageNumber++;
                if (pageNumber >= cap) {
                    break;
                }
                bb = pfc.readPage(pageNumber);
                bb.position(0);
                length = bb.getInt();
            }
            
            // compare dictionary word with the target keyword
            if (dictWord.equals(target)) {
                wordList = getPositionalIndexList(segID, pageID, offset, length);
                return wordList;
            }
        }
        return wordList;
    }
    
    /**
     * Get all the documents matching the ID list in a segment.
     *
     * @param segID the number of segment
     * @param idList a list of document IDs
     * @return a list of documents matching the search
     */

    private List<Document> getDocs(int segID, List<Integer> idList) {
        List<Document> docIDList = new ArrayList<>();
        
        // reading the documents in the segment
        String path = indexFolder + "/segment" + segID + ".db";
        DocumentStore ds = MapdbDocStore.createOrOpen(path);
        Iterator<Integer> docsIterator = ds.keyIterator();
        
        // finding docIDs that match the targetID
        while (docsIterator.hasNext()) {
            int tempID = docsIterator.next();
            if (!idList.isEmpty() && idList.contains(tempID)) {
                docIDList.add(ds.getDocument(tempID));
            }
        }
        
        // closing the document store
        ds.close();
        return docIDList;
    }

    /**
     * Get the inverted list in a certain page of a segment with given offset and length.
     *
     * @param segID the ID of segment
     * @param pageID the ID of page
     * @param offset an offset of the inverted list
     * @param length the length of the inverted list
     * @return a list of document IDs matching the search
     */

    private Map<Integer, List<Integer>> getPositionalIndexList(int segID, int pageID, int offset, int length) {
        Path path = Paths.get(indexFolder + "/segment" + segID + "b");
        PageFileChannel pfc = PageFileChannel.createOrOpen(path);
        
        // number of pages in this segment
        int cap = pfc.getNumPages();
        ByteBuffer indexBuffer = pfc.readPage(pageID);
        indexBuffer.position(offset);
        
        // reading docID, offset, and length in the positional listing
        Map<Integer, List<Integer>> posList = new HashMap<>();
        for (int i = 0; i < length; i++) {
            int docID;
            int positionalOffset;
            int positionalLength;
            try {
                docID = indexBuffer.getInt();
            }
            catch (BufferUnderflowException e) {
                pageID++;
                if (pageID >= cap) {
                    System.err.println("reached end of pages while reading list!");
                    System.exit(-1);
                }
                indexBuffer = pfc.readPage(pageID);
                indexBuffer.position(0);
                docID = indexBuffer.getInt();
            }
            try {
                positionalOffset = indexBuffer.getInt();
            }
            catch (BufferUnderflowException e) {
                pageID++;
                if (pageID >= cap) {
                    System.err.println("reached end of pages while reading list!");
                    System.exit(-1);
                }
                indexBuffer = pfc.readPage(pageID);
                indexBuffer.position(0);
                positionalOffset = indexBuffer.getInt();
            }
            try {
                positionalLength = indexBuffer.getInt();
            }
            catch (BufferUnderflowException e) {
                pageID++;
                if (pageID >= cap) {
                    System.err.println("reached end of pages while reading list!");
                    System.exit(-1);
                }
                indexBuffer = pfc.readPage(pageID);
                indexBuffer.position(0);
                positionalLength = indexBuffer.getInt();
            }
            List<Integer> positions = getPositionalList(segID, positionalOffset, positionalLength);
            posList.put(docID, positions);
        }
        pfc.close();
        return posList;
    }

    /**
     * Get the inverted list in a certain page of a segment with given offset and length.
     *
     * @param segID the segment ID
     * @param offset an offset of the positional list
     * @param length the length of the positional list
     * @return a list of integers declaring the positional indices
     */

    private List<Integer> getPositionalList(int segID, int offset, int length) {
        List<Integer> list = new ArrayList<>();
        Path path = Paths.get(indexFolder + "/segment" + segID + "c");
        PageFileChannel pfc = PageFileChannel.createOrOpen(path);
        int cap = pfc.getNumPages();
        
        // getting the pageID
        int pageID = offset / PageFileChannel.PAGE_SIZE;
        ByteBuffer posBuffer = pfc.readPage(pageID);

        // obtaining the offset in the page
        int pos = offset - pageID * PageFileChannel.PAGE_SIZE;
        posBuffer.position(pos);
        
        // reading the list
        for (int i = 0; i < length; i++) {
            int id;
            try {
                id = posBuffer.getInt();
                list.add(id);
            }
            catch (BufferUnderflowException e) {
                pageID++;
                if (pageID >= cap) {
                    System.err.println("reached end of file while reading positional list");
                    System.exit(1);
                }
                posBuffer = pfc.readPage(pageID);
                posBuffer.position(0);
                id = posBuffer.getInt();
                list.add(id);
            }
        }
        pfc.close();
        return list;
    }

    /**
     * Finding the overlap of two listings as a map
     *
     * @param prev the previous state of the listings
     * @param curr the current state of the listings
     * @param offset the offset that needs to be checked
     * @return the special overlap of the two listings based on the position lists
     */

    private Map<Integer, List<Integer>> mapMerge(Map<Integer, List<Integer>> prev, Map<Integer, List<Integer>> curr, int offset) {
        Map<Integer, List<Integer>> merged = new HashMap<>();
        if (offset == 0) {
            return curr;
        }
        if (curr.isEmpty() || prev.isEmpty()) {
            throw new RuntimeException("empty map(s) passed");
        }
        if (offset < 0) {
            throw new RuntimeException("offset cannot be negative");
        }
        List<Integer> prevList = new ArrayList<>(prev.keySet());
        for (int id : prevList) {
            if (!curr.containsKey(id)) {
                continue;
            }
            List<Integer> list = postingMerge(prev.get(id), curr.get(id), offset);
            if (list.isEmpty()) {
                continue;
            }
            merged.put(id, list);
        }
        return merged;
    }

    /**
     * Performs merge for two lists
     *
     * @param list1 the old list of results
     * @param list2 the new list of results
     * @param offset the comparator to combine to lists
     * @return the overlap of two lists
     */

    private List<Integer> postingMerge(List<Integer> list1, List<Integer> list2, int offset) {
        List<Integer> merged = new ArrayList<>();
        if (list1.size() == 0 || list2.size() == 0) {
            return merged;
        }
        int p1 = 0;
        int p2 = 0;
        while (p1 < list1.size() && p2 < list2.size()) {
            int num = list2.get(p2) - list1.get(p1);
            if (num == offset) {
                int first = list1.get(p1);
                merged.add(first);
                p1++;
                p2++;
            }
            else if (num < offset) {
                p2++;
            }
            else {
                p1++;
            }
        }
        return merged;
    }

    /**
     * Reads a disk segment of a positional index into memory based on segmentNum.
     * This function is mainly used for checking correctness in test cases.
     *
     * Throws UnsupportedOperationException if the inverted index is not a positional index.
     *
     * @param segmentNum n-th segment in the inverted index (start from 0).
     * @return in-memory data structure with all contents in the index segment, null if segmentNum don't exist.
     */

    @Override
    public PositionalIndexSegmentForTest getIndexSegmentPositional(int segmentNum) {
        if (segmentID == 0) {
            return null;
        }
        Map<String, List<Integer>> invertedLists = new TreeMap<>();
        Map<Integer, Document> documents = new TreeMap<>();
        Map<String, List<Integer>> wordDic = new TreeMap<>();
        Table<String, Integer, List<Integer>> positions = TreeBasedTable.create();

        // read segmentXXa
        Path wordsPath = Paths.get(indexFolder + "/segment" + segmentNum + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);

        ByteBuffer wordsBuffer = wordsFileChannel.readAllPages();
        wordsFileChannel.close();
        readFirstPageOfWord(wordsBuffer);

        // based on remaining page, build map<String, List<Integer>> in which key is keyword, value is length(list),
        // lenOfByte(offsetPos list)
        PositionalWordInfo wi = new PositionalWordInfo();
        while (wordsBuffer.hasRemaining()) {
            wi.readOneWord(wordsBuffer);
            wordDic.put(wi.word, Arrays.asList(wi.lenB, wi.lenD));
        }

        // read segmentXXb, segmentXXc and segmentXXd, build invertedLists and positions
        Path listPath = Paths.get(indexFolder + "/segment" + segmentNum + "b");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(listPath);
        ByteBuffer listBuffer = listFileChannel.readAllPages();
        listFileChannel.close();
        listBuffer.rewind();

        Path positionPath = Paths.get(indexFolder + "/segment" + segmentNum + "c");
        PageFileChannel positionFileChannel = PageFileChannel.createOrOpen(positionPath);
        ByteBuffer positionBuffer = positionFileChannel.readAllPages();
        positionFileChannel.close();
        positionBuffer.rewind();

        Path offPosPath = Paths.get(indexFolder + "/segment" + segmentNum + "d");
        PageFileChannel offPosFileChannel = PageFileChannel.createOrOpen(offPosPath);
        ByteBuffer offPosBuffer = offPosFileChannel.readAllPages();
        offPosFileChannel.close();
        offPosBuffer.rewind();

        for (String word: wordDic.keySet()) {
            int listLen = wordDic.get(word).get(0);
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < listLen; i++) {
                list.add(listBuffer.getInt());
                listBuffer.getInt();
            }
            invertedLists.put(word, list);

            int offPosLen = wordDic.get(word).get(1);
            byte[] offPosb = new byte[offPosLen];
            offPosBuffer.get(offPosb, 0, offPosLen);
            List<Integer> offPos = compressor.decode(offPosb, 0, offPosLen);

            for (int i = 0; i < offPos.size() - 1; i++) {
                int lenPos = offPos.get(i + 1) - offPos.get(i);
                byte[] positionb = new byte[lenPos];
                positionBuffer.get(positionb, 0, lenPos);
                List<Integer> positionList = compressor.decode(positionb, 0, positionb.length);
                positions.put(word, list.get(i), positionList);
            }
        }

        // read segmentXX.db, build map<Integer, Document> documents
        DocumentStore ds = MapdbDocStore.createOrOpen(indexFolder + "/segment" + segmentNum + ".db");
        Iterator<Map.Entry<Integer, Document>> itr = ds.iterator();
        while(itr.hasNext()) {
            Map.Entry<Integer, Document> entry = itr.next();
            documents.put(entry.getKey(), entry.getValue());
        }
        ds.close();
        return new PositionalIndexSegmentForTest(invertedLists, documents, positions);
    }


    /**
     * Performs top-K ranked search using TF-IDF.
     * Returns an iterator that returns the top K documents with highest TF-IDF scores.
     *
     * Each element is a pair of <Document, Double (TF-IDF Score)>.
     *
     * If parameter `topK` is null, then returns all the matching documents.
     *
     * Unlike Boolean Query and Phrase Query where order of the documents doesn't matter,
     * for ranked search, order of the document returned by the iterator matters.
     *
     * @param keywords, a list of keywords in the query
     * @param topK, number of top documents weighted by TF-IDF, all documents if topK is null
     * @return a iterator of top-k ordered documents matching the query
     */
    @Override
    public Iterator<Pair<Document, Double>> searchTfIdf(List<String> keywords, Integer topK) {
        // analyze the query
        String q = String.join(" ", keywords);
        List<String> words = analyzer.analyze(q);
        Map<String, Double> IDF = new HashMap<>();
        Map<String, Integer> queryTF = new HashMap<>();
        Set<String> wordSet = new HashSet<>();
        wordSet.addAll(words);

        // If there are only one word in query, set IDF to 1, queryTF to 1
        if (wordSet.size() == 1) {
            IDF.put(words.get(0), 1.0);
            queryTF.put(words.get(0), 1);
        } else {
            // In the first pass, access each segment to calculate the IDF of the query keywords
            for (String w : words) {
                if (!IDF.containsKey(w)) {
                    IDF.put(w, computeIDF(w));
                }

                if(queryTF.containsKey(w))
                    queryTF.put(w, queryTF.get(w) + 1);
                else
                    queryTF.put(w, 1);
            }
        }

        PriorityQueue<Map.Entry<Pair<Integer, Integer>, Double>> pq = new PriorityQueue<>(
                (a,b) -> a.getValue().compareTo(b.getValue())
        );

        // In the second pass
        int segNum = getNumSegments();
        for (int i = 0; i < segNum; i++) {
            Map<Pair<Integer, Integer>, Double> score = new HashMap<>();
            Map<Pair<Integer, Integer>, Double> dotProductAccumulator = new HashMap<>();
            Map<Pair<Integer, Integer>, Double> vectorLengthAccumulator = new HashMap<>();

            // read segmentXXa
            Path wordsPath = Paths.get(indexFolder + "/segment" + i + "a");
            PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);
            Path listPath = Paths.get(indexFolder + "/segment" + i + "b");
            PageFileChannel listFileChannel = PageFileChannel.createOrOpen(listPath);

            // search the dictionary for the token, get the posting list and TF for each document
            for (String w: wordSet) {
                PositionalWordInfo wi = findPositionalWord(wordsFileChannel, w);

                // if there are no keyword in dictionary, continue the next loop
                if (wi.word == null) {
                    continue;
                }

                int page = wi.offsetB/ PageFileChannel.PAGE_SIZE;
                ByteBuffer listBuffer = listFileChannel.readPage(page);
                listBuffer.position(wi.offsetB);
                BufferAndList bl = getIndexListGivenLen(i, listBuffer, page, wi.lenB, false, 0);
                Map<Integer,Integer> docMap = bl.map;

                // for each docID on the postingList of w, compute tfidf
                for (int docID: docMap.keySet()) {
                    double tfIdf = docMap.get(docID) * IDF.get(w);
                    double queryTfIdf = queryTF.get(w) * IDF.get(w);
//                    System.out.println("doc:"+i+","+docID+";word:"+w+";tfIdf"+tfIdf+";queryTfIdf"+queryTfIdf);
                    Pair<Integer, Integer> doc = new Pair<>(i, docID);

                    if (dotProductAccumulator.containsKey(doc)) {
                        dotProductAccumulator.put(doc, dotProductAccumulator.get(doc) + tfIdf * queryTfIdf);
                        vectorLengthAccumulator.put(doc, vectorLengthAccumulator.get(doc) + tfIdf * tfIdf);
                    } else {
                        dotProductAccumulator.put(doc, tfIdf * queryTfIdf);
                        vectorLengthAccumulator.put(doc, tfIdf * tfIdf);
                    }
                }
            }

            wordsFileChannel.close();
            listFileChannel.close();

            // for each docID in this segment, compute the score and add it to priority queue
            for (Pair<Integer, Integer> d: dotProductAccumulator.keySet()) {
                if (vectorLengthAccumulator.get(d) != 0.0) {
                    score.put(d, (double) dotProductAccumulator.get(d) / Math.sqrt(vectorLengthAccumulator.get(d)));
                }
            }
            pq.addAll(score.entrySet());
            if (topK != null) {
                while (pq.size() > topK)
                    pq.poll();
            }
        }

        // based on <SegmentID, LocalDocID> retrieve document
        List<Pair<Document, Double>> result = new ArrayList<>();
        int pqSize = pq.size();
        for (int i = 0; i < pqSize; i++) {
            Map.Entry<Pair<Integer, Integer>, Double> tmp = pq.poll();
            Pair<Integer, Integer> doc = tmp.getKey();
            result.add(0, new Pair<>(getDoc(doc), tmp.getValue()));
        }
//        System.out.println(result);
        return result.iterator();
    }


    /**
     * Find a word in the dictionary, if can not find, return an empty PositionalWordInfo
     */
    private PositionalWordInfo findPositionalWord (PageFileChannel wordsFileChannel, String w) {
        ByteBuffer wordsBuffer = wordsFileChannel.readAllPages();
        readFirstPageOfWord(wordsBuffer);

        PositionalWordInfo wi = new PositionalWordInfo();
        while (wordsBuffer.hasRemaining()) {
            wi.readOneWord(wordsBuffer);
            if (w.equals(wi.word)) {
                return wi;
            }
        }
        return new PositionalWordInfo();
    }

    /**
     * Returns the number of documents containing the token within the given segment.
     * The token should be already analyzed by the analyzer. The analyzer shouldn't be applied again.
     */

    @Override
    public int getDocumentFrequency(int segmentNum, String token) {
        int lenList = 0;
        // read segmentXXa
        Path wordsPath = Paths.get(indexFolder + "/segment" + segmentNum + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);
        ByteBuffer wordsBuffer = wordsFileChannel.readAllPages();
        wordsFileChannel.close();
        readFirstPageOfWord(wordsBuffer);

        // based on remaining page, search the dictionary for the token and get the len(list)
        PositionalWordInfo wi = new PositionalWordInfo();
        while (wordsBuffer.hasRemaining()) {
            wi.readOneWord(wordsBuffer);
            if (token.equals(wi.word)) {
                lenList = wi.lenB;
                break;
            }
        }
        return lenList;
    }
}
