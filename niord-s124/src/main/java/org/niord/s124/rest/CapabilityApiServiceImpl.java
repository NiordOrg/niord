package org.niord.s124.rest;

import org.niord.s124.rest.generated.api.ApiResponseMessage;
import org.niord.s124.rest.generated.api.CapabilityApiService;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@RequestScoped
public class CapabilityApiServiceImpl implements CapabilityApiService {
    public Response capabilityGet(SecurityContext securityContext) {
        return Response.status(501).entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Not yet implemented")).build();
    }
}
