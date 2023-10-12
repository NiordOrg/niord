/*
 * Copyright 2016 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.core;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Lucene test
 */
public class LuceneTest {

    @Test
    public void testLucene() throws IOException, ParseException {

        // Something seems to have change in Lucene. In earlier versions (e.g. 4.6), you could
        // use the StandardAnalyzer and quoted phrase searches including stop words (e.g. "the")
        // would still work. See example below:
        // However, in the current version of Lucene, I can only get this scenario to work with
        // SimpleAnalyzer for the QueryParser :-(

        Directory directory = new RAMDirectory();
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        IndexWriter writer = new IndexWriter(directory, iwc);

        Document doc = new Document();

        FieldType ft=new FieldType();
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        ft.setTokenized(true);
        ft.setStoreTermVectors(true);
        ft.setStoreTermVectorPositions(true);
        ft.freeze();

        doc.add(new Field("message", "The quick brown fox jumps over the lazy dog", ft));
        writer.addDocument(doc);
        writer.close();

        IndexReader reader = DirectoryReader.open(directory);
        assertEquals(1, reader.numDocs());

        analyzer = new StandardAnalyzer();
        QueryParser parser = new ComplexPhraseQueryParser("message", analyzer);
        parser.setDefaultOperator(QueryParser.OR_OPERATOR);
        parser.setAllowLeadingWildcard(true); // NB: Expensive!
        Query query = parser.parse("\"over the lazy\" +quick -goat bro*");

        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs results = searcher.search(query, 10000);

        assertEquals(1, results.scoreDocs.length);

        reader.close();
    }
}
