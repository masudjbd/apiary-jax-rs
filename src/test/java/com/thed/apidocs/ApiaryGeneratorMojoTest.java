package com.thed.apidocs;

import java.io.File;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.thed.apidocs.*;
/**
 * Created by smangal on 5/22/14.
 */
public class ApiaryGeneratorMojoTest {

	private String BASE_PATH = "../zfj/zephyr-je/target/classes/";	// "src/test/resources/apidocs/"
	private String packageName="com.thed.zephyr.je.rest";

    @Before
    public void setup(){
        File file = new File("target/apiary.txt");
        if(file.exists()){
            file.delete();
        }
    }

    @Test
    public void testExecute() throws Exception {
        String fileName = "target/apiary.txt";
		File file = new File(fileName);
        System.out.println("apiary.txt file path--"+file.getAbsolutePath());
        Assert.assertFalse(file.exists());
        ApiaryGeneratorMojo mojo = new ApiaryGeneratorMojo(packageName,BASE_PATH,fileName);
        mojo.execute();
        Assert.assertTrue(file.exists());
    }

//    @Test
//    public void testExecuteTwoStep() throws Exception {
//    	String fileName = "target/apiary.txt";
//		File file = new File(fileName);
//    	System.out.println("apiary.txt file path--"+file.getAbsolutePath());
//    	Assert.assertFalse(file.exists());
//    	ApiaryGeneratorMojo mojo = new ApiaryGeneratorMojo(packageName,BASE_PATH,fileName);
//    	List<Resource> resourceList = mojo.generateResourceList();
//        //All resources
//      System.out.println("All resources");
//      for (Resource resource : resourceList) {
//			System.out.println(resource.getName());
//		}
//
//    	File generateDocFile = mojo.generateDocFile(resourceList);
//    	System.out.println(generateDocFile);
//    	Assert.assertTrue(file.exists());
//    }

}
