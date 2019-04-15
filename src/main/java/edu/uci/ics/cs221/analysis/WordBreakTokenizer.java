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
    Map<String, Double> wordDict = new HashMap<>(); // a map to store the word dictionary, key is the word, value is the frequency

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
     * For the input String, tokenize it based on white spaces, remove stop words, and
     * compute the log probability, assuming the tokens are independent
     */

    private double computeFrequency(String s, Map<String, Double> dict) {
        PunctuationTokenizer pt = new PunctuationTokenizer();
        List<String> l = pt.tokenize(s);
        double logFreq = 0.0;
        for (int i = 0; i < l.size(); i++) {
            logFreq += Math.log(dict.get(l.get(i)));
        }
//        System.out.println(l);
//        System.out.println(logFreq);
        return logFreq;
    }

    public static void main(String[] args) {
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        System.out.println(tokenizer.tokenize("THISiswhATItoldyourI'llFRIendandI'llgoonlinecontactcan'tforget"));
    }
}
