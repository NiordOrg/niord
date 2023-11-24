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
package org.niord.core.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Lock;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import org.apache.commons.lang.StringUtils;
import org.niord.core.cache.CacheElement;
import org.niord.core.service.BaseService;
import org.niord.core.util.JsonUtils;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.keycloak.util.JsonSerialization.mapper;

/**
 * Interface for accessing settings.
 * <p/>
 * This bean can either be injected directly,
 * or the {@code @Setting} annotation can be used.
 */
@ApplicationScoped
@SuppressWarnings("unused")
public class SettingsService extends BaseService {

    private final  static  String SETTINGS_FILE = "/niord.json";

    @Inject
    private Logger log;

    @Inject
    SettingsCache settingsCache;

    /**
     * Called when the system starts up.
     *
     * First, loads all settings from the classpath "/niord.json" file.<br>
     *
     * Then, loads all settings from the "${niord.home}/niord.json" file
     * and add these to the list of loaded settings.<br>
     *
     * Lastly, persists all the loaded settings that do not already exists in the database.
     */
    @Transactional
    @Lock(Lock.Type.WRITE)
    @Startup(ObserverMethod.DEFAULT_PRIORITY - 100)
    void init() {
        Log.info("Starting Initialization Settings");
        try {
            // Read the settings from the "/niord.json" classpath file
            Map<String, Setting> settingMap = loadSettingsFromClasspath();

            // Read the settings from the "${niord.home}/niord.json" file
            settingMap = loadSettingsFromNiordHome(settingMap);

            // Determine the keys that are not yet persisted to the database
            em.createNamedQuery("Setting.findSettingsWithKeys", Setting.class)
                    .setParameter("keys", settingMap.keySet())
                    .getResultList()
                    .stream()
                    .map(Setting::getKey)
                    .forEach(settingMap::remove);

            // Persist all settings not yet persisted to the database
            settingMap.values().forEach(s -> {
                s.updateType();
                em.persist(s);
                log.info(String.format("Loaded setting %s = %s from niord.json", s.getKey(), s.getValue().toString()));
            });

        } catch (Exception e) {
            // Stop the application starting up
            throw new RuntimeException("Error loading settings from niord.json", e);
        }
        Log.info("Finished Initializing Settings");
    }


    /** Called upon startup. Read the settings from the "/niord.json" classpath file */
    @Transactional
    Map<String, Setting> loadSettingsFromClasspath() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        URL resource = getClass().getResource(SETTINGS_FILE);
        Log.info("Loading config from file" + resource);
        List<Setting> settings = mapper.readValue(
                getClass().getResource(SETTINGS_FILE),
                new TypeReference<List<Setting>>(){});
        for (Setting s : settings) {
            Log.info("Loaded setting " + s.getKey() + " = " + s.getValue());
        }
        return settings.stream()
                .collect(Collectors.toMap(Setting::getKey, Function.identity()));
    }


    /** Called upon startup. Read the settings from the "${niord.home}/niord.json" file and update the settingMap */
    @Transactional
    @Lock(Lock.Type.READ)
    Map<String, Setting> loadSettingsFromNiordHome(Map<String, Setting> settingMap) throws IOException {
        log.info("loadSettingsFromNiordHome start");

        Object niordHome = peek("niord.home");
        if (niordHome == null && settingMap.containsKey("niord.home")) {
            log.info(String.format("niordHome value is null. Defaulting to value from settingMap(DB) = %s.", settingMap.get("niord.home").getValue().toString()));
            niordHome = settingMap.get("niord.home").getValue();
        }
        if (niordHome != null && niordHome instanceof String) {
            niordHome = expandSettingValue((String)niordHome);
            Path niordFile = Paths.get(niordHome.toString(), "niord.json");
            log.info(String.format("loadSettingsFromNiordHome generate filepath from environment variable. Expecting settings file in location %s", niordFile.toAbsolutePath()));

            if (Files.exists(niordFile) && Files.isRegularFile(niordFile)) {
                log.info("loadSettingsFromNiordHome. File exists. Loading settings");

                List<Setting> settings = mapper.readValue(
                        niordFile.toFile(),
                        new TypeReference<List<Setting>>(){});
                // Update (and overwrite) the setting map with these settings
                settings.forEach(s -> settingMap.put(s.getKey(), s));
            }
        }
        return settingMap;
    }


    /**
     * Returns all settings that should be emitted to the web application
     * @return all settings that should be emitted to the web application
     */
    @Transactional
    @Lock(Lock.Type.READ)
    public List<Setting> getAllForWeb() {
        return em.createNamedQuery("Setting.findAllForWeb", Setting.class)
                .getResultList();
    }

    /**
     * Returns all settings that are editable on the Settings admin page
     * @return all settings that are editable on the Settings admin page
     */
    @Transactional
    @Lock(Lock.Type.READ)
    public List<Setting> getAllEditable() {
        return em.createNamedQuery("Setting.findAllEditable", Setting.class)
                .getResultList();
    }

    /**
     * Returns the value associated with the setting.
     * If it does not exist, it is created
     *
     * @param key the setting key
     * @return the associated value
     */
    public Object get(String key) {
        return get(new Setting(key));
    }

    /**
     * Returns the value associated with the setting.
     * If it does not exist, it is created
     *
     * @param setting the source
     * @return the associated value
     */
    @Transactional
    @Lock(Lock.Type.READ)
    public Object get(Setting setting) {
        Objects.requireNonNull(setting, "Must specify valid setting");

        // If a corresponding system or environment property is set, it takes precedence
        if (System.getProperty(setting.getKey()) != null) {
            return System.getProperty(setting.getKey());
        }
        if (System.getenv(setting.getKey()) != null) {
            return System.getenv(setting.getKey());
        }

        // Look for a cached value
        CacheElement<Object> value = settingsCache.getCache().get(setting.getKey());

        // No cached value
        try {
            if (value == null) {
                Setting result = em.find(Setting.class, setting.getKey());
                if (result == null) {
                    result = new Setting(setting);
                    em.persist(result);
                }
                value = new CacheElement<>(result.getValue());


                // Cache it.
                if (setting.isCached()) {
                    settingsCache.getCache().put(setting.getKey(), value);
                }
            }
        } catch (IllegalStateException ex) {
            return "/";
        }

        // Check if we need to substitute with system properties. Only applies to String-based settings.
        Object result = value.getElement();
        if (result != null && result instanceof String) {
            result = expandSettingValue((String)result);
        }

        return result;
    }


    /**
     * Returns the value associated with the setting.
     * Always use the value from the database, and never creates the setting like "get()".
     *
     * @param key the setting key
     * @return the associated value
     */
    @Transactional
    @Lock(Lock.Type.READ)
    public Object peek(String key) {
        Objects.requireNonNull(key, "Must specify valid setting key");

        // If a corresponding system property is set, it takes precedence
        if (System.getProperty(key) != null) {
            log.info(String.format("Peek system value. Property: %s has system value %s. Return", key, System.getProperty(key).toString()));
            return System.getProperty(key);
        }
        
        // If a corresponding environment property is set, it takes precedence
        if (System.getenv(key) != null) {
            log.info(String.format("Peek Environment value. Property: %s has system value %s. Return", key, System.getenv(key).toString()));
            return System.getenv(key);
        }

        Setting setting = em.find(Setting.class, key);
        if (setting == null) {
            return null;
        }

        // Check if we need to substitute with system properties. Only applies to String-based settings.
        Object result = setting.getValue();
        if (result != null && result instanceof String) {
            result = expandSettingValue((String)result);
        }

        log.info(String.format("Peek finished. Key = %s return value %s", key, result.toString()));
        return result;
    }


    /**
     * Replace any nested token with the format "${token}" with either the setting with the given name
     * or with a System property with the given name.
     *
     * @param value the value to expand
     * @return the expanded value
     */
    private String expandSettingValue(String value) {
        SettingValueExpander valueExpander = new SettingValueExpander(value);
        String token;
        int liveLockControlCount = 500;
        while ((token = valueExpander.nextToken()) != null) {
            if (liveLockControlCount--<0) {
                throw new RuntimeException("Livelock encountered for key " + value);
            }
            Object setting = peek(token);
            if (setting != null) {
                valueExpander.replaceToken(token, setting.toString());
                continue;
            }
            String sysProp = System.getProperty(token);
            if (StringUtils.isNotBlank(sysProp)) {
                valueExpander.replaceToken(token, sysProp);
                continue;
            }

            sysProp = System.getenv(token);
            if (StringUtils.isNotBlank(sysProp)) {
                valueExpander.replaceToken(token, sysProp);
                continue;
            }
            
            
            valueExpander.replaceToken(token, "");
        }
        return valueExpander.getValue();
    }


    /**
     * Updates the database value of the given setting
     * @param template the setting to update
     * @return the updated setting
     */
    @Lock(Lock.Type.WRITE)
    @Transactional
    public Setting set(Setting template) {
        Setting setting = em.find(Setting.class, template.getKey());
        if (setting == null) {
            throw new IllegalArgumentException("Non-existing setting " + template.getKey());
        }

        // Update the DB
        setting.setValue(template.getValue());
        setting = em.merge(setting);

        // Invalidate the cache
        evictFromCache(setting.getKey());

        return setting;
    }


    /** Evicts any setting with the given key from the cache **/
    public void evictFromCache(String key) {
        settingsCache.getCache().remove(key);
    }


    /**
     * Updates the database value of the given setting
     * @param key the key of the setting to update
     * @param value the value of the setting to update
     * @return the updated setting
     */
    public Setting set(String key, Object value) {
        return set(new Setting(key, value));
    }

    /**
     * Returns the setting as a String
     *
     * @param setting the source
     * @return the associated value
     */
    public String getString(Setting setting) {
        Object value = get(setting);
        return value == null
                ? null
                : (value instanceof String) ? (String)value : value.toString();
    }

    public String getString(String key) {
        return getString(new Setting(key));
    }

    /**
     * Returns the setting as a boolean
     *
     * @param setting the source
     * @return the associated value
     */
    public Boolean getBoolean(Setting setting) {
        Object value = get(setting);
        if (value != null) {
            if (value instanceof Boolean) {
                return (Boolean)value;
            }
            switch(value.toString().toLowerCase()) {
                case "true":
                case "yes":
                case "t" :
                case "y":
                    return true;
            }
        }
        return null;
    }

    public Boolean getBoolean(String key) {
        return getBoolean(new Setting(key));
    }


    /**
     * Returns the setting as a long
     *
     * @param setting the source
     * @return the associated value
     */
    public Long getLong(Setting setting) {
        Object value = get(setting);
        if (value != null) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.valueOf(value.toString());
        }
        return null;
    }

    public Long getLong(String key) {
        return getLong(new Setting(key));
    }

    /**
     * Returns the setting as an integer
     *
     * @param setting the source
     * @return the associated value
     */
    public Integer getInteger(Setting setting) {
        Object value = get(setting);
        if (value != null) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.valueOf(value.toString());
        }
        return null;
    }

    public Integer getInteger(String key) {
        return getInteger(new Setting(key));
    }

    /**
     * Returns the setting as a Double
     *
     * @param setting the source
     * @return the associated value
     */
    public Double getDouble(Setting setting) {
        Object value = get(setting);
        if (value != null) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.valueOf(value.toString());
        }
        return null;
    }

    public Double getDouble(String key) {
        return getDouble(new Setting(key));
    }

    /**
     * Returns the setting as a Float
     *
     * @param setting the source
     * @return the associated value
     */
    public Float getFloat(Setting setting) {
        Object value = get(setting);
        if (value != null) {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
            return Float.valueOf(value.toString());
        }
        return null;
    }

    public Float getFloat(String key) {
        return getFloat(new Setting(key));
    }

    /**
     * Returns the setting as a Path
     *
     * @param setting the source
     * @return the associated value
     */
    public Path getPath(Setting setting) {
        String value = getString(setting);
        return value == null ? null : Paths.get(value);
    }

    public Path getPath(String key) {
        return getPath(new Setting(key));
    }

    public void setPath(String key, Path path) {
        set(key, path == null ? null : path.toAbsolutePath().toString());
    }


    /**
     * Returns the setting as a Date
     *
     * @param setting the source
     * @return the associated value
     */
    public Date getDate(Setting setting) {
        Long value = getLong(setting);
        return value == null ? null : new Date(value);
    }

    public Date getDate(String key) {
        return getDate(new Setting(key));
    }

    public void setDate(String key, Date date) {
        set(key, date == null ? null : date.getTime());
    }


    /**
     * Returns the setting from a JSON representation
     *
     * @param setting the source
     * @return the associated value
     */
    public <T> T getFromJson(Setting setting, Class<T> dataClass) {
        try {
            return JsonUtils.fromJson(JsonUtils.toJson(get(setting)), dataClass);
        } catch (IOException e) {
            return null;
        }
    }

    public <T> T getFromJson(String key, Class<T> dataClass) {
        return getFromJson(new Setting(key), dataClass);
    }

    public void setAsJson(String key, Object data) {
        try {
            set(key, JsonUtils.toJson(data));
        } catch (IOException ignored) {
        }
    }


    /**
     * Injects the String setting defined by the {@code @Setting} annotation
     *
     * @param ip the injection point
     * @return the String setting value
     */
    @Produces
    @org.niord.core.settings.annotation.Setting
    public String getString(InjectionPoint ip) {
        return getString(ip2setting(ip));
    }


    /**
     * Injects the Boolean setting defined by the {@code @Setting} annotation
     *
     * @param ip the injection point
     * @return the boolean setting value
     */
    @Produces
    @org.niord.core.settings.annotation.Setting
    public Boolean getBoolean(InjectionPoint ip) {
        return getBoolean(ip2setting(ip));
    }


    /**
     * Injects the Long setting defined by the {@code @Setting} annotation
     *
     * @param ip the injection point
     * @return the Long setting value
     */
    @Produces
    @org.niord.core.settings.annotation.Setting
    public Long getLong(InjectionPoint ip) {
        return getLong(ip2setting(ip));
    }


    /**
     * Injects the Integer setting defined by the {@code @Setting} annotation
     *
     * @param ip the injection point
     * @return the Long setting value
     */
    @Produces
    @org.niord.core.settings.annotation.Setting
    public Integer getInteger(InjectionPoint ip) {
        return getInteger(ip2setting(ip));
    }


    /**
     * Injects the Float setting defined by the {@code @Setting} annotation
     *
     * @param ip the injection point
     * @return the Float setting value
     */
    @Produces
    @org.niord.core.settings.annotation.Setting
    public Float getFloat(InjectionPoint ip) {
        return getFloat(ip2setting(ip));
    }


    /**
     * Injects the Double setting defined by the {@code @Setting} annotation
     *
     * @param ip the injection point
     * @return the Double setting value
     */
    @Produces
    @org.niord.core.settings.annotation.Setting
    public Double getDouble(InjectionPoint ip) {
        return getDouble(ip2setting(ip));
    }


    /**
     * Injects the Path setting defined by the {@code @Setting} annotation
     *
     * @param ip the injection point
     * @return the Path setting value
     */
    @Produces
    @org.niord.core.settings.annotation.Setting
    public Path getPath(InjectionPoint ip) {
        return getPath(ip2setting(ip));
    }


    /**
     * Injects the Date setting defined by the {@code @Setting} annotation
     *
     * @param ip the injection point
     * @return the Date setting value
     */
    @Produces
    @org.niord.core.settings.annotation.Setting
    public Date getDate(InjectionPoint ip) {
        return getDate(ip2setting(ip));
    }


    /**
     * Converts the injection point into the associated setting with a default value
     *
     * @param ip the injection point
     * @return the associated setting
     */
    private Setting ip2setting(InjectionPoint ip) {
        org.niord.core.settings.annotation.Setting ann = ip.getAnnotated()
                .getAnnotation(org.niord.core.settings.annotation.Setting.class);

        String key = StringUtils.isBlank(ann.value()) ? ip.getMember().getName() : ann.value();
        Object value = null;
        String defVal = ann.defaultValue();
        switch (ann.type()) {
            case String:
            case Password:
                value = ann.defaultValue();
                break;
            case Integer:
                value = defVal.isEmpty() ? null : Integer.valueOf(defVal);
                break;
            case Long:
                value = defVal.isEmpty() ? null : Long.valueOf(defVal);
                break;
            case Float:
                value = defVal.isEmpty() ? null : Float.valueOf(defVal);
                break;
            case Double:
                value = defVal.isEmpty() ? null : Double.valueOf(defVal);
                break;
            case Boolean:
                value = defVal.isEmpty() ? null : Boolean.valueOf(defVal);
                break;
            case Date:
                value = defVal.isEmpty() ? null : Long.valueOf(defVal);
                break;
            case Path:
                value = defVal.isEmpty() ? null : Paths.get(defVal);
                break;
            case json:
                try {
                    value = defVal.isEmpty() ? null : new ObjectMapper().readValue(defVal, Object.class);
                } catch (Exception ignored) {
                }
                break;
        }
        return new Setting(key, value, ann.type(), ann.description(), ann.cached(), ann.web(), ann.editable());
    }

}
