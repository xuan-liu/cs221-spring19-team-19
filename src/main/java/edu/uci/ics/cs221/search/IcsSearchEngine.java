
package edu.uci.ics.cs221.search;

import edu.uci.ics.cs221.analysis.Analyzer;
import edu.uci.ics.cs221.analysis.ComposableAnalyzer;
import edu.uci.ics.cs221.analysis.PorterStemmer;
import edu.uci.ics.cs221.analysis.PunctuationTokenizer;
import edu.uci.ics.cs221.index.inverted.InvertedIndexManager;
import edu.uci.ics.cs221.index.inverted.PageFileChannel;
import edu.uci.ics.cs221.index.inverted.Pair;
import edu.uci.ics.cs221.index.inverted.Pair.*;
import edu.uci.ics.cs221.storage.Document;
import edu.uci.ics.cs221.storage.DocumentStore;
import edu.uci.ics.cs221.storage.MapdbDocStore;

import java.io.*;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class IcsSearchEngine {

    private InvertedIndexManager manager;

    private Path docPath;
    // private ByteBuffer buffer;

    // private Map<Integer, Document> docMap;
    // private Map<Integer, Double> ranking;
    // private Map<Integer, String> urlMap;

    // private List<Pair<Integer, Double>> pageRanks;

    /**
     * Initializes an IcsSearchEngine from the directory containing the documents and the InvertedIndexManager
     *
     */

    public static IcsSearchEngine createSearchEngine(Path documentDirectory, InvertedIndexManager indexManager) {
        return new IcsSearchEngine(documentDirectory, indexManager);
    }

    private IcsSearchEngine(Path documentDirectory, InvertedIndexManager indexManager) {
        if (Files.exists(documentDirectory) && Files.isDirectory(documentDirectory)) {
            manager = indexManager;
            manager.STORE_PARAMETER = 220;
            docPath = documentDirectory;
            //docMap = new TreeMap<>();
            // urlMap = new TreeMap<>();
            //ranking = new TreeMap<>();
            //buffer = ByteBuffer.allocate(InvertedIndexManager.STORE_PARAMETER * 250);
            //buffer.rewind();
        }
        else {
            throw new RuntimeException(documentDirectory + " is not a directory!");
        }
    }

    private long getNumFiles(String path) {
        try {
            Stream<Path> files = Files.list(Paths.get(path));
            return files.count();
        }
        catch (IOException e) {
            throw new RuntimeException("IO Error Encountered! " + e.toString());
        }
    }

    /**
     * Writes all ICS web page documents in the document directory to the inverted index.
     */

    public void writeIndex() {
        String folder = docPath.toString() + "/cleaned";
        long size = getNumFiles(folder);
        int seg = 0;
        if (size <= 0) {
            throw new RuntimeException("Empty Directory!");
        }
        // docSize = docFiles.length;
        for (int index = 0; index < size; index++) {
            Path tempPath = Paths.get(folder + "/" + index);
            try {
                List<String> lines = Files.readAllLines(tempPath);
                int id = Integer.parseInt(lines.get(0).trim());
                String url = lines.get(1).trim();
                String text;
                if (lines.size() < 3) {
                    text = "_";
                }
                else {
                    text = lines.get(2).trim();
                }
                // urlMap.put(id, url);
                Document doc = new Document(text);
                // urlMap.put(id, url);
                manager.addDocument(doc);

                // docMap.put(id, doc);
                //if (urlMap.size() >= InvertedIndexManager.DEFAULT_FLUSH_THRESHOLD) {
                //    appendDocs(seg);
                //    seg++;
                //}
            }
            catch (FileNotFoundException notFound) {
                System.out.println("file #" + index + " skipped!");
            }
            catch (IOException e) {
                throw new RuntimeException("IO Error Encountered! " + "(" + e.toString() + ")");
            }
        }
        //writeBufferOnDisk();
    }

    /*private void appendDocs(int index) {
        System.out.println("appending documents!");
        String path = docPath.toString() + "/Documents/segment " + index + ".db";
        DocumentStore ds = MapdbDocStore.createWithBulkLoad(path, docMap.entrySet().iterator());
        System.out.println(ds.size());
        ds.close();
        docMap = new HashMap<>();
    }*/

    /**
     * loading the url and id info into the byte buffer
     */

    private void loadBuffer(int id, String url) {
        //buffer.putInt(id);
        //buffer.putInt(url.length());
        //buffer.put(url.getBytes());
    }

    /**
     * write the information of the documents into segments on disk
     */

    private void writeBufferOnDisk() {
        String path = docPath.toString() + "./output/URLs";
        //PageFileChannel pfc = PageFileChannel.createOrOpen(Paths.get(path));
        //pfc.appendAllBytes(buffer);
        //buffer = ByteBuffer.allocate(0);
    }

    /**
     * compute the graph representation of the pages, incoming and outgoing edges
     */

    private void makeGraph() {
        String folder = docPath.toString() + "/id-graph.tsv";
        Map<Integer, List<Integer>> incoming = new HashMap<>();
        Map<Integer, List<Integer>> outgoing = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(folder));
            for (String edge : lines) {
                String[] info = edge.trim().split("\t");
                int from = Integer.parseInt(info[0]);
                int to = Integer.parseInt(info[1]);
                if (incoming.containsKey(to)) {
                    List<Integer> edges = incoming.get(to);
                    edges.add(from);
                    incoming.replace(to, edges);
                }
                else {
                    List<Integer> edges = new ArrayList<>();
                    edges.add(from);
                    incoming.put(to, edges);
                }
                if (outgoing.containsKey(from)) {
                    List<Integer> edges = outgoing.get(from);
                    edges.add(to);
                    outgoing.replace(from, edges);
                }
                else {
                    List<Integer> edges = new ArrayList<>();
                    edges.add(to);
                    outgoing.put(from, edges);
                }
            }

            graphOnDisk(incoming, outgoing);
        }
        catch (IOException e) {
            throw new RuntimeException("IO Error Encountered! " + "(" + e.toString() + ")");
        }
    }

    private void graphOnDisk(Map<Integer, List<Integer>> incoming, Map<Integer, List<Integer>> outgoing) {
        String path = docPath.toString() + "/output/graph";
        ByteBuffer bb = ByteBuffer.allocate(incoming.size() * 8 + 600 * 4);
        bb.rewind();
        for (int id : incoming.keySet()) {
            int cap = 8 + incoming.get(id).size() * 4;
            //ByteBuffer bb = ByteBuffer.allocate(cap);
            //bb.rewind();
            bb.putInt(id);
            bb.putInt(incoming.get(id).size());
            for (int tempID : incoming.get(id)) {
                bb.putInt(tempID);
            }
        }
    }

    /**
     * Computes the page rank score from the "id-graph.tsv" file in the document directory.
     * The results of the computation can be saved in a class variable and will be later retrieved by `getPageRankScores`.
     */

    public void computePageRank(int numIterations) {
        makeGraph();
        double damping = 0.85;
        for (int itr = 0; itr < numIterations; itr++) {
            //pageRanks = new ArrayList<>();
            /*for (int id : incoming.keySet()) {
                double pr = 1 - damping;
                for (int tempID : incoming.get(id)) {
                    double tempRank;
                    if (ranking.containsKey(tempID)) {
                        tempRank = ranking.get(tempID);
                    }
                    else {
                        tempRank = 1;
                    }
                    int c = outgoing.get(tempID).size();
                    pr += damping * (tempRank / c);
                }
                if (ranking.containsKey(id)) {
                    ranking.replace(id, pr);
                }
                else {
                    ranking.put(id, pr);
                }
                Pair temp = new Pair(id, pr);
                pageRanks.add(temp);
            }*/
        }
    }

    /**
     * Gets the page rank score of all documents previously computed. Must be called after `cmoputePageRank`.
     * Returns an list of <DocumentID - Score> Pairs that is sorted by score in descending order (high scores first).
     */

    public List<Pair<Integer, Double>> getPageRankScores() {
        //return pageRanks;
        return null;
    }

    /**
     * get the document size
     * @return the size of all docs added
     */

    //private long getDocSize() {
    //    return docSize;
    //}

    /**
     * Searches the ICS document corpus and returns the top K documents ranked by combining TF-IDF and PageRank.
     *
     * The search process should first retrieve ALL the top documents from the InvertedIndex by TF-IDF rank,
     * by calling `searchTfIdf(query, null)`.
     *
     * Then the corresponding PageRank score of each document should be retrieved. (`computePageRank` will be called beforehand)
     * For each document, the combined score is  tfIdfScore + pageRankWeight * pageRankScore.
     *
     * Finally, the top K documents of the combined score are returned. Each element is a pair of <Document, combinedScore>
     *
     *
     * Note: We could get the Document ID by reading the first line of the document.
     * This is a workaround because our project doesn't support multiple fields. We cannot keep the documentID in a separate column.
     */

    public Iterator<Pair<Document, Double>> searchQuery(List<String> query, int topK, double pageRankWeight) {
        Iterator<Pair<Document, Double>> rawSearch = manager.searchTfIdf(query, null);
        Map<Double, Document> result = new HashMap<>();
        /*while (rawSearch.hasNext()) {
            Pair curr = rawSearch.next();
            Document temp = (Document) curr.getLeft();
            double tf = (double) curr.getRight();
            int id = docMap.get(temp);
            double pr = ranking.get(id);
            double score = tf + pr * pageRankWeight;
            result.put(score, temp);
        }
        List<Double> keys = new ArrayList<>(result.keySet());
        Collections.sort(keys);
        while (keys.size() > topK) {
            keys.remove(0);
        }
        Collections.reverse(keys);*/
        List<Pair<Document, Double>> res = new ArrayList<>();
        /*for (double score : keys) {
            Document doc = result.get(score);
            Pair ans = new Pair(doc, score);
            res.add(ans);
        }*/
        return res.iterator();
    }

    public static void main(String[] args) throws FileNotFoundException, IOException {

        /*String indexFolder = "./";
        Path path = Paths.get(indexFolder);
        String testing = "./index/IcsSearchEngineTest";
        deleteDirectory(testing);
        Path indexPath = Paths.get(testing);
        Analyzer analyzer = new ComposableAnalyzer(new PunctuationTokenizer(), new PorterStemmer());

        InvertedIndexManager invertedIndexManager;
        IcsSearchEngine icsSearchEngine;

        InvertedIndexManager.DEFAULT_FLUSH_THRESHOLD = 8;

        invertedIndexManager = InvertedIndexManager.createOrOpen(indexPath.toString(), analyzer);
        System.out.println();
        icsSearchEngine = IcsSearchEngine.createSearchEngine(path, invertedIndexManager);

        icsSearchEngine.writeIndex();

        icsSearchEngine.computePageRank(1);

        System.out.println(icsSearchEngine.ranking.get(0));

        System.out.println(invertedIndexManager.getNumSegments());

        deleteDirectory(testing);*/
    }

    private static void deleteDirectory(String path) {
        File file = new File(path);
        String[] fn = file.list();
        if (fn == null) {
            return;
        }
        for (String f : fn){
            File temp = new File(path, f);
            temp.delete();
        }
        file.delete();
    }

}
