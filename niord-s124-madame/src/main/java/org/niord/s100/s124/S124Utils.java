/*
 * Copyright (c) 2024 GLA Research and Development Directorate
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.s100.s124;

import static java.util.Objects.requireNonNull;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.S100SpatialAttributeType;
import dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.impl.CurvePropertyImpl;
import dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.impl.PointPropertyImpl;
import dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.impl.SurfacePropertyImpl;
import dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.AbstractFeatureType;
import dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.AbstractGMLType;
import dk.dma.baleen.s100.xmlbindings.s124.v1_0_0.Dataset;
import dk.dma.baleen.s100.xmlbindings.s124.v1_0_0.NAVWARNAreaAffected;
import dk.dma.baleen.s100.xmlbindings.s124.v1_0_0.NAVWARNPart;
import dk.dma.baleen.s100.xmlbindings.s124.v1_0_0.S100TruncatedDate;
import dk.dma.baleen.s100.xmlbindings.s124.v1_0_0.TextPlacement;
import dk.dma.baleen.s100.xmlbindings.s124.v1_0_0.impl.DatasetImpl;
import dk.dma.baleen.s100.xmlbindings.s124.v1_0_0.impl.S100TruncatedDateImpl;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.JAXBIntrospector;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

/**
 * The S-124 Utility Class.
 * <p/>
 * A static utility function class that allows easily manipulation of the S-124 datasets.
 *
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
class S124Utils {

    /**
     * Overloading the S-124 marshalling operation to easily perform the task with the formatting turned on by default.
     *
     * @param dataset
     *            the S-124 Dataset object
     * @return the marshalled S-124 Dataset XML representation
     * @throws JAXBException
     *             for errors in the unmarshalling operation
     */
    static String marshalS124(Dataset dataset) throws JAXBException {
        return marshalS124(dataset, Boolean.TRUE);
    }

    /**
     * Using the S-124 utilities we can marshall back an S-124 DatasetType object in its XML view.
     *
     * @param dataset
     *            the S-124 Dataset object
     * @param format
     *            whether to format the XML string
     * @return the marshalled S-124 Dataset XML representation
     * @throws JAXBException
     *             for errors in the marshalling operation
     */
    static String marshalS124(Dataset dataset, Boolean format) throws JAXBException {
        requireNonNull(dataset, "dataset is null");

        // Manipulate the class loader for the JAXBContext
        Locale locale = Locale.getDefault();
        try {
            // Make sure decimal numbers are using . and not ,
            // This is an ugly hack, but the only simple solution I could find
            Locale.setDefault(Locale.US);
            final Thread thread = Thread.currentThread();
            final ClassLoader originalClassLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(DatasetImpl.class.getClassLoader());
            final JAXBContext jaxbContext = JAXBContext.newInstance(DatasetImpl.class);

            // Create the JAXB Marshaller
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, format);

            // Transform the S-124 object to an output stream
            ByteArrayOutputStream xmlStream = new ByteArrayOutputStream();
            jaxbMarshaller.marshal(dataset, xmlStream);

            // Replace the original context loader
            thread.setContextClassLoader(originalClassLoader);

            // Return the XML string

            return xmlStream.toString();
        } finally {
            Locale.setDefault(locale);
        }
    }

    /**
     * The character string input contains the S-124 XML content of the message. We can easily translate that into an S-124
     * Dataset object so that it can be accessed more efficiently.
     *
     * @param s124
     *            the S-124 dataset XML representation
     * @return The unmarshalled S-124 DatasetType object
     * @throws JAXBException
     *             for errors in the unmarshalling operation
     */
    static Dataset unmarshallS124(String s124) throws JAXBException {
        // Manipulate the class loader for the JAXBContext
        final Thread thread = Thread.currentThread();
        final ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(DatasetImpl.class.getClassLoader());
        final JAXBContext jaxbContext = JAXBContext.newInstance(DatasetImpl.class);

        // Create the JAXB Unmarshaller
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

        // Transform the S-124 context into an input stream and then a dataset
        final ByteArrayInputStream is = new ByteArrayInputStream(s124.getBytes());
        final Object value = JAXBIntrospector.getValue(jaxbUnmarshaller.unmarshal(is));

        // Replace the original context loader
        thread.setContextClassLoader(originalClassLoader);

        // And return the dataset
        return (Dataset) value;
    }

    /**
     * For easier access to a dataset members, this function will parse the S-124 XML content of a dataset and returned the
     * included objects.
     *
     * @param s124
     *            the S-124 dataset XML representation
     * @return the list of all dataset member entries as abstract feature types
     * @throws JAXBException
     *             for errors in the unmarshalling operation
     */
    static List<? extends AbstractGMLType> getDatasetMembers(String s124) throws JAXBException {
        return getDatasetMembers(S124Utils.unmarshallS124(s124));
    }

    /**
     * Returns a list of all defined abstract feature types in a given dataset. This list will basically contain all the
     * entries of the XML-based dataset regardless of their individual types. These can then be further processed and
     * handled according to their types.
     *
     * @param dataset
     *            the dataset to be processed
     * @return the list of all dataset member entries as abstract feature types
     */
    static List<? extends AbstractGMLType> getDatasetMembers(Dataset dataset) {
        // First get the dataset members
        Dataset.Members members = Optional.ofNullable(dataset).map(Dataset::getMembers).orElse(null);

        // Sanity Check
        if (Objects.isNull(members)) {
            return Collections.emptyList();
        }

        // Otherwise combine all member data
        return Stream.of(members.getNAVWARNPreamblesAndReferencesAndNAVWARNParts()).flatMap(Collection::stream).filter(AbstractGMLType.class::isInstance)
                .map(AbstractGMLType.class::cast).toList();
    }

    /**
     * Populates the appropriate list of the Dataset Members with the Aids to Navigation entry provided, based on its class.
     * This is done simply by checking the class type of the Aids to Navigation entry and adding it in the appropriate
     * dataset members list.
     *
     * @param dataset
     *            the dataset whose members will be populated
     * @param memberEntries
     *            the collection of member entries to be added
     * @param <T>
     *            the generic type of the entry, extending the AbstractFeatureTypeImpl
     */
    static <T extends AbstractGMLType> void addDatasetMembers(Dataset dataset, Collection<T> memberEntries) {
        // Sanity checks
        if (Objects.isNull(dataset) || Objects.isNull(memberEntries)) {
            return;
        }

        // First get the dataset members
        final Dataset.Members members = Optional.of(dataset).map(Dataset::getMembers).orElseGet(DatasetImpl.MembersImpl::new);

        // Add all the member entries iteratively
        for (T member : memberEntries) {
            switch (member) {
            case NAVWARNAreaAffected spatialUncertainty -> members.getNAVWARNPreamblesAndReferencesAndNAVWARNParts().add(member);
            case NAVWARNPart atoNFixingMethod -> members.getNAVWARNPreamblesAndReferencesAndNAVWARNParts().add(member);
            case TextPlacement positioningInformation -> members.getNAVWARNPreamblesAndReferencesAndNAVWARNParts().add(member);
            case null, default -> members.getNAVWARNPreamblesAndReferencesAndNAVWARNParts().add(member);
            }
        }

        // Now add the updated members back
        dataset.setMembers(members);
    }

    /**
     * This is a helper function to alleviate the complicated situation of updating the geometry of an S-124 Aids to
     * Navigation structure. These are generated based on the S-124 XSD schema and can have a number of geometries that
     * require packaging inside a specific Geometry subclass each time. These are then inserted into lists for each of S-124
     * the Aids to Navigation types.
     *
     * @param aidsToNavigationTypeClass
     *            the Aids to Navigation Type feature class
     * @param values
     *            the list of S100 spatial attribute values to be populated
     * @return the geometry list populated for the specific S-124 Aids to Navigation Type
     */
    static List<?> generateS124AidsToNavigationTypeGeometriesList(Class<?> aidsToNavigationTypeClass, List<S100SpatialAttributeType> values) {
        // Sanity Checks
        if (aidsToNavigationTypeClass == null || values == null) {
            return Collections.emptyList();
        }

        // Create a new custom AtoN geometry object to insert to the list
        final Class<?> geometryClass = Optional.of(aidsToNavigationTypeClass).map(clazz -> getS124AidsToNavigationDeclaredClass("GeometryImpl", clazz))
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("The %s S-124 Aids to Navigation type does not specify a geometry", aidsToNavigationTypeClass.getSimpleName())));

        // Now we need to instantiate and populate the geometry objects based
        // on the geometries value type of each of the provided S100 spatial
        // attributes. The generated list will be collected and returned
        return values.stream().map(val -> {
            final Object geometriesClassObj = Optional.ofNullable(geometryClass).map(c -> {
                try {
                    return c.getConstructor();
                } catch (Exception ex) {
                    return null;
                }
            }).map(c -> {
                try {
                    return c.newInstance();
                } catch (Exception ex) {
                    return null;
                }
            }).map(geometryClass::cast).orElse(null);

            if (geometriesClassObj != null) {
                try {
                    if (val instanceof PointPropertyImpl) {
                        new PropertyDescriptor("pointProperty", geometriesClassObj.getClass()).getWriteMethod().invoke(geometriesClassObj, val);
                    } else if (val instanceof CurvePropertyImpl) {
                        new PropertyDescriptor("curveProperty", geometriesClassObj.getClass()).getWriteMethod().invoke(geometriesClassObj, val);
                    } else if (val instanceof SurfacePropertyImpl) {
                        new PropertyDescriptor("surfaceProperty", geometriesClassObj.getClass()).getWriteMethod().invoke(geometriesClassObj, val);
                    }
                } catch (Exception ex) {
                    return null;
                }
            }

            return geometriesClassObj;
        }).filter(Objects::nonNull).map(geometryClass::cast).collect(Collectors.toList());
    }

    /**
     * Retrieves the geometry values of any S-124 Aids to Navigation type feature and returns it in a harmonised way as a
     * list of S100SpatialAttributeType objects.
     *
     * @param aidsToNavigationType
     *            the S-124 Aids to Navigation Type feature
     * @return The populated S100SpatialAttributeType object list
     */
    @SuppressWarnings("unchecked")
    static List<S100SpatialAttributeType> getS124AidsToNavigationTypeGeometriesList(AbstractFeatureType aidsToNavigationType) {
        // Sanity Check
        if (aidsToNavigationType == null) {
            return Collections.emptyList();
        }

        return (List<S100SpatialAttributeType>) Optional.of(aidsToNavigationType).map(aton -> getS124AidsToNavigationField("geometries", aton))
                .map(geometriesField -> {
                    try {
                        return geometriesField.get(aidsToNavigationType);
                    } catch (IllegalAccessException ex) {
                        return null;
                    }
                }).filter(List.class::isInstance).map(List.class::cast).orElse(Collections.emptyList()).stream().map(geom -> {
                    try {
                        PropertyDescriptor pointPropertyPD = new PropertyDescriptor("pointProperty", geom.getClass());
                        return pointPropertyPD.getReadMethod().invoke(geom);
                    } catch (IntrospectionException | InvocationTargetException | IllegalAccessException | ClassCastException ex) {
                        // Don't do anything yet
                    }
                    try {
                        PropertyDescriptor curvePropertyPD = new PropertyDescriptor("curveProperty", geom.getClass());
                        return curvePropertyPD.getReadMethod().invoke(geom);
                    } catch (IntrospectionException | InvocationTargetException | IllegalAccessException | ClassCastException ex) {
                        // Don't do anything yet
                    }
                    try {
                        PropertyDescriptor surfacePropertyPD = new PropertyDescriptor("surfaceProperty", geom.getClass());
                        return surfacePropertyPD.getReadMethod().invoke(geom);
                    } catch (IntrospectionException | InvocationTargetException | IllegalAccessException | ClassCastException ex) {
                        // Don't do anything yet
                    }
                    // Now if everything failed return null
                    return null;
                }).filter(Objects::nonNull).filter(S100SpatialAttributeType.class::isInstance).map(S100SpatialAttributeType.class::cast)
                .collect(Collectors.toList());
    }

    /**
     * A useful utility function to run any operation on a generic S-124 Aids to Navigation type feature, but based on its
     * actual class.
     *
     * @param aidsToNavigationType
     *            The S-124 Aids to Navigation Type feature
     * @param clazz
     *            the class type to cast the feature to
     * @param function
     *            the function to be operated
     * @return
     * @param <T>
     *            the actual type of the generic feature
     * @param <R>
     *            the type of the resulting object
     */
    static <T extends AbstractFeatureType, R> R handlePerS124AidsToNavigationType(AbstractFeatureType aidsToNavigationType, Class<T> clazz,
            Function<T, R> function) {
        return Optional.ofNullable(aidsToNavigationType).filter(clazz::isInstance).map(clazz::cast).map(function::apply).orElse(null);
    }

    /**
     * In many cases the AidsToNavigationType does not itself have a geometries field but this belongs to a superclass.
     * Therefore, it is difficult to detect that purely with reflections so a manual iterative way is employed
     * instead.Notice that the geometry field is titled geometries since it contains a list of geometries.
     *
     * @param aidsToNavigationType
     *            The S-124 Aids to Navigation Type feature
     * @return The detected geometries field
     */
    static Field getS124AidsToNavigationField(String field, AbstractFeatureType aidsToNavigationType) {
        Class<?> clazz = aidsToNavigationType.getClass();
        Field requiredField;

        // Try iteratively to find where the geometries field is
        while (clazz != null && clazz != Object.class) {
            // Try to get the required field
            try {
                requiredField = clazz.getDeclaredField(field);
                requiredField.setAccessible(true);
                return requiredField;
            } catch (NoSuchFieldException e) {
                // the required field is still null so no problem
            }

            // If not found check again the super class
            clazz = clazz.getSuperclass();
        }

        // If nothing found return null
        return null;
    }

    /**
     * In many cases the AidsToNavigationField does not itself have a geometries field but this belongs to a superclass.
     * Therefore, it is difficult to detect that purely with reflections so a manual iterative way is employed
     * instead.Notice that the geometry field is titled geometries since it contains a list of geometries.
     *
     * @param className
     *            The required declared class name
     * @param aidsToNavigationTypeClass
     *            The Aids to Navigation Type class
     * @return The detected geometries field
     */
    static Class<?> getS124AidsToNavigationDeclaredClass(String className, Class<?> aidsToNavigationTypeClass) {
        Class<?> clazz = aidsToNavigationTypeClass;
        Class<?> requiredClass;

        // Try iteratively to find where the geometries field is
        while (clazz != null && clazz != Object.class) {
            // Try to get the required class
            requiredClass = Arrays.stream(clazz.getDeclaredClasses()).filter(c -> c.getSimpleName().equals(className)).findAny().orElse(null);
            if (requiredClass != null) {
                return requiredClass;
            }

            // If not found check again the super class
            clazz = clazz.getSuperclass();
        }

        // If nothing found return null
        return null;
    }

    /**
     * A helper function that translates the provided S100TruncatedDate objects into Java LocalDate objects.
     *
     * @param s100TruncatedDate
     *            the S100TruncatedDate object to be translated
     * @return the constructed LocalDate object
     */
    static LocalDate s100TruncatedDateToLocalDate(S100TruncatedDate s100TruncatedDate) {
        // Sanity Check
        if (s100TruncatedDate == null) {
            return null;
        }

        // First try to get the date object
        if (s100TruncatedDate.getDate() != null) {
            return s100TruncatedDate.getDate();
        }
        // Otherwise try to reconstruct the date from the fields
        else if (s100TruncatedDate.getGYear() != null && s100TruncatedDate.getGMonth() != null && s100TruncatedDate.getGDay() != null) {
            return LocalDate.of(s100TruncatedDate.getGYear().getYear(), s100TruncatedDate.getGMonth().getMonth(), s100TruncatedDate.getGDay().getDay());
        }

        // Otherwise always return null
        return null;
    }

    /**
     * A helper function that translates the provided LocalDate objects into Java S100TruncatedDate objects.
     *
     * @param localDate
     *            the LocalDate object to be translated
     * @return the constructed S100TruncatedDate object
     */
    static S100TruncatedDate localDateToS100TruncatedDate(LocalDate localDate) {
        // Sanity Check
        if (localDate == null) {
            return null;
        }

        // Always use the local date field which is easier
        final S100TruncatedDate s100TruncatedDate = new S100TruncatedDateImpl();
        s100TruncatedDate.setDate(localDate);
        return s100TruncatedDate;
    }

}
