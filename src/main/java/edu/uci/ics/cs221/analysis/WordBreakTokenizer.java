package edu.uci.ics.cs221.analysis;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.lang.Long;

/**
 * Project 1, task 2: Implement a Dynamic-Programming based Word-Break Tokenizer.
 *
 * Word-break is a problem where given a dictionary and a string (text with all white spaces removed),
 * determine how to break the string into sequence of words.
 * For example:
 * input string "catanddog" is broken to tokens ["cat", "and", "dog"]
 *
 * We provide an English dictionary corpus with frequency information in "resources/cs221_frequency_dictionary_en.txt".
 * Use frequency statistics to choose the optimal way when there are many alternatives to break a string.
 * For example,
 * input string is "ai",
 * dictionary and probability is: "a": 0.1, "i": 0.1, and "ai": "0.05".
 *
 * Alternative 1: ["a", "i"], with probability p("a") * p("i") = 0.01
 * Alternative 2: ["ai"], with probability p("ai") = 0.05
 * Finally, ["ai"] is chosen as result because it has higher probability.
 *
 * Requirements:
 *  - Use Dynamic Programming for efficiency purposes.
 *  - Use the the given dictionary corpus and frequency statistics to determine optimal alternative.
 *      The probability is calculated as the product of each token's probability, assuming the tokens are independent.
 *  - A match in dictionary is case insensitive. Output tokens should all be in lower case.
 *  - Stop words should be removed.
 *  - If there's no possible way to break the string, throw an exception.
 *
 */
public class WordBreakTokenizer implements Tokenizer {
    static Map<String, Double> wordDict = new HashMap<>(); // a map to store the word dictionary, key is the word, value is the frequency

    /**
     * Initialize the wordDict
     */

    public WordBreakTokenizer() {
        try {
            // load the dictionary corpus
            URL dictResource = WordBreakTokenizer.class.getClassLoader().getResource("cs221_frequency_dictionary_en.txt");
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
                this.wordDict.put(s.split(" ")[0], (double) Long.parseLong(s.split(" ")[1])/freqSum);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return the optimal tokenize result with highest probability
     */

    public List<String> tokenize(String text) {
        if (text.length() == 0) {
            return Arrays.asList();
        }
        return breakWordDP(text.toLowerCase(), wordDict);
    }

    /**
     * Given dictionary, use Dynamic Programming to break the word, always keep a best backtracking path for each entry
     */

    private List<String> breakWordDP(String word, Map<String, Double> dict) {
        int path[][] = new int[word.length()][word.length()];
        double logFreq[][] = new double[word.length()][word.length()];

        for (int i = 0; i < path.length; i++) {
            for (int j = 0; j < path[i].length ; j++) {
                path[i][j] = -1; //-1 indicates string between i to j cannot be split
                logFreq[i][j] = -Double.MAX_VALUE; //initialize with the lowest log frequency
            }
        }

        //fill up the matrix in bottom up manner. always keep a best backtracking path for each entry
        for (int l = 1; l <= word.length(); l++) {
            for (int i = 0; i < word.length() - l + 1 ; i++) {
                int j = i + l-1;
                String str = word.substring(i,j+1);

                //if string between i to j is in dictionary T[i][j]
                if (dict.containsKey(str)) {
                    logFreq[i][j] = Math.log(dict.get(str));
                    path[i][j] = i;
                }
                //find a k between i+1 to j such that T[i][k-1] && T[k][j] are both true
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

        //If there's no possible way to break the string, throw an exception
        if(path[0][word.length()-1] == -1){
            throw new RuntimeException("there's no possible way to break the string!");
        }

        String result = backwardResult(path, word, 0, word.length() -1);
        PunctuationTokenizer pt = new PunctuationTokenizer();
        return pt.tokenize(result);
    }

    /**
     * Help method to backward the result
     */

    private String backwardResult(int [][] path, String s, int i, int j) {
        int k = path[i][j];
        if (i == k) {
            return s.substring(i, j + 1);
        }

        String sLeft = backwardResult(path, s, i, k - 1);
        String sRight = backwardResult(path, s, k, j);
        return sLeft + " " + sRight;
    }


//    public static void main(String[] args) {
//        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
//        System.out.println(tokenizer.breakWordDP("",wordDict));
//    }
}