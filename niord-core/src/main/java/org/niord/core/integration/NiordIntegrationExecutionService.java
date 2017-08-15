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

package org.niord.core.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.niord.core.area.Area;
import org.niord.core.area.AreaService;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.core.chart.Chart;
import org.niord.core.chart.ChartService;
import org.niord.core.geojson.FeatureService;
import org.niord.core.integration.vo.MessageSeriesMappingVo;
import org.niord.core.integration.vo.NiordIntegrationVo;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageSeriesService;
import org.niord.core.message.MessageService;
import org.niord.core.service.BaseService;
import org.niord.core.util.WebUtils;
import org.niord.model.message.MessageVo;
import org.niord.model.message.Status;
import org.slf4j.Logger;

import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handles the actual Niord integration execution
 */
@Stateless
@SuppressWarnings("unused")
public class NiordIntegrationExecutionService extends BaseService {

    public static final int TIMEOUT = 5; // 5 seconds

    @Inject
    private Logger log;

    @Inject
    MessageSeriesService messageSeriesService;

    @Inject
    MessageService messageService;

    @Inject
    AreaService areaService;

    @Inject
    CategoryService categoryService;

    @Inject
    ChartService chartService;

    @Inject
    FeatureService featureService;

    /**
     * Processes the given Niord Integration
     */
    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void processNiordIntegration(NiordIntegrationVo integration) {

        // Check that proper message series have been defined
        if (!integration.isActive() || integration.getMessageSeriesMappings().isEmpty()) {
            return;
        }

        try {
            // Fetch message for all source message series
            List<MessageVo> messages = fetchMessages(integration);

            Set<String> targetSeriesIds = integration.getMessageSeriesMappings().stream()
                    .map(MessageSeriesMappingVo::getTargetSeriesId)
                    .collect(Collectors.toSet());

            // Handle target message series one by one
            for (String targetSeriesId : targetSeriesIds) {

                MessageSeries targetSeries = messageSeriesService.findBySeriesId(targetSeriesId);
                if (targetSeries == null) {
                    continue;
                }

                // Get the source message series IDs that are mapped to the target message series
                // Typically, there will only be one, but there may actually be more
                Set<String> sourceSeriesIds = integration.getMessageSeriesMappings().stream()
                        .filter(ms -> targetSeriesId.equals(ms.getTargetSeriesId()))
                        .map(MessageSeriesMappingVo::getSourceSeriesId)
                        .collect(Collectors.toSet());

                // Get all source message whose message series ids should be mapped to the target message series
                List<MessageVo> importMessages = messages.stream()
                        .filter(m -> sourceSeriesIds.contains(m.getMessageSeries().getSeriesId()))
                        .collect(Collectors.toList());
                Map<String, MessageVo> importMessageMap = importMessages.stream()
                        .collect(Collectors.toMap(MessageVo::getId, Function.identity()));

                // Get our own messages for the target message series
                List<Message> ownMessages = fetchOwnMessages(targetSeriesId);
                Map<String, Message> ownMessageMap = ownMessages.stream()
                        .collect(Collectors.toMap(Message::getLegacyId, Function.identity()));

                // Determine which of our current messages to cancel and which of the fetched messages to import
                List<Message> cancelMessages = ownMessages.stream()
                        .filter(m -> !importMessageMap.containsKey(m.getLegacyId()))
                        .collect(Collectors.toList());
                List<MessageVo> createMessages = importMessages.stream()
                        .filter(m -> !ownMessageMap.containsKey(m.getId()))
                        .collect(Collectors.toList());
                List<MessageVo> updateMessages = importMessages.stream()
                        .filter(m -> ownMessageMap.containsKey(m.getId()))
                        .filter(m -> m.getUpdated().after(ownMessageMap.get(m.getId()).getUpdated()))
                        .collect(Collectors.toList());

                // Cancel own message not found in the fetched message list
                for (Message msg : cancelMessages) {
                    messageService.updateStatus(msg.getUid(), Status.CANCELLED);
                }

                // Create fetched messages not yet imported
                for (MessageVo msg : createMessages) {
                    importMessage(msg, targetSeries, integration);
                }

                // Update existing message that have been updated after import
                for (MessageVo msg : updateMessages) {
                    Message message = ownMessageMap.get(msg.getId());
                    messageService.updateStatus(message.getUid(), Status.CANCELLED);
                    importMessage(msg, targetSeries, integration);
                }
            }

        } catch (Exception e) {
            log.error("Error fetching messages from integration " + integration.getId(), e);
        }
    }


    /**
     * Fetches published messages from this Niord server for the given message series.
     * The messages should also have a defined legacyId to partake in the process.
     *
     * @param seriesId the messages series
     * @return published messages from this Niord server for the given message series
     */
    List<Message> fetchOwnMessages(String seriesId) {
        MessageSearchParams params = new MessageSearchParams()
                .seriesIds(Collections.singleton(seriesId))
                .statuses(Status.PUBLISHED);
        params.maxSize(10000);

        return messageService.search(params)
                .getData().stream()
                .filter(m -> StringUtils.isNotBlank(m.getLegacyId()))
                .collect(Collectors.toList());
    }



    /** Imports the message and assigns the given message series */
    protected void importMessage(MessageVo msg, MessageSeries messageSeries, NiordIntegrationVo integration) throws Exception {
        try {
            Message message = new Message(msg);
            message.setMessageSeries(messageSeries);

            message.setVersion(0);
            message.setUid(null);
            message.setLegacyId(msg.getId());

            if (integration.isAssignNewUids()) {
                message.assignNewUid();
            }

            // Ensure that the main type and type adheres to the message series
            message.setMainType(messageSeries.getMainType());
            if (!messageSeries.getTypes().isEmpty() && !messageSeries.getTypes().contains(message.getType())) {
                message.setType(messageSeries.getTypes().iterator().next());
            } else if (message.getType().getMainType() != message.getMainType()) {
                message.setType(message.getMainType().anyType());
            }

            if (message.getPublishDateFrom() == null) {
                // Published messages should have a published from-date
                message.setPublishDateFrom(new Date());
                message.checkUpdateYear();
            }

            // Make sure areas are resolved and/or created
            List<Area> areas = message.getAreas().stream()
                    .map(a -> areaService.importArea(a, integration.isCreateBaseData(), false))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            message.setAreas(areas);

            // Make sure categories are resolved and/or created
            List<Category> categories = message.getCategories().stream()
                    .map(c -> categoryService.importCategory(c, integration.isCreateBaseData(), false))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            message.setCategories(categories);

            // Make sure charts are resolved and/or created
            List<Chart> charts = message.getCharts().stream()
                    .map(c -> chartService.importChart(c, integration.isCreateBaseData(), false))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            message.setCharts(charts);

            // Reset all geometry IDs
            message.getParts().stream()
                    .filter(p -> p.getGeometry() != null)
                    .forEach(p -> {
                        p.getGeometry().setUid(null);
                        featureService.assignNewFeatureUids(p.getGeometry());
                    });

            // Reset all attachment IDs
            message.getAttachments().forEach(att -> att.setId(null));

            messageService.createMessage(message);

            log.info("Imported message " + message);

        } catch (Exception e) {
            log.error("Failed importing message: " + e, e);
            throw e;
        }
    }


    /**
     * Fetches the messages defined by the Niord integration point
     * @param integration the Niord integration point to fetch message from
     * @return the messages fetched from the Niord integration point
     */
    public List<MessageVo> fetchMessages(NiordIntegrationVo integration) throws Exception {

        long t0 = System.currentTimeMillis();

        // Construct the URL to fetch messages from
        String params = integration.getMessageSeriesMappings().stream()
                .map(m -> "messageSeries=" + WebUtils.encodeURIComponent(m.getSourceSeriesId()))
                .collect(Collectors.joining("&"));

        String url = integration.getUrl() + "/rest/public/v1/messages?" + params;

        // See https://stackoverflow.com/questions/19517538/ignoring-ssl-certificate-in-apache-httpclient-4-3
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, (chain, authType) -> true);

        SSLConnectionSocketFactory sslSF = new SSLConnectionSocketFactory(builder.build(),
                SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT * 1000)
                .setConnectionRequestTimeout(TIMEOUT * 1000)
                .setSocketTimeout(TIMEOUT * 1000).build();

        CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .setSSLSocketFactory(sslSF)
                .setHostnameVerifier(new AllowAllHostnameVerifier())
                .build();


        try (CloseableHttpResponse response = client.execute(new HttpGet(url))) {
            int status = response.getStatusLine().getStatusCode();
            if (status < 200 || status > 299) {
                try {
                    response.getEntity().getContent().close();
                } catch (Exception ignored) {
                }
                throw new Exception("Unable to execute request " + url + ", status = " + status);
            }

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                throw new Exception("No response received from URL " + url);
            }


            try (InputStream is = entity.getContent()) {
                List<MessageVo> result = new ObjectMapper().readValue(is, new TypeReference<List<MessageVo>>(){});
                log.info("Fetching " + result.size() + " messages from URL " + url + " in " +
                        (System.currentTimeMillis() - t0) + " ms");
                return result;
            }
        }
    }

}
