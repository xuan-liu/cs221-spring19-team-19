
package edu.uci.ics.cs221.analysis.punctuation;

import edu.uci.ics.cs221.analysis.PunctuationTokenizer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PunctuationTokenizerTest {

    @Test
    public void test() {
        String text = "I am Happy Today!";
        List<String> expected = Arrays.asList("happy", "today");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team3Test1(){
        String text = "Good morning, Sara!";
        List<String> expected = Arrays.asList("good", "morning","sara");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team3Test2(){
        String text = "Information Retrival is      the best course in UCI!";
        List<String> expected = Arrays.asList("information", "retrival","best","course","uci");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team3Test3(){
        String text = "Information Retrival is \t \n the best course in UCI!";
        List<String> expected = Arrays.asList("information", "retrival","best","course","uci");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team6Test1() {
        String text = " testcase\tgood example\nyes great example\n";
        List<String> expected = Arrays.asList("testcase", "good", "example",
                "yes", "great", "example");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team6Test2() {
        String text = "Word LOL means Laughing. WHO";
        List<String> expected = Arrays.asList("word", "lol", "means", "laughing");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team6Test3() {
        String text = "good, four-year-old children. never asia come? it's china! thanks.";
        List<String> expected = Arrays.asList("good", "four-year-old", "children", "never", "asia",
                "come", "it's", "china", "thanks");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team6Test4() {
        String text = "I cannot decide which car I like best " +
                "the Ferrari, with its quick acceleration and " +
                "sporty look; the midsize Ford Taurus, with " +
                "its comfortable seats and ease of handling; " +
                "or the compact Geo, with its economical fuel consumption.";
        List<String> expected = Arrays.asList("cannot", "decide", "car", "like", "best",
                "ferrari", "quick", "acceleration", "sporty", "look", "midsize", "ford",
                "taurus", "comfortable", "seats", "ease", "handling", "compact", "geo", "economical",
                "fuel", "consumption");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team1Test1() {
        String text = "uci cs221\tinformation\nretrieval";
        List<String> expected = Arrays.asList("uci", "cs221", "information", "retrieval");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team1Test2() {
        String text = "uci,cs221.information;retrieval?project!1";
        List<String> expected = Arrays.asList("uci", "cs221", "information", "retrieval", "project", "1");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team1Test3() {
        String text = "uci~cs221/information>retrieval";
        List<String> expected = Arrays.asList("uci~cs221/information>retrieval");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team1Test4() {
        String text = "UciCS221InformationRetrieval";
        List<String> expected = Arrays.asList("ucics221informationretrieval");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team1Test5() {
        String text = "uci \tcs221\t\ninformation\n \tretrieval";
        List<String> expected = Arrays.asList("uci", "cs221", "information", "retrieval");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team1Test6() {
        String text = "uci,.cs221.;information;?retrieval?!project!,.1";
        List<String> expected =
                Arrays.asList("uci", "cs221", "information", "retrieval", "project", "1");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team1Test7() {
        String text = " \t\nucics221informationretrieval \t\n";
        List<String> expected = Arrays.asList("ucics221informationretrieval");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team1Test8() {
        String text = ",.;?!ucics221informationretrieval,.;?!";
        List<String> expected = Arrays.asList("ucics221informationretrieval");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team1Test9() {
        String text = " Do UCI CS221:\tInformation Retrieval, project 1 by yourself.\n";
        List<String> expected = Arrays.asList("uci", "cs221:", "information", "retrieval", "project", "1");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team4Test1() {
        System.out.println("It: can deal with empty string");
        String emptyText = "";
        List<String> expected = new ArrayList<>();
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(emptyText));
    }

    @Test
    public void team4Test2() {
        System.out.println("It: can tokenize normal string with white spaces");
        String text = "I am Happy Today!";
        List<String> expected = Arrays.asList("happy", "today");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team4Test3() {
        System.out.println("It: can deal with any punctuations and regard other special characters as normal tokens");
        String Text = "......I am not happy today!? , ) ;";
        List<String> expected = Arrays.asList("happy", "today", ")");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(Text));
    }

    @Test
    public void team4Test4() {
        System.out.println("It: should tokenize the string with multiple adjacent white spaces");
        String text = "   I     am    Happy Today!        ";
        List<String> expected = Arrays.asList("happy", "today");
        PunctuationTokenizer tokenizer = new PunctuationTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }
}
