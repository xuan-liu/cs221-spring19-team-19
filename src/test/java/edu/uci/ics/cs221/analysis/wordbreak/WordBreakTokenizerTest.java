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

    @Test(timeout=20000)
    public void longTest1() {
        String text = "tosherlockholmessheisalwaysthewomanihaveseldomheardhimmentionherunderanyothernameinhiseyessheeclipsesandpredominatesthewholeofhersexitwasnotthathefeltanyemotionakintoloveforireneadlerallemotionsandthatoneparticularlywereabhorrenttohiscoldprecisebutadmirablybalancedmindhewasitakeitthemostperfectreasoningandobservingmachinethattheworldhasseenbutasaloverhewouldhaveplacedhimselfinafalsepositionheneverspokeofthesofterpassionssavewithagibeandasneertheywereadmirablethingsfortheobserverexcellentfordrawingtheveilfrommenmotivesandactionsbutforthetrainedreasonertoadmitsuchintrusionsintohisowndelicateandfinelyadjustedtemperamentwastointroduceadistractingfactorwhichmightthrowadoubtuponallhismentalresultsgritinasensitiveinstrumentoracrackinoneofhisownhighpowerlenseswouldnotbemoredisturbingthanastrongemotioninanaturesuchashisandyettherewasbutonewomantohimandthatwomanwasthelateireneadlerofdubiousandquestionablememory";
        String expectedStr = "sherlock holmes always woman seldom heard mention name eyes eclipses predominates whole sex felt emotion akin love irene adler emotions one particularly abhorrent cold precise admirably balanced mind take perfect reasoning observing machine world seen lover would placed false position never spoke softer passions save gibe sneer admirable things observer excellent drawing veil men motives actions trained reasoner admit intrusions delicate finely adjusted temperament introduce distracting factor might throw doubt upon mental results grit sensitive instrument crack one high power lenses would disturbing strong emotion nature yet one woman woman late irene adler dubious questionable memory";
        List<String> expected = Arrays.asList(expectedStr.split(" "));
        WordBreakTokenizer tokenizer = new WordBreakTokenizer();
        assertEquals(expected, tokenizer.tokenize(text));
    }

    @Test(timeout=20000)
    public void longTest2() {
        String text = "ihadseenlittleofholmeslatelymymarriagehaddriftedusawayfromeachothermyowncompletehappinessandthehomecentredinterestswhichriseuparoundthemanwhofirstfindshimselfmasterofhisownestablishmentweresufficienttoabsorballmyattentionwhileholmeswholoathedeveryformofsocietywithhiswholesoulremainedinourlodgingsinbakerstreetburiedamonghisoldbooksandalternatingfromweektoweekbetweencocaineandambitionthedrowsinessofthedrugandthefierceenergyofhisownkeennaturehewasstillaseverdeeplyattractedbythestudyofcrimeandoccupiedhisimmensefacultiesandextraordinarypowersofobservationinfollowingoutthosecluesandclearingupthosemysterieswhichhadbeenabandonedashopelessbytheofficialpolicefromtimetotimeiheardsomevagueaccountofhisdoingsofhissummonstoodessainthecaseofthemurderofhisclearingupofthesingulartragedyoftheatkinsonbrothersattrincomaleeandfinallyofthemissionwhichhehadaccomplishedsodelicatelyandsuccessfullyforthereigningfamilyofhollandbeyondthesesignsofhisactivityhoweverwhichimerelysharedwithallthereadersofthedailypressiknewlittleofmyformerfriendandcompanion";
        String expectedStr = "seen little holmes lately marriage drifted us away complete happiness home centred interests rise around man first finds master establishment sufficient absorb attention holmes loathed every form society whole soul remained lodgings baker street buried among old books alternating week week cocaine ambition drowsiness drug fierce energy keen nature still ever deeply attracted study crime occupied immense faculties extraordinary powers observation following clues clearing mysteries abandoned hopeless official police time time heard vague account doings summons odessa case murder clearing singular tragedy atkinson brothers trincomalee finally mission accomplished delicately successfully reigning family holland beyond signs activity however merely shared readers daily press knew little former friend companion";
        List<String> expected = Arrays.asList(expectedStr.split(" "));
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
        List<String> expected = Arrays.asList();
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

    @Test(expected = RuntimeException.class)
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

    @Test(expected = RuntimeException.class)
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

    @Test(expected = RuntimeException.class)
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
