package org.niord.model.vo;

import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizedDesc;

/**
 * The entity description VO
 */
public class AreaDescVo implements ILocalizedDesc, IJsonSerializable {

    String lang;
    String name;

    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(name);
    }

    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc desc) {
        this.name = ((AreaDescVo)desc).getName();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Override
    public String getLang() {
        return lang;
    }

    @Override
    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
