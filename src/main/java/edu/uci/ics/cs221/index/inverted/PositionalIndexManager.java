package edu.uci.ics.cs221.index.inverted;

import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import edu.uci.ics.cs221.analysis.Analyzer;
import edu.uci.ics.cs221.storage.Document;
import edu.uci.ics.cs221.storage.DocumentStore;
import edu.uci.ics.cs221.storage.MapdbDocStore;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class PositionalIndexManager extends InvertedIndexManager {
    private static Table<String, Integer, List<Integer>> positions;
    private Compressor compressor;

    public PositionalIndexManager(String indexFolder, Analyzer analyzer, Compressor compressor) {
        super(indexFolder, analyzer);
        this.positions = TreeBasedTable.create();
        this.compressor = compressor;
    }

    /**
     * Adds a document to the inverted index and the position list
     * Document should live in a in-memory buffer until `flush()` is called to write the segment to disk.
     * @param document
     */

    @Override
    public void addDocument(Document document) {
        List<String> wordList = analyzer.analyze(document.getText());
        for (int i = 0; i < wordList.size(); i++) {
            String word = wordList.get(i);
            if (invertedLists.containsKey(word)) {
                List<Integer> tmp = invertedLists.get(word);
                if (!(tmp.get(tmp.size() - 1) == docID)) {
                    tmp.add(docID);
                }
                tmp = positions.get(word, docID);
                if (tmp == null) {
                    positions.put(word, docID, new LinkedList<>(Arrays.asList(i)));
                } else {
                    tmp.add(i);
                }
            } else {
                invertedLists.put(word, new LinkedList<>(Arrays.asList(docID)));
                positions.put(word, docID, new LinkedList<>(Arrays.asList(i)));
            }
        }
        documents.put(docID, document);
        docID += 1;

        // if the num of document reach DEFAULT_FLUSH_THRESHOLD, call flush()
        if (docID >= DEFAULT_FLUSH_THRESHOLD) {
            flush();
        }
    }

    /**
     * Flushes all the documents in the in-memory segment buffer to disk. If the buffer is empty, it should not do anything.
     * flush() writes the segment to disk containing the posting list and the corresponding document store.
     */

    @Override
    public void flush() {
        // If the buffer is empty, return
        if (invertedLists.size() == 0 && documents.size() == 0) {
            return;
        }
        docID = 0;

        // store the len(keywords), keywords, page(list), offset(list) (the offset of this page), len(list)
        // in segmentXXa, with the first page have the total number of bytes the remaining pages will use

        ByteBuffer wordsBuffer = ByteBuffer.allocate(STORE_PARAMETER * invertedLists.size());
        int offset = 0;
        int pageID = 0;

        for (String word: invertedLists.keySet()) {
            WordInfo wi = new WordInfo();
            wi.setWordInfo(word, pageID, offset, invertedLists.get(word).size());
            wi.writeOneWord(wordsBuffer);

            offset += invertedLists.get(word).size() * 3 * 4;
            if (offset >= PageFileChannel.PAGE_SIZE) {
                pageID += 1;
                offset -= PageFileChannel.PAGE_SIZE;
            }
        }

        Path wordsPath = Paths.get(indexFolder + "/segment" + segmentID + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);

        // write the first page
        writeFirstPageOfWord(wordsFileChannel, wordsBuffer.position());

        // write the remaining page
        wordsFileChannel.appendAllBytes(wordsBuffer);
        wordsFileChannel.close();

        // store the posting lists in segmentXXb (for every docID, offset(position list), len(position list)),
        // store all the position lists in segmentXXc

        ByteBuffer listBuffer = ByteBuffer.allocate(STORE_PARAMETER * invertedLists.size());
        ByteBuffer positionBuffer = ByteBuffer.allocate(STORE_PARAMETER * 2 * invertedLists.size());

        int offsetPos = 0;
        for (String word: invertedLists.keySet()) {
            List<Integer> postingList = invertedLists.get(word);
            for (int docID: postingList) {
                List<Integer> positionList = positions.get(word, docID);


                for (int pos: positionList) {
                    positionBuffer.putInt(pos);
                }
                listBuffer.putInt(docID);
                listBuffer.putInt(offsetPos);
                listBuffer.putInt(positionList.size());
                offsetPos += positionList.size() * 4;
            }
        }

        Path listPath = Paths.get(indexFolder+"/segment" + segmentID + "b");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(listPath);
        listFileChannel.appendAllBytes(listBuffer);
        listFileChannel.close();

        Path positionPath = Paths.get(indexFolder+"/segment" + segmentID + "c");
        PageFileChannel positionFileChannel = PageFileChannel.createOrOpen(positionPath);
        positionFileChannel.appendAllBytes(positionBuffer);
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
     * Get the posting list of a word and the position list info: starting offset and whole position
     * list length in a given segment from a buffer, using the length of the list. If addNum = true,
     * add the number n to all the elements in the list and the key of map
     *
     * @param segID the segment ID
     * @param bb the BybeBuffer being read with capacity = PAGE_SIZE
     * @param pageIDRead the page of the list file being read
     * @param len the length of the posting list
     * @param addNum whether to add number n to all the elements in the list
     * @param n the number being added
     * @return the BybeBuffer being read, the posting list, the map whose key is the docID, value is length
     * of the position list of the docID, the page of the list file being read, the starting offset of the
     * whole position list, the length of the whole position list
     */

    @Override
    BufferListMap getIndexListGivenLen(int segID, ByteBuffer bb, int pageIDRead, int len, boolean addNum, int n) {
        List<Integer> list = new LinkedList<>();
        Map<Integer, Integer> map = new TreeMap<>();
        int remainInt = (bb.limit() - bb.position()) / (4 * 3);
        int lSize = len;
        int TotallenPos = 0;
        int OffsetPos = 0;
        boolean readOffsetPos = true;

        // if the posting list is longer than the remaining buffer, first read the buffer,
        // then open the next page and read

        while (lSize / remainInt >= 1) {
            for (int i = 0; i < remainInt; i++) {
                int docID = bb.getInt();
                if (addNum) {
                    docID += n;
                }
                list.add(docID);
                int tmp = bb.getInt();
                if (readOffsetPos) {
                    OffsetPos = tmp;
                    readOffsetPos = false;
                }
                int lenPos = bb.getInt();
                TotallenPos += lenPos;
                map.put(docID, lenPos);
            }
            pageIDRead += 1;
            bb = readSegPage(segID, "b", pageIDRead);
            lSize -= remainInt;
            remainInt = PageFileChannel.PAGE_SIZE / (4 * 3);
        }

        // if the posting list is no longer than the remaining buffer, just read the buffer
        for (int i = 0; i < lSize; i++) {
            int docID = bb.getInt();
            if (addNum) {
                docID += n;
            }
            list.add(docID);
            int tmp = bb.getInt();
            if (readOffsetPos) {
                OffsetPos = tmp;
                readOffsetPos = false;
            }
            int lenPos = bb.getInt();
            TotallenPos += lenPos;
            map.put(docID, lenPos);
        }
        return new BufferListMap(bb, list, map, pageIDRead, OffsetPos, TotallenPos);
    }

    /**
     * Write the posting list of a word and position list info buffer by page, with order: docID, offset(positionList), len(positionList)
     * if the list length is larger than the page size, append the page and open another buffer
     *
     * @param pfc the file being written
     * @param bb the BybeBuffer being written with capacity = PAGE_SIZE
     * @param l the posting list
     * @param map the map whose key is the docID, value is length of the position list of the docID
     * @param offsetPos the starting offset of the position list
     * @return the ending offset of the position list
     */

    @Override
    int writeListBufferByPage(PageFileChannel pfc, ByteBuffer bb, List<Integer> l, Map<Integer, Integer> map, int offsetPos) {
        int lSize = l.size();
        int remainInt = (bb.limit() - bb.position()) / (4 * 3);
        int lPos = 0;

        // if the posting list is longer than the remaining buffer, first write the buffer,
        // then append the page and open another buffer to write
        while (lSize / remainInt >= 1) {
            for (int i = 0; i < remainInt; i++, lPos++) {
                int docID = l.get(lPos);
                bb.putInt(docID);
                bb.putInt(offsetPos);
                int lenPos = map.get(docID);
                bb.putInt(lenPos);
                offsetPos += lenPos * 4;
            }
            pfc.appendPage(bb);
            bb.clear();
            lSize -= remainInt;
            remainInt = PageFileChannel.PAGE_SIZE / (4 * 3);
        }

        // if the posting list is no longer than the remaining buffer, just write the buffer
        for (int i = 0; i < lSize; i++, lPos++) {
            int docID = l.get(lPos);
            bb.putInt(docID);
            bb.putInt(offsetPos);
            int lenPos = map.get(docID);
            bb.putInt(lenPos);
            offsetPos += lenPos * 4;
        }
        return offsetPos;
    }

    /**
     * Get a ByteBuffer containing the whole position list of a word be page in a given segment from a buffer,
     * using the total length of position lists.
     *
     * @param segID the segment ID
     * @param bbr the BybeBuffer being read with capacity = PAGE_SIZE
     * @param pageIDReadPos the page of the posting list file being read
     * @param totalLenPos the total length of position lists
     * @return the BybeBuffer being read, the BybeBuffer being written, the page of the posting list file being read
     */

    @Override
    BufferBuffer readPositionBufferByPage(int segID, ByteBuffer bbr, int pageIDReadPos, int totalLenPos) {
        int remainInt = (bbr.limit() - bbr.position()) / 4;
        int lSize = totalLenPos;
        ByteBuffer bbw = ByteBuffer.allocate(4 * totalLenPos);

        // if the whole position lists length is bigger than the remaining buffer, first read the buffer,
        // then open the next page and read

        while (lSize / remainInt >= 1) {
            byte[] positionList = new byte[4 * remainInt];
            bbr.get(positionList, 0, 4 * remainInt);
            bbw.put(positionList);
            pageIDReadPos += 1;
            bbr = readSegPage(segID, "c", pageIDReadPos);
            lSize -= remainInt;
            remainInt = PageFileChannel.PAGE_SIZE / 4;
        }

        // if the whole position lists length is no bigger than the remaining buffer, just read the buffer
        byte[] positionList = new byte[4 * lSize];
        bbr.get(positionList, 0, 4 * lSize);
        bbw.put(positionList);
        bbw.rewind();
        return new BufferBuffer(bbr, bbw, pageIDReadPos);
    }

    /**
     * write a ByteBuffer containing position list into buffer by page, if the list length is larger than the page size,
     * append the page and open another buffer
     *
     * @param pfc the file being written
     * @param bbw the BybeBuffer being written with capacity = PAGE_SIZE
     * @param bbr the BybeBuffer being read
     */

    @Override
    void writePositionBufferByPage(PageFileChannel pfc, ByteBuffer bbw, ByteBuffer bbr) {
        int Size = bbr.limit() - bbr.position();
        int remain = bbw.limit() - bbw.position();

        // if the reading buffer is longer than the remaining writing buffer, first write the buffer,
        // then append the page and open another buffer to write
        while (Size / remain >= 1) {
            byte[] positionList = new byte[remain];
            bbr.get(positionList, 0, remain);
            bbw.put(positionList);

            pfc.appendPage(bbw);
            bbw.clear();
            Size -= remain;
            remain = PageFileChannel.PAGE_SIZE;
        }

        // if the reading buffer is no longer than the remaining writing buffer, just write the buffer
        byte[] positionList = new byte[Size];
        bbr.get(positionList, 0, Size);
        bbw.put(positionList);
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
        ByteBuffer positionBuffer = ByteBuffer.allocate(PageFileChannel.PAGE_SIZE);

        WordInfo wi1 = new WordInfo();
        wi1.readOneWord(wb1);
        WordInfo wi2 = new WordInfo();
        wi2.readOneWord(wb2);

        int offset = 0;
        int pageID = 0;
        int offsetPos = 0;
        int pageIDRead1 = 0;
        int pageIDRead2 = 0;
        int pageIDReadPos1 = 0;
        int pageIDReadPos2 = 0;
        ByteBuffer lb1 = readSegPage(segID1, "b", pageIDRead1);
        ByteBuffer lb2 = readSegPage(segID2, "b", pageIDRead2);
        ByteBuffer pb1 = readSegPage(segID1, "c", pageIDReadPos1);
        ByteBuffer pb2 = readSegPage(segID2, "c", pageIDReadPos2);

        path = Paths.get(indexFolder + "/segment b tmp");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(path);

        path = Paths.get(indexFolder + "/segment c tmp");
        PageFileChannel positionFileChannel = PageFileChannel.createOrOpen(path);

        while (true) {
            if (wi1.word.equals(wi2.word)) {
                // add them to the dictionary, find their posting lists and add them to the disk
                //find their position list and add them to the disk, move both bb1 and bb2 to the next words

                //get the list according to the word, for list 2, all docID add numDoc1
                BufferListMap bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len, false, numDoc1);
                lb1 = bl1.bb;
                List<Integer> ls1 = bl1.list;
                Map<Integer, Integer> map1 = bl1.map;
                pageIDRead1 = bl1.pageIDRead;

                BufferListMap bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len, true, numDoc1);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                Map<Integer, Integer> map2 = bl2.map;
                pageIDRead2 = bl2.pageIDRead;

                ls1.addAll(ls2);
                map1.putAll(map2);

                //write the word info into buffer
                WordInfo wi = new WordInfo();
                wi.setWordInfo(wi1.word, pageID, offset, ls1.size());
                wi.writeOneWord(wordsBuffer);
                offset += ls1.size() * (4 * 3);

                //write the list info into buffer, if buffer full, append it into disk
                offsetPos = writeListBufferByPage(listFileChannel, listBuffer, ls1, map1, offsetPos);

                //read and write the position list into buffer, if buffer full, append it into disk
                BufferBuffer rp = readPositionBufferByPage(segID1, pb1, pageIDReadPos1, bl1.lenPos);
                pb1 = rp.bbRead;
                pageIDReadPos1 = rp.pageIDReadPos;
                writePositionBufferByPage(positionFileChannel, positionBuffer, rp.bbWrite);

                rp = readPositionBufferByPage(segID2, pb2, pageIDReadPos2, bl2.lenPos);
                pb2 = rp.bbRead;
                pageIDReadPos2 = rp.pageIDReadPos;
                writePositionBufferByPage(positionFileChannel, positionBuffer, rp.bbWrite);

                //check whether bb1 and bb2 can move to the next words
                if (!wb1.hasRemaining() || !wb2.hasRemaining()) {
                    break;
                }

                //move bb1 and bb2 to the next words
                wi1 = new WordInfo();
                wi1.readOneWord(wb1);
                wi2 = new WordInfo();
                wi2.readOneWord(wb2);
            }
            else if (wi1.word.compareTo(wi2.word) > 0) {
                // add key2 and its list to the disk, move bb2 to the next word
                BufferListMap bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len, true, numDoc1);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                Map<Integer, Integer> map2 = bl2.map;
                pageIDRead2 = bl2.pageIDRead;

                WordInfo wi = new WordInfo();
                wi.setWordInfo(wi2.word, pageID, offset, ls2.size());
                wi.writeOneWord(wordsBuffer);
                offset += ls2.size() * (4 * 3);

                offsetPos = writeListBufferByPage(listFileChannel, listBuffer, ls2, map2, offsetPos);

                BufferBuffer rp = readPositionBufferByPage(segID2, pb2, pageIDReadPos2, bl2.lenPos);
                pb2 = rp.bbRead;
                pageIDReadPos2 = rp.pageIDReadPos;
                writePositionBufferByPage(positionFileChannel, positionBuffer, rp.bbWrite);

                if (!wb2.hasRemaining()) {
                    if (offset >= PageFileChannel.PAGE_SIZE) {
                        pageID += 1;
                        offset -= PageFileChannel.PAGE_SIZE;
                    }

                    BufferListMap bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len, false, numDoc1);
                    lb1 = bl1.bb;
                    List<Integer> ls1 = bl1.list;
                    Map<Integer, Integer> map1 = bl1.map;
                    pageIDRead1 = bl1.pageIDRead;

                    wi = new WordInfo();
                    wi.setWordInfo(wi1.word, pageID, offset, ls1.size());
                    wi.writeOneWord(wordsBuffer);
                    offset += ls1.size() * (4 * 3);

                    offsetPos = writeListBufferByPage(listFileChannel, listBuffer, ls1, map1, offsetPos);

                    rp = readPositionBufferByPage(segID1, pb1, pageIDReadPos1, bl1.lenPos);
                    pb1 = rp.bbRead;
                    pageIDReadPos1 = rp.pageIDReadPos;
                    writePositionBufferByPage(positionFileChannel, positionBuffer, rp.bbWrite);

                    break;
                }
                wi2 = new WordInfo();
                wi2.readOneWord(wb2);
            }
            else {
                //add key1 and its list to the disk, move bb1 to the next word
                BufferListMap bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len, false, numDoc1);
                lb1 = bl1.bb;
                List<Integer> ls1 = bl1.list;
                Map<Integer, Integer> map1 = bl1.map;
                pageIDRead1 = bl1.pageIDRead;

                WordInfo wi = new WordInfo();
                wi.setWordInfo(wi1.word, pageID, offset, ls1.size());
                wi.writeOneWord(wordsBuffer);
                offset += ls1.size() * (4 * 3);

                offsetPos = writeListBufferByPage(listFileChannel, listBuffer, ls1, map1, offsetPos);

                BufferBuffer rp = readPositionBufferByPage(segID1, pb1, pageIDReadPos1, bl1.lenPos);
                pb1 = rp.bbRead;
                pageIDReadPos1 = rp.pageIDReadPos;
                writePositionBufferByPage(positionFileChannel, positionBuffer, rp.bbWrite);

                if (!wb1.hasRemaining()) {
                    if (offset >= PageFileChannel.PAGE_SIZE) {
                        pageID += 1;
                        offset -= PageFileChannel.PAGE_SIZE;
                    }

                    BufferListMap bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len, true, numDoc1);
                    lb2 = bl2.bb;
                    List<Integer> ls2 = bl2.list;
                    Map<Integer, Integer> map2 = bl2.map;
                    pageIDRead2 = bl2.pageIDRead;

                    wi = new WordInfo();
                    wi.setWordInfo(wi2.word, pageID, offset, ls2.size());
                    wi.writeOneWord(wordsBuffer);
                    offset += ls2.size() * (4 * 3);

                    offsetPos = writeListBufferByPage(listFileChannel, listBuffer, ls2, map2, offsetPos);

                    rp = readPositionBufferByPage(segID2, pb2, pageIDReadPos2, bl2.lenPos);
                    pb2 = rp.bbRead;
                    pageIDReadPos2 = rp.pageIDReadPos;
                    writePositionBufferByPage(positionFileChannel, positionBuffer, rp.bbWrite);

                    break;
                }
                wi1 = new WordInfo();
                wi1.readOneWord(wb1);
            }

            if (offset >= PageFileChannel.PAGE_SIZE) {
                pageID += 1;
                offset -= PageFileChannel.PAGE_SIZE;
            }
        }

        if (!wb1.hasRemaining() && wb2.hasRemaining()) {
            while (wb2.hasRemaining()) {
                if (offset >= PageFileChannel.PAGE_SIZE) {
                    pageID += 1;
                    offset -= PageFileChannel.PAGE_SIZE;
                }

                wi2 = new WordInfo();
                wi2.readOneWord(wb2);

                BufferListMap bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len, true, numDoc1);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                Map<Integer, Integer> map2 = bl2.map;
                pageIDRead2 = bl2.pageIDRead;

                WordInfo wi = new WordInfo();
                wi.setWordInfo(wi2.word, pageID, offset, ls2.size());
                wi.writeOneWord(wordsBuffer);
                offset += ls2.size() * (4 * 3);

                offsetPos = writeListBufferByPage(listFileChannel, listBuffer, ls2, map2, offsetPos);

                BufferBuffer rp = readPositionBufferByPage(segID2, pb2, pageIDReadPos2, bl2.lenPos);
                pb2 = rp.bbRead;
                pageIDReadPos2 = rp.pageIDReadPos;
                writePositionBufferByPage(positionFileChannel, positionBuffer, rp.bbWrite);
            }
        }

        if (wb1.hasRemaining() && !wb2.hasRemaining()) {
            while (wb1.hasRemaining()) {
                if (offset >= PageFileChannel.PAGE_SIZE) {
                    pageID += 1;
                    offset -= PageFileChannel.PAGE_SIZE;
                }

                wi1 = new WordInfo();
                wi1.readOneWord(wb1);

                BufferListMap bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len, false, numDoc1);
                lb1 = bl1.bb;
                List<Integer> ls1 = bl1.list;
                Map<Integer, Integer> map1 = bl1.map;
                pageIDRead1 = bl1.pageIDRead;

                WordInfo wi = new WordInfo();
                wi.setWordInfo(wi1.word, pageID, offset, ls1.size());
                wi.writeOneWord(wordsBuffer);
                offset += ls1.size() * (4 * 3);

                offsetPos = writeListBufferByPage(listFileChannel, listBuffer, ls1, map1, offsetPos);

                BufferBuffer rp = readPositionBufferByPage(segID1, pb1, pageIDReadPos1, bl1.lenPos);
                pb1 = rp.bbRead;
                pageIDReadPos1 = rp.pageIDReadPos;
                writePositionBufferByPage(positionFileChannel, positionBuffer, rp.bbWrite);
            }
        }

        // set position file
        positionFileChannel.appendAllBytes(positionBuffer);
        positionFileChannel.close();
        deleteFile(indexFolder + "/segment" + segID1 + "c");
        deleteFile(indexFolder + "/segment" + segID2 + "c");

        File f1 = new File(indexFolder + "/segment c tmp");
        File f2 = new File(indexFolder + "/segment" + segID1/2 + "c");
        f1.renameTo(f2);

        // set list file
        listFileChannel.appendAllBytes(listBuffer);
        listFileChannel.close();
        deleteFile(indexFolder + "/segment" + segID1 + "b");
        deleteFile(indexFolder + "/segment" + segID2 + "b");

        f1 = new File(indexFolder + "/segment b tmp");
        f2 = new File(indexFolder + "/segment" + segID1/2 + "b");
        f1.renameTo(f2);

        // set word file
        path = Paths.get(indexFolder + "/segment" + segID1/2 + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(path);

        writeFirstPageOfWord(wordsFileChannel, wordsBuffer.position());

        wordsFileChannel.appendAllBytes(wordsBuffer);
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

        throw new UnsupportedOperationException();
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
        Map<String, Integer> wordDic = new TreeMap<>();
        Table<String, Integer, List<Integer>> positions = TreeBasedTable.create();

        // read segmentXXa
        Path wordsPath = Paths.get(indexFolder + "/segment" + segmentNum + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);

        ByteBuffer wordsBuffer = wordsFileChannel.readAllPages();
        wordsFileChannel.close();
        int lim = readFirstPageOfWord(wordsBuffer);

        // based on remaining page, build map<String, Integer> in which key is keyword, value is len(list)
        WordInfo wi = new WordInfo();
        while (wordsBuffer.hasRemaining()) {
            wi.readOneWord(wordsBuffer);
            wordDic.put(wi.word, wi.len);
        }

        // read segmentXXb and segmentXXc, build invertedLists and positions
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

        for (String word: wordDic.keySet()) {
            List<Integer> postingList = new LinkedList<>();
            int listLen = wordDic.get(word);
            for (int i = 0; i < listLen; i++) {
                int docID = listBuffer.getInt();
                postingList.add(docID);
                int offsetPos = listBuffer.getInt();
                int lenPos = listBuffer.getInt();

                List<Integer> positionList = new LinkedList<>();
                for (int j = 0; j < lenPos; j++) {
                    positionList.add(positionBuffer.getInt());
                }
                positions.put(word, docID, positionList);
            }
            invertedLists.put(word, postingList);
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
}
