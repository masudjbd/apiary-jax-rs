package com.thed.apidocs;

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

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

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
       List<Resource> list = new ArrayList<Resource>();
         	for(Class type : sortedTypes){
 					if(type.getName().startsWith(packageName)) {
						try {
							list.add(getResourceMetadata(type));
						} catch (Exception e) {
							e.printStackTrace();
							System.out.println(e.getMessage());
						}
					}
         	}

        return list;
    }
    
    public File generateDocFile(List<Resource> list) {
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

	/**
	 * Get Resource Meta Data from Class/Type
	 * @param clazz
	 * @return
	 * @throws IOException
     */
	private Resource getResourceMetadata(Class clazz) throws IOException {
		Resource r = new Resource();
		Annotation[] ann = clazz.getAnnotations() ;
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

		try {
			for (Method m : clazz.getDeclaredMethods()) {
				getOperationMetadata(r, m);
			}
		}catch (Exception exception){
			exception.printStackTrace();
		}
		return r ;
	}

	/**
	 * Get Filtered Resource Name from Original Resource
	 * @param simpleName
	 * @return
     */
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

	/**
	 * Get Operation Meta Data from Resource and Method,
	 * If Method annotation is unavailable pick from resource.
	 * @param r
	 * @param m
	 * @throws IOException
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

	/**
	 * Get Request And Response Json Data from Method Annotation.
	 * @param r
	 * @param m
	 * @param op
	 * @param reqRes
	 * @return
     * @throws IOException
     */
	private List<String> getRequestResponse(Resource r, Method m, Operation op, String reqRes) throws IOException {
		List<String> list = new ArrayList<String>();
		ApiImplicitParams aps = m.getAnnotation(ApiImplicitParams.class);
		if (aps != null) {
			ApiImplicitParam[] ap = aps.value();
			list.add(getJsonData(ap, reqRes));
		}
		return list;
	}
	private String getJsonData(ApiImplicitParam[] aps, String jsonType) {
		String result = "";
		for(ApiImplicitParam ap: aps){
			if(ap.name().equals(jsonType)){
				result = ap.value().replaceAll("[\t\n\r]", "");
			}
		}
		return result;
	}

	/**
	 * Get URL Parameter from Resource, Method, Operation
	 * @param r
	 * @param op
     * @param m
     */
	private void getUrlParameter(Resource r, Operation op, Method m) {
		Annotation[][] pa = m.getParameterAnnotations() ;
		Class[] params = m.getParameterTypes() ;
		StringBuilder queryParamsPath = new StringBuilder();
		for (int i = 0; i < pa.length; i++) {
			Annotation[] eachParam = pa[i] ;
			QueryParam qpAnnotation = hasQueryParam(eachParam) ;
			if ( null != qpAnnotation) {
				if (op.getQueryParams() == null) {
					List<QueryParameter> queryParams = new ArrayList<QueryParameter>();
					op.setQueryParams(queryParams);
					
				}
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
	}

	/**
	 * Parse Alloweable Values
	 * @param names
	 * @param types
	 * @param descriptions
	 * @param allowableValues
     * @return
     */
	public int parseAllowableValues(List names, List types, List descriptions, String allowableValues) {
		String[] params = StringUtils.split(allowableValues, ",");
		if(params==null) return 0;
		for(String param : params) {
			names.add(StringUtils.substringBefore(param, ":"));
			types.add(StringUtils.substringBetween(param, ":", ":"));
			descriptions.add(StringUtils.substringAfterLast(param, ":"));
		}
		return params.length;
	}

	/**
	 * Get Api Alloweable Values
	 * @param paramAnnotaions
	 * @return
     */
	private String getApiAllowableValues(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof ApiParam) {
				return ((ApiParam) ax).allowableValues();
			}
		}
		return null ;
	}

	/**
	 * Get Api Required Value
	 * @param paramAnnotaions
	 * @return
     */
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

	/**
	 * Check Query Parameter
	 * @param paramAnnotaions
	 * @return
     */
	private QueryParam hasQueryParam(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof QueryParam) {
				return (QueryParam) ax ;
			}
		}
		return null ;
	}

	/**
	 * Check Path Parameter
	 * @param paramAnnotaions
	 * @return
     */
	private PathParam hasPathParam(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof PathParam) {
				return (PathParam) ax ;
			}
		}
		return null ;
	}

	/**
	 * Check Context Annotation
	 * @param paramAnnotaions
	 * @return
     */
	private Context hasContextAnnotation(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof Context) {
				return (Context) ax ;
			}
		}
		return null ;
	}

	/**
	 * Get ApiParam Description
	 * @param paramAnnotaions
	 * @return
     */
	private String getApiDescription(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof ApiParam) {
				return ((ApiParam) ax).value() ;
			}
		}
		return " " ;
	}


	/**
	 * Get Strip Slasses from duplicate
	 * @param s
	 * @return
     */
	private String supressDuplicateSlash(String s) {
		if(null != s) {
			return  s.replaceAll("//", "/");
		}else{
			return s ;
		}

	}

    /**
     * Generate Apiary Output File from Resource List
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
	//Setters, Getters
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