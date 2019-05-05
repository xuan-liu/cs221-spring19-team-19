package edu.uci.ics.cs221.index.inverted;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import edu.uci.ics.cs221.analysis.Analyzer;
import edu.uci.ics.cs221.analysis.ComposableAnalyzer;
import edu.uci.ics.cs221.analysis.PunctuationTokenizer;
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
    public Map<String, List<Integer>> invertedLists;
    public Map<Integer, Document> documents;
    public int docID;
    public int segmentID;
    public Analyzer analyzer;
    public String indexFolder;


    private InvertedIndexManager(String indexFolder, Analyzer analyzer) {
        this.analyzer = analyzer;
        this.indexFolder = indexFolder;
        this.docID = 0;
        this.segmentID = 0;
        this.invertedLists = new TreeMap<>();
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
        if (docID >= DEFAULT_FLUSH_THRESHOLD) {
            flush();
        }
    }

    /**
     * Flushes all the documents in the in-memory segment buffer to disk. If the buffer is empty, it should not do anything.
     * flush() writes the segment to disk containing the posting list and the corresponding document store.
     */
    public void flush() {
        if (invertedLists.size() == 0 && documents.size() == 0) {
            return;
        }
        docID = 0;
        Path wordsPath = Paths.get("./segment" + segmentID + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);

//        ByteBuffer wordsBuffer = ByteBuffer.allocate(20 * invertedLists.size());
//        int offset = 0;
//        int listLength;
//        for (String word: invertedLists.keySet()) {
//            byte[] tmp = word.getBytes();
//            wordsBuffer.put(tmp);
//            wordsBuffer.put((byte) offset);
//            listLength = invertedLists.get(word).size();
//            wordsBuffer.put((byte) listLength);
////            System.out.println(word+","+offset+","+listLength);
//            offset += listLength;
//
//        }
//        wordsFileChannel.appendAllBytes(wordsBuffer);
//        wordsFileChannel.close();

        ByteBuffer wordsBuffer = ByteBuffer.allocate(20 * invertedLists.size());
        for (String word: invertedLists.keySet()) {
            wordsBuffer.putInt(word.length());
            byte[] tmp = word.getBytes();
            wordsBuffer.put(tmp);
            wordsBuffer.putInt(invertedLists.get(word).size());
//            System.out.println(word+","+offset+","+listLength);

        }
        System.out.println(wordsBuffer);
        ByteBuffer limitBuffer = ByteBuffer.allocate(4096);
        System.out.println(wordsBuffer.position());
        limitBuffer.putInt(38);
        wordsFileChannel.appendPage(limitBuffer);

        wordsFileChannel.appendAllBytes(wordsBuffer);
        wordsFileChannel.close();

        Path listPath = Paths.get("./segment" + segmentID + "b");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(listPath);

        ByteBuffer listBuffer = ByteBuffer.allocate(200 * invertedLists.size());
        for (String word: invertedLists.keySet()) {
            List<Integer> postingList = invertedLists.get(word);
            for (int num: postingList) {
                listBuffer.putInt(num);
            }
        }
        System.out.println(listBuffer);
        listFileChannel.appendAllBytes(listBuffer);
        listFileChannel.close();

        DocumentStore ds = MapdbDocStore.createWithBulkLoad("segment" + segmentID + ".db",documents.entrySet().iterator());
        ds.close();

        this.invertedLists = new TreeMap<>();
        this.documents = new TreeMap<>();
        segmentID += 1;
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

        throw new UnsupportedOperationException();
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

    /**
     * Iterates through all the documents in all disk segments.
     */
    public Iterator<Document> documentIterator() {
        DocumentStore ds = MapdbDocStore.createOrOpen("segment" + 0 + ".db");
        Iterator<Document> docsIterator = Iterators.transform(ds.iterator(), entry -> entry.getValue());
        ds.close();
        System.out.println(segmentID);
        for (int i = 1; i < segmentID; i++) {
            ds = MapdbDocStore.createOrOpen("segment" + i + ".db");
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
        Map<String, List<Integer>> invertedLists = new TreeMap<>();
        Map<Integer, Document> documents = new TreeMap<>();
        Map<String, Integer> wordDic = new TreeMap<>();

        Path wordsPath = Paths.get("./segment" + segmentNum + "a");
        PageFileChannel wordsFileChannel = PageFileChannel.createOrOpen(wordsPath);

//        ByteBuffer limBuffer = wordsFileChannel.readPage(1);
//        System.out.println(limBuffer);
//
//        int lim = limBuffer.getInt();
//        System.out.println(lim);

        ByteBuffer wordsBuffer = wordsFileChannel.readAllPages();
        wordsBuffer.rewind();
        int lim = wordsBuffer.getInt();
        System.out.println(lim);

        wordsBuffer.position(4096);
        wordsBuffer.limit(4096+lim);

        System.out.println(wordsBuffer);
        while (wordsBuffer.hasRemaining()) {
//            String word = "";
            int wordLen = wordsBuffer.getInt();
//            for (int i = 0; i < wordLen; i++) {
//                word += wordsBuffer.getChar();
//            }
            byte[] wordb = new byte[wordLen];
            wordsBuffer.get(wordb, 0, wordLen);
            int listLen = wordsBuffer.getInt();
            String words = new String(wordb);
            System.out.println(wordLen+","+words+","+listLen);
            wordDic.put(words, listLen);
            System.out.println(wordsBuffer);
        }

        wordsFileChannel.close();

        Path listPath = Paths.get("./segment" + segmentNum + "b");
        PageFileChannel listFileChannel = PageFileChannel.createOrOpen(listPath);
        ByteBuffer listBuffer = listFileChannel.readAllPages();

        listBuffer.rewind();
//        listBuffer.limit(16);
        System.out.println(listBuffer);
        for (String word: wordDic.keySet()) {

            List<Integer> list = new LinkedList<>();
            int listLen = wordDic.get(word);
            System.out.println(word+","+listLen);
            for (int i = 0; i < listLen; i++) {
                list.add(listBuffer.getInt());
//                System.out.println(word+","+listLen);
            }
            invertedLists.put(word, list);

        }

        listFileChannel.close();

        DocumentStore ds = MapdbDocStore.createOrOpen("segment" + segmentNum + ".db");
        Iterator<Map.Entry<Integer, Document>> itr = ds.iterator();
        while(itr.hasNext()) {
            Map.Entry<Integer, Document> entry = itr.next();
            documents.put(entry.getKey(), entry.getValue());
        }
        ds.close();
        return new InvertedIndexSegmentForTest(invertedLists, documents);
    }

    public static void main(String args[]) {
        Analyzer an = new ComposableAnalyzer(new PunctuationTokenizer(), token -> token);
        String file = "./index/Team19FlushTest/";
        InvertedIndexManager iim = createOrOpen(file, an);
        iim.addDocument(new Document("cat dog"));
        iim.addDocument(new Document("cat elephant"));
        iim.flush();
//        iim.addDocument(new Document("cat dog"));
//        iim.addDocument(new Document("wolf dog"));
//        iim.flush();
        System.out.println(iim.getNumSegments());

        InvertedIndexSegmentForTest test = iim.getIndexSegment(0);
        System.out.println(test.getInvertedLists().get("cat"));


//        Iterator<Document> documentIterator = iim.documentIterator();
//        while(documentIterator.hasNext()) {
//            System.out.println(documentIterator.next().getText());
//        }

//        ByteBuffer bf = ByteBuffer.allocateDirect(20);
//        String word = "abcd";
//
//        byte[] tmp = word.getBytes();
//        bf.put(tmp);
//        bf.put(0,"q");
//        bf.rewind();
//
//        byte[] tmp1 = new byte[4];
//        bf.get(tmp1,0,4);
//        String words = new String(tmp1);
//        System.out.println(words);


//        String tem = "";
//        for (int i = 0; i < word.length(); i++) {
//            bf.putChar(word.charAt(i));
//        }
//
//        bf.rewind();
//        for (int i = 0; i < word.length(); i++) {
//            tem += bf.getChar();
//            System.out.println(bf);
//        }
//        System.out.println(tem);
    }
}
