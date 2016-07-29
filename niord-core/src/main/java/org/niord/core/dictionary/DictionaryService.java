/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.dictionary;

import org.niord.core.NiordApp;
import org.niord.core.dictionary.vo.DictionaryVo;
import org.niord.core.service.BaseService;
import org.niord.model.DataFilter;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Business interface for accessing dictionaries
 */
@Singleton
@Lock(LockType.READ)
@Startup
@SuppressWarnings("unused")
public class DictionaryService extends BaseService {

    public static final String[] DEFAULT_BUNDLES = { "web", "message", "pdf", "mail" };

    @Inject
    private Logger log;

    @Inject
    private NiordApp app;

    private Map<String, DictionaryVo> cachedDictionaries = new ConcurrentHashMap<>();

    /**
     * Called when the system starts up.
     */
    @PostConstruct
    private void init() {

        // Cache all dictionaries
        long t0 = System.currentTimeMillis();
        List<Dictionary> dictionaries = getAll(Dictionary.class);
        dictionaries.forEach(d -> getCachedDictionary(d.getName()));
        log.info(String.format("Cached %d dictionaries in %d ms", dictionaries.size(), System.currentTimeMillis() - t0));

        // Load default resource bundles into dictionaries
        Arrays.stream(DEFAULT_BUNDLES).forEach(this::loadResourceBundle);
    }


    /**
     * Returns the dictionary names in sorted order
     * @return the dictionary names
     */
    public List<String> getDictionaryNames() {
        return getAll(Dictionary.class).stream()
                .map(Dictionary::getName)
                .sorted()
                .collect(Collectors.toList());
    }


    /**
     * Returns the dictionary with the given name
     * @param name the name
     * @return the dictionary with the given name
     */
    public Dictionary findByName(String name) {
        try {
            return em
                    .createNamedQuery("Dictionary.findByName", Dictionary.class)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Adds the given dictionary entry template
     * @param name the name of the dictionary
     * @param entry the template dictionary entry to add
     * @return the added dictionary entry
     */
    @Lock(LockType.WRITE)
    public DictionaryEntry createEntry(String name, DictionaryEntry entry) {
        Dictionary dict = findByName(name);
        if (dict == null) {
            throw new IllegalArgumentException("Non-existing dictionary: " + name);
        }

        DictionaryEntry original = dict.getEntries().get(entry.getKey());
        if (original != null) {
            throw new IllegalArgumentException("Entry already exists: " + entry.getKey());
        }

        entry.getDescs().removeIf(desc -> !desc.descDefined());
        if (entry.getDescs().isEmpty()) {
            throw new IllegalArgumentException("No localized values defined for entry: " + entry.getKey());
        }

        entry.setDictionary(dict);
        dict.getEntries().put(entry.getKey(), entry);
        entry.getDescs().forEach(e -> e.setEntity(entry));

        saveEntity(dict);

        // Remove the cached dictionary
        cachedDictionaries.remove(name);

        return entry;
    }


    /**
     * Updates the given dictionary entry template
     * @param name the name of the dictionary
     * @param entry the template dictionary entry to update
     * @return the updated dictionary entry
     */
    @Lock(LockType.WRITE)
    public DictionaryEntry updateEntry(String name, DictionaryEntry entry) {
        Dictionary dict = findByName(name);
        if (dict == null) {
            throw new IllegalArgumentException("Non-existing dictionary: " + name);
        }

        DictionaryEntry original = dict.getEntries().get(entry.getKey());
        if (original == null) {
            throw new IllegalArgumentException("Non-existing dictionary entry: " + entry.getKey());
        }

        original.copyDescsAndRemoveBlanks(entry.getDescs());
        original.getDescs().forEach(e -> e.setEntity(original));

        saveEntity(original);

        // Remove the cached dictionary
        cachedDictionaries.remove(name);

        return original;
    }


    /**
     * Deletes the given dictionary key
     * @param name the name of the dictionary
     * @param key the dictionary key to delete
     * @return if the entry was deleted
     */
    @Lock(LockType.WRITE)
    public boolean deleteEntry(String name, String key) {
        Dictionary dict = findByName(name);
        if (dict == null) {
            throw new IllegalArgumentException("Non-existing dictionary: " + name);
        }

        DictionaryEntry original = dict.getEntries().get(key);
        if (original == null) {
            return false;
        }

        dict.getEntries().remove(key);
        original.setDictionary(null);
        remove(original);
        saveEntity(dict);

        // Remove the cached dictionary
        cachedDictionaries.remove(name);

        return true;
    }


    /**
     * Returns the cached dictionary with the given name.
     * @param name the name
     * @return the cached dictionary with the given name
     */
    public DictionaryVo getCachedDictionary(String name) {
        // Check if the dictionary is cached already
        DictionaryVo dict = cachedDictionaries.get(name);
        if (dict == null) {
            Dictionary dictionary = findByName(name);
            if (dictionary != null) {
                // TODO: Load more efficiently by using "DictionaryEntry.loadWithDescs" query
                dict = dictionary.toVo(DataFilter.get());
                cachedDictionaries.put(dict.getName(), dict);
            }
        }

        return dict;
    }


    /**
     * Returns the given dictionaries as a Properties object for the given language.
     * Returns null if undefined.
     * @param names the dictionary names
     * @param language the language
     * @return the dictionaries for the given language as a Properties object
     */
    public Properties getDictionariesAsProperties(String[] names, String language) {
        Properties langDict = new Properties();
        for (String name : names) {
            langDict.putAll(getCachedDictionary(name).toProperties(language));
        }
        return langDict;
    }


    /**
     * Returns the given dictionaries as a ResourceBundle for the given language.
     * Returns null if undefined.
     * @param names the dictionary names
     * @param language the language
     * @return the dictionaries for the given language as a ResourceBundle
     */
    public ResourceBundle getDictionariesAsResourceBundle(String[] names, String language) {

        // Construct a property file with all language-specific values from all included dictionaries
        Properties langDict = getDictionariesAsProperties(names, language);

        // Convert the Properties object to a resource bundle
        return new ResourceBundle() {

            /** {@inheritDoc} **/
            @Override
            @SuppressWarnings("all")
            protected Object handleGetObject(String key) {
                return langDict.getProperty(key);
            }

            /** {@inheritDoc} **/
            @Override
            @SuppressWarnings("all")
            public Enumeration<String> getKeys() {
                Set<String> handleKeys = langDict.stringPropertyNames();
                return Collections.enumeration(handleKeys);
            }
        };
    }


    /**
     * Loads the resource bundles with the given name for all supported languages.
     * Updates the associated dictionary with new entries.
     * @param baseName the base name of the resource bundle
     */
    @Lock(LockType.WRITE)
    public void loadResourceBundle(String baseName) {

        log.info("Loading dictionary resource bundle " + baseName);

        for (String lang : app.getLanguages()) {
            String resource = baseName.replace('.', '/') + "_" + lang + ".properties";
            if (!resource.startsWith("/")) {
                resource = "/" + resource;
            }
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                Properties props = new Properties();
                props.load(new InputStreamReader(in, "UTF-8"));

                // Merge the resource bundle with the existing dictionary
                mergeDictWithResourceBundle(baseName, lang, props);
            } catch (IOException e) {
                log.error("Error loading resource bundle " + resource + ": " + e);
            }
        }
    }

    /**
     * Merge a resource bundle with the existing dictionary
     * @param name the name of the dictionary
     * @param lang the language of the resource bundle
     * @param properties the resource bundle to merge into the dictionary
     */
    private void mergeDictWithResourceBundle(String name, String lang, Properties properties) {
        // Sanity check
        if (properties.size() == 0) {
            return;
        }

        // Get hold of the list of resource bundle keys not present in the dictionary
        DictionaryVo dict = getCachedDictionary(name);
        List<String> undefKeys = properties.stringPropertyNames().stream()
                .filter(k -> dict == null ||
                        !dict.getEntries().containsKey(k) ||
                        dict.getEntries().get(k).getDesc(lang) == null)
                .collect(Collectors.toList());

        if (!undefKeys.isEmpty()) {
            long t0 = System.currentTimeMillis();

            // Update the underlying dictionary
            Dictionary dictionary = findByName(name);

            // If the dictionary did not exist, create it
            if (dictionary == null) {
                dictionary = new Dictionary();
                dictionary.setName(name);
                em.persist(dictionary);
            }

            for (String key : undefKeys) {
                updateEntry(dictionary, lang, key, properties.getProperty(key));
            }

            // Remove the cached dictionary
            cachedDictionaries.remove(name);

            log.info(String.format("Persisted %d new %s dictionary entries in %d ms",
                    undefKeys.size(), name, System.currentTimeMillis() - t0));
        }
    }

    /**
     * Creates or updates the given dictionary entry
     * @param dict the dictionary
     * @param lang the language of the resource bundle
     * @param key the key
     * @param value the value
     */
    private void updateEntry(Dictionary dict, String lang, String key, String value) {
        DictionaryEntry entry = dict.getEntries().get(key);
        if (entry == null) {
            entry = dict.createEntry(key);
        }
        DictionaryEntryDesc desc = entry.checkCreateDesc(lang);
        desc.setValue(value);
    }

}
