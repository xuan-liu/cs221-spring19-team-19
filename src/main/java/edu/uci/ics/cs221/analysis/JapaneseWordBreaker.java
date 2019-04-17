package edu.uci.ics.cs221.analysis;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class JapaneseWordBreaker implements Tokenizer {
    Map<String, Double> wordDict = new HashMap<>(); // a map to store the word dictionary, key is the word, value is the frequency
    List<String> dictLines;
    /**
     * Initialize the wordDict
     */

    public JapaneseWordBreaker() {
        try {
            // load the dictionary corpus
            URL dictResource = WordBreakTokenizer.class.getClassLoader().getResource("cs221_frequency_dictionary_jp.txt");
            dictLines = Files.readAllLines(Paths.get(dictResource.toURI()));

            double freqSum = 0;
            for (int i = 0; i < dictLines.size(); i++) {
                freqSum += Double.parseDouble(dictLines.get(i).split(" ")[1]);
            }

            for (int i = 0; i < dictLines.size(); i++) {
                String s = dictLines.get(i);
                if (s.startsWith("\uFEFF")) {
                    s = s.substring(1);
                }
                this.wordDict.put(s.split(" ")[2], (double) Double.parseDouble(s.split(" ")[1]) / freqSum);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return the optimal tokenize result with highest probability
     */

    public List<String> tokenize(String text) {
        List<String> possibleString = wordBreakTopDown(text.toLowerCase(), wordDict.keySet());

        if (possibleString.size() == 0) {
            throw new UnsupportedOperationException("there's no possible way to break the string!");
        }

        double maxFreq = -Double.MAX_VALUE;
        String result = "";
        for (int i = 0; i < possibleString.size(); i++) {
            double freq = computeFrequency(possibleString.get(i), wordDict);
            if (freq > maxFreq) {
                result = possibleString.get(i);
                maxFreq = freq;
            }
        }
        PunctuationTokenizer pt = new PunctuationTokenizer();
        return pt.tokenize(result);
    }

    /**
     * Find all the possible word combinations of the input String.
     */

    private List<String> wordBreakTopDown(String s, Set<String> wordDict) {
        Map<Integer, List<String>> dp = new HashMap<>();
        int max = 0;
        for (String s1 : wordDict) {
            max = Math.max(max, s1.length());
        }
        return wordBreakUtil(s, wordDict, dp, 0, max);
    }

    private List<String> wordBreakUtil(String s, Set<String> dict, Map<Integer, List<String>> dp, int start, int max) {
        if (start == s.length()) {
            return Collections.singletonList("");
        }

        if (dp.containsKey(start)) {
            return dp.get(start);
        }

        List<String> words = new ArrayList<>();
        for (int i = start; i < start + max && i < s.length(); i++) {
            String newWord = s.substring(start, i + 1);
            if (!dict.contains(newWord)) {
                continue;
            }
            List<String> result = wordBreakUtil(s, dict, dp, i + 1, max);
            for (String word : result) {
                String extraSpace = word.length() == 0 ? "" : " ";
                words.add(newWord + extraSpace + word);
            }
        }
        dp.put(start, words);
        return words;
    }

    /**
     * For the input String, tokenize it based on white spaces, and
     * compute the log probability, assuming the tokens are independent
     */

    private double computeFrequency(String s, Map<String, Double> dict) {
        StringTokenizer st = new StringTokenizer(s, " \t\n,.;?!");
        double logFreq = 0.0;
        while (st.hasMoreTokens()) {
            String temp = st.nextToken();
            logFreq += Math.log(dict.get(temp));
        }
        return logFreq;
    }

    public static void main(String[] args) {
        JapaneseWordBreaker br = new JapaneseWordBreaker();
        String s = br.dictLines.get(0);
        String[] ss = s.split(" ");
        System.out.println(br.wordDict.size());
    }
}
