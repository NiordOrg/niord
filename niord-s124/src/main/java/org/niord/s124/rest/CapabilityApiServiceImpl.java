package org.niord.s124.rest;

import org.niord.s124.rest.generated.api.ApiResponseMessage;
import org.niord.s124.rest.generated.api.CapabilityApiService;
import org.niord.s124.rest.generated.api.NotFoundException;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@RequestScoped
public class CapabilityApiServiceImpl implements CapabilityApiService {
      public Response capabilityGet(SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
}
