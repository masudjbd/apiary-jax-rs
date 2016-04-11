package com.thed.apidocs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import io.swagger.annotations.*;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.google.common.collect.Ordering;
//import com.wordnik.swagger.annotations.Api;
//import com.wordnik.swagger.annotations.ApiOperation;
//import com.wordnik.swagger.annotations.ApiParam;

/**
 * Generates apiary documentation
 *
 */
@Mojo(requiresDependencyResolution = ResolutionScope.RUNTIME, name = "generateApiDocs")
 
public class ApiaryGeneratorMojo extends AbstractMojo {
	
    Logger logger = Logger.getLogger(ApiaryGeneratorMojo.class.getName());

	private String vmFile = "apiary.vm";

	@Parameter
    private String packageName="com.thed.zephyr.je.rest";

	@Parameter
	private String basePath = "target/classes/";	// "src/test/resources/apidocs/"
    /**
     * Location of the file.
     * @parameter expression="${project.build.directory}"
     * @required
     */
    @Parameter
    private String outputFileName="target/apiary.txt";

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	private MavenProject project;

    public ApiaryGeneratorMojo() {
    	// default
    }

    public ApiaryGeneratorMojo(String packageName, String basePath ,String outputFile) {
    	this.packageName = packageName;
    	this.basePath = basePath;
    	this.outputFileName = outputFile;
    }
    

    public void execute() throws MojoExecutionException, MojoFailureException {
    	List<Resource> resourceList = generateResourceList();
    	File file = generateDocFile(resourceList);
    }
    
    public List<Resource> generateResourceList(){

    	Collection<Class<?>> sortedTypes = getResourceClasses();
		/*try {
			sortedTypes = new Util().getClasses(packageName);
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}//getResourceClasses();
*/        List<Resource> list = new ArrayList<Resource>();
         	for(Class type : sortedTypes){
//				System.out.println(type.getName().startsWith(packageName));
 					if(type.getName().startsWith(packageName)) {
//						System.out.println(type);
						try {
							list.add(getResourceMetadata(type));
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
         	}

        return list;
    }
    
    public File generateDocFile(List<Resource> list) {
        //All resources
//        System.out.println("All resources");
//        for (Resource resource : list) {
//			System.out.println(resource.getName());
//		}
        File file = generateDocs(list);
        return file;
    }
    

	private List<Class<?>> getResourceClasses(){

		List<String> classes = getClassList(basePath.concat(packageName.replace(".","/")));
		List<Class<?>> sortedTypes = new ArrayList<Class<?>>();

		File file = new File(basePath);
		URL[] cp = new URL[0];
		try {
			cp = new URL[]{file.toURI().toURL()};
			URLClassLoader urlClassLoader = new URLClassLoader(cp);
			for(String s: classes){
				String fileName  = packageName.concat("."+s.replace(".class",""));
				Class<?> loadedClass =  urlClassLoader.loadClass(fileName);
				sortedTypes.add(loadedClass);
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		return sortedTypes;
	}

	/**
	 * Get Resource Class List
	 * @param basePath
	 * @return
     */
	private List<String> getClassList(String basePath) {
		List<String> sl = new ArrayList<>();

			File root = new File( basePath );
			File[] list = root.listFiles();

			if (list == null) return sl;

			for ( File f : list ) {
				if ( f.isDirectory() ) {
					getClassList( f.getAbsolutePath() );
				}
				else {
					sl.add(f.getName().toString());
				}
			}
		return sl;
	}


	/* Sample annotations -
	@Service("zephyrTestcase")
	@Path("/testcase")
	@Produces({MediaType.APPLICATION_JSON , MediaType.APPLICATION_XML})
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Api(value = "/testcase", description = "get testcase by id and criteria")
	*/
	private Resource getResourceMetadata(Class clazz) throws IOException {
		Resource r = new Resource();

		Annotation[] ann = clazz.getAnnotations() ;

//		Service service = (Service)clazz.getAnnotation(Service.class);
//		Service s = (Service) service ;
		Path path = (Path) clazz.getAnnotation(Path.class);
		Produces produces = (Produces) clazz.getAnnotation(Produces.class);
		Consumes consumes = (Consumes) clazz.getAnnotation(Consumes.class);
		Api api = (Api) clazz.getAnnotation(Api.class);
		String resourceName = filteredResourceName(clazz.getSimpleName());

		if(null != api && api.value() != null) {
			r.setName(resourceName);
			r.setGroupNotes(api.description());
		}
		if(null != path && path.value() != null) {
			r.setPath(supressDuplicateSlash(path.value()));
		}
		if(null != produces && produces.value() != null) {
			r.setProduces(StringUtils.join(produces.value(), " "));
		}
		if(null != consumes && consumes.value() != null) {
			r.setConsumes(StringUtils.join(consumes.value(), " "));
		}

		for (Method m: clazz.getDeclaredMethods())  {
			getOperationMetadata(r, m);
//			System.out.println("method name--"+m.getName());
		}
		return r ;
	}

	private String filteredResourceName(String simpleName) {
		if(simpleName.contains("Schedule")){
			simpleName = simpleName.replace("Schedule","Execution");
		}
		return simpleName;
	}

	private String extractResourcePrefix(String s) {
		String name = s.replace("Resource","");
		return name ;
	}

	/*
	@GET
	@Path("/{id}")
	@ApiOperation(value = "Get testcase by ID", //notes = "Add extra notes here",
					responseClass = "com.thed.rpc.bean.RemoteRepositoryTreeTestcase")
	@ApiErrors(value = { @ApiError(code = 400, reason = "Invalid ID supplied"),
							@ApiError(code = 404, reason = "Testcase not found") })
	*/
	private void getOperationMetadata(Resource r, Method m) throws IOException {
		Path path = (Path) m.getAnnotation(Path.class);

			Operation op = new Operation();
			if (m.getAnnotation(GET.class) != null) {
				op.setRequestType("GET");
			} else if (m.getAnnotation(POST.class) != null) {
				op.setRequestType("POST");
			} else if (m.getAnnotation(PUT.class) != null) {
				op.setRequestType("PUT");
			} else if (m.getAnnotation(DELETE.class) != null) {
				op.setRequestType("DELETE");
			}
			if(null != path) {
				op.setPath(supressDuplicateSlash(r.getPath() + "/" + path.value()));
			}else{
				op.setPath(supressDuplicateSlash(r.getPath()));
			}

			ApiOperation api = (ApiOperation) m.getAnnotation(ApiOperation.class);
			if (api != null) {
					op.setName(api.value());
					if(StringUtils.isNotBlank(api.value())){
						op.setSummary(api.value());
					}
				if(StringUtils.isNotBlank(api.notes())) {
					op.setDescription(api.notes());
					}else{
					op.setDescription(api.value());
				}




			// use Resource's annotation if required
			if (m.getAnnotation(Produces.class) != null) {
				Produces produces = (Produces) m.getAnnotation(Produces.class);
				op.setProduces(StringUtils.join(produces.value(), " "));
			} else {
				if(r.getProduces() != null) {
					op.setProduces(r.getProduces());
				}else{
					op.setProduces("application/json");
				};
			}

			if (m.getAnnotation(Consumes.class) != null) {
				Consumes consumes = (Consumes) m.getAnnotation(Consumes.class);
				op.setConsumes(StringUtils.join(consumes.value(), " "));
			} else {
				if(r.getConsumes() != null) {
					op.setConsumes(r.getConsumes());
				}else{
					op.setConsumes("application/json");
				}
			}

			if (r.getOperations() == null) {
				r.setOperations(new ArrayList<Operation>());
			}
			r.getOperations().add(op);
			op.setJsonRequest(getRequestResponse(r,m,op,new String("request")));
			op.setJsonResponse(getRequestResponse(r,m,op,new String("response")));
			op.setResponseCode("200");
			getUrlParameter(r, op, m);
			}
	}

	// Get the requst/response from a text file on a particular location src/main/resources/apidocs/<resource>/<method>/<request>/response.json e.g. src/main/resources/apidocs/attachment/getAttachment/GET/response.json
	private List<String> getRequestResponse(Resource r, Method m, Operation op, String reqRes) throws IOException {
//		String file = null;
//		FileInputStream fstream;

		
//		if (exampleString.equals("request")) {
//			if (m.getName().equals("getManifest")) {
//				file =  basePath + "manifest" + "/" +  m.getName() + "/" + op.getRequestType() +"/" + "request.json";
//			} else {
//				file = basePath + r.getPath() + "/" +  m.getName() + "/" + op.getRequestType() +"/" + "request.json";
//			}
//		}else {
//			if (m.getName().equals("getManifest")) {
//				file = basePath + "manifest" + "/" +  m.getName() + "/" + op.getRequestType() +"/" + "response.json";
//			} else {
//				file = basePath + r.getPath() + "/" +  m.getName() + "/" + op.getRequestType() +"/" + "response.json";
//			}
//		}
		List<String> list = new ArrayList<String>();

//		ApiOperation apo = m.getAnnotation(ApiOperation.class);
		ApiImplicitParams aps = m.getAnnotation(ApiImplicitParams.class);
		if (aps != null) {
			ApiImplicitParam[] ap = aps.value();

			//set request dummy json
			list.add(getJsonData(ap, reqRes));
//			op.setJsonRequest(jreq);
//
//			//set response dummy json
//			reqRes.add(getJsonData(ap, reqRes));
//			op.setJsonResponse(jres);
		}

//		File fileReal = new File(file);
//		if(!fileReal.exists()) {
//			return list;
//		}
//		fstream = new FileInputStream(file);
//		BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
//		String line;
//		while ((line = br.readLine()) != null) {
//			list.add(line);
//		}
//		br.close();
//		fstream.close();
		return list;
	}
	private String getJsonData(ApiImplicitParam[] aps, String jsonType) {
		String result = "";
		for(ApiImplicitParam ap: aps){
			if(ap.name().equals(jsonType)){
				result = ap.value();
			}
		}
		return result;
	}

	private void getUrlParameter(Resource r, Operation op, Method m) {
		Annotation[][] pa = m.getParameterAnnotations() ;
//		System.out.println(pa);

		/* E.g. AttachmentResource  */
		/*
		public List<RemoteAttachment> getAttachments(
				@ApiParam(value = "Id of entity which need to be fetched", required = true)
				@QueryParam("entityid") String entityId,
				@ApiParam(value = "Entity name, possible values : testcase, requirement, testStepResult, releaseTestSchedule")
				@QueryParam("entityname") String entityName,
				@ApiParam(value = "Token stored in cookie, fetched automatically if available", required = false)
				@CookieParam("token") Cookie tokenFromCookie) throws ZephyrServiceException;
		*/
		Class[] params = m.getParameterTypes() ;
		
		StringBuilder queryParamsPath = new StringBuilder();
//		TypeVariable<Method>[] tvm = m.getTypeParameters();
		for (int i = 0; i < pa.length; i++) {
			Annotation[] eachParam = pa[i] ;
			// ignore ApiParam or PathParam or CookieParam ignore
			QueryParam qpAnnotation = hasQueryParam(eachParam) ;
			
			if ( null != qpAnnotation) {

				if (op.getQueryParams() == null) {
					List<QueryParameter> queryParams = new ArrayList<QueryParameter>();
					op.setQueryParams(queryParams);
					
				}
//				System.out.println(qpAnnotation.value());
				QueryParameter qParam = new QueryParameter();
				qParam.setName(qpAnnotation.value());
				qParam.setType(params[i].getSimpleName());
				qParam.setDescription(qpAnnotation.value()+" of "+ extractResourcePrefix(r.getName()));
				qParam.setIsRequired(getApiRequiredValue(eachParam));
				queryParamsPath.append(qpAnnotation.value() + ",");
				op.getQueryParams().add(qParam);
			}
			
			PathParam pathParamAnno = hasPathParam(eachParam) ;
			
			if (null != pathParamAnno) {
				PathParameter pathParam = new PathParameter();
				pathParam.setName(pathParamAnno.value());
				pathParam.setType(params[i].getSimpleName());
				pathParam.setDescription(pathParamAnno.value()+" of "+ extractResourcePrefix(r.getName()));
				pathParam.setIsRequired("required");
				pathParam.setValue(pathParamAnno.value());
				op.addPathParam(pathParam);
			}
			
			
			Context contextAnnotation = hasContextAnnotation(eachParam) ;
			
			if (contextAnnotation != null) {
				List<String> names = new ArrayList<String>();
				List<String> types = new ArrayList<String>();
				List<String> descriptions = new ArrayList<String>();
				String allowableValues = getApiAllowableValues(eachParam);
				int lengthOfQP = parseAllowableValues(names, types, descriptions, allowableValues);
				if (op.getQueryParams() == null) {
					List<QueryParameter> queryParams = new ArrayList<QueryParameter>();
					op.setQueryParams(queryParams);
					
				}
				for (int j=0; j<lengthOfQP; j++) {
					QueryParameter qParam = new QueryParameter();
					qParam.setName(names.get(j));
					qParam.setType(types.get(j));
					qParam.setDescription(names.get(j)+" of "+ extractResourcePrefix(r.getName()));
					qParam.setIsRequired(getApiRequiredValue(eachParam));
					queryParamsPath.append(names.get(j) + ",");
					op.getQueryParams().add(qParam);
				}
				
			}
			
		}
		if (queryParamsPath.length()>0) {
			if (op.getPath().endsWith("/")) {
				int index = op.getPath().lastIndexOf("/");
				op.setPath(op.getPath().substring(0,index));
			}
			String path = "{?" + queryParamsPath.deleteCharAt(queryParamsPath.lastIndexOf(","))+"}";
			op.setPath(op.getPath() + path);
		}
//		System.out.println(op.getPath());
		
		
	}
	
	public int parseAllowableValues(List names, List types, List descriptions, String allowableValues) {
		String[] params = StringUtils.split(allowableValues, ",");
		if(params==null) return 0;
//		allowableValues = "id:number:Id of cycle, name:String: Name of cycle, build:String:Build of cycle, environment:String:Environment of cycle, startDate:Date:Start date of cycle, endDate:Date:End date of cycle, releaseId:Number:Release id of cycle"
		for(String param : params) {
			names.add(StringUtils.substringBefore(param, ":"));
			types.add(StringUtils.substringBetween(param, ":", ":"));
			descriptions.add(StringUtils.substringAfterLast(param, ":"));
		}
		return params.length;
	}
	
	private String getApiValue(Annotation[] eachParam) {
		// TODO Auto-generated method stub
		return null;
	}

	private String getApiAllowableValues(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof ApiParam) {
				return ((ApiParam) ax).allowableValues();
			}
		}
		return null ;
	}
	

	private String getApiRequiredValue(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof ApiParam) {
				if (((ApiParam) ax).required()) {
					return "required";
				}
			}
		}
		return "required";
	}

	private QueryParam hasQueryParam(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof QueryParam) {
				return (QueryParam) ax ;
			}
		}
		return null ;
	}
	private PathParam hasPathParam(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof PathParam) {
				return (PathParam) ax ;
			}
		}
		return null ;
	}
	
	private Context hasContextAnnotation(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof Context) {
				return (Context) ax ;
			}
		}
		return null ;
	}
	
	private String getApiDescription(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof ApiParam) {
				return ((ApiParam) ax).value() ;
			}
		}
		return " " ;
	}

	
	private String supressDuplicateSlash(String s) {
		if(null != s) {
			return  s.replaceAll("//", "/");
		}else{
			return s ;
		}

	}
	

    /**
     *
     * @param resources
     * @return 
     */
	private File generateDocs(List<Resource> resources) {
		try {
			Velocity.init();
		} catch (Exception e) {
			e.printStackTrace();
		}
		VelocityContext context = new VelocityContext();
		context.put("name", new String("Velocity"));
		Template template = null;
		
		context.put("resources", resources);
		context.put("DOUBLE_HASH", "##");
		context.put("TRIPLE_HASH", "###");
        PrintWriter pw = null;
		try {
            VelocityEngine ve = new VelocityEngine();
            ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
            ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
			template = ve.getTemplate(vmFile);
			StringWriter sw = new StringWriter();
			template.merge(context, sw);
			File file = new File(outputFileName);
			pw = new PrintWriter(file);
            pw.write(sw.toString());
            pw.flush();
            logger.fine("Log file is generated " + outputFileName);
            return file;
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
            pw.close();
        }
		return null;
	}	
	
	 public String getPackageName() {
			return packageName;
		}

		public void setPackageName(String packageName) {
			this.packageName = packageName;
		}

		public String getVmFile() {
			return vmFile;
		}

		public void setVmFile(String vmFile) {
			this.vmFile = vmFile;
		}

		public String getOutputFileName() {
			return outputFileName;
		}

		public void setOutputFileName(String outputFileName) {
			this.outputFileName = outputFileName;
		}

}