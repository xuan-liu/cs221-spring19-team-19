package edu.uci.ics.cs221.analysis.wordbreak;

import edu.uci.ics.cs221.analysis.WordBreakTokenizer;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class WordBreakTokenizerTest {

    @Test
    public void test1() {
        String text = "catdog";
        List<String> expected = Arrays.asList("cat", "dog");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team8Test1() {
        String text = "THISiswhATItoldyourI'llFRIendandI'llgoonlinecontactcan'tforget";
        List<String> expected = Arrays.asList("old", "i'll", "friend", "i'll","go","online","contact","can't","forget");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team8Test2(){
        String text = "informationinforTHOUGHTFULLYcopyrightwhatevercontactablewhatevergreen";
        List<String> expected = Arrays.asList("information", "thoughtfully", "copyright", "whatever", "contact", "able","whatever", "green" );
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test(expected = RuntimeException.class)
    public void team8Test3(){
        String text = "$reLLL(  ghn)iog*";
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        tokenizer.tokenize(text);
    }

    @Test
    public void team9Test1() {
        String text = "";
        List<String> expected = Arrays.asList("");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team9Test2() {
        String text = "ILIKEINFORMATIONRETRIEVAL";
        List<String> expected = Arrays.asList("like", "information", "retrieval");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team9Test3() {
        String text = "thereareelevenpineapples";
        List<String> expected = Arrays.asList("eleven", "pineapples");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test(expected = IllegalArgumentException.class)
    public void team9Test4() {
        String text = "abc123";
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        tokenizer.tokenize(text);
    }

    @Test
    public void team10Test1() {
        String text = "Itisnotourgoal";
        List<String> expected = Arrays.asList("goal");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team10Test2() {
        String text = "FindthelongestpalindromicstringYoumayassumethatthemaximumlengthisonehundred";
        List<String> expected = Arrays.asList("find", "longest", "palindromic", "string", "may",
                "assume", "maximum", "length", "one", "hundred");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team11Test1() {
        String text = "tobeornottobe";
        List<String> expected = Arrays.asList();
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team11Test2() {
        String text = "";
        List<String> expected = Arrays.asList();
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void team11Test3() {
        String text = "b";
        List<String> expected = Arrays.asList();
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        tokenizer.tokenize(text);
        assert(false);
    }

    @Test
    public void team11Test4() {
        String text = "searchnewtimeuse";
        List<String> expected = Arrays.asList("search", "new", "time", "use");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team11Test5() {
        String text = "seaRchneWtiMeuSe";
        List<String> expected = Arrays.asList("search", "new", "time", "use");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team11Test6() {
        String text = "SEARCHNEWTIMEUSE";
        List<String> expected = Arrays.asList("search", "new", "time", "use");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team11Test7() {
        String text = "thesearchnewtimeuse";
        List<String> expected = Arrays.asList("search", "new", "time", "use");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team11Test8() {
        String text = "searchthenewtimeuse";
        List<String> expected = Arrays.asList("search", "new", "time", "use");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team11Test9() {
        String text = "searchnewtimeusethe";
        List<String> expected = Arrays.asList("search", "new", "time", "use");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team12Test1() {
        String text = "thelordofthering";
        List<String> expected = Arrays.asList("lord", "ring");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test
    public void team12Test2()
    {
        String text = "IWANTtohavepeanutbuttersandwich";
        List<String> expected = Arrays.asList("want", "peanut", "butter", "sandwich");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void team12Test3()
    {
        String text = "Where did Ghada go?";
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        tokenizer.tokenize(text);
    }

    @Test(expected = RuntimeException.class)
    public void team14Test1() {
        String text = "fralprtnqela";
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        tokenizer.tokenize(text);
    }

    @Test
    public void team14Test2() {
        String text = "WEhaveaCOOLTaskinFrontOfUSANDwEShouldbehavingAgoodTIme";
        List<String> expected = Arrays.asList("cool","task","front","us","behaving","good","time");
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();

        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test(expected = RuntimeException.class)
    public void team14Test3() {
        String text = "WhatHappensWhenWeaddAperiod.";
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        tokenizer.tokenize(text);
    }

    @Test(expected = RuntimeException.class)
    public void team14Test4() {
        String text = "This is too check if an exception is thrown when there are spaces";
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        tokenizer.tokenize(text);
    }

}
