package DP_entity_linking;

/**
 * Created by miroslav.kudlac on 10/3/2015.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TrainJsonRead {
    private static Logger LOGGER = Logger.getLogger(TrainJsonRead.class);
    private static final File JSON_FILE = new File("/Users/fjuras/OneDriveBusiness/DPResources/webquestionsRelationDataset.json");

    private ObjectMapper mapper;
    private Directory directory = new MMapDirectory(new File("/Users/fjuras/OneDriveBusiness/DP/erd/index_wikipedia"));
    private Analyzer analyzer;
    private int numSearchRes = 20;
    private ArrayList<IndexSearcher> searchers = new ArrayList<IndexSearcher>();
    private IEvaluation iEvaluation;
    private IndexSearcher indexSearcher;
    private IndexReader indexreader;

    public TrainJsonRead() throws IOException {
        analyzer = new CountryAnalyzer();
        mapper = new ObjectMapper();
        indexreader = IndexReader.open(directory);
        indexSearcher = new IndexSearcher(indexreader);
        int num = indexreader.numDocs();
        iEvaluation = new SimpleEvaluation(indexSearcher);
    }
    
    public IndexReader getIndexReader(){
    	return this.indexreader;
    }

    public static class Records extends ArrayList<Record> {
        public Records() {
        }
    }

    /**
     * @return
     * @throws IOException
     */
    public Records loadWebquestions() throws IOException {
        return mapper.readValue(JSON_FILE, Records.class);
    }

    /**
     * @param r
     * @throws IOException
     */
    public void retrieveRecords(Record r) throws IOException, ParseException {
        TokenStream stream = analyzer.tokenStream(null, new StringReader(r.getUtterance()));
        CharTermAttribute cattr = stream.addAttribute(CharTermAttribute.class);

        stream.reset();
        String[] newList = new String[30];
        StringBuilder strBuilder = new StringBuilder();
        while (stream.incrementToken()) {
            strBuilder.append(cattr.toString() + " ");
        }
        stream.end();
        stream.close();
        String newQuery = strBuilder.toString().trim();
        this.retrieve(r, newQuery, numSearchRes);
    }

    public boolean backMapped(String query, String form) {

        boolean isMapped;
        isMapped = query.toLowerCase().contains(form.toLowerCase());
        return isMapped;
    }

    private final Query buildLuceneQuery(final String queryIn, final Analyzer analyzerIn) throws ParseException {
        Query queryL = null;
        String query = queryIn;
        String[] fields = null;
        Map<String, Float> boosts = new HashMap<String, Float>();
        fields = new String[]{"title", "fb_alias", "alt", "fb_name", "abs"};
        //fields = new String[] { "title", "anchor", "alt", "fb_name", "abs" };
        boosts.put("title", (float) 0.4);
        MultiFieldQueryParser parser = new MultiFieldQueryParser(Version.LUCENE_43, fields, analyzerIn, boosts);

        queryL = parser.parse(query);
        BooleanQuery categoryQuery = new BooleanQuery();
        BooleanQuery mainQuery = new BooleanQuery();
        TermQuery catQuery1 = new TermQuery(new Term("db_category", "person"));
        TermQuery catQuery2 = new TermQuery(new Term("db_category", "place"));
        TermQuery catQuery3 = new TermQuery(new Term("db_category", "country"));
        TermQuery catQuery4 = new TermQuery(new Term("db_category", "movie"));
        TermQuery catQuery5 = new TermQuery(new Term("db_category", "language"));
        categoryQuery.add(new BooleanClause(catQuery1, BooleanClause.Occur.SHOULD));
        categoryQuery.add(new BooleanClause(catQuery2, BooleanClause.Occur.SHOULD));
        categoryQuery.add(new BooleanClause(catQuery3, BooleanClause.Occur.SHOULD));
        categoryQuery.add(new BooleanClause(catQuery4, BooleanClause.Occur.SHOULD));
        categoryQuery.add(new BooleanClause(catQuery5, BooleanClause.Occur.SHOULD));
        mainQuery.add(new BooleanClause(queryL, BooleanClause.Occur.SHOULD));
        mainQuery.add(new BooleanClause(categoryQuery, BooleanClause.Occur.MUST));
        return queryL;
    }

    private Statistics<Float> statisticScore = new Statistics();
    private Statistics<String> statisticDbCategory = new Statistics();
    private Statistics<String> statisticFbCategory = new Statistics();

    private void retrieve(Record record, String query, int numSearchRes) throws IOException, ParseException {
        boolean backMapped;
        Similarity[] sims = {
                new BM25Similarity((float) 1.79, (float) 0.35),
                //new LMJelinekMercerSimilarity((float) 0.0003),
                new LMJelinekMercerSimilarity((float) 0.00001),
                new LMDirichletSimilarity(),
                //new DefaultSimilarity(),
        };
        indexSearcher.setSimilarity(new MultiSimilarity(sims));
        Query queryL = this.buildLuceneQuery(query, analyzer);
        TopDocs results = indexSearcher.search(queryL, numSearchRes);
        ScoreDoc[] hits = results.scoreDocs;

        String answer = record.getAnswer();
        float score = iEvaluation.getScore(hits, answer);
        statisticScore.count(score);

        LOGGER.info(record.getQuestion() + ", " + answer + " => " + score);
        for (int i = 0; i < hits.length; i++) {
            Document doc = indexSearcher.doc(hits[i].doc);
            IndexableField[] dbCats = doc.getFields("db_category");
            IndexableField[] fbCats = doc.getFields("fb_category");
            for(IndexableField dbcat : dbCats) {
                statisticDbCategory.count(dbcat.stringValue());
            }
            for(IndexableField fbcat : fbCats) {
                statisticFbCategory.count(fbcat.stringValue());
            }

        }
    // USE FOR BACKMAPPING
        /*for (int i = 0; i < hits.length; i++) {
            Document doc = indexSearcher.doc(hits[i].doc);
            backMapped = this.backMapped(record.getQuestion(), doc.get("title"));
            if (backMapped) {
                String question = record.getUtterance();
                String haystack = doc.get("title").toLowerCase();
                String backMappedRecord = question.replaceAll(haystack, "").replace("?", "").trim();
                Query queryBackMapped = this.buildLuceneQuery(backMappedRecord, analyzer);
                TopDocs resultsBackMapped = indexSearcher.search(queryBackMapped, 5);
                ScoreDoc[] hitsBackMapped = resultsBackMapped.scoreDocs;

                answer = record.getAnswer();
                score = iEvaluation.getScore(hitsBackMapped, answer);
                statistics.count(score);
                for (int j = 0; j < hitsBackMapped.length; j++) {
                    Document docBackMapped = indexSearcher.doc(hitsBackMapped[j].doc);
                    LOGGER.info(docBackMapped.get("title") + " --- back mapped: " + backMapped + " ----");
                }
                LOGGER.info(backMappedRecord + ", " + answer + " => " + score);

            } else {
                LOGGER.info(doc.get("title"));
            }
        }*/
    }

    /**
     * @throws IOException
     */
    public void doJob() throws IOException, ParseException {
        Records records = this.loadWebquestions();

        for (Record record : records) {
            LOGGER.info("------------" + record.getUtterance() + "--------------");
            this.retrieveRecords(record);

        }

        for (Map.Entry entry : statisticScore.entrySet()) {
            LOGGER.info(entry.getKey() + "," + entry.getValue());
        }
        for (Map.Entry entry : statisticDbCategory.entrySet()) {
            LOGGER.info(entry.getKey() + "," + entry.getValue());
        }
        for (Map.Entry entry : statisticFbCategory.entrySet()) {
            LOGGER.info(entry.getKey() + "," + entry.getValue());
        }
    }

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException, ParseException {

        PropertyConfigurator.configure("log4j.properties");
        // Spellcheck spell = new Spellcheck(new WordFrequenciesT("EntityStore"), "C:\\workspace\\erd\\EntityStore\\spellindex\\1\\spellindex");

        try {
            TrainJsonRead app = new TrainJsonRead();
            app.doJob();

        } catch (IOException ex) {
            LOGGER.error(" error: ", ex);
        }

    }

}

