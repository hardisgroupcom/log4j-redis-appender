package com.hardis.log4j;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisAppenderTest {
	
	private Jedis redis;
	private static String key;	
	
	private static int nbThread = 1;
	private static int nbLogs = 1000;
	
	public static class LogThread extends Thread {
		
		private static final AtomicInteger count = new AtomicInteger();
		
		Logger log = Logger.getLogger("LogThread" + count.incrementAndGet());
		
		public void run() {
			try {
				for (long i = 0; i < 1000; i++) {
					log.trace("whatever " + i);
					Thread.sleep(10);
				}
			} catch (InterruptedException e) {}
		}
	}

	private static final Logger log = Logger.getLogger("LogMainThread");
	
	@BeforeClass
	public static void beforeClass() throws IOException{
		
		// Read the redis key		
		Properties prop = new Properties();
		InputStream input = null;

		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			input = loader.getResourceAsStream("log4j.properties");		
			prop.load(input);
			key = prop.getProperty("log4j.appender.redis.key");
		} finally {
			if (input != null) {
				input.close();
			}
		}
		
		// Remove the redis key
		JedisPool pool = null;
		Jedis jedis = null;
		try {
			pool = new JedisPool("localhost");
			jedis = pool.getResource();
			jedis.del(key);
		} finally {
			if (pool != null){
				pool.close();	
			}				
			if (jedis != null){
				jedis.close();	
			}					
		}
		
	}	
	
	@Before
	public void setUp() {		
		JedisPool pool = new JedisPool("localhost");
		redis = pool.getResource();
		// clear the redis list first
		redis.ltrim(key, 1, 0);
	}
	
	@After
	public void tearDown() {		
		if (redis != null){
			redis.close();
		}
	}	

	
	@Test
	public void test() throws Throwable {		
		for (int i = 0; i < RedisAppenderTest.nbThread; i++) {
			new RedisAppenderTest.LogThread().start();
		}

		for (long i = 0; i < RedisAppenderTest.nbLogs; i++) {
			log.debug("that's me " + i);
			Thread.sleep(100);
		}
		
		// list length check
		long len = redis.llen(key);
		assertEquals(RedisAppenderTest.nbThread*RedisAppenderTest.nbLogs, len);		
	}

}
