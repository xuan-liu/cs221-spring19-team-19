
package edu.uci.ics.cs221.search;

import edu.uci.ics.cs221.index.inverted.InvertedIndexManager;
import edu.uci.ics.cs221.index.inverted.Pair;
import edu.uci.ics.cs221.storage.Document;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class IcsSearchEngine {

    private InvertedIndexManager manager;

    private Path docPath;

    private Map<Document, Integer> docMap;
    private Map<Integer, Double> ranking;

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
            docMap = new HashMap<>();
            ranking = new TreeMap<>();
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
        if (size <= 0) {
            throw new RuntimeException("Empty Directory!");
        }
        // reading all the files in the directory
        for (int index = 0; index < size; index++) {
            Path tempPath = Paths.get(folder + "/" + index);
            try {
                String content = new String(Files.readAllBytes(tempPath), "UTF-8");

                // loading the whole text in the document
                Document doc = new Document(content);
                manager.addDocument(doc);

                // note that index is the doc ID
                docMap.put(doc, index);
            }
            catch (FileNotFoundException notFound) {
                System.out.println("file #" + index + " skipped!");
            }
            catch (IOException e) {
                throw new RuntimeException("IO Error Encountered! " + "(" + e.toString() + ")");
            }
        }
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

    private void populateGraph(Map<Integer, List<Integer>> incoming, Map<Integer, List<Integer>> outgoing) {
        String folder = docPath.toString() + "/id-graph.tsv";
        try {

            // loading the graph information
            List<String> lines = Files.readAllLines(Paths.get(folder));
            for (String edge : lines) {
                String[] info = edge.trim().split("\t");

                // reading from and to nodes
                int from = Integer.parseInt(info[0]);
                int to = Integer.parseInt(info[1]);

                // adding to incoming edges map
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

                // adding to outgoing edges map
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
        }
        catch (IOException e) {
            throw new RuntimeException("IO Error Encountered! " + "(" + e.toString() + ")");
        }
    }

    /**
     * Computes the page rank score from the "id-graph.tsv" file in the document directory.
     * The results of the computation can be saved in a class variable and will be later retrieved by `getPageRankScores`.
     */

    public void computePageRank(int numIterations) {
        Map<Integer, List<Integer>> incoming = new TreeMap<>();
        Map<Integer, List<Integer>> outgoing = new TreeMap<>();

        // making the graph structure
        populateGraph(incoming, outgoing);
        double damping = 0.85;

        // starting the iteration
        for (int itr = 0; itr < numIterations; itr++) {
            for (int id : incoming.keySet()) {
                double pr = 1 - damping;

                // calculating the page rank
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

                // adding the page rank to the map
                if (ranking.containsKey(id)) {
                    ranking.replace(id, pr);
                }
                else {
                    ranking.put(id, pr);
                }
            }
        }
    }

    /**
     * Gets the page rank score of all documents previously computed. Must be called after `cmoputePageRank`.
     * Returns an list of <DocumentID - Score> Pairs that is sorted by score in descending order (high scores first).
     */

    public List<Pair<Integer, Double>> getPageRankScores() {
        List<Pair<Integer, Double>> pageRank = new ArrayList<>();
        for (int id : ranking.keySet()) {
            Pair<Integer, Double> target = new Pair(id, ranking.get(id));
            insertScores(target, pageRank);
        }
        return pageRank;
    }

    /**
     * insert in order in a list
     * @param target
     * @param arr
     */

    private void insertScores(Pair<Integer, Double> target, List<Pair<Integer, Double>> arr) {
        int n = arr.size();
        for (int i = 0; i < n; i++) {
            if (target.getRight() >= arr.get(i).getRight()) {
                arr.add(i, target);
                return;
            }
        }
        arr.add(target);
    }

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
        Map<Document, Double> result = new HashMap<>();

        // getting each score
        while (rawSearch.hasNext()) {
            Pair curr = rawSearch.next();
            Document temp = (Document) curr.getLeft();
            double tf = (double) curr.getRight();
            int id = docMap.get(temp);
            double pr;

            // get the value of pr
            if (ranking.containsKey(id)) {
                pr = ranking.get(id);
            }
            else {
                pr = 0;
            }
            double score = tf + pr * pageRankWeight;
            result.put(temp, score);
        }

        // adding the score and documents to a list
        List<Pair<Document, Double>> res = new ArrayList<>();
        for (Document doc : result.keySet()) {
            Pair<Document, Double> target = new Pair(doc, result.get(doc));
            insertDocs(target, res);
        }

        // cutting the result
        while (res.size() > topK) {
            res.remove(res.size() - 1);
        }
        return res.iterator();
    }

    /**
     * insert documents and scores in order
     * @param target
     * @param arr
     */

    private void insertDocs(Pair<Document, Double> target, List<Pair<Document, Double>> arr) {
        int n = arr.size();
        for (int i = 0; i < n; i++) {
            if (target.getRight() >= arr.get(i).getRight()) {
                arr.add(i, target);
                return;
            }
        }
        arr.add(target);
    }

    /**
     * delete files and directories if needed
     * @param path
     */

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
