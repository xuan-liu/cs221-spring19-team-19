
package edu.uci.ics.cs221.analysis.punctuation;

import edu.uci.ics.cs221.analysis.PunctuationTokenizer;
import org.junit.Test;

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
        String text = "\tgood example\nyes great example";
        List<String> expected = Arrays.asList("good", "example", "yes", "great", "example");
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

}
