
package edu.uci.ics.cs221.analysis;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class JapaneseWordBreaker implements Tokenizer {

    Map<String, Double> dictionary;

    public JapaneseWordBreaker() {
        try {
            dictionary = new HashMap<>();
            String corpus = "cs221_frequency_dictionary_jp.txt";
            URL dictResource = WordBreakTokenizer.class.getClassLoader().getResource(corpus);
            List<String> dictLines = Files.readAllLines(Paths.get(dictResource.toURI()));
            double freqSum = 0;
            for (int i = 0; i < dictLines.size(); i++) {
                freqSum += Double.parseDouble(dictLines.get(i).split(" ")[1]);
            }
            for (int i = 0; i < dictLines.size(); i++) {
                String s = dictLines.get(i);
                if (s.startsWith("\uFEFF")) {
                    s = s.substring(1);
                }
                dictionary.put(s.split(" ")[2], (double) Double.parseDouble(s.split(" ")[1]) / freqSum);
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> tokenize(String text) {
        if (text.length() == 0) {
            return Arrays.asList();
        }
        return breakWordDP(text);
    }

    private List<String> breakWordDP(String word) {
        int path[][] = new int[word.length()][word.length()];
        double logFreq[][] = new double[word.length()][word.length()];
        for (int i = 0; i < path.length; i++) {
            for (int j = 0; j < path[i].length ; j++) {
                path[i][j] = -1;
                logFreq[i][j] = -Double.MAX_VALUE;
            }
        }
        for (int l = 1; l <= word.length(); l++) {
            for (int i = 0; i < word.length() - l + 1 ; i++) {
                int j = i + l-1;
                String str = word.substring(i,j+1);
                if (dictionary.containsKey(str)) {
                    logFreq[i][j] = Math.log(dictionary.get(str));
                    path[i][j] = i;
                }
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
        if (path[0][word.length()-1] == -1){
            throw new RuntimeException("WordBreakerTokenizer Error: unable to break the text!");
        }
        String result = backwardResult(path, word, 0, word.length() -1);
        PunctuationTokenizer pt = new PunctuationTokenizer();
        List<String> ans = pt.tokenize(result);
        for (int l = 0; l < ans.size(); l++) {
            String temp = ans.get(l);
            if (StopWords.stopWordsJapanese.contains(temp)) {
                ans.remove(l);
                l--;
            }
        }
        return ans;
    }

    private String backwardResult(int[][] path, String s, int i, int j) {
        int k = path[i][j];
        if (i == k) {
            return s.substring(i, j + 1);
        }
        String sLeft = backwardResult(path, s, i, k - 1);
        String sRight = backwardResult(path, s, k, j);
        return sLeft + " " + sRight;
    }

}
