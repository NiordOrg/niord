package org.niord.core.schedule.vo;

import org.niord.core.area.vo.SystemAreaVo;
import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a firing area along with its firing periods
 */
@SuppressWarnings("unused")
public class FiringAreaPeriodsVo implements IJsonSerializable {

    SystemAreaVo area;
    List<FiringPeriodVo> firingPeriods = new ArrayList<>();


    /** Constructor **/
    public FiringAreaPeriodsVo() {
    }


    /** Constructor **/
    public FiringAreaPeriodsVo(SystemAreaVo area) {
        this.area = area;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public SystemAreaVo getArea() {
        return area;
    }

    public void setArea(SystemAreaVo area) {
        this.area = area;
    }

    public List<FiringPeriodVo> getFiringPeriods() {
        return firingPeriods;
    }

    public void setFiringPeriods(List<FiringPeriodVo> firingPeriods) {
        this.firingPeriods = firingPeriods;
    }
}
