
package edu.uci.ics.cs221.analysis.stemmer;

import edu.uci.ics.cs221.analysis.PorterStemmer;
import edu.uci.ics.cs221.analysis.Stemmer;
import org.junit.Test;

import java.util.Arrays;

import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;

public class PorterStemmerTest {

    public static String testStem(Stemmer stemmer, String sentence) {
        return Arrays.stream(sentence.split("\\s+"))
                .map(token -> stemmer.stem(token))
                .collect(joining(" "));
    }

    @Test
    public void test() {
        String original = "stemming is an important concept in computer science";
        String expected = "stem is an import concept in comput scienc";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team19Test1() {
        String original = "I am writing to test the Stemmer. Turning in the final results of the applications is due this week";
        String expected = "I am write to test the Stemmer. Turn in the final result of the applic is due thi week";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team19Test2() {
        String original = "information retrieval is the activity of obtaining information system resources relevant to an information need from a collection";
        String expected = "inform retriev is the activ of obtain inform system resourc relev to an inform need from a collect";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team19Test3() {
        String original = "He is an old man who fished alone in a skiff in the Gulf Stream and he had gone twenty-two weeks without taking a fish";
        String expected = "He is an old man who fish alon in a skiff in the Gulf Stream and he had gone twenty-two week without take a fish";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team21Test1() {
        String original = "ties dogs caress need agreed disabled fitting making missing meeting meetings";
        String expected = "ti dog caress need agre disabl fit make miss meet meet";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team21Test2() {
        String original = "organization organizer international responsibility fitness";
        String expected = "organ organ intern respons fit";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team21Test3() {
        String original = "department humorousness dependence helpfulness analytical despotism";
        String expected = "depart humor depend help analyt despot";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team23Test1() {
        String original = "stemming is an important concept in computer science";
        String expected = "stem is an import concept in comput scienc";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team23Test2() {
        String original = "stemming is an important concept in computer science";
        String expected = "stem is an import concept in comput scienc";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team20Test1() {
        String original = "rate roll sky feed bled sing caress 1234";
        String expected = "rate roll sky feed bled sing caress 1234";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team20Test2() {
        String original = "caresses ponies cats";
        String expected = "caress poni cat";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team20Test3() {
        String original = "";
        String expected = "";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

    @Test
    public void team22Test1() {
        String original = "the allowance of collaboration between media and tech company help activate the revival of journalism";
        String expected = "the allow of collabor between media and tech compani help activ the reviv of journal";
        PorterStemmer porterStemmer = new PorterStemmer();
        assertEquals(expected, testStem(porterStemmer, original));
    }

}
