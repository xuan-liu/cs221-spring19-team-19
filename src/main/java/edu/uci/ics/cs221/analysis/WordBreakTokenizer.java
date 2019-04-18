
package edu.uci.ics.cs221.analysis;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class WordBreakTokenizer implements Tokenizer {

    static HashMap<String, Double> dictionary;

    // initialize the dictionary

    public WordBreakTokenizer() {
        try {
            String corpus = "cs221_frequency_dictionary_en.txt";
            dictionary = new HashMap<>();
            URL dictResource = WordBreakTokenizer.class.getClassLoader().getResource(corpus);
            List<String> dictLines = Files.readAllLines(Paths.get(dictResource.toURI()));
            long freqSum = 0;
            for (int i = 0; i < dictLines.size(); i++) {
                freqSum += Long.parseLong(dictLines.get(i).split(" ")[1]);
            }
            for (int i = 0; i < dictLines.size(); i++) {
                String s = dictLines.get(i);
                if (s.startsWith("\uFEFF")) {
                    s = s.substring(1);
                }
                dictionary.put(s.split(" ")[0], (double) Long.parseLong(s.split(" ")[1]) / freqSum);
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // return the optimal tokenize result with highest probability

    public List<String> tokenize(String text) {
        if (text.length() == 0) {
            return Arrays.asList();
        }
        return breakWordDP(text.toLowerCase());
    }

    // given dictionary, use Dynamic Programming to break the word, always keep a best backtracking path for each entry

    private List<String> breakWordDP(String word) {
        int path[][] = new int[word.length()][word.length()];
        double logFreq[][] = new double[word.length()][word.length()];
        for (int i = 0; i < path.length; i++) {
            for (int j = 0; j < path[i].length ; j++) {
                // -1 indicates string between i to j cannot be split
                path[i][j] = -1;
                //initialize with the lowest log frequency
                logFreq[i][j] = -Double.MAX_VALUE;
            }
        }
        // fill up the matrix in bottom up manner, always keep a best backtracking path for each entry
        for (int l = 1; l <= word.length(); l++) {
            for (int i = 0; i < word.length() - l + 1 ; i++) {
                int j = i + l-1;
                String str = word.substring(i,j+1);
                // if string between i to j is in dictionary T[i][j]
                if (dictionary.containsKey(str)) {
                    logFreq[i][j] = Math.log(dictionary.get(str));
                    path[i][j] = i;
                }
                // find a k between i + 1 to j such that T[i][k - 1] && T[k][j] are both true
                for(int k = i + 1; k <= j; k++){
                    if(path[i][k-1] != -1 && path[k][j] != -1){
                        if (logFreq[i][k-1] + logFreq[k][j] > logFreq[i][j]) {
                            logFreq[i][j] = logFreq[i][k-1] + logFreq[k][j];
                            path[i][j] = k;
                        }
                    }
                }
            }
        }
        // if there is no possible way to break the string, throw an exception
        if (path[0][word.length() - 1] == -1){
            throw new RuntimeException("WordBreakerTokenizer Error: unable to break the text!");
        }
        String result = backwardResult(path, word, 0, word.length() -1);
        PunctuationTokenizer pt = new PunctuationTokenizer();
        return pt.tokenize(result);
    }

    // help method to backward the result

    private String backwardResult(int [][] path, String s, int i, int j) {
        int k = path[i][j];
        if (i == k) {
            return s.substring(i, j + 1);
        }
        String sLeft = backwardResult(path, s, i, k - 1);
        String sRight = backwardResult(path, s, k, j);
        return sLeft + " " + sRight;
    }

}
