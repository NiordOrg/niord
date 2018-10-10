package org.niord.s124.rest;

import org.niord.s124.S124Service;
import org.niord.s124.rest.generated.api.ApiResponseMessage;
import org.niord.s124.rest.generated.api.S124ApiService;
import org.niord.s124.rest.generated.model.*;
import org.slf4j.Logger;
import org.slf4j.MDC;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static javax.ws.rs.core.Response.Status.OK;

@RequestScoped
public class S124ApiServiceImpl implements S124ApiService {

    private Logger log;

    private S124Service s124Service;

    @Inject
    public S124ApiServiceImpl(Logger log, S124Service s124Service) {
        this.log = log;
        this.s124Service = s124Service;
    }

    public Response s124AccessNotificationPost(AccessNotificationRequestObject requestObject, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124AccessPost(RequestAccessRequestObject requestObject, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124Get(Integer id, Integer status, String wkt, SecurityContext securityContext) {
        final long startTime = System.nanoTime();

        if (status != null)
            return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "'status' query parameter not yet supported.")).build();
        if (wkt != null)
            return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "'wkt' query parameter not yet supported.")).build();

        final UUID requestId = UUID.randomUUID();
        final String language = "en";
        final GetMessageResponseObject responseObject = new GetMessageResponseObject();

        try {
            log.info("Now processing requestId {}", requestId);
            MDC.put("requestId", requestId.toString());

            String messageId = null;
            if (id != null)
                messageId = String.format("NW-%03d-17", id);  // TODO how to map integer to DMA NW shortId format NW-015-17?

            List<String> gmls = s124Service.generateGMLv2(messageId, language);
            log.info("Produced {} gmls", gmls.size());

            responseObject.setMessageId(requestId);
            responseObject.setTimestamp(OffsetDateTime.now());
            responseObject.getMessages().addAll(gmls);

            final long endTime = System.nanoTime();

            log.info("requestId {} processed in {} msecs", requestId, (endTime-startTime)/1e6);
        } finally {
            MDC.clear();
        }

        //return Response.status(OK).type(MediaType.TEXT_XML_TYPE).entity(responseObject.getMessages().get(0)).build();
        return Response.status(OK).entity(responseObject).build();
    }

    public Response s124ListGet(String wkt, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124Post(PostMessageRequestObject requestObject, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124SubscriptionCreatedNotificationPost(SubscriptionNotificationRequestObject requestObject, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124SubscriptionDelete(RemoveSubscriptionRequestObject requestObject, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124SubscriptionGet(SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124SubscriptionPost(RequestSubscriptionRequestObject requestObject, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124SubscriptionRemovedNotificationPost(SubscriptionNotificationRequestObject requestObject, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }
}
