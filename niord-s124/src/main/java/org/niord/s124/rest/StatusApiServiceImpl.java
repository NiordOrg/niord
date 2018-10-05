package org.niord.s124.rest;

import org.niord.s124.rest.generated.api.ApiResponseMessage;
import org.niord.s124.rest.generated.api.StatusApiService;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@RequestScoped
public class StatusApiServiceImpl implements StatusApiService {
    public Response statusGet(SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }
}
