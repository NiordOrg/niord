package org.niord.core.aton.vo;

/**
 * An AtoN OSM seamark node tag metadata.
 * <p>
 * The AtoN model adheres to the OSM seamark specification, please refer to:
 * http://wiki.openstreetmap.org/wiki/Key:seamark
 * and sub-pages.
 * <p>
 * The model represents the metadata attached to the AtoN tags, such as the
 * description text and its type.
 * <p>
 */
public class AtonTagMetaVo {
    /**
     * The K.
     */
    String k;
    /**
     * The Text.
     */
    String text;
    /**
     * The Type.
     */
    String type;

    /**
     * Instantiates a new Aton tag meta.
     */
    public AtonTagMetaVo() {

    }

    /**
     * Instantiates a new Aton tag meta.
     *
     * @param k    the k
     * @param text the text
     * @param type the type
     */
    public AtonTagMetaVo(String k, String text, String type) {
        this.k = k;
        this.text = text;
        this.type = type;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    /**
     * Gets k.
     *
     * @return the k
     */
    public String getK() {
        return k;
    }

    /**
     * Sets k.
     *
     * @param k the k
     */
    public void setK(String k) {
        this.k = k;
    }

    /**
     * Gets text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets text.
     *
     * @param text the text
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Gets type.
     *
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets type.
     *
     * @param type the type
     */
    public void setType(String type) {
        this.type = type;
    }
}
