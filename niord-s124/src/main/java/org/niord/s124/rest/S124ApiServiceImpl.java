package org.niord.s124.rest;

import org.niord.core.message.MessageService;
import org.niord.s124.S124Service;
import org.niord.s124.rest.generated.api.ApiResponseMessage;
import org.niord.s124.rest.generated.api.S124ApiService;
import org.niord.s124.rest.generated.model.*;
import org.slf4j.Logger;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static javax.ws.rs.core.Response.Status.OK;

@RequestScoped
public class S124ApiServiceImpl implements S124ApiService {

    private Logger log;
    private S124Service s124Service;
    private MessageService messageService;

    @Inject
    public S124ApiServiceImpl(Logger log, S124Service s124Service, MessageService messageService) {
        this.log = log;
        this.s124Service = s124Service;
        this.messageService = messageService;
    }

    public Response s124AccessNotificationPost(AccessNotificationRequestObject requestObject, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124AccessPost(RequestAccessRequestObject requestObject, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124Get(Integer id, Integer status, String wkt, SecurityContext securityContext) {
        final long startTime = System.nanoTime();
        final String language = "en";
        final GetMessageResponseObject responseObject = new GetMessageResponseObject();
        final UUID requestId = UUID.randomUUID();
        log.info("Now processing requestId {}", requestId);

        // ---

        if (status != null)
            return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "'status' query parameter not yet supported.")).build();

        if (Stream.of(id, status, wkt).filter(o -> o != null).count() > 1)
            return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Only one query parameter is allowed.")).build();

        // ---

        try {
            List<String> gmls = s124Service.generateGML(id, status, wkt, language);

            responseObject.setMessageId(requestId);
            responseObject.setTimestamp(OffsetDateTime.now());
            responseObject.getMessages().addAll(gmls);
        } catch (Throwable t) {
            String errorMessage  = String.format("[requestId %s] %s", requestId, t.getMessage());
            log.error(errorMessage, t);
            return Response.status(500).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, errorMessage)).build();
        } finally {
            final long endTime = System.nanoTime();
            log.info("requestId {} processed in {} msecs", requestId, (endTime-startTime)/1e6);
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
