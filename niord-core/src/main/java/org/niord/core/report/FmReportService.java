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
package org.niord.core.report;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang.StringUtils;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.report.vo.FmReportVo;
import org.niord.core.service.BaseService;
import org.niord.core.util.TimeUtils;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main interface for accessing and processing Freemarker reports
 */
@ApplicationScoped
@SuppressWarnings("unused")
public class FmReportService extends BaseService {

    @Inject
    Logger log;

    @Inject
    DomainService domainService;

    /***************************************/
    /** Report methods                    **/
    /***************************************/


    /** Upon start-up, check that the standard report is defined **/
    @PostConstruct
    private void init() {
        getStandardReport();
        getDraftReport();
    }


    /**
     * Returns the standard report used for generating PDFs from message lists
     * @return the standard report used for generating PDFs from message lists
     */
    public FmReport getStandardReport() {
        // Check that the standard report is defined
        FmReport report = findByReportId("standard");
        if (report == null) {
            report = new FmReport();
            report.setReportId("standard");
            report.setName("Standard");
            report.setSortOrder(0);
            report.setTemplatePath("/templates/messages/message-list-pdf.ftl");
            try {
                em.persist(report);
                log.info("Created standard report");
            } catch (Exception e) {
                log.error("Failed creating standard report", e);
            }
        }
        return report;
    }

    /**
     * Returns the draft report used for generating PDFs from message lists
     * @return the draft report used for generating PDFs from message lists
     */
    public FmReport getDraftReport() {
        // Check that the standard report is defined
        FmReport report = findByReportId("draft");
        if (report == null) {
            report = new FmReport();
            report.setReportId("draft");
            report.setName("Draft");
            report.setSortOrder(1);
            report.setTemplatePath("/templates/messages/message-list-pdf.ftl");
            report.getProperties().put("draft", true);
            try {
                em.persist(report);
                log.info("Created draft report");
            } catch (Exception e) {
                log.error("Failed draft standard report", e);
            }
        }
        return report;
    }

    /**
     * Returns the report with the given report ID, or null if not found
     * @param reportId the report ID
     * @return the report with the given report ID, or null if not found
     */
    public FmReport findByReportId(String reportId) {
        try {
            return em.createNamedQuery("FmReport.findByReportId", FmReport.class)
                    .setParameter("reportId", reportId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns the report with the given report ID, or the standard report if not found
     * @param reportId the report ID
     * @return the report with the given report ID, or the standard report if not found
     */
    public FmReport getReport(String reportId) {
        FmReport report = (StringUtils.isNotBlank(reportId)) ? findByReportId(reportId) : null;
        if (report == null) {
            report = getStandardReport();
        }
        return report;
    }


    /**
     * Returns the reports available in the current domain
     * @return the reports available in the current domain
     */
    public List<FmReport> getReports() {
        List<FmReport> reports = new ArrayList<>();

        // First get the public reports
        reports.addAll(em.createNamedQuery("FmReport.findPublicReports", FmReport.class)
                .getResultList());

        // If a current domain is defined, add the domain specific reports
        Domain domain = domainService.currentDomain();
        if (domain != null) {
            reports.addAll(em.createNamedQuery("FmReport.findReportsByDomain", FmReport.class)
                    .setParameter("domain", domain)
                    .getResultList());

        }

        Collections.sort(reports);

        return reports;
    }


    /**
     * Returns all reports
     * @return all reports
     */
    public List<FmReport> getAllReports() {
        return em.createNamedQuery("FmReport.findAll", FmReport.class)
                .getResultList();
    }


    /**
     * Expand report parameter values with the format ${week}, ${year}, etc.
     * @param report the report to expand parameter values for
     * @return the updated report
     */
    public FmReportVo expandReportParams(FmReportVo report) {
        if (report.getProperties() != null && !report.getProperties().isEmpty()) {
            Date today = new Date();
            int year = TimeUtils.getCalendarField(today, Calendar.YEAR);
            int week = TimeUtils.getISO8601WeekOfYear(today);

            Map<String, String> replacementValues = new HashMap<>();
            replacementValues.put("${year-2-digits}", String.valueOf(year).substring(2));
            replacementValues.put("${year}", String.valueOf(year));
            replacementValues.put("${week}", String.format("%d", week));
            replacementValues.put("${week-2-digits}", String.format("%02d", week));

            report.setParams(report.getParams().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> expandReportParamValue(e.getValue(), replacementValues)
                    )));
        }
        return report;
    }


    /** Expands the given parameter value if it has the format ${week}, ${year}, etc. **/
    private Object expandReportParamValue(Object value, Map<String, String> replacementValues) {
        if (value != null && value instanceof String) {
            String str = (String)value;
            for (Map.Entry<String, String> e : replacementValues.entrySet()) {
                str = str.replace(e.getKey(), e.getValue()).trim();
            }
            return str;
        }
        return value;
    }


    /**
     * Creates a new report based on the report parameter
     * @param report the report to create
     * @return the created report
     */
    @Transactional
    public FmReport createReport(FmReport report) {
        FmReport original = findByReportId(report.getReportId());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create report with duplicate id " + report.getReportId());
        }

        // Replace template domains with persisted ones
        report.setDomains(domainService.persistedDomains(report.getDomains()));

        return saveEntity(report);
    }


    /**
     * Updates the report data from the report parameter
     * @param report the report to update
     * @return the updated report
     */
    @Transactional
    public FmReport updateReport(FmReport report) {
        FmReport original = findByReportId(report.getReportId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing report " + report.getReportId());
        }

        // Copy the report data
        original.setSortOrder(report.getSortOrder());
        original.setName(report.getName());
        original.setTemplatePath(report.getTemplatePath());
        original.getProperties().clear();
        original.getProperties().putAll(report.getProperties());
        original.getParams().clear();
        original.getParams().putAll(report.getParams());
        original.setDomains(domainService.persistedDomains(report.getDomains()));

        return saveEntity(original);
    }


    /**
     * Deletes the report with the given ID
     * @param reportId the ID of the report to delete
     */
    @Transactional
    public boolean deleteReport(String reportId) {

        FmReport report = findByReportId(reportId);
        if (report != null) {
            remove(report);
            return true;
        }
        return false;
    }

}
