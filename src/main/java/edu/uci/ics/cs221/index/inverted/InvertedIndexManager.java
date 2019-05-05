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

        // store the len(keywords), keywords, offset(list), len(list) in segmentXXa,
        // with the first page have the total number of bytes the remaining pages will use

        Path wordsPath = Paths.get(indexFolder+"segment" + segmentID + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);

        ByteBuffer wordsBuffer = ByteBuffer.allocate(5000 * invertedLists.size());
        int offset = 0;
        for (String word: invertedLists.keySet()) {
            wordsBuffer.putInt(word.length());
            byte[] tmp = word.getBytes();
            wordsBuffer.put(tmp);
            wordsBuffer.putInt(offset);
            wordsBuffer.putInt(invertedLists.get(word).size());
            offset += invertedLists.get(word).size();
        }

        // write the first page with an integer, which is the total number of bytes
        // the remaining pages will use

//        System.out.println(wordsBuffer);
        ByteBuffer limitBuffer = ByteBuffer.allocate(wordsFileChannel.PAGE_SIZE);
//        System.out.println(wordsBuffer.position());
        limitBuffer.putInt(wordsBuffer.position());
        wordsFileChannel.appendPage(limitBuffer);

        wordsFileChannel.appendAllBytes(wordsBuffer);
        wordsFileChannel.close();

        // store all the lists in segmentXXb

        Path listPath = Paths.get(indexFolder+"segment" + segmentID + "b");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(listPath);

        ByteBuffer listBuffer = ByteBuffer.allocate(5000 * invertedLists.size());
        for (String word: invertedLists.keySet()) {
            List<Integer> postingList = invertedLists.get(word);
            for (int num: postingList) {
                listBuffer.putInt(num);
            }
        }
//        System.out.println(listBuffer);
        listFileChannel.appendAllBytes(listBuffer);
        listFileChannel.close();
//        System.out.println(documents.size());

        // store all the documents in segmentXX.db
        DocumentStore ds = MapdbDocStore.createWithBulkLoad(indexFolder+"segment" + segmentID + ".db",documents.entrySet().iterator());
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
     * Merges all the disk segments of the inverted index pair-wise.
     */
    public void mergeAllSegments() {
        // merge only happens at even number of segments
        Preconditions.checkArgument(getNumSegments() % 2 == 0);
        throw new UnsupportedOperationException();
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
        Preconditions.checkNotNull(keyword);
        List<String> word = analyzer.analyze(keyword);
        if (word.size() == 0 || word.get(0).length() == 0) {
            return null;
        }
        keyword = word.get(0);
        List<Document> docs = new ArrayList<>();
        int totalSegments = getNumSegments();
        for (int seg = 0; seg < totalSegments; seg++) {
            Path dictSeg = Paths.get(indexFolder + "segment" + seg + "a");
            PageFileChannel pfc = PageFileChannel.createOrOpen(dictSeg);
            ByteBuffer bb = pfc.readAllPages();
            bb.rewind();
            int cap = bb.getInt();
            bb.limit(PageFileChannel.PAGE_SIZE + cap);
            List<Integer> info = findKeyword(bb, keyword, seg);
            if (info == null) {
                continue;
            }
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

        throw new UnsupportedOperationException();
    }

    /**
     * Performs an OR boolean search on the inverted index.
     *
     * @param keywords a list of keywords in the OR query
     * @return a iterator of documents matching the query
     */
    public Iterator<Document> searchOrQuery(List<String> keywords) {
        Preconditions.checkNotNull(keywords);

        throw new UnsupportedOperationException();
    }
    
    private List<Integer> findKeyword(ByteBuffer bb, String target, int segID) {
        bb.position(PageFileChannel.PAGE_SIZE);
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
                List<Integer> ans = getIndexList(segID, pageID, offset, length);
                return ans;
            }
        }
        return null;
    }

    public List<Document> getDocuments(int segID, List<Integer> idList) {
        List<Document> ans = new ArrayList<>();
        String path = indexFolder + "segment" + segID + ".db";
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

    private List<Integer> getIndexList(int segID, int pageID, int offset, int length) {
        Path path = Paths.get(indexFolder + "segment" + segID + "b");
        PageFileChannel pfc = PageFileChannel.createOrOpen(path);
        ByteBuffer indexBuffer = pfc.readPage(pageID);
        indexBuffer.rewind();
        indexBuffer.position(offset);
        List<Integer> ans = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            int temp = indexBuffer.getInt();
            ans.add(temp);
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

        DocumentStore ds = MapdbDocStore.createOrOpen(indexFolder+"segment" + 0 + ".db");
        Iterator<Document> docsIterator = Iterators.transform(ds.iterator(), entry -> entry.getValue());
        ds.close();
//        System.out.println(segmentID);

        for (int i = 1; i < segmentID; i++) {
            ds = MapdbDocStore.createOrOpen(indexFolder+"segment" + i + ".db");
            docsIterator = Iterators.concat(docsIterator, Iterators.transform(ds.iterator(), entry -> entry.getValue()));
            ds.close();
            System.out.println(i);
        }

        return docsIterator;
    }

    /**
     * Deletes all documents in all disk segments of the inverted index that match the query.
     * @param keyword
     */
    public void deleteDocuments(String keyword) {
        throw new UnsupportedOperationException();
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
        Path wordsPath = Paths.get(indexFolder+"segment" + segmentNum + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);

        ByteBuffer wordsBuffer = wordsFileChannel.readAllPages();
        wordsBuffer.rewind();

        // get the integer in first page.
        int lim = wordsBuffer.getInt();
//        System.out.println(lim);
        wordsBuffer.position(4096);
        wordsBuffer.limit(4096+lim);

        // based on remaining page, build map<String, Integer> in which key is keyword, value is len(list)
//        System.out.println(wordsBuffer);
        while (wordsBuffer.hasRemaining()) {
            int wordLen = wordsBuffer.getInt();
            byte[] wordb = new byte[wordLen];
            wordsBuffer.get(wordb, 0, wordLen);
            int listOff = wordsBuffer.getInt();
            int listLen = wordsBuffer.getInt();
            String words = new String(wordb);
//            System.out.println(wordLen+","+words+","+listLen);
            wordDic.put(words, listLen);
//            System.out.println(wordsBuffer);
        }

        wordsFileChannel.close();

        // read segmentXXb, build map<String, List<Integer>> invertedLists
        Path listPath = Paths.get(indexFolder+"segment" + segmentNum + "b");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(listPath);
        ByteBuffer listBuffer = listFileChannel.readAllPages();

        listBuffer.rewind();
//        System.out.println(listBuffer);
        for (String word: wordDic.keySet()) {
            List<Integer> list = new LinkedList<>();
            int listLen = wordDic.get(word);
//            System.out.println(word+","+listLen);
            for (int i = 0; i < listLen; i++) {
                list.add(listBuffer.getInt());
            }
            invertedLists.put(word, list);
        }

        listFileChannel.close();

        // read segmentXX.db, build map<Integer, Document> documents
        DocumentStore ds = MapdbDocStore.createOrOpen(indexFolder+"segment" + segmentNum + ".db");
        Iterator<Map.Entry<Integer, Document>> itr = ds.iterator();
        while(itr.hasNext()) {
            Map.Entry<Integer, Document> entry = itr.next();
            documents.put(entry.getKey(), entry.getValue());
        }
        ds.close();
        return new InvertedIndexSegmentForTest(invertedLists, documents);
    }

    public static void main(String args[]) {
//        Analyzer an = new ComposableAnalyzer(new PunctuationTokenizer(), token -> token);
//        String file = "./index/Team19FlushTest/";
//        InvertedIndexManager iim = createOrOpen(file, an);
//        iim.addDocument(new Document("cat dog"));
//        iim.addDocument(new Document("cat elephant"));
//        iim.flush();
////        iim.addDocument(new Document("cat dog"));
////        iim.addDocument(new Document("wolf dog"));
////        iim.flush();
//        System.out.println(iim.getNumSegments());
//
//        InvertedIndexSegmentForTest test = iim.getIndexSegment(0);
//        System.out.println(test.getInvertedLists().get("cat"));


//        Iterator<Document> documentIterator = iim.documentIterator();
//        while(documentIterator.hasNext()) {
//            System.out.println(documentIterator.next().getText());
//        }

//        ByteBuffer bf = ByteBuffer.allocateDirect(80);
//        String word = "informationdocumentary";
//
//        byte[] tmp = word.getBytes();
//        bf.put(tmp);
//        bf.rewind();
//
//        byte[] tmp1 = new byte[22];
//        bf.get(tmp1,0,22);
//        String words = new String(tmp1);
//        System.out.println(words);
    }
}
