package edu.uci.ics.cs221.analysis.wordbreak;

import edu.uci.ics.cs221.analysis.JapaneseWordBreaker;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class JapaneseWordBreakerTest {

    @Test
    public void test1() {
        String text = "にをで";
        List<String> expected = Arrays.asList("に", "を", "で");
        JapaneseWordBreaker tokenizer = new JapaneseWordBreaker();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void test2() {
        String text = "いただくサイトあまり自由子";
        List<String> expected = Arrays.asList("いただく", "サイト", "あまり", "自由", "子");
        JapaneseWordBreaker tokenizer = new JapaneseWordBreaker();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void test3() {
        String text = "ものできる|ござる";
        List<String> expected = Arrays.asList("もの", "できる", "|", "ござる");
        JapaneseWordBreaker tokenizer = new JapaneseWordBreaker();
        assertEquals(expected, tokenizer.tokenize(text));
    }
}
