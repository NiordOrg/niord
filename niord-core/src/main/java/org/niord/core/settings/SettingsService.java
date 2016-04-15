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
package org.niord.core.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.niord.core.cache.CacheElement;
import org.niord.core.service.BaseService;
import org.niord.core.util.JsonUtils;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.keycloak.util.JsonSerialization.mapper;

/**
 * Interface for accessing settings.
 * <p/>
 * This bean can either be injected directly,
 * or the {@code @Setting} annotation can be used.
 */
@Singleton
@Lock(LockType.READ)
@Startup
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
    @PostConstruct
    public void loadSettingsFromPropertiesFile() {
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
            settingMap.values().stream()
                    .forEach(s -> {
                        s.updateType();
                        em.persist(s);
                        log.info(String.format("Loaded setting %s from niord.json", s.getKey()));
                    });

        } catch (Exception e) {
            // Stop the application starting up
            throw new RuntimeException("Error loading settings from niord.json", e);
        }
    }


    /** Called upon startup. Read the settings from the "/niord.json" classpath file */
    private Map<String, Setting> loadSettingsFromClasspath() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<Setting> settings = mapper.readValue(
                getClass().getResource(SETTINGS_FILE),
                new TypeReference<List<Setting>>(){});
        return settings.stream()
                .collect(Collectors.toMap(Setting::getKey, Function.identity()));
    }


    /** Called upon startup. Read the settings from the "${niord.home}/niord.json" file and update the settingMap */
    private Map<String, Setting> loadSettingsFromNiordHome(Map<String, Setting> settingMap) throws IOException {
        Object niordHome = peek("niord.home");
        if (niordHome == null && settingMap.containsKey("niord.home")) {
            niordHome = settingMap.get("niord.home").getValue();
        }
        if (niordHome != null && niordHome instanceof String) {
            niordHome = expandSettingValue((String)niordHome);
            Path niordFile = Paths.get(niordHome.toString(), "niord.json");
            if (Files.exists(niordFile) && Files.isRegularFile(niordFile)) {
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
    public List<Setting> getAllForWeb() {
        return em.createNamedQuery("Setting.findAllForWeb", Setting.class)
                .getResultList();
    }

    /**
     * Returns all settings that are editable on the Settings admin page
     * @return all settings that are editable on the Settings admin page
     */
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
    public Object get(Setting setting) {
        Objects.requireNonNull(setting, "Must specify valid setting");

        // If a corresponding system property is set, it takes precedence
        if (System.getProperty(setting.getKey()) != null) {
            return System.getProperty(setting.getKey());
        }

        // Look for a cached value
        CacheElement<Object> value = settingsCache.getCache().get(setting.getKey());

        // No cached value
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
    public Object peek(String key) {
        Objects.requireNonNull(key, "Must specify valid setting key");

        // If a corresponding system property is set, it takes precedence
        if (System.getProperty(key) != null) {
            return System.getProperty(key);
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
        while ((token = valueExpander.nextToken()) != null) {
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
            valueExpander.replaceToken(token, "");
        }
        return valueExpander.getValue();
    }


    /**
     * Updates the database value of the given setting
     * @param template the setting to update
     * @return the updated setting
     */
    @Lock(LockType.WRITE)
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
        return new Setting(key, value, ann.description(), ann.cached(), ann.web(), ann.editable());
    }

}
