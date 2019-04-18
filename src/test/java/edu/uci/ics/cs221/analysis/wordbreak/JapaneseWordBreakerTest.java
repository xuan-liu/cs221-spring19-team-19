
package edu.uci.ics.cs221.analysis.wordbreak;

import edu.uci.ics.cs221.analysis.JapaneseWordBreaker;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class JapaneseWordBreakerTest {

    // test if JapaneseWordBreaker can tokenize and eliminate stop words
    @Test
    public void test1() {
        String text = "にをで";
        List<String> expected = Arrays.asList();
        JapaneseWordBreaker tokenizer = new JapaneseWordBreaker();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    // test if JapaneseWordBreaker can break a sentence without space properly
    @Test
    public void test2() {
        String text = "いただくサイトあまり自由子";
        List<String> expected = Arrays.asList("いただく", "サイト", "あまり", "自由", "子");
        JapaneseWordBreaker tokenizer = new JapaneseWordBreaker();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    // test if JapaneseWordBreaker can break the sentence correctly and eliminate the stop words in the sentence
    @Test
    public void test3() {
        String text = "ものできる|ござる";
        List<String> expected = Arrays.asList("ござる");
        JapaneseWordBreaker tokenizer = new JapaneseWordBreaker();
        assertEquals(expected, tokenizer.tokenize(text));
    }
}
