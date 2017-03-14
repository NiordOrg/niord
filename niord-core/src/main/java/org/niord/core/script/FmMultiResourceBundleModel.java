/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.script;

import freemarker.core.Environment;
import freemarker.core._DelayedJQuote;
import freemarker.core._TemplateModelException;
import freemarker.ext.beans.BeanModel;
import freemarker.ext.beans.BeansWrapper;
import freemarker.ext.beans.StringModel;
import freemarker.ext.util.ModelFactory;
import freemarker.template.ObjectWrapper;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>A hash model that wraps a list of resource bundle.
 *
 * The class is mainly based on {@linkplain freemarker.ext.beans.ResourceBundleModel}, but extended to
 * handle a list of resource bundles for different languages.
 *
 * The resource bundle version actually used, is determined from the locale of the current environment.
 * In the Freemarker templates switch between languages using:
 * <pre>
 *     &lt;#setting locale=lang&gt;
 * </pre>
 */
@SuppressWarnings("all")
public class FmMultiResourceBundleModel
        extends
        BeanModel
        implements
        TemplateMethodModelEx
{
    static final String DEFAULT_LANGUAGE = "en";

    static final ModelFactory FACTORY = new ModelFactory()  {
                public TemplateModel create(Object object, ObjectWrapper wrapper)  {
                    return new freemarker.ext.beans.ResourceBundleModel((ResourceBundle)object, (BeansWrapper)wrapper);
                }
            };

    private Hashtable formats = null;


    /** Constructor **/
    public FmMultiResourceBundleModel(List<ResourceBundle> bundles, BeansWrapper wrapper) {
        super(toBundleMap(bundles), wrapper);
    }


    /** Returns the language associated with the locale, and the default language if the locale is undefined **/
    private static String language(Locale locale) {
        return locale != null ? locale.getLanguage() : DEFAULT_LANGUAGE;
    }


    /** Converts a list of resource bundles to a map that maps language to the bundle **/
    private static Map<String, ResourceBundle> toBundleMap(List<ResourceBundle> bundles) {
        return bundles.stream()
                .collect(Collectors.toMap(b -> language(b.getLocale()), Function.identity()));
    }


    /** Returns the resource bundle for the current language. If the bundle is not found, use the default language **/
    public ResourceBundle getResourceBundle() {
        Environment env = Environment.getCurrentEnvironment();
        String currentTemplateLanguage = language(env.getLocale());
        Map<String, ResourceBundle> bundleMap = (Map<String, ResourceBundle>)object;
        ResourceBundle bundle = bundleMap.get(currentTemplateLanguage);
        return bundle != null ? bundle : bundleMap.get(DEFAULT_LANGUAGE);
    }


    /**
     * Overridden to invoke the getObject method of the resource bundle.
     */
    protected TemplateModel invokeGenericGet(Map keyMap, Class clazz, String key)
            throws
            TemplateModelException
    {
        try {
            return wrap(getResourceBundle().getObject(key));
        } catch(MissingResourceException e) {
            throw new _TemplateModelException(e,
                    new Object[] { "No ", new _DelayedJQuote(key), " key in the ResourceBundle. "
                            + "Note that conforming to the ResourceBundle Java API, this is an error and not just "
                            + "a missing sub-variable (a null)." });
        }
    }

    /**
     * Returns true if this bundle contains no objects.
     */
    public boolean isEmpty()
    {
        return !getResourceBundle().getKeys().hasMoreElements() &&
                super.isEmpty();
    }

    public int size()
    {
        return keySet().size();
    }

    protected Set keySet()
    {
        Set set = super.keySet();
        Enumeration e = getResourceBundle().getKeys();
        while (e.hasMoreElements()) {
            set.add(e.nextElement());
        }
        return set;
    }

    /**
     * Takes first argument as a resource key, looks up a string in resource bundle
     * with this key, then applies a MessageFormat.format on the string with the
     * rest of the arguments. The created MessageFormats are cached for later reuse.
     */
    public Object exec(List arguments)
            throws
            TemplateModelException
    {
        // Must have at least one argument - the key
        if(arguments.size() < 1)
            throw new TemplateModelException("No message key was specified");
        // Read it
        Iterator it = arguments.iterator();
        String key = unwrap((TemplateModel)it.next()).toString();
        try
        {
            if(!it.hasNext())
            {
                return wrap(getResourceBundle().getObject(key));
            }

            // Copy remaining arguments into an Object[]
            int args = arguments.size() - 1;
            Object[] params = new Object[args];
            for(int i = 0; i < args; ++i)
                params[i] = unwrap((TemplateModel)it.next());

            // Invoke format
            return new StringModel(format(key, params), wrapper);
        }
        catch(MissingResourceException e)
        {
            throw new TemplateModelException("No such key: " + key);
        }
        catch(Exception e)
        {
            throw new TemplateModelException(e.getMessage());
        }
    }

    /**
     * Provides direct access to caching format engine from code (instead of from script).
     */
    public String format(String key, Object[] params)
            throws
            MissingResourceException
    {
        // Check to see if we already have a cache for message formats
        // and construct it if we don't
        // NOTE: this block statement should be synchronized. However
        // concurrent creation of two caches will have no harmful
        // consequences, and we avoid a performance hit.
        /* synchronized(this) */
        {
            if(formats == null)
                formats = new Hashtable();
        }

        MessageFormat format = null;
        // Check to see if we already have a requested MessageFormat cached
        // and construct it if we don't
        // NOTE: this block statement should be synchronized. However
        // concurrent creation of two formats will have no harmful
        // consequences, and we avoid a performance hit.
        /* synchronized(formats) */
        {
            format = (MessageFormat)formats.get(key);
            if(format == null)
            {
                format = new MessageFormat(getResourceBundle().getString(key));
                format.setLocale(getResourceBundle().getLocale());
                formats.put(key, format);
            }
        }

        // Perform the formatting. We synchronize on it in case it
        // contains date formatting, which is not thread-safe.
        synchronized(format) {
            return format.format(params);
        }
    }
}
