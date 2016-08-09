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
package org.niord.core.fm;

import org.niord.core.domain.Domain;
import org.niord.core.model.BaseEntity;
import org.niord.core.fm.vo.FmReportVo;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a freemarker report that generates PDF for a list of messages
 */
@Entity
@NamedQueries({
        @NamedQuery(name="FmReport.findByReportId",
                query="SELECT r FROM FmReport r where r.reportId = :reportId"),
        @NamedQuery(name="FmReport.findPublicReports",
                query="SELECT r FROM FmReport r where r.domains is empty"),
        @NamedQuery(name="FmReport.findReportsByDomain",
                query="SELECT r FROM FmReport r join r.domains d where d = :domain")
})
@SuppressWarnings("unused")
public class FmReport extends BaseEntity<Integer> implements Comparable<FmReport> {

    @Column(unique = true, nullable = false)
    String reportId;

    @NotNull
    String name;

    @NotNull
    String templatePath;

    @ManyToMany
    List<Domain> domains = new ArrayList<>();

    /**
     * Constructor
     */
    public FmReport() {
    }

    /** Converts this entity to a value object */
    public FmReportVo toVo() {
        FmReportVo report = new FmReportVo();
        report.setReportId(reportId);
        report.setName(name);
        report.setTemplatePath(templatePath);
        return report;
    }


    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public int compareTo(FmReport t) {
        return t == null ? -1 : name.toLowerCase().compareTo(t.getName().toLowerCase());
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public List<Domain> getDomains() {
        return domains;
    }

    public void setDomains(List<Domain> domains) {
        this.domains = domains;
    }
}
