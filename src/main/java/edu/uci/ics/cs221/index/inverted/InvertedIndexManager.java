package edu.uci.ics.cs221.index.inverted;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import edu.uci.ics.cs221.analysis.Analyzer;
import edu.uci.ics.cs221.storage.Document;
import edu.uci.ics.cs221.storage.DocumentStore;
import edu.uci.ics.cs221.storage.MapdbDocStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.*;
import java.io.File;

import static com.google.common.collect.Maps.immutableEntry;


/**
 * This class manages an disk-based inverted index and all the documents in the inverted index.
 *
 * Please refer to the project 2 wiki page for implementation guidelines.
 */

public class InvertedIndexManager {

    /**
     * The default flush threshold, in terms of number of documents.
     * For example, a new Segment should be automatically created whenever there's 1000 documents in the buffer.
     *
     * In test cases, the default flush threshold could possibly be set to any number.
     */
    
    public static int DEFAULT_FLUSH_THRESHOLD = 1000;

    /**
     * The default merge threshold, in terms of number of segments in the inverted index.
     * When the number of segments reaches the threshold, a merge should be automatically triggered.
     *
     * In test cases, the default merge threshold could possibly be set to any number.
     */
    
    public static int DEFAULT_MERGE_THRESHOLD = 8;
    public static int STORE_PARAMETER = 50000;

    Map<String, List<Integer>> invertedLists;
    Map<Integer, Document> documents;
    int docID;
    int segmentID;
    Analyzer analyzer;
    String indexFolder;

    InvertedIndexManager(String indexFolder, Analyzer analyzer) {
        this.analyzer = analyzer;
        this.indexFolder = indexFolder;
        this.docID = 0;
        this.segmentID = 0;
        this.invertedLists = new TreeMap<>(); // use TreeMap so that the map is sorted
        this.documents = new TreeMap<>();
    }

    /**
     * Creates an inverted index manager with the folder and an analyzer
     */
    
    public static InvertedIndexManager createOrOpen(String indexFolder, Analyzer analyzer) {
        try {
            Path indexFolderPath = Paths.get(indexFolder);
            if (Files.exists(indexFolderPath) && Files.isDirectory(indexFolderPath)) {
                if (Files.isDirectory(indexFolderPath)) {
                    return new InvertedIndexManager(indexFolder, analyzer);
                } else {
                    throw new RuntimeException(indexFolderPath + " already exists and is not a directory");
                }
            } else {
                Files.createDirectories(indexFolderPath);
                return new InvertedIndexManager(indexFolder, analyzer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates a positional index with the given folder, analyzer, and the compressor.
     * Compressor must be used to compress the inverted lists and the position lists.
     *
     */

    public static InvertedIndexManager createOrOpenPositional(String indexFolder, Analyzer analyzer, Compressor compressor) {
        try {
            Path indexFolderPath = Paths.get(indexFolder);
            if (Files.exists(indexFolderPath) && Files.isDirectory(indexFolderPath)) {
                if (Files.isDirectory(indexFolderPath)) {
                    return new PositionalIndexManager(indexFolder, analyzer, compressor);
                } else {
                    throw new RuntimeException(indexFolderPath + " already exists and is not a directory");
                }
            } else {
                Files.createDirectories(indexFolderPath);
                return new PositionalIndexManager(indexFolder, analyzer, compressor);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Adds a document to the inverted index.
     * Document should live in a in-memory buffer until `flush()` is called to write the segment to disk.
     * @param document
     */
    
    public void addDocument(Document document) {
        List<String> wordList = analyzer.analyze(document.getText());
        for (String word: wordList) {
            if (invertedLists.containsKey(word)) {
                List<Integer> tmp = invertedLists.get(word);
                if (!(tmp.get(tmp.size() - 1) == docID)) {
                    tmp.add(docID);
                }
            } else {
                invertedLists.put(word, new LinkedList<>(Arrays.asList(docID)));
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
    
    public void flush() {
        // If the buffer is empty, return
        if (invertedLists.size() == 0 || documents.size() == 0) {
            return;
        }
        docID = 0;

        // store the len(keywords), keywords, page(list), offset(list) (the offset of this page), len(list)
        // in segmentXXa, with the first page have the total number of bytes the remaining pages will use

        Path wordsPath = Paths.get(indexFolder + "/segment" + segmentID + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);

        ByteBuffer wordsBuffer = ByteBuffer.allocate(STORE_PARAMETER * invertedLists.size());
        int offset = 0;
        int pageID = 0;

        for (String word: invertedLists.keySet()) {
            WordInfo wi = new WordInfo();
            wi.setWordInfo(word, pageID, offset, invertedLists.get(word).size());
            wi.writeOneWord(wordsBuffer);

            offset += invertedLists.get(word).size() * 4;
            if (offset >= PageFileChannel.PAGE_SIZE) {
                pageID += 1;
                offset -= PageFileChannel.PAGE_SIZE;
            }
        }

        // write the first page
        writeFirstPageOfWord(wordsFileChannel, wordsBuffer.position());

        // write the remaining page
        wordsFileChannel.appendAllBytes(wordsBuffer);
        wordsFileChannel.close();

        // store all the lists in segmentXXb

        Path listPath = Paths.get(indexFolder+"/segment" + segmentID + "b");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(listPath);

        ByteBuffer listBuffer = ByteBuffer.allocate(STORE_PARAMETER * invertedLists.size());
        for (String word: invertedLists.keySet()) {
            List<Integer> postingList = invertedLists.get(word);
            for (int num: postingList) {
                listBuffer.putInt(num);
            }
        }

        listFileChannel.appendAllBytes(listBuffer);
        listFileChannel.close();

        // store all the documents in segmentXX.db
        DocumentStore ds = MapdbDocStore.createWithBulkLoad(indexFolder + "/segment" + segmentID + ".db",documents.entrySet().iterator());
        ds.close();

        // clear the invertedLists and documents
        this.invertedLists = new TreeMap<>();
        this.documents = new TreeMap<>();
        segmentID += 1;

        // if the num of segment reach DEFAULT_MERGE_THRESHOLD, call merge()
        if (segmentID >= DEFAULT_MERGE_THRESHOLD) {
            mergeAllSegments();
        }
    }

    /**
     * write the first page into the file with an integer, which is the total number of bytes
     * the remaining pages will use
     *
     * @param pfc file being written
     * @param lim the total number of bytes the remaining pages will use
     */

    void writeFirstPageOfWord(PageFileChannel pfc, int lim) {
        ByteBuffer limitBuffer = ByteBuffer.allocate(PageFileChannel.PAGE_SIZE);
        limitBuffer.putInt(lim);
        pfc.appendPage(limitBuffer);
    }

    /**
     * read the first page on buffer, get the integer, and prepare the buffer for remaining reading
     *
     * @param bb ByteBuffer being read
     * @return cap the total number of bytes the remaining pages will use
     */

    int readFirstPageOfWord(ByteBuffer bb) {
        bb.rewind();
        int cap = bb.getInt();
        bb.limit(PageFileChannel.PAGE_SIZE + cap);
        bb.position(PageFileChannel.PAGE_SIZE);
        return cap;
    }

    /**
     * delete a file
     *
     * @param fileName the name of the file being deleted
     */

    void deleteFile(String fileName) {
        File file = new File(fileName);
        file.delete();
    }

    /**
     * Merges all the disk segments of the inverted index pair-wise.
     */
    
    public void mergeAllSegments() {
        // merge only happens at even number of segments
        Preconditions.checkArgument(getNumSegments() % 2 == 0);
        for (int i = 0; i < segmentID; i += 2) {
            int numDoc1 = mergeDocuments(i, i + 1);
            mergeInvertedLists(i, i + 1, numDoc1);
        }
        segmentID = segmentID / 2;
    }


    /**
     * Merges the documents of two disk segments, return the number of documents in segment ID1
     *
     * @param segID1 the first segment ID
     * @param segID2 the second segment ID
     * @return the number of documents in segment ID1
     */

    int mergeDocuments(int segID1, int segID2) {
        DocumentStore ds1 = MapdbDocStore.createOrOpen(indexFolder + "/segment" + segID1 + ".db");
        DocumentStore ds2 = MapdbDocStore.createOrOpen(indexFolder + "/segment" + segID2 + ".db");
        int numDoc1 = (int) ds1.size();


        Iterator<Map.Entry<Integer, Document>> itr2 = Iterators.transform(ds2.iterator(),
                entry -> immutableEntry(entry.getKey() + numDoc1, entry.getValue()));
        Iterator<Map.Entry<Integer, Document>> itr = Iterators.concat(ds1.iterator(), itr2);

        DocumentStore ds_new = MapdbDocStore.createWithBulkLoad(indexFolder + "/segment tmp .db", itr);
        ds_new.close();

        ds1.close();
        ds2.close();

        deleteFile(indexFolder + "/segment" + segID1 + ".db");
        deleteFile(indexFolder + "/segment" + segID2 + ".db");

        File f1 = new File(indexFolder + "/segment tmp .db");
        File f2 = new File(indexFolder + "/segment" + segID1/2 + ".db");
        f1.renameTo(f2);

        return numDoc1;
    }

    /**
     * A help class for method getIndexListGivenLen
     */

    class BufferAndList{
        ByteBuffer bb;
        List<Integer> list;
        int pageIDRead;

        public BufferAndList(ByteBuffer bb, List<Integer> list, int pageIDRead){
            this.bb = bb;
            this.list = list;
            this.pageIDRead = pageIDRead;
        }
    }

    /**
     * read a given page in the segment x into buffer (x = a represent reading dictionary, b represent reading
     * posting list, c represent reading position list, d represent offset position list)
     *
     * @param segID the segment ID
     * @param x the part of segment (a represent dictionary, b represent posting list, c represent position list)
     * @param pageID the page of the file being read
     * @return the BybeBuffer
     */

    ByteBuffer readSegPage(int segID, String x, int pageID) {
        Path path = Paths.get(indexFolder + "/segment" + segID + x);
        PageFileChannel pfc = PageFileChannel.createOrOpen(path);
        ByteBuffer indexBuffer = pfc.readPage(pageID);
        indexBuffer.rewind();
        pfc.close();
        return indexBuffer;
    }

    /**
     * Get the posting list of a word in a given segment from a buffer, using the length of the list. If
     * addNum = true, add the number n to all the elements in the list
     *
     * @param segID the segment ID
     * @param bb the BybeBuffer being read with capacity = PAGE_SIZE
     * @param pageIDRead the page of the list file being read
     * @param len the length of the posting list
     * @param addNum whether to add number n to all the elements in the list
     * @param n the number being added
     * @return the BybeBuffer being read, the posting list, the page of the list file being read
     */

    BufferAndList getIndexListGivenLen(int segID, ByteBuffer bb, int pageIDRead, int len, boolean addNum, int n) {
        List<Integer> list = new LinkedList<>();
        int remainInt = (bb.limit() - bb.position()) / 4;
        int lSize = len;

        // if the posting list is longer than the remaining buffer, first read the buffer,
        // then open the next page and read

        while (lSize / remainInt >= 1) {
            for (int i = 0; i < remainInt; i++) {
                int docID = bb.getInt();
                if (addNum) {
                    docID += n;
                }
                list.add(docID);
            }
            pageIDRead += 1;
            bb = readSegPage(segID, "b", pageIDRead);
            lSize -= remainInt;
            remainInt = PageFileChannel.PAGE_SIZE / 4;
        }

        // if the posting list is no longer than the remaining buffer, just read the buffer

        for (int i = 0; i < lSize; i++) {
            int docID = bb.getInt();
            if (addNum) {
                docID += n;
            }
            list.add(docID);
        }
        return new BufferAndList(bb, list, pageIDRead);
    }

    /**
     * Write the posting list of a word into buffer by page, if the list length is larger than the page size,
     * append the page and open another buffer
     *
     * @param pfc the file being written
     * @param bb the BybeBuffer being written with capacity = PAGE_SIZE
     * @param l the posting list
     */

    void writeListBufferByPage(PageFileChannel pfc, ByteBuffer bb, List<Integer> l) {
        int lSize = l.size();
        int remainInt = (bb.limit() - bb.position()) / 4;
        int lPos = 0;

        // if the posting list is longer than the remaining buffer, first write the buffer,
        // then append the page and open another buffer to write

        while (lSize / remainInt >= 1) {
            for (int i = 0; i < remainInt; i++, lPos++) {
                bb.putInt(l.get(lPos));
            }
            pfc.appendPage(bb);
            bb.clear();
            lSize -= remainInt;
            remainInt = PageFileChannel.PAGE_SIZE / 4;
        }

        // if the posting list is no longer than the remaining buffer, just write the buffer
        for (int i = 0; i < lSize; i++, lPos++) {
            bb.putInt(l.get(lPos));
        }
    }

    /**
     * Merges the invertedLists of two disk segments
     *
     * @param segID1 the first segment ID
     * @param segID2 the second segment ID
     * @param numDoc1 the number of documents in the first segment
     */

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

        WordInfo wi1 = new WordInfo();
        wi1.readOneWord(wb1);
        WordInfo wi2 = new WordInfo();
        wi2.readOneWord(wb2);

        int offset = 0;
        int pageID = 0;
        int pageIDRead1 = 0;
        int pageIDRead2 = 0;
        ByteBuffer lb1 = readSegPage(segID1, "b", pageIDRead1);
        ByteBuffer lb2 = readSegPage(segID2, "b", pageIDRead2);

        path = Paths.get(indexFolder + "/segment b tmp");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(path);

        while (true) {
            if (wi1.word.equals(wi2.word)) {
                // add them to the dictionary, find their lists and add them to the disk
                // move both bb1 and bb2 to the next words

                //get the list according to the word, for list 2, all docID add numDoc1
                BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len, false, numDoc1);
                lb1 = bl1.bb;
                List<Integer> ls1 = bl1.list;
                pageIDRead1 = bl1.pageIDRead;
                BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len, true, numDoc1);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                pageIDRead2 = bl2.pageIDRead;

                ls1.addAll(ls2);

                //write the word info into buffer
                WordInfo wi = new WordInfo();
                wi.setWordInfo(wi1.word, pageID, offset, ls1.size());
                wi.writeOneWord(wordsBuffer);
                offset += ls1.size() * 4;

                //write the list info into buffer, if buffer full, append it into disk
                writeListBufferByPage(listFileChannel, listBuffer, ls1);

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
                BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len, true, numDoc1);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                pageIDRead2 = bl2.pageIDRead;

                WordInfo wi = new WordInfo();
                wi.setWordInfo(wi2.word, pageID, offset, ls2.size());
                wi.writeOneWord(wordsBuffer);
                offset += ls2.size() * 4;

                writeListBufferByPage(listFileChannel, listBuffer, ls2);

                if (!wb2.hasRemaining()) {
                    if (offset >= PageFileChannel.PAGE_SIZE) {
                        pageID += 1;
                        offset -= PageFileChannel.PAGE_SIZE;
                    }

                    BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len, false, numDoc1);
                    lb1 = bl1.bb;
                    List<Integer> ls1 = bl1.list;
                    pageIDRead1 = bl1.pageIDRead;

                    wi = new WordInfo();
                    wi.setWordInfo(wi1.word, pageID, offset, ls1.size());
                    wi.writeOneWord(wordsBuffer);
                    offset += ls1.size() * 4;

                    writeListBufferByPage(listFileChannel, listBuffer, ls1);
                    break;
                }
                wi2 = new WordInfo();
                wi2.readOneWord(wb2);
            }
            else {
                // add key1 and its list to the disk, move bb1 to the next word
                BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len, false, numDoc1);
                lb1 = bl1.bb;
                List<Integer> ls1 = bl1.list;
                pageIDRead1 = bl1.pageIDRead;

                WordInfo wi = new WordInfo();
                wi.setWordInfo(wi1.word, pageID, offset, ls1.size());
                wi.writeOneWord(wordsBuffer);
                offset += ls1.size() * 4;

                writeListBufferByPage(listFileChannel, listBuffer, ls1);

                if (!wb1.hasRemaining()) {
                    if (offset >= PageFileChannel.PAGE_SIZE) {
                        pageID += 1;
                        offset -= PageFileChannel.PAGE_SIZE;
                    }

                    BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len, true, numDoc1);
                    lb2 = bl2.bb;
                    List<Integer> ls2 = bl2.list;
                    pageIDRead2 = bl2.pageIDRead;

                    wi = new WordInfo();
                    wi.setWordInfo(wi2.word, pageID, offset, ls2.size());
                    wi.writeOneWord(wordsBuffer);
                    offset += ls2.size() * 4;

                    writeListBufferByPage(listFileChannel, listBuffer, ls2);

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

                BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len, true, numDoc1);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                pageIDRead2 = bl2.pageIDRead;

                WordInfo wi = new WordInfo();
                wi.setWordInfo(wi2.word, pageID, offset, ls2.size());
                wi.writeOneWord(wordsBuffer);
                offset += ls2.size() * 4;

                writeListBufferByPage(listFileChannel, listBuffer, ls2);
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

                BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len, false, numDoc1);
                lb1 = bl1.bb;
                List<Integer> ls1 = bl1.list;
                pageIDRead1 = bl1.pageIDRead;

                WordInfo wi = new WordInfo();
                wi.setWordInfo(wi1.word, pageID, offset, ls1.size());
                wi.writeOneWord(wordsBuffer);
                offset += ls1.size() * 4;

                writeListBufferByPage(listFileChannel, listBuffer, ls1);
            }
        }

        // set list file
        listFileChannel.appendAllBytes(listBuffer);
        listFileChannel.close();
        deleteFile(indexFolder + "/segment" + segID1 + "b");
        deleteFile(indexFolder + "/segment" + segID2 + "b");

        File f1 = new File(indexFolder + "/segment b tmp");
        File f2 = new File(indexFolder + "/segment" + segID1/2 + "b");
        f1.renameTo(f2);

        // set word file
        path = Paths.get(indexFolder + "/segment" + segID1/2 + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(path);

        writeFirstPageOfWord(wordsFileChannel, wordsBuffer.position());

        wordsFileChannel.appendAllBytes(wordsBuffer);
        wordsFileChannel.close();
    }

    /**
     * Performs a single keyword search on the inverted index.
     * You could assume the analyzer won't convert the keyword into multiple tokens.
     * If the keyword is empty, it should not return anything.
     *
     * @param keyword keyword, cannot be null.
     * @return a iterator of documents matching the query
     */

    /**
     * Performs a single keyword search on the inverted index.
     * You could assume the analyzer won't convert the keyword into multiple tokens.
     * If the keyword is empty, it should not return anything.
     *
     * @param keyword keyword, cannot be null.
     * @return a iterator of documents matching the query
     */

    public Iterator<Document> searchQuery(String keyword) {
        Preconditions.checkNotNull(keyword);
        List<String> word = analyzer.analyze(keyword);
        List<Document> docs = new ArrayList<>();

        // check if the processed keyword is null
        if (word.size() == 0 || word.get(0).length() == 0) {
            return docs.iterator();
        }
        keyword = word.get(0);
        int totalSegments = getNumSegments();

        // searching each individual segment
        for (int seg = 0; seg < totalSegments; seg++) {
            Path dictSeg = Paths.get(indexFolder + "/segment" + seg + "a");
            PageFileChannel pfc = PageFileChannel.createOrOpen(dictSeg);

            // loading the dictionary
            List<Integer> info = findKeyword(pfc, keyword, seg);
            pfc.close();
            if (info.isEmpty()) {
                continue;
            }

            // all the documents in the segment match the keyword
            List<Document> segmentDocs = getDocuments(seg, info);
            docs.addAll(segmentDocs);
        }
        return docs.iterator();
    }

    /**
     * Performs an AND boolean search on the inverted index.
     *
     * @param keywords a list of keywords in the AND query
     * @return a iterator of documents matching the query
     */

    public Iterator<Document> searchAndQuery(List<String> keywords) {
        Preconditions.checkNotNull(keywords);
        int totalSegments = getNumSegments();
        List<Document> andDocs = new ArrayList<>();

        // for the first merge, just copy the result
        boolean flag = true;

        // search segments
        for (int seg = 0; seg < totalSegments; seg++) {
            Path dictSeg = Paths.get(indexFolder + "/segment" + seg + "a");
            PageFileChannel pfc = PageFileChannel.createOrOpen(dictSeg);

            // result of the and search
            List<Integer> andSearch = new ArrayList<>();
            for (String keyword : keywords) {
                List<String> word = analyzer.analyze(keyword);
                if (word.size() == 0 || word.get(0).length() == 0) {
                    continue;
                }
                keyword = word.get(0);
                List<Integer> info = findKeyword(pfc, keyword, seg);
                if (info.isEmpty()) {
                    andSearch.clear();
                    break;
                }
                if (flag) {

                    // copy the result for the first search
                    andSearch = andMerge(info, info);
                    flag = false;
                }
                else {

                    // merge the results
                    andSearch = andMerge(andSearch, info);
                }
                if (andSearch.isEmpty()) {
                    break;
                }
            }
            pfc.close();
            if (andSearch.size() == 0) {
                continue;
            }
            List<Document> segmentDocs = getDocuments(seg, andSearch);
            andDocs.addAll(segmentDocs);
            flag = false;
        }
        return andDocs.iterator();
    }

    /**
     * Performs an OR boolean search on the inverted index.
     *
     * @param keywords a list of keywords in the OR query
     * @return a iterator of documents matching the query
     */

    public Iterator<Document> searchOrQuery(List<String> keywords) {
        Preconditions.checkNotNull(keywords);
        List<Document> orDocs = new ArrayList<>();
        int totalSegments = getNumSegments();

        // search each segment
        for (int seg = 0; seg < totalSegments; seg++) {
            Path dictSeg = Paths.get(indexFolder + "/segment" + seg + "a");
            PageFileChannel pfc = PageFileChannel.createOrOpen(dictSeg);

            // result of or search
            List<Integer> orSearch = new ArrayList<>();

            // search fo each keyword
            for (String keyword : keywords) {
                List<String> word = analyzer.analyze(keyword);

                // check if keyword is empty
                if (word.size() == 0 || word.get(0).length() == 0) {
                    continue;
                }
                keyword = word.get(0);
                List<Integer> info = findKeyword(pfc, keyword, seg);
                if (info.isEmpty()) {
                    continue;
                }

                // merge the results
                orSearch = orMerge(orSearch, info);
            }
            pfc.close();
            if (orSearch.size() == 0) {
                continue;
            }
            List<Document> segmentDocs = getDocuments(seg, orSearch);
            orDocs.addAll(segmentDocs);
        }
        return orDocs.iterator();
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

    public Iterator<Document> searchPhraseQuery(List<String> phrase) {
        throw new UnsupportedOperationException("This is method for PositionalIndexManager, InvertedIndexManager does not support!");
    }

    /**
     * Finds the keyword in the loaded PageChannelFile of the dictionary.
     *
     * @param pfc loaded segment from the disk
     * @param target the keyword to look for
     * @param segID the segment number to look for the target in
     * @return a list of integers containing the ID of documents matching the search
     */

    private List<Integer> findKeyword(PageFileChannel pfc, String target, int segID) {
        List<Integer> ans = new ArrayList<>();
        int lim = pfc.getNumPages();
        int pageNumber = 1;
        while (pageNumber < lim) {
            ByteBuffer bb = pfc.readPage(pageNumber);
            bb.limit(PageFileChannel.PAGE_SIZE);
            bb.rewind();
            while (bb.hasRemaining()) {
                int wordLength = bb.getInt();
                if (wordLength == 0) {
                    break;
                }
                byte[] word = new byte[wordLength];
                for (int i = 0; i < wordLength; i++) {
                    word[i] = bb.get();
                }
                String dictWord = new String(word);
                int pageID = bb.getInt();
                int offset = bb.getInt();
                int length = bb.getInt();
                if (dictWord.equals(target)) {
                    ans = getIndexList(segID, pageID, offset, length);
                    return ans;
                }
            }
            pageNumber++;
        }
        return ans;
    }

    /**
     * Get all the documents matching the ID list in a segment.
     *
     * @param segID the number of segment
     * @param idList a list of document IDs
     * @return a list of documents matching the search
     */

    private List<Document> getDocuments(int segID, List<Integer> idList) {
        List<Document> ans = new ArrayList<>();
        String path = indexFolder + "/segment" + segID + ".db";
        DocumentStore ds = MapdbDocStore.createOrOpen(path);
        Iterator<Integer> docsIterator = ds.keyIterator();
        while (docsIterator.hasNext()) {
            int tempID = docsIterator.next();
            if (!idList.isEmpty() && idList.contains(tempID)) {
                ans.add(ds.getDocument(tempID));
            }
        }
        ds.close();
        return ans;
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

    private List<Integer> getIndexList(int segID, int pageID, int offset, int length) {
        Path path = Paths.get(indexFolder + "/segment" + segID + "b");
        PageFileChannel pfc = PageFileChannel.createOrOpen(path);
        ByteBuffer indexBuffer = pfc.readPage(pageID);
        indexBuffer.position(offset);
        List<Integer> ans = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            try {
                int docID = indexBuffer.getInt();
                ans.add(docID);
            }
            catch (BufferUnderflowException e) {
                pageID++;
                indexBuffer = pfc.readPage(pageID);
                indexBuffer.position(0);
                int docID = indexBuffer.getInt();
                ans.add(docID);
            }
        }
        pfc.close();
        return ans;
    }

    /**
     * Performs merge for the and search query
     *
     * @param list1 a new list of results
     * @param list2 old list
     * @return a merged list
     */

    private List<Integer> andMerge(List<Integer> list1, List<Integer> list2) {
        // Collections.sort(list1);
        // Collections.sort(list2);

        List<Integer> ans = new ArrayList<>();
        // lists are considered to be sorted already
        if (list1.size() == 0 || list2.size() == 0) {
            return ans;
        }
        int p1 = 0;
        int p2 = 0;
        while (p1 < list1.size() && p2 < list2.size()) {
            int num1 = list1.get(p1);
            int num2 = list2.get(p2);
            if (num1 == num2) {
                ans.add(num1);
                p1++;
                p2++;
            }
            else if (num1 < num2) {
                p1++;
            }
            else {
                p2++;
            }
        }
        return ans;
    }

    /**
     * Performs merge for the or search query
     *
     * @param list1 a new list of results
     * @param list2 old list
     * @return a merged list
     */

    private List<Integer> orMerge(List<Integer> list1, List<Integer> list2) {
        // Collections.sort(list1);
        // Collections.sort(list2);

        List<Integer> ans = new ArrayList<>();
        // lists are considered to be sorted already
        if (list1.size() == 0 && list2.size() == 0) {
            return ans;
        }
        if (list1.size() == 0) {
            return list2;
        }
        if (list2.size() == 0) {
            return list1;
        }
        int p1 = 0;
        int p2 = 0;
        while (p1 < list1.size() && p2 < list2.size()) {
            int num1 = list1.get(p1);
            int num2 = list2.get(p2);
            if (num1 == num2) {
                ans.add(num1);
                p1++;
                p2++;
            }
            else if (num1 < num2) {
                ans.add(num1);
                p1++;
            }
            else {
                ans.add(num2);
                p2++;
            }
        }
        while (p1 < list1.size()) {
            int num1 = list1.get(p1);
            ans.add(num1);
            p1++;
        }
        while (p2 < list2.size()) {
            int num2 = list2.get(p2);
            ans.add(num2);
            p2++;
        }
        return ans;
    }

    /**
     * Iterates through all the documents in all disk segments.
     */
    
    public Iterator<Document> documentIterator() {
        if (segmentID == 0) {
            return null;
        }

        DocumentStore ds = MapdbDocStore.createOrOpen(indexFolder + "/segment" + 0 + ".db");
        Iterator<Document> docsIterator = Iterators.transform(ds.iterator(), entry -> entry.getValue());
        ds.close();

        for (int i = 1; i < segmentID; i++) {
            ds = MapdbDocStore.createOrOpen(indexFolder + "/segment" + i + ".db");
            docsIterator = Iterators.concat(docsIterator, Iterators.transform(ds.iterator(), entry -> entry.getValue()));
            ds.close();
        }

        return docsIterator;
    }

    /**
     * Deletes all documents in all disk segments of the inverted index that match the query.
     * @param keyword
     */

    /**
     * Deletes all documents in all disk segments of the inverted index that match the query.
     * @param keyword
     */

    public void deleteDocuments(String keyword) {
        List<String> word = analyzer.analyze(keyword);
        if (word.size() == 0 || word.get(0).length() == 0) {
            return;
        }
        keyword = word.get(0);
        int totalSegments = getNumSegments();
        for (int seg = 0; seg < totalSegments; seg++) {
            Path dictSeg = Paths.get(indexFolder + "/segment" + seg + "a");
            PageFileChannel pfc = PageFileChannel.createOrOpen(dictSeg);
            List<Integer> info = findKeyword(pfc, keyword, seg);
            if (info == null) {
                continue;
            }

            // creating a separate file for deleted documents for each segment
            Path deleted = Paths.get(indexFolder + "/segment" + seg + "d");
            pfc = PageFileChannel.createOrOpen(deleted);
            ByteBuffer deletedBuffer = ByteBuffer.allocate(info.size() * 4);
            for (int post : info) {
                deletedBuffer.putInt(post);
            }
            pfc.appendAllBytes(deletedBuffer);
            pfc.close();
        }
    }

    /**
     * Checks if the docID is in the list of deleted IDs.
     * @param segID the ID of the segment
     * @param docID the document ID
     */

    private boolean isDeleted(int segID, int docID) {
        Path path = Paths.get(indexFolder + "/segment" + segID + "d");
        PageFileChannel pfc = PageFileChannel.createOrOpen(path);
        int lim = pfc.getNumPages();
        int pageID = 0;
        ByteBuffer buf = pfc.readPage(pageID);
        buf.position(0);
        while (true) {
            try {
                int id = buf.getInt();
                if (id == docID) {
                    return true;
                }
            }
            catch (BufferUnderflowException e) {
                pageID++;
                if (pageID >= lim) {
                    break;
                }
                buf = pfc.readPage(pageID);
                buf.position(0);
            }
        }
        return false;
    }

    /**
     * Gets the total number of segments in the inverted index.
     * This function is used for checking correctness in test cases.
     *
     * @return number of index segments.
     */
    
    public int getNumSegments() {
        return segmentID;
    }

    /**
     * Reads a disk segment into memory based on segmentNum.
     * This function is mainly used for checking correctness in test cases.
     *
     * @param segmentNum n-th segment in the inverted index (start from 0).
     * @return in-memory data structure with all contents in the index segment, null if segmentNum don't exist.
     */

    public InvertedIndexSegmentForTest getIndexSegment(int segmentNum) {
        if (segmentID == 0) {
            return null;
        }
        Map<String, List<Integer>> invertedLists = new TreeMap<>();
        Map<Integer, Document> documents = new TreeMap<>();
        Map<String, Integer> wordDic = new TreeMap<>();

        // read segmentXXa
        Path wordsPath = Paths.get(indexFolder + "/segment" + segmentNum + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);

        ByteBuffer wordsBuffer = wordsFileChannel.readAllPages();
        wordsFileChannel.close();
        readFirstPageOfWord(wordsBuffer);

        // based on remaining page, build map<String, Integer> in which key is keyword, value is len(list)
        WordInfo wi = new WordInfo();
        while (wordsBuffer.hasRemaining()) {
            wi.readOneWord(wordsBuffer);
            wordDic.put(wi.word, wi.len);
        }

        // read segmentXXb, build map<String, List<Integer>> invertedLists
        Path listPath = Paths.get(indexFolder + "/segment" + segmentNum + "b");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(listPath);
        ByteBuffer listBuffer = listFileChannel.readAllPages();
        listFileChannel.close();

        listBuffer.rewind();
        for (String word: wordDic.keySet()) {
            List<Integer> list = new LinkedList<>();
            int listLen = wordDic.get(word);
            for (int i = 0; i < listLen; i++) {
                list.add(listBuffer.getInt());
            }
            invertedLists.put(word, list);
        }

        // read segmentXX.db, build map<Integer, Document> documents
        DocumentStore ds = MapdbDocStore.createOrOpen(indexFolder + "/segment" + segmentNum + ".db");
        Iterator<Map.Entry<Integer, Document>> itr = ds.iterator();
        while(itr.hasNext()) {
            Map.Entry<Integer, Document> entry = itr.next();
            documents.put(entry.getKey(), entry.getValue());
        }
        ds.close();
        return new InvertedIndexSegmentForTest(invertedLists, documents);
    }

    /**
     * Reads a disk segment of a positional index into memory based on segmentNum.
     * This function is mainly used for checking correctness in test cases.
     * Throws UnsupportedOperationException if the inverted index is not a positional index.
     * (method for PositionalIndexManager)
     *
     * @param segmentNum n-th segment in the inverted index (start from 0).
     * @return in-memory data structure with all contents in the index segment, null if segmentNum don't exist.
     */
    public PositionalIndexSegmentForTest getIndexSegmentPositional(int segmentNum) {
        throw new UnsupportedOperationException("This is method for PositionalIndexManager, InvertedIndexManager does not support!");
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
    public Iterator<Pair<Document, Double>> searchTfIdf(List<String> keywords, Integer topK) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the total number of documents within the given segment.
     */
    public int getNumDocuments(int segmentNum) {
        DocumentStore ds = MapdbDocStore.createOrOpen(indexFolder + "/segment" + segmentNum + ".db");
        int numDoc = (int) ds.size();
        ds.close();
        return numDoc;
    }

    /**
     * Returns the number of documents containing the token within the given segment.
     * The token should be already analyzed by the analyzer. The analyzer shouldn't be applied again.
     */
    public int getDocumentFrequency(int segmentNum, String token) {
        int lenList = 0;
        // read segmentXXa
        Path wordsPath = Paths.get(indexFolder + "/segment" + segmentNum + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);
        ByteBuffer wordsBuffer = wordsFileChannel.readAllPages();
        wordsFileChannel.close();
        readFirstPageOfWord(wordsBuffer);

        // based on remaining page, search the dictionary for the token and get the len(list)
        WordInfo wi = new WordInfo();
        while (wordsBuffer.hasRemaining()) {
            wi.readOneWord(wordsBuffer);
            if (token.equals(wi.word)) {
                lenList = wi.len;
                break;
            }
        }
        return lenList;
    }
}
