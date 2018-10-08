package org.niord.s124.rest;

import org.niord.s124.S124Service;
import org.niord.s124.rest.generated.api.ApiResponseMessage;
import org.niord.s124.rest.generated.api.S124ApiService;
import org.niord.s124.rest.generated.model.*;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static javax.ws.rs.core.Response.Status.OK;

@RequestScoped
public class S124ApiServiceImpl implements S124ApiService {

    @Inject
    private S124Service s124Service;

    public Response s124AccessNotificationPost(AccessNotificationRequestObject requestObject, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124AccessPost(RequestAccessRequestObject requestObject, SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }

    public Response s124Get(Integer id, Integer status, String wkt, SecurityContext securityContext) {
        if (id != null)
            return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "'id' query parameter not yet supported.")).build();
        if (status != null)
            return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "'status' query parameter not yet supported.")).build();
        if (wkt != null)
            return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "'wkt' query parameter not yet supported.")).build();

        String language = "en";

        List<String> gmls = s124Service.generateGML(language);

        GetMessageResponseObject responseObject = new GetMessageResponseObject();
        responseObject.setMessageId(UUID.randomUUID());
        responseObject.setTimestamp(OffsetDateTime.now());
        responseObject.getMessages().addAll(gmls);

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
