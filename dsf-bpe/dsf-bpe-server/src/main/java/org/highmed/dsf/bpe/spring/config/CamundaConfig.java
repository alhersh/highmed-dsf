package org.highmed.dsf.bpe.spring.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.dbcp2.BasicDataSource;
import org.camunda.bpm.engine.impl.variable.serializer.TypedValueSerializer;
import org.camunda.bpm.engine.impl.variable.serializer.VariableSerializerFactory;
import org.camunda.bpm.engine.spring.ProcessEngineFactoryBean;
import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.highmed.dsf.bpe.camunda.FallbackSerializerFactory;
import org.highmed.dsf.bpe.camunda.MultiVersionSpringProcessEngineConfiguration;
import org.highmed.dsf.bpe.delegate.DelegateProvider;
import org.highmed.dsf.bpe.delegate.DelegateProviderImpl;
import org.highmed.dsf.bpe.listener.CallActivityListener;
import org.highmed.dsf.bpe.listener.DefaultBpmnParseListener;
import org.highmed.dsf.bpe.listener.EndListener;
import org.highmed.dsf.bpe.listener.StartListener;
import org.highmed.dsf.bpe.plugin.ProcessPluginProvider;
import org.highmed.dsf.bpe.plugin.ProcessPluginProviderImpl;
import org.highmed.dsf.fhir.client.FhirWebserviceClientProvider;
import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CamundaConfig
{
	@Value("${org.highmed.dsf.bpe.db.url}")
	private String dbUrl;

	@Value("${org.highmed.dsf.bpe.db.camunda_user}")
	private String dbUsernameCamunda;

	@Value("${org.highmed.dsf.bpe.db.camunda_user_password}")
	private String dbPasswordCamunda;

	@Value("${org.highmed.dsf.bpe.process_plugin_directroy:process}")
	private String pluginDirectory;

	@Autowired
	private FhirConfig fhirConfig;

	@Autowired
	private FhirWebserviceClientProvider clientProvider;

	@Autowired
	private ApplicationContext applicationContext;

	@Bean
	public PlatformTransactionManager transactionManager()
	{
		return new DataSourceTransactionManager(camundaDataSource());
	}

	@Bean
	public TransactionAwareDataSourceProxy transactionAwareDataSource()
	{
		return new TransactionAwareDataSourceProxy(camundaDataSource());
	}

	@Bean
	public BasicDataSource camundaDataSource()
	{
		BasicDataSource dataSource = new BasicDataSource();
		dataSource.setDriverClassName(Driver.class.getName());
		dataSource.setUrl(dbUrl);
		dataSource.setUsername(dbUsernameCamunda);
		dataSource.setPassword(dbPasswordCamunda);

		dataSource.setTestOnBorrow(true);
		dataSource.setValidationQuery("SELECT 1");
		return dataSource;
	}

	@Bean
	public StartListener startListener()
	{
		return new StartListener(fhirConfig.taskHelper());
	}

	@Bean
	public EndListener endListener()
	{
		return new EndListener(clientProvider.getLocalWebserviceClient(), fhirConfig.taskHelper());
	}

	@Bean
	public CallActivityListener callActivityListener()
	{
		return new CallActivityListener();
	}

	@Bean
	public DefaultBpmnParseListener defaultBpmnParseListener()
	{
		return new DefaultBpmnParseListener(startListener(), endListener(), callActivityListener());
	}

	@Bean
	public SpringProcessEngineConfiguration processEngineConfiguration(
			@SuppressWarnings("rawtypes") List<TypedValueSerializer> baseSerializers) throws IOException
	{
		var c = new MultiVersionSpringProcessEngineConfiguration(delegateProvider());
		c.setProcessEngineName("highmed");
		c.setDataSource(transactionAwareDataSource());
		c.setTransactionManager(transactionManager());
		c.setDatabaseSchemaUpdate("false");
		c.setJobExecutorActivate(true);
		c.setCustomPreBPMNParseListeners(List.of(defaultBpmnParseListener()));
		c.setCustomPreVariableSerializers(baseSerializers);
		c.setFallbackSerializerFactory(getFallbackSerializerFactory());

		return c;
	}

	@Bean
	public VariableSerializerFactory getFallbackSerializerFactory()
	{
		return new FallbackSerializerFactory(delegateProvider().getTypedValueSerializers());
	}

	@Bean
	public DelegateProvider delegateProvider()
	{
		return new DelegateProviderImpl(processPluginProvider().getClassLoadersByProcessDefinitionKeyAndVersion(),
				ClassLoader.getSystemClassLoader(),
				processPluginProvider().getApplicationContextsByProcessDefinitionKeyAndVersion(), applicationContext);
	}

	@Bean
	public ProcessEngineFactoryBean processEngineFactory(
			@SuppressWarnings("rawtypes") List<TypedValueSerializer> baseSerializers) throws IOException
	{
		var f = new ProcessEngineFactoryBean();
		f.setProcessEngineConfiguration(processEngineConfiguration(baseSerializers));
		return f;
	}

	@Bean
	public ProcessPluginProvider processPluginProvider()
	{
		Path pluginDirectoryPath = Paths.get(pluginDirectory);

		if (!Files.isDirectory(pluginDirectoryPath))
			throw new RuntimeException(
					"Process plug in directory '" + pluginDirectoryPath.toString() + "' not readable");

		return new ProcessPluginProviderImpl(fhirConfig.fhirContext(), pluginDirectoryPath, applicationContext);
	}
}
