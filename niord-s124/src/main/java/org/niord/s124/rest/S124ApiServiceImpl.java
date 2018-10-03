package org.niord.s124.rest;

import org.niord.s124.rest.generated.api.ApiResponseMessage;
import org.niord.s124.rest.generated.api.NotFoundException;
import org.niord.s124.rest.generated.api.S124ApiService;
import org.niord.s124.rest.generated.model.*;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@RequestScoped
public class S124ApiServiceImpl implements S124ApiService {
      public Response s124AccessNotificationPost(AccessNotificationRequestObject requestObject,SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
      public Response s124AccessPost(RequestAccessRequestObject requestObject,SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
      public Response s124Get(Integer id,Integer status,String wkt,SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
      public Response s124ListGet(String wkt,SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
      public Response s124Post(PostMessageRequestObject requestObject,SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
      public Response s124SubscriptionCreatedNotificationPost(SubscriptionNotificationRequestObject requestObject,SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
      public Response s124SubscriptionDelete(RemoveSubscriptionRequestObject requestObject,SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
      public Response s124SubscriptionGet(SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
      public Response s124SubscriptionPost(RequestSubscriptionRequestObject requestObject,SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
      public Response s124SubscriptionRemovedNotificationPost(SubscriptionNotificationRequestObject requestObject,SecurityContext securityContext)
      throws NotFoundException {
      // do some new magic!
      return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "new magic!")).build();
  }
}
