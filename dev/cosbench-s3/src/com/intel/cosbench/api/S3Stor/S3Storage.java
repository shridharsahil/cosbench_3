package com.intel.cosbench.api.S3Stor;

import static com.intel.cosbench.client.S3Stor.S3Constants.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpStatus;

import com.amazonaws.*;
import com.amazonaws.auth.*;
import com.amazonaws.services.s3.*;
import com.amazonaws.services.s3.model.*;

import com.intel.cosbench.api.storage.*;
import com.intel.cosbench.api.context.*;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;

public class S3Storage extends NoneStorage {
	private int timeout;
	
    private String accessKey;
    private String secretKey;
    private String endpoint;
    
    private AmazonS3 client;

    @Override
    public void init(Config config, Logger logger) {
    	super.init(config, logger);
    	
    	timeout = config.getInt(CONN_TIMEOUT_KEY, CONN_TIMEOUT_DEFAULT);

    	parms.put(CONN_TIMEOUT_KEY, timeout);
    	
    	endpoint = config.get(ENDPOINT_KEY, ENDPOINT_DEFAULT);
        accessKey = config.get(AUTH_USERNAME_KEY, AUTH_USERNAME_DEFAULT);
        secretKey = config.get(AUTH_PASSWORD_KEY, AUTH_PASSWORD_DEFAULT);

        boolean pathStyleAccess = config.getBoolean(PATH_STYLE_ACCESS_KEY, PATH_STYLE_ACCESS_DEFAULT);
        
		String proxyHost = config.get(PROXY_HOST_KEY, "");
		String proxyPort = config.get(PROXY_PORT_KEY, "");
        
        parms.put(ENDPOINT_KEY, endpoint);
    	parms.put(AUTH_USERNAME_KEY, accessKey);
    	parms.put(AUTH_PASSWORD_KEY, secretKey);
    	parms.put(PATH_STYLE_ACCESS_KEY, pathStyleAccess);
    	parms.put(PROXY_HOST_KEY, proxyHost);
    	parms.put(PROXY_PORT_KEY, proxyPort);

        logger.debug("using storage config: {}", parms);
        
        ClientConfiguration clientConf = new ClientConfiguration();
        clientConf.setConnectionTimeout(timeout);
        clientConf.setSocketTimeout(timeout);
//        clientConf.setProtocol(Protocol.HTTP);
		if((!proxyHost.equals(""))&&(!proxyPort.equals(""))){
			clientConf.setProxyHost(proxyHost);
			clientConf.setProxyPort(Integer.parseInt(proxyPort));
		}
        
        AWSCredentials myCredentials = new BasicAWSCredentials(accessKey, secretKey);
        client = new AmazonS3Client(myCredentials, clientConf);
        client.setEndpoint(endpoint);
        client.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(pathStyleAccess));
        
        logger.debug("S3 client has been initialized");
    }
    
    @Override
    public void setAuthContext(AuthContext info) {
        super.setAuthContext(info);
//        try {
//        	client = (AmazonS3)info.get(S3CLIENT_KEY);
//            logger.debug("s3client=" + client);
//        } catch (Exception e) {
//            throw new StorageException(e);
//        }
    }

    @Override
    public void dispose() {
        super.dispose();
        client = null;
    }
    
    public String MD5(String md5) {
   	   try {
   	        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
   	        byte[] array = md.digest(md5.getBytes());
   	        StringBuffer sb = new StringBuffer();
   	        for (int i = 0; i < array.length; ++i) {
   	          sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1,3));
   	       }
   	        return sb.toString();
   	    } catch (java.security.NoSuchAlgorithmException e) {
   	    }
   	    return null;
   	}

	@Override
    public InputStream getObject(String container, String object, Config config) {
		
		String containerHash = MD5(container);
    	String objectHash = MD5(object);
    	container = containerHash;
    	object = objectHash;
    	
        super.getObject(container, object, config);
        InputStream stream;
        try {
            S3Object s3Obj = client.getObject(container, object);
            stream = s3Obj.getObjectContent();
            
        } catch (Exception e) {
            throw new StorageException(e);
        }
        return stream;
    }
	
	//this function below gets the metadata of an object or lists all the objects in a container
		//to invoke this, just specify "list" as an operation in the config xml file and it will use this function
			//if S3 storage is specified
	@Override
	public InputStream getList(String container, String object, Config config) {
		
		String containerHash = MD5(container);
    	String objectHash = MD5(object);
    	container = containerHash;
    	object = objectHash;
    	
		super.getList(container, object, config);
		InputStream metadata = null;
		try {
			
			if(object.isEmpty()){ //if the client is looking at a container, then it should get all the objects within it
				//in this case, the client is the container
				S3Object s3Obj = client.getObject(container, object);
	            metadata = s3Obj.getObjectContent();
			}
			else {//if the client is looking at a specific object within a container, then list the metadata of the specific object
				//in this case, the client is the object
				ObjectMetadata s3objectHead = client.getObjectMetadata(container, object);
				String meta_string = s3objectHead.toString();
				metadata = new ByteArrayInputStream(meta_string.getBytes(StandardCharsets.UTF_8));
				//return new ByteArrayInputStream(new byte[] {});
			}	
		} catch(Exception e) {
			throw new StorageException(e);
		}
		if (metadata != null) {
			logger.info("SUCCESSFULY got METADATA at /{}/{}", container, object);
		}
		else {
			logger.info("METADATA is NULL!!!!");
		}
		return metadata;
	}

    @Override
    public void createContainer(String container, Config config) {
    	
    	String containerHash = MD5(container);
    	container = containerHash;
    	
        super.createContainer(container, config);
        try {
        	if(!client.doesBucketExist(container)) {
	        	
	            client.createBucket(container);
        	}
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

	@Override
    public void createObject(String container, String object, InputStream data,
            long length, Config config) {
		
		String containerHash = MD5(container);
    	String objectHash = MD5(object);
    	container = containerHash;
    	object = objectHash;
    	
        super.createObject(container, object, data, length, config);
        try {
    		ObjectMetadata metadata = new ObjectMetadata();
    		metadata.setContentLength(length);
    		metadata.setContentType("application/octet-stream");
    		
        	client.putObject(container, object, data, metadata);
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteContainer(String container, Config config) {
    	
    	String containerHash = MD5(container);
    	container = containerHash;
    	
        super.deleteContainer(container, config);
        try {
        	if(client.doesBucketExist(container)) {
        		client.deleteBucket(container);
        	}
        } catch(AmazonS3Exception awse) {
        	if(awse.getStatusCode() != HttpStatus.SC_NOT_FOUND) {
        		throw new StorageException(awse);
        	}
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteObject(String container, String object, Config config) {
    	
    	String containerHash = MD5(container);
    	String objectHash = MD5(object);
    	container = containerHash;
    	object = objectHash;
    	
        super.deleteObject(container, object, config);
        try {
            client.deleteObject(container, object);
        } catch(AmazonS3Exception awse) {
        	if(awse.getStatusCode() != HttpStatus.SC_NOT_FOUND) {
        		throw new StorageException(awse);
        	}
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

}
