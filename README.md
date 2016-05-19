apiary-blueprint-generator
==========================

# APIARY Blueprint generator from JAX RS

## To user this you will have to define you'r api endpoint jax rs with Swaggar annotation as follows:

### Complete Annotation example -

    @ApiOperation(name = "api name", ...)
    @ApiImplicitparams({
            @ApiImplicitParam(name="request", value="JSON Object")
            @ApiImplicitParam(name="response", value="JSON Object")
        })
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateObject(@PathParam("id") Long objectId, @QueryParam("qid") Long objectQId) {
        ...
    }