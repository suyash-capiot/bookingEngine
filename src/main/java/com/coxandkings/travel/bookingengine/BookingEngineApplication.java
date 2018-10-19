package com.coxandkings.travel.bookingengine;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

import com.coxandkings.travel.bookingengine.config.ConfigType;
import com.coxandkings.travel.bookingengine.config.LoadConfig;
import com.coxandkings.travel.bookingengine.config.MDMConfig;
import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ThreadPoolConfig;
import com.coxandkings.travel.bookingengine.utils.TrackingContextPatternConverter;

@SpringBootApplication
@ComponentScan
public class BookingEngineApplication extends SpringBootServletInitializer {
	
	private static final String CLASS_EXTENSION = ".class";
	private static final int CLASS_EXTENSION_LENGTH = CLASS_EXTENSION.length();
	private static final String CONFIG_PACKAGE = "config";
	private static final String FILE_SEPARATOR = System.getProperty("file.separator", "/");
	
	private static class ConfigLoader {
		private ConfigType configType;
		private Method configLoaderMethod;
		
		private ConfigLoader(ConfigType cfgType, Method cfgLdrMthd) {
			configType = cfgType;
			configLoaderMethod = cfgLdrMthd;
		}
	}

	/**
	 * Acts as a PreProcessor for the SpringBoot bookingengine Application Adds a
	 * Key onto the redis cache - Try fetching it on the server command prompt. Add
	 * anything in this function to make it work like a Preprocessor for the
	 * application.
	 * 
	 * @throws Exception
	 */
	@PostConstruct
	public void init() throws Exception {
		PluginManager.addPackage(TrackingContextPatternConverter.class.getPackage().getName());
		Logger logger = LogManager.getLogger(BookingEngineApplication.class);
		
		long startTime = System.currentTimeMillis();
		List<ConfigLoader> configLoaders = introspectAndFetchConfigLoaders();
		logger.trace("Retrieved configuration loader classes from classpath in {}ms", (System.currentTimeMillis() - startTime));

		List<ConfigLoader> infraConfigLoaders = new ArrayList<ConfigLoader>();
		List<ConfigLoader> productConfigLoaders = new ArrayList<ConfigLoader>();
		for (ConfigLoader configLoader : configLoaders) {
			Method loadConfigMethod = configLoader.configLoaderMethod;
			if (Modifier.isStatic(loadConfigMethod.getModifiers()) == false) {
				logger.info("Method {}.{} annotated by @{} must be static. This configuration loader will be skipped.\n", loadConfigMethod.getDeclaringClass().getName(), loadConfigMethod.getName(), LoadConfig.class.getSimpleName());
				continue;
			}

			if (loadConfigMethod.getParameterCount() > 0) {
				logger.info("Method {}.{} annotated by @{} must be without parameters. This configuration loader will be skipped.\n", loadConfigMethod.getDeclaringClass().getName(), loadConfigMethod.getName(), LoadConfig.class.getSimpleName());
				continue;
			}

			if (configLoader.configType == ConfigType.COMMON) {
				infraConfigLoaders.add(configLoader);
				continue;
			}
			
			if (configLoader.configType == ConfigType.PRODUCT) {
				productConfigLoaders.add(configLoader);
				continue;
			}
		}
		
		for (ConfigLoader infraConfigLoader : infraConfigLoaders) {
			try {
				infraConfigLoader.configLoaderMethod.invoke(null,  new Object[0]);
				logger.trace("Configuration {} loaded successfully\n", infraConfigLoader.configLoaderMethod.getDeclaringClass().getName());
			}
			catch (Exception x) {
				logger.warn(String.format("Loading of configuration %s unsuccessful\n", infraConfigLoader.configLoaderMethod.getDeclaringClass().getName()), x);
			}
		}
		
		for (ConfigLoader productConfigLoader : productConfigLoaders) {
			try {
				productConfigLoader.configLoaderMethod.invoke(null,  new Object[0]);
				logger.trace("Configuration {} loaded successfully\n", productConfigLoader.configLoaderMethod.getDeclaringClass().getName());
			}
			catch (Exception x) {
				logger.warn(String.format("Loading of configuration %s unsuccessful\n", productConfigLoader.configLoaderMethod.getDeclaringClass().getName()), x);
			}
		}

	}

	public static void main(String[] args) {
		try {
			SpringApplication.run(BookingEngineApplication.class, args);
		} catch (Exception e) {
			// Something is really Fishy !
			e.printStackTrace();
		}
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(BookingEngineApplication.class);
	}

	@PreDestroy
	public void shutdown() {
		System.out.println("Shutting down application");
		RedisConfig.unloadConfig();
		MDMConfig.unloadConfig();
		ThreadPoolConfig.unloadConfig();
	}
	
	private static List<ConfigLoader> introspectAndFetchConfigLoaders() {
		List<ConfigLoader> configLoadersList = new ArrayList<ConfigLoader>();
		String pkgName = String.format("%s.%s", BookingEngineApplication.class.getPackage().getName(), CONFIG_PACKAGE);
		try {
			URLClassLoader currentClassLoader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
			URL[] cpURLs = currentClassLoader.getURLs();
			for (URL cpURL : cpURLs) {
				File cpCompFile = new File(cpURL.toURI());
				configLoadersList.addAll((cpCompFile.isDirectory()) ? instrospectDirectory(pkgName, cpCompFile, "") : introspectJARFile(pkgName, cpCompFile));
			}
		}
		catch (Exception x) {
			x.printStackTrace();
		}
		
		return configLoadersList;
	}
	
	private static List<ConfigLoader> instrospectDirectory(String pkgName, File dir, String relPath) {
		List<ConfigLoader> configLoadersList = new ArrayList<ConfigLoader>();
		String[] childFileNames = dir.list();
		for (String childFileName : childFileNames) {
			String newRelPath = relPath.concat(childFileName);
			if (pkgName.startsWith((newRelPath.length() > pkgName.length()) ? newRelPath.substring(0, pkgName.length()) : newRelPath) == false) {
				continue;
			}
			
			File childFile = new File(dir.getAbsolutePath().concat(FILE_SEPARATOR).concat(childFileName));
			if (childFile.isDirectory()) {
				configLoadersList.addAll(instrospectDirectory(pkgName, childFile, newRelPath.concat(".")));
				continue;
			}
			
			// This is a file
			if (childFileName.endsWith(CLASS_EXTENSION)) {
				String className = relPath.concat(childFileName.substring(0, childFileName.length() - CLASS_EXTENSION_LENGTH));
				ConfigLoader cfgLoaderCls = getLoadConfigClass(className);
				if (cfgLoaderCls != null) {
					configLoadersList.add(cfgLoaderCls);
				}
			}
			
		}
		
		return configLoadersList;
	}
	
	private static List<ConfigLoader> introspectJARFile(String pkgName, File jarFile) {
		List<ConfigLoader> configLoadersList = new ArrayList<ConfigLoader>();
		try ( JarFile cpCompJar = new JarFile(jarFile) ) {
			Enumeration<JarEntry> cpCompJarEntries = cpCompJar.entries();
			while (cpCompJarEntries.hasMoreElements()) {
				JarEntry cpCompJarEntry = cpCompJarEntries.nextElement();
				if (cpCompJarEntry.isDirectory()) {
					continue;
				}
				
				String jarEntryName = cpCompJarEntry.getName().replaceAll("/", ".");
				
				if (jarEntryName.endsWith(CLASS_EXTENSION) == false) {
					continue;
				}
				
				if (jarEntryName.startsWith(pkgName)) {
					String className = jarEntryName.substring(0, jarEntryName.length() - CLASS_EXTENSION_LENGTH);
					ConfigLoader cfgLoaderCls = getLoadConfigClass(className);
					if (cfgLoaderCls != null) {
						configLoadersList.add(cfgLoaderCls);
					}
				}
			}
		}
		catch (IOException iox) {
			iox.printStackTrace();
		}
		
		return configLoadersList;
	}
	
	@SuppressWarnings("rawtypes")
	private static ConfigLoader getLoadConfigClass(String className) {
		try {
			Class cls = Class.forName(className);
			Method[] methods = cls.getMethods();
			for (Method method : methods) {
				LoadConfig loadCfg = method.getAnnotation(LoadConfig.class);
				if (loadCfg != null) {
					return new ConfigLoader(loadCfg.configType(), method);
				}
			}
		}
		catch (Exception x) {
			x.printStackTrace();
		}
		
		return null;
	}
}
