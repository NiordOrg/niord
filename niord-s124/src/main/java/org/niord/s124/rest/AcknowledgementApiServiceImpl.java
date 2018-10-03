package org.niord.s124.rest;

import org.niord.s124.rest.generated.api.AcknowledgementApiService;
import org.niord.s124.rest.generated.api.ApiResponseMessage;
import org.niord.s124.rest.generated.api.NotFoundException;
import org.niord.s124.rest.generated.model.AcknowledgementObject;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@RequestScoped
public class AcknowledgementApiServiceImpl implements AcknowledgementApiService {
      public Response acknowledgementPost(AcknowledgementObject acknowledgementObject,SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
}
