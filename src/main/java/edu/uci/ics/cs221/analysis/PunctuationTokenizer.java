
package edu.uci.ics.cs221.analysis;

import java.util.*;

/**
 * Project 1, task 1: Implement a simple tokenizer based on punctuations and white spaces.
 *
 * For example: the text "I am Happy Today!" should be tokenized to ["happy", "today"].
 *
 * Requirements:
 *  - White spaces (space, tab, newline, etc..) and punctuations provided below should be used to tokenize the text.
 *  - White spaces and punctuations should be removed from the result tokens.
 *  - All tokens should be converted to lower case.
 *  - Stop words should be filtered out. Use the stop word list provided in `StopWords.java`
 *
 */

public class PunctuationTokenizer implements Tokenizer {

    public static Set<String> punctuations = new HashSet<>();
    static {
        punctuations.addAll(Arrays.asList(",", ".", ";", "?", "!"));
    }

    public PunctuationTokenizer() {}

    public List<String> tokenize(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder del = new StringBuilder(" \n\t\r");
        for (String temp : punctuations) {
            del.append(temp);
        }
        StringTokenizer st = new StringTokenizer(text, del.toString());
        while (st.hasMoreTokens()) {
            String s = st.nextToken().toLowerCase();
            if (!StopWords.stopWords.contains(s) && s.length() != 0) {
                result.add(s);
            }
        }
        return result;
    }

}
