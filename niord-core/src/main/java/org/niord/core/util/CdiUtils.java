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
package org.niord.core.util;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * Can be used to force injection in classes that do not support CDI
 */
public class CdiUtils {

    /**
     * Don't instantiate this class
     */
    private CdiUtils() {
    }

    /**
     * Performs the injection of the given class upon the given injectionObject
     *
     * @param clazz The class of the object to inject upon
     * @param injectionObject the object to inject upon
     */
    @SuppressWarnings({ "rawtypes", "unchecked", "unused"})
    public static <T> void programmaticInjection(Class clazz, T injectionObject) throws NamingException {
        InitialContext initialContext = new InitialContext();
        Object lookup = initialContext.lookup("java:comp/BeanManager");
        BeanManager beanManager = (BeanManager) lookup;
        AnnotatedType annotatedType = beanManager.createAnnotatedType(clazz);
        InjectionTarget injectionTarget = beanManager.createInjectionTarget(annotatedType);
        CreationalContext creationalContext = beanManager.createCreationalContext(null);
        injectionTarget.inject(injectionObject, creationalContext);
        creationalContext.release();
    }

    /**
     * Looks up a CDI managed bean with the given class
     *
     * @param clazz The class of the object to look up
     * @return the object with the given class
     */
    @SuppressWarnings({ "unchecked", "unused" })
    public static <T> T getBean(Class<T> clazz) throws NamingException {
        InitialContext initialContext = new InitialContext();
        Object lookup = initialContext.lookup("java:comp/BeanManager");
        BeanManager beanManager = (BeanManager) lookup;
        Bean<T> bean = (Bean<T>)beanManager.getBeans(clazz).iterator().next();
        CreationalContext<T> cc = beanManager.createCreationalContext(bean);
        return (T)beanManager.getReference(bean, clazz, cc);
    }
}
