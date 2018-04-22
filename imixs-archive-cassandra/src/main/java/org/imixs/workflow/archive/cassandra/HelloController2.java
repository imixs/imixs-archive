package org.imixs.workflow.archive.cassandra;

import javax.inject.Inject;
import javax.mvc.Models;
import javax.mvc.annotation.Controller;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

@Controller
@Path("test")
public class HelloController2 {
 
  @Inject
  Models models;
 
  @GET
  public String sayHello(@QueryParam("name") String name) {
   
    return "welcome.jsf";
  }
}