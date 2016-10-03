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
package org.niord.core.message;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.niord.core.NiordApp;
import org.niord.core.area.Area;
import org.niord.core.area.AreaDesc;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryDesc;
import org.niord.core.service.BaseService;
import org.niord.core.settings.annotation.Setting;
import org.niord.core.util.LuceneUtils;
import org.niord.core.util.TextUtils;
import org.niord.model.message.Status;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import static org.niord.core.settings.Setting.Type.Boolean;

/**
 * A Lucene index used for free-text searching all messages.
 * <p>
 * The index will initially index all messages, and subsequently check every minute
 * for changed message to add or update in the index.
 * <p>
 * Note to self: Using "Hibernate Search" for message (as for AtoNs), was ruled out because it would
 * be too complex to index all related entities by language.
 * <p>
 * Something seems to have change in Lucene. In earlier versions (e.g. 4.6), you could
 * use the StandardAnalyzer and quoted phrase searches including stop words (e.g. "the").<br>
 * However, in the current version of Lucene, I can only get this scenario to work with
 * SimpleAnalyzer. :-(
 */
@Singleton
@Lock(LockType.READ)
@Startup
@SuppressWarnings("unused")
public class MessageLuceneIndex extends BaseService {

    final static String LUCENE_ID_FIELD             = "id";
    final static String LUCENE_SEARCH_FIELD         = "message";
    final static String LUCENE_LAST_UPDATE          = "lastUpdate";
    final static int LUCENE_MAX_INDEX_COUNT         = 5000;
    final static int LUCENE_OPTIMIZE_INDEX_COUNT    = 5000;
    final static int LUCENE_MAX_NUM_SEGMENTS        = 4;

    @Inject
    @Setting(value="messageIndexPath", defaultValue="${niord.home}/message-index",
            description="The message Lucene index directory")
    Path indexFolder;

    @Inject
    @Setting(value = "messageIndexDeleteOnStartup", defaultValue = "true", type = Boolean,
            description = "Whether the message lucene index is re-created for each restart or not")
    Boolean deleteOnStartup;

    @Inject
    @Setting(value = "messageIndexIncludeDeletedMessages", defaultValue = "false", type = Boolean,
            description = "Whether the message lucene index should include deleted messages or not")
    Boolean includeDeletedMessages;

    @Inject
    Logger log;

    @Resource
    TimerService timerService;

    @Inject
    MessageService messageService;

    @Inject
    NiordApp app;


    DirectoryReader reader;
    int optimizeIndexCount = 0;
    boolean allMessagesIndexed;
    private final ReentrantLock lock = new ReentrantLock();


    /**
     * Initialize the index
     */
    @PostConstruct
    private void init() {
        // Create the lucene index directory
        if (!Files.exists(indexFolder)) {
            try {
                Files.createDirectories(indexFolder);
            } catch (IOException e) {
                log.error("Error creating index dir " + indexFolder, e);
            }
        }

        // Check if we need to delete the old index on start-up
        if (deleteOnStartup) {
            try {
                deleteIndex();
            } catch (IOException e) {
                log.error("Failed re-creating the index on startup", e);
            }
        }

        // Wait 5 seconds before initializing the message index
        timerService.createSingleActionTimer(5000, new TimerConfig());
    }

    /**
     * Clean up Lucene index
     */
    @PreDestroy
    private void closeIndex() {
        closeReader();
    }


    /**
     * Called when the service starts up and then every minute to update the Lucene index
     * <p>
     * Note to self: It's tempting to use @Lock(WRITE) here. However, that would lock search access
     * to the index while it is being updated, and we really do not want that.
     */
    @Timeout
    @Schedule(persistent=false, second="38", minute="*/1", hour="*")
    private int updateLuceneIndex() {
        lock.lock();
        try {
            return updateLuceneIndex(LUCENE_MAX_INDEX_COUNT);
        } finally {
            lock.unlock();
        }
    }


    /**
     * Returns if all messages have been indexed
     * @return if all messages have been indexed
     */
    public boolean allMessagesIndexed() {
        return allMessagesIndexed;
    }


    /**
     * Returns the language specific language field
     * @param language the language
     * @return the language specific language field
     */
    private String searchField(String language) {
        return LUCENE_SEARCH_FIELD + "_" + app.getLanguage(language);
    }


    /**
     * Returns the list of messages updated since the given date
     * @param fromDate the date after which to look for changed messages
     * @param maxCount the max number of messages to return
     * @return the updated messages
     */
    private List<Message> findUpdatedMessages(Date fromDate, int maxCount) {

        List<Message> messages = messageService.findUpdatedMessages(fromDate, maxCount);

        // The first time less that the maximum number of messages are found,
        // we flag that the indexing is complete
        if (messages.size() < maxCount) {
            allMessagesIndexed = true;
        }

        return messages;
    }


    /**
     * Adds the given message to the given document
     *
     * @param doc the document to add the message to
     * @param message the message to add
     */
    private void addMessageToDocument(Document doc, Message message) {
        // For each supported language, update a search field
        for (String language : app.getLanguages()) {
            String searchField = searchField(language);

            addPhraseSearchField(doc, searchField, message.getStatus());

            // Message series identifier
            addPhraseSearchField(doc, searchField, message.getShortId()); // e.g. "DK-074-14"
            addPhraseSearchField(doc, searchField, message.getMrn());  // e.g. "MSI-DK-074-14"
            if (message.getNumber() != null) {
                addPhraseSearchField(doc, searchField, String.valueOf(message.getNumber()));
            }

            // References
            message.getReferences().forEach(ref -> {
                addPhraseSearchField(doc, searchField, ref.getMessageId());
                ReferenceDesc desc = ref.getDesc(language);
                if (desc != null) {
                    addPhraseSearchField(doc, searchField, desc.getDescription());
                }
            });

            // Areas
            message.getAreas().forEach(a -> {
                for (Area area = a; area != null; area = area.getParent()) {
                    AreaDesc desc = area.getDesc(language);
                    if (desc != null) {
                        addPhraseSearchField(doc, searchField, desc.getName());
                    }
                }
            });

            // Category
            message.getCategories().forEach(category -> {
                for (Category cat = category; cat != null; cat = cat.getParent()) {
                    CategoryDesc desc = cat.getDesc(language);
                    if (desc != null) {
                        addPhraseSearchField(doc, searchField, desc.getName());
                    }
                }
            });

            // Charts
            message.getCharts().forEach(chart -> {
                addPhraseSearchField(doc, searchField, chart.getChartNumber());
                addPhraseSearchField(doc, searchField, chart.getInternationalNumber());
            });

            // Horizontal datum
            addPhraseSearchField(doc, searchField, message.getHorizontalDatum());

            // Add language specific fields
            MessageDesc msgDesc = message.getDesc(language);
            if (msgDesc != null) {
                addPhraseSearchField(doc, searchField, msgDesc.getTitle());
                addPhraseSearchField(doc, searchField, msgDesc.getOtherCategories());
                addPhraseSearchField(doc, searchField, msgDesc.getVicinity());
                addPhraseSearchField(doc, searchField, msgDesc.getPublication());
                addPhraseSearchField(doc, searchField, msgDesc.getSource());
            }

            // Add message parts
            message.getParts().stream()
                    .flatMap(part -> part.getDescs().stream())
                    .filter(desc -> language.equals(desc.getLang()))
                    .forEach(desc -> {
                        addPhraseSearchField(doc, searchField, desc.getSubject());
                        addPhraseSearchField(doc, searchField, TextUtils.html2txt(desc.getDetails()));
                    });

            message.getAtonUids()
                    .forEach(a -> addPhraseSearchField(doc, searchField, a));

            // Attachments
            message.getAttachments().forEach(att -> {
                AttachmentDesc desc = att.getDesc(language);
                if (desc != null) {
                    addPhraseSearchField(doc, searchField, desc.getCaption());
                }
            });


            // TODO
            // Add geometry
            //if (message.getGeometry() != null) {
            //}
        }
    }


    /**
     * Creates and returns a Lucene writer
     */
    private IndexWriter getNewWriter() throws IOException {

        // NB: See class javadoc for a discussion of analyzers
        Analyzer analyzer = new SimpleAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        // Add new documents to an existing index:
        iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);

        try {
            Directory dir = FSDirectory.open(indexFolder);
            return new IndexWriter(dir, iwc);
        } catch (IOException ex) {
            log.error("Failed to create message Lucene Index in folder " + indexFolder, ex);
            throw ex;
        }
    }


    /**
     * Returns the cached index reader, or creates one if none is defined
     * @return the shared index reader
     */
    private DirectoryReader getIndexReader() throws IOException {
        if (reader == null) {
           try {
                reader = DirectoryReader.open(FSDirectory.open(indexFolder));
            } catch (IOException ex) {
                log.error("Failed to open Lucene Index in folder " + indexFolder);
                throw ex;
            }
        }
        return reader;
    }


    /**
     * Closes the given writer
     * @param writer the writer to close
     */
    private void closeWriter(IndexWriter writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                log.warn("Error closing writer");
            }
        }
    }


    /**
     * Closes the current reader
     */
    private void closeReader() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                log.warn("Error closing reader");
            }
            reader = null;
        }
    }


    /**
     * Refreshes the current reader from the given writer
     *
     * @param writer the index writer
     */
    private void refreshReader(IndexWriter writer) throws IOException {
        closeReader();
        reader = DirectoryReader.open(writer, true);
    }


    /**
     * Call this to re-index the message index completely
     */
    @Asynchronous
    public Future<Integer> recreateIndexAsync() throws IOException {
        int updateCount = recreateIndex();
        return new AsyncResult<>(updateCount);
    }


    /**
     * Call this to re-index the message index completely
     */
    public int recreateIndex() throws IOException {
        lock.lock();
        try {
            // delete the old index
            deleteIndex();

            // Update all messages
            return updateLuceneIndex(Integer.MAX_VALUE);

        } finally {
            lock.unlock();
        }
    }


    /**
     * Deletes the current index
     */
    private void deleteIndex() throws IOException {
        // Delete the index
        IndexWriter writer = null;
        try {
            writer = getNewWriter();
            writer.deleteAll();
            writer.setCommitData(new HashMap<>());
            writer.commit();
        } finally {
            closeWriter(writer);
        }
    }


    /**
     * Returns the last updated time
     * @return the last updated time
     */
    private Date getLastUpdated() {
        try {
            DirectoryReader reader = getIndexReader();
            if (reader.getIndexCommit().getUserData().containsKey(LUCENE_LAST_UPDATE)) {
                return new Date(Long.valueOf(reader.getIndexCommit().getUserData().get(LUCENE_LAST_UPDATE)));
            }
        } catch (Exception e) {
            log.debug("Could not get last-updated flag from index reader");
        }
        return new Date(0);
    }


    /**
     * Sets the last updated time
     * @param date the last updated time
     */
    private void setLastUpdated(Date date, IndexWriter writer) {
        Map<String,String> userData = new HashMap<>();
        userData.put(LUCENE_LAST_UPDATE, String.valueOf(date.getTime()));
        writer.setCommitData(userData);
    }


    /**
     * Updates the Lucene index
     *
     * @param maxIndexCount max number of messages to index at a time
     * @return the number of updates
     */
    private int updateLuceneIndex(int maxIndexCount) {

        Date lastUpdated = getLastUpdated();

        long t0 = System.currentTimeMillis();
        log.debug(String.format("Indexing at most %d changed messages since %s", maxIndexCount, lastUpdated));

        IndexWriter writer = null;
        try {
            // Find all messages changed since the lastUpdated time stamp
            List<Message> updatedMessages = findUpdatedMessages(lastUpdated, maxIndexCount);
            if (updatedMessages.size() == 0) {
                return 0;
            }


            // Create a new index writer
            writer = getNewWriter();

            // Update the index with the changes
            for (Message message : updatedMessages) {
                indexMessage(writer, message);
                if (message.getUpdated().after(lastUpdated)) {
                    lastUpdated = message.getUpdated();
                }
            }

            // Update the last-updated flag
            setLastUpdated(lastUpdated, writer);

            // Commit the changes
            writer.commit();

            // Re-open the reader from the writer
            refreshReader(writer);

            // Check if we need to optimize the index
            optimizeIndexCount += updatedMessages.size();
            if (optimizeIndexCount > LUCENE_OPTIMIZE_INDEX_COUNT) {
                writer.forceMerge(LUCENE_MAX_NUM_SEGMENTS);
                optimizeIndexCount = 0;
            }

            log.info("Indexed " + updatedMessages.size() + " messages in "
                    + (System.currentTimeMillis() - t0) + " ms");

            return updatedMessages.size();
        } catch (Exception ex) {
            log.error("Error updating Lucene index: " + ex.getMessage(), ex);
            return 0;
        } finally {
            closeWriter(writer);
        }
    }


    /**
     * Indexes the given message by deleting and adding the document
     *
     * @param message the message to index
     */
    private void indexMessage(IndexWriter writer, Message message) {
        // First delete the message
        deleteMessageFromIndex(writer, message);
        // Then add the message
        if (shouldAddMessage(message)) {
            addMessageToIndex(writer, message);
        }
    }

    /**
     * By default, add all eligible messages.
     * @param message the message to check
     * @return whether to add the message to the index
     */
    private boolean shouldAddMessage(Message message) {
        return includeDeletedMessages || message.getStatus() != Status.DELETED;
    }


    /**
     * Deletes the given message from the index
     *
     * @param message the message to delete
     */
    private void deleteMessageFromIndex(IndexWriter writer, Message message) {
        try {
            Term idTerm = new Term(LUCENE_ID_FIELD, message.getId().toString());
            writer.deleteDocuments(idTerm);
        } catch (IOException e) {
            log.debug("Error deleting message " + message.getId());
        }
    }


    /**
     * Adds the given message to the index
     *
     * @param message the message to add
     */
    private void addMessageToIndex(IndexWriter writer, Message message) {
        Document doc = new Document();

        // ID field
        doc.add(new StringField(LUCENE_ID_FIELD, message.getId().toString(), Field.Store.YES));

        // Add the message specific fields
        addMessageToDocument(doc, message);

        // Add the document to the index
        try {
            writer.addDocument(doc);
        } catch (IOException ex) {
            log.error("Error adding message " + message.getId() + " to the Lucene index: " + ex.getMessage(), ex);
        }
    }

    /**
     * If the given value is not null, it is added to the search index
     *
     * @param doc the document to add the field value to
     * @param obj the value to add
     */
    private void addPhraseSearchField(Document doc, String field, Object obj) {
        if (obj != null) {
            String str = (obj instanceof String) ? (String)obj : obj.toString();
            if (StringUtils.isNotBlank(str)) {
                doc.add(new PhraseSearchLuceneField(field, str));
            }
        }
    }

    /**
     * If the given value is not null, it is added to the search index
     *
     * @param doc the document to add the field value to
     * @param obj the value to add
     * @param store the store value of the field
     */
    private void addStringSearchField(Document doc, String field, Object obj, Field.Store store) {
        if (obj != null) {
            String str = (obj instanceof String) ? (String)obj : obj.toString();
            if (StringUtils.isNotBlank(str)) {
                doc.add(new StringField(field, str, store));
            }
        }
    }

    /**
     * Performs a search in the index and returns the ids of matching messages
     *
     * @param freeTextSearch the search string
     * @param language the language to search
     * @param maxHits the max number of hits to return
     * @return the matching ids
     */
    public List<Long> searchIndex(String freeTextSearch, String language, int maxHits) throws IOException, ParseException {

        Query query;
        if (StringUtils.isNotBlank(freeTextSearch)) {
            // Normalize query text
            freeTextSearch = LuceneUtils.normalizeQuery(freeTextSearch);
            String field = searchField(language);

            // NB: See class javadoc for a discussion of analyzers
            Analyzer analyzer = new SimpleAnalyzer();

            // Create a query parser with "or" operator as the default
            QueryParser parser = new ComplexPhraseQueryParser(
                    field,
                    analyzer);
            parser.setDefaultOperator(QueryParser.OR_OPERATOR);
            parser.setAllowLeadingWildcard(true); // NB: Expensive!
            query = parser.parse(freeTextSearch);

        } else {
            query = new MatchAllDocsQuery();
        }

        // Perform the search and collect the ids
        IndexSearcher searcher = new IndexSearcher(getIndexReader());
        TopDocs results = searcher.search(query, maxHits);

        List<Long> ids = new ArrayList<>();
        for (ScoreDoc hit : results.scoreDocs) {
            Document d = searcher.doc(hit.doc);
            ids.add(Long.valueOf(d.get(LUCENE_ID_FIELD)));
        }
        return ids;
    }


    /**
     * A Lucene field that stores positional information
     * in order to support phrase searches (quoted search terms).
     *
     * Also, the text value is normalized, i.e. accented chars are
     * replaced with non-accented versions.
     */
    private static class PhraseSearchLuceneField extends Field {

        /* Indexed, tokenized, not stored. */
        public static final FieldType TYPE_NOT_STORED = new FieldType();

        /* Indexed, tokenized, stored. */
        public static final FieldType TYPE_STORED = new FieldType();

        static {
            TYPE_NOT_STORED.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
            TYPE_NOT_STORED.setTokenized(true);
            TYPE_NOT_STORED.setStoreTermVectors(true);
            TYPE_NOT_STORED.setStoreTermVectorPositions(true);
            TYPE_NOT_STORED.freeze();

            TYPE_STORED.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
            TYPE_STORED.setTokenized(true);
            TYPE_STORED.setStored(true);
            TYPE_STORED.setStoreTermVectors(true);
            TYPE_STORED.setStoreTermVectorPositions(true);
            TYPE_STORED.freeze();
        }

        /** Creates a new TextField with String value. */
        public PhraseSearchLuceneField(String field, String value) {
            super(field, LuceneUtils.normalize(value), TYPE_NOT_STORED);
        }
    }
}

