
package edu.uci.ics.cs221.index.inverted;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import edu.uci.ics.cs221.analysis.Analyzer;
import edu.uci.ics.cs221.storage.Document;
import edu.uci.ics.cs221.storage.DocumentStore;
import edu.uci.ics.cs221.storage.MapdbDocStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.*;
import java.io.File;

import edu.uci.ics.cs221.analysis.*;

import java.nio.charset.StandardCharsets;

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
    public static int STORE_PARAMETER = 5000;

    private Map<String, List<Integer>> invertedLists;
    private Map<Integer, Document> documents;
    private int docID;
    private int segmentID;
    private Analyzer analyzer;
    private String indexFolder;

    private InvertedIndexManager(String indexFolder, Analyzer analyzer) {
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
        if (invertedLists.size() == 0 && documents.size() == 0) {
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
            writeListBuffer(listBuffer, postingList);
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
     */

    private void writeFirstPageOfWord(PageFileChannel pfc, int lim) {
        ByteBuffer limitBuffer = ByteBuffer.allocate(PageFileChannel.PAGE_SIZE);
        limitBuffer.putInt(lim);
        pfc.appendPage(limitBuffer);
    }

    /**
     * read the first page on buffer, get the integer, and prepare the buffer for remaining reading
     */

    private int readFirstPageOfWord(ByteBuffer bb) {
        bb.rewind();
        int cap = bb.getInt();
        bb.limit(PageFileChannel.PAGE_SIZE + cap);
        bb.position(PageFileChannel.PAGE_SIZE);
        return cap;
    }

    /**
     * Write in a buffer with the information of a list
     */

    private void writeListBuffer(ByteBuffer bb, List<Integer> l) {
        for (int num: l) {
            bb.putInt(num);
        }
    }

    /**
     * delete a file
     */

    private void deleteFile(String fileName) {
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
     */

    private int mergeDocuments(int segID1, int segID2) {
        DocumentStore ds1 = MapdbDocStore.createOrOpen(indexFolder + "/segment" + segID1 + ".db");
        DocumentStore ds2 = MapdbDocStore.createOrOpen(indexFolder + "/segment" + segID2 + ".db");
        int numDoc1 = (int) ds1.size();


        Iterator<Map.Entry<Integer, Document>> itr2 = Iterators.transform(ds2.iterator(),
                entry -> immutableEntry(entry.getKey() + numDoc1, entry.getValue()));
        Iterator<Map.Entry<Integer, Document>> itr = Iterators.concat(ds1.iterator(), itr2);

        ds1.close();
        ds2.close();

        deleteFile(indexFolder + "/segment" + segID1 + ".db");
        deleteFile(indexFolder + "/segment" + segID2 + ".db");

        DocumentStore ds_new = MapdbDocStore.createWithBulkLoad(indexFolder + "/segment" + segID1/2 + ".db", itr);
        ds_new.close();

        return numDoc1;
    }

    /**
     * A help class to method getIndexListGivenLen
     */

    private class BufferAndList{
        //todo: change later
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
     * Get a list of a word in given segment, using the length of the list
     */

    private BufferAndList getIndexListGivenLen(int segID, ByteBuffer bb, int pageIDRead, int len) {
        List<Integer> list = new LinkedList<>();
        int remainInt = (bb.limit() - bb.position()) / 4;
        int lSize = len;
        while (lSize / remainInt >= 1) {
            for (int i = 0; i < remainInt; i++) {
                list.add(bb.getInt());
            }
            pageIDRead += 1;
            bb = readIndexListPage(segID, pageIDRead);
            lSize -= remainInt;
            remainInt = PageFileChannel.PAGE_SIZE / 4;
        }
        for (int i = 0; i < lSize; i++) {
            list.add(bb.getInt());
        }
        return new BufferAndList(bb, list, pageIDRead);
    }

    /**
     * read a page in a given segment into buffer
     */

    private ByteBuffer readIndexListPage(int segID, int pageID) {
        Path path = Paths.get(indexFolder + "/segment" + segID + "b");
        PageFileChannel pfc = PageFileChannel.createOrOpen(path);
        ByteBuffer indexBuffer = pfc.readPage(pageID);
        indexBuffer.rewind();
        pfc.close();
        return indexBuffer;
    }

    /**
     * Add the a number n to all the elements in a list
     */

    private void addDocId(List<Integer> list, int n) {
        for (int i = 0; i < list.size(); i++) {
            list.set(i, list.get(i) + n);
        }
    }

    /**
     * Write the List of a word into buffer, if the list length is larger than the page size,
     * append the page and open another buffer
     */

    private void writeListBufferByPage(PageFileChannel pfc, ByteBuffer bb, List<Integer> l) {
        int lSize = l.size();
        int remainInt = (bb.limit() - bb.position()) / 4;
        int lPos = 0;
        while (lSize / remainInt >= 1) {
            for (int i = 0; i < remainInt; i++, lPos++) {
                bb.putInt(l.get(lPos));
            }
            pfc.appendPage(bb);
            bb.clear();
            lSize -= remainInt;
            remainInt = PageFileChannel.PAGE_SIZE / 4;
        }
        for (int i = 0; i < lSize; i++, lPos++) {
            bb.putInt(l.get(lPos));
        }
    }

    /**
     * Merges the invertedLists of two disk segments
     */

    private void mergeInvertedLists(int segID1, int segID2, int numDoc1) {
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
        ByteBuffer lb1 = readIndexListPage(segID1, pageIDRead1);
        ByteBuffer lb2 = readIndexListPage(segID2, pageIDRead2);

        path = Paths.get(indexFolder + "/segment b tmp"); //todo:change later
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(path);

        while (true) {
            if (wi1.word.equals(wi2.word)) {
                // add them to the dictionary, find their lists and add them to the disk
                // move both bb1 and bb2 to the next words

                //get the list according to the word
                BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len);
                lb1 = bl1.bb;
                List<Integer> ls1 = bl1.list;
                pageIDRead1 = bl1.pageIDRead;
                BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                pageIDRead2 = bl2.pageIDRead;

                //for list 2, all docID add numDoc1
                addDocId(ls2, numDoc1);
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
                BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                pageIDRead2 = bl2.pageIDRead;

                addDocId(ls2, numDoc1);

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

                    BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len);
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
                BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len);
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

                    BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len);
                    lb2 = bl2.bb;
                    List<Integer> ls2 = bl2.list;
                    pageIDRead2 = bl2.pageIDRead;

                    addDocId(ls2, numDoc1);

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

                BufferAndList bl2 = getIndexListGivenLen(segID2, lb2, pageIDRead2, wi2.len);
                lb2 = bl2.bb;
                List<Integer> ls2 = bl2.list;
                pageIDRead2 = bl2.pageIDRead;

                addDocId(ls2, numDoc1);
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

                BufferAndList bl1 = getIndexListGivenLen(segID1, lb1, pageIDRead1, wi1.len);
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

        listFileChannel.appendAllBytes(listBuffer);
        listFileChannel.close();
        deleteFile(indexFolder + "/segment" + segID1 + "b");
        deleteFile(indexFolder + "/segment" + segID2 + "b");

        File f1 = new File(indexFolder + "/segment b tmp");
        File f2 = new File(indexFolder + "/segment" + segID1/2 + "b");
        f1.renameTo(f2);

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
    
    public Iterator<Document> searchQuery(String keyword) {
        // check if the keyword is not null
        Preconditions.checkNotNull(keyword);
        List<String> word = analyzer.analyze(keyword);
        List<Document> docs = new ArrayList<>();
        // check if the processed keyword is not null
        if (word.size() == 0 || word.get(0).length() == 0) {
            return docs.iterator();
        }
        keyword = word.get(0);
        // documents that match the search
        
        int totalSegments = getNumSegments();
        // searching each individual segment
        for (int seg = 0; seg < totalSegments; seg++) {
            Path dictSeg = Paths.get(indexFolder + "/segment" + seg + "a");
            PageFileChannel pfc = PageFileChannel.createOrOpen(dictSeg);
            // loading the dictionary
            ByteBuffer bb = pfc.readAllPages();
            bb.rewind();
            // consume the total number of bytes in the segment
            int cap = bb.getInt();
            // set the limit to the one page (for the cap) and the total bytes in the segment
            bb.limit(PageFileChannel.PAGE_SIZE + cap);
            // find if the keyword exists in the dictionary
            bb.position(PageFileChannel.PAGE_SIZE);
            List<Integer> info = findKeyword(bb, keyword, seg);
            if (info == null) {
                // move on if dictionary does not contain the keyword
                continue;
            }
            // all the documents in the segment match the keyword
            List<Document> segmentDocs = getDocuments(seg, info);
            for (Document temp : segmentDocs) {
                docs.add(temp);
            }
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
        // documents match the and search
        List<Document> andDocs = new ArrayList<>();
        // for the first merge, we just copy the result
        boolean flag = true;
        // search segments
        for (int seg = 0; seg < totalSegments; seg++) {
            Path dictSeg = Paths.get(indexFolder + "/segment" + seg + "a");
            PageFileChannel pfc = PageFileChannel.createOrOpen(dictSeg);
            ByteBuffer bb = pfc.readAllPages();
            bb.rewind();
            int cap = bb.getInt();
            bb.limit(PageFileChannel.PAGE_SIZE + cap);
            // result of the and search
            List<Integer> andSearch = new ArrayList<>();
            for (String keyword : keywords) {
                List<String> word = analyzer.analyze(keyword);
                if (word.size() == 0 || word.get(0).length() == 0) {
                    return andDocs.iterator();
                }
                keyword = word.get(0);
                bb.position(PageFileChannel.PAGE_SIZE);
                List<Integer> info = findKeyword(bb, keyword, seg);
                bb.position(PageFileChannel.PAGE_SIZE);
                if (info == null) {
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
            }
            List<Document> segmentDocs = getDocuments(seg, andSearch);
            for (Document temp : segmentDocs) {
                andDocs.add(temp);
            }
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
        int totalSegments = getNumSegments();
        // documents match the or search
        List<Document> orDocs = new ArrayList<>();
        // search each segment
        for (int seg = 0; seg < totalSegments; seg++) {
            Path dictSeg = Paths.get(indexFolder + "/segment" + seg + "a");
            PageFileChannel pfc = PageFileChannel.createOrOpen(dictSeg);
            // load the dictionary
            ByteBuffer bb = pfc.readAllPages();
            bb.rewind();
            int cap = bb.getInt();
            bb.limit(PageFileChannel.PAGE_SIZE + cap);
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
                // set the position to the beginning of the dictionary
                bb.position(PageFileChannel.PAGE_SIZE);
                List<Integer> info = findKeyword(bb, keyword, seg);
                // set the position back
                bb.position(PageFileChannel.PAGE_SIZE);
                if (info == null) {
                    continue;
                }
                // merge the results
                orSearch = orMerge(orSearch, info);
            }
            List<Document> segmentDocs = getDocuments(seg, orSearch);
            for (Document temp : segmentDocs) {
                orDocs.add(temp);
            }
        }
        return orDocs.iterator();
    }
    
    /**
     * Finds the keyword in the loaded bytes of the dictionary.
     *
     * @param bb loaded dictionary in bytes
     * @param target the keyword to look for
     * @param segID the segment number to look for the target in
     * @return a list of integers containing the ID of documents matching the search
     */

    private List<Integer> findKeyword(ByteBuffer bb, String target, int segID) {
        while (bb.hasRemaining()) {
            int wordLength = bb.getInt();
            if (wordLength == 0) {
                break;
            }
            byte[] word = new byte[wordLength];
            bb.get(word, 0, wordLength);
            String dictWord = new String(word,StandardCharsets.UTF_8);
            int pageID = bb.getInt();
            int offset = bb.getInt();
            int length = bb.getInt();
            if (dictWord.equals(target)) {
                List<Integer> ans = getIndexList(segID, pageID, offset, length);
                return ans;
            }
        }
        return null;
    }

    /**
     * Get all the documents matching the ID list in a segment.
     *
     * @param segID the number of segment
     * @param idList a list of document IDs
     * @return a list of documents matching the search
     */

    public List<Document> getDocuments(int segID, List<Integer> idList) {
        List<Document> ans = new ArrayList<>();
        String path = indexFolder + "/segment" + segID + ".db";
        DocumentStore ds = MapdbDocStore.createOrOpen(path);
        Iterator<Integer> docsIterator = ds.keyIterator();
        while (docsIterator.hasNext()) {
            int tempID = docsIterator.next();
            if (idList.contains(tempID)) {
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

    public List<Integer> getIndexList(int segID, int pageID, int offset, int length) {
        ByteBuffer indexBuffer = readIndexListPage(segID, pageID);
        indexBuffer.position(offset);

        List<Integer> ans = new ArrayList<>();
        int remainInt = (indexBuffer.limit() - indexBuffer.position()) / 4;
        int lSize = length;
        while (lSize / remainInt >= 1) {
            for (int i = 0; i < remainInt; i++) {
                ans.add(indexBuffer.getInt());
            }
            pageID += 1;
            indexBuffer = readIndexListPage(segID, pageID);
            lSize -= remainInt;
            remainInt = PageFileChannel.PAGE_SIZE / 4;
        }
        for (int i = 0; i < lSize; i++) {
            ans.add(indexBuffer.getInt());
        }
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
        // lists are considered to be sorted already
        if (list1.size() == 0 || list2.size() == 0) {
            return null;
        }
        List<Integer> ans = new ArrayList<>();
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
        // lists are considered to be sorted already
        if (list1.size() == 0 && list2.size() == 0) {
            return null;
        }
        if (list1.size() == 0) {
            return list2;
        }
        if (list2.size() == 0) {
            return list1;
        }
        List<Integer> ans = new ArrayList<>();
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

    public void deleteDocuments(String keyword) {
        List<String> word = analyzer.analyze(keyword);
        keyword = word.get(0);
        if (keyword.length() == 0 || word.size() == 0) {
            return;
        }
        int totalSegments = getNumSegments();
        for (int seg = 0; seg < totalSegments; seg++) {
            Path dictSeg = Paths.get(indexFolder + "/segment" + seg + "a");
            PageFileChannel pfc = PageFileChannel.createOrOpen(dictSeg);
            // load the dictionary
            ByteBuffer bb = pfc.readAllPages();
            pfc.close();
            bb.rewind();
            int cap = bb.getInt();
            bb.position(PageFileChannel.PAGE_SIZE);
            List<Integer> info = findKeyword(bb, keyword, seg);
            if (info == null) {
                continue;
            }
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
        ByteBuffer buf = pfc.readAllPages();
        pfc.close();
        buf.rewind();
        while (buf.hasRemaining()) {
            int id = buf.getInt();
            if (id == docID) {
                return true;
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
        wordsBuffer.rewind();

        // get the integer in first page.
        int lim = wordsBuffer.getInt();
        wordsBuffer.position(PageFileChannel.PAGE_SIZE);
        wordsBuffer.limit(PageFileChannel.PAGE_SIZE+lim);

        // based on remaining page, build map<String, Integer> in which key is keyword, value is len(list)
        while (wordsBuffer.hasRemaining()) {
            int wordLen = wordsBuffer.getInt();
            byte[] wordb = new byte[wordLen];
            wordsBuffer.get(wordb, 0, wordLen);
            int listPage = wordsBuffer.getInt();
            int listOff = wordsBuffer.getInt();
            int listLen = wordsBuffer.getInt();
            String words = new String(wordb,StandardCharsets.UTF_8);
            wordDic.put(words, listLen);
        }

        wordsFileChannel.close();

        // read segmentXXb, build map<String, List<Integer>> invertedLists
        Path listPath = Paths.get(indexFolder + "/segment" + segmentNum + "b");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(listPath);
        ByteBuffer listBuffer = listFileChannel.readAllPages();

        listBuffer.rewind();
        for (String word: wordDic.keySet()) {
            List<Integer> list = new LinkedList<>();
            int listLen = wordDic.get(word);
            for (int i = 0; i < listLen; i++) {
                list.add(listBuffer.getInt());
            }
            invertedLists.put(word, list);
        }

        listFileChannel.close();

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
     * Creates a positional index with the given folder, analyzer, and the compressor.
     * Compressor must be used to compress the inverted lists and the position lists.
     *
     */
    public static InvertedIndexManager createOrOpenPositional(String indexFolder, Analyzer analyzer, Compressor compressor) {
        throw new UnsupportedOperationException();
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
    public PositionalIndexSegmentForTest getIndexSegmentPositional(int segmentNum) {
        throw new UnsupportedOperationException();
    }
}
