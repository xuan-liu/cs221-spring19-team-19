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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> tokenize(String text) {
        Set<String> keys = dictionary.keySet();
        List<String> possibleString = wordBreakTopDown(text.toLowerCase(), keys);
        if (possibleString.size() == 0) {
            throw new UnsupportedOperationException("WordBreakerTokenizer Error: Unable to break the text!");
        }
        double maxFreq = -1 * Double.MAX_VALUE;
        String result = "";
        for (int i = 0; i < possibleString.size(); i++) {
            double freq = computeFrequency(possibleString.get(i));
            if (freq > maxFreq) {
                result = possibleString.get(i);
                maxFreq = freq;
            }
        }
        PunctuationTokenizer pt = new PunctuationTokenizer();
        return pt.tokenize(result);
    }

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

    private double computeFrequency(String s) {
        StringTokenizer st = new StringTokenizer(s, " \t\n,.;?!");
        double logFreq = 0.0;
        while (st.hasMoreTokens()) {
            String temp = st.nextToken();
            logFreq += Math.log(dictionary.get(temp));
        }
        return logFreq;
    }

}
