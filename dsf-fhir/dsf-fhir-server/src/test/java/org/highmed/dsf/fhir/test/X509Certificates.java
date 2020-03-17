package org.highmed.dsf.fhir.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.rwh.utils.crypto.CertificateAuthority;
import de.rwh.utils.crypto.CertificateHelper;
import de.rwh.utils.crypto.CertificationRequestBuilder;
import de.rwh.utils.crypto.io.CertificateWriter;
import de.rwh.utils.crypto.io.PemIo;

public class X509Certificates extends ExternalResource
{
	public static final class ClientCertificate
	{
		private final X509Certificate certificate;
		private final KeyStore trustStore;
		private final KeyStore keyStore;
		private final char[] keyStorePassword;

		ClientCertificate(X509Certificate certificate, KeyStore trustStore, KeyStore keyStore, char[] keyStorePassword)
		{
			this.certificate = certificate;
			this.trustStore = trustStore;
			this.keyStore = keyStore;
			this.keyStorePassword = keyStorePassword;
		}

		public X509Certificate getCertificate()
		{
			return certificate;
		}

		public KeyStore getTrustStore()
		{
			return trustStore;
		}

		public KeyStore getKeyStore()
		{
			return keyStore;
		}

		public char[] getKeyStorePassword()
		{
			return keyStorePassword;
		}
	}

	private static final Logger logger = LoggerFactory.getLogger(X509Certificates.class);

	private boolean beforeRun;

	private final X509Certificates parent;

	private ClientCertificate clientCertificate;
	private ClientCertificate externalClientCertificate;

	private Path caCertificateFile;
	private Path serverCertificateFile;
	private Path clientCertificateFile;
	private Path externalClientCertificateFile;

	private List<Path> filesToDelete;

	public X509Certificates()
	{
		this(null);
	}

	public X509Certificates(X509Certificates parent)
	{
		this.parent = parent;
	}

	private boolean parentBeforeRan()
	{
		return parent != null && parent.beforeRun;
	}

	@Override
	protected void before() throws Throwable
	{
		if (parentBeforeRan())
			logger.debug("X509Certificates created by parent");
		else
			createX509Certificates();

		beforeRun = true;
	}

	@Override
	protected void after()
	{
		if (parentBeforeRan())
			logger.debug("X509Certificates will be deleted by parent");
		else
			deleteX509Certificates();
	}

	public ClientCertificate getClientCertificate()
	{
		if (parentBeforeRan())
			return parent.getClientCertificate();
		else
			return clientCertificate;
	}

	public ClientCertificate getExternalClientCertificate()
	{
		if (parentBeforeRan())
			return parent.getExternalClientCertificate();
		else
			return externalClientCertificate;
	}

	public Path getCaCertificateFile()
	{
		if (parentBeforeRan())
			return parent.getCaCertificateFile();

		return caCertificateFile;
	}

	public Path getServerCertificateFile()
	{
		if (parentBeforeRan())
			return parent.getServerCertificateFile();

		return serverCertificateFile;
	}

	public Path getClientCertificateFile()
	{
		if (parentBeforeRan())
			return parent.getClientCertificateFile();

		return clientCertificateFile;
	}

	public Path getExternalClientCertificateFile()
	{
		if (parentBeforeRan())
			return parent.getExternalClientCertificateFile();

		return externalClientCertificateFile;
	}

	private void createX509Certificates() throws InvalidKeyException, NoSuchAlgorithmException, KeyStoreException,
			CertificateException, OperatorCreationException, IllegalStateException, IOException, InvalidKeySpecException
	{
		logger.info("Creating certificates ...");

		Path caCertificateFile = Paths.get("target", UUID.randomUUID().toString() + ".pem");
		Path serverCertificateFile = Paths.get("target", UUID.randomUUID().toString() + ".p12");
		Path clientCertificateFile = Paths.get("target", UUID.randomUUID().toString() + ".p12");
		Path externalClientCertificateFile = Paths.get("target", UUID.randomUUID().toString() + ".p12");

		CertificateAuthority.registerBouncyCastleProvider();

		CertificateAuthority ca = new CertificateAuthority("DE", null, null, null, null, "test-ca");
		ca.initialize();
		X509Certificate caCertificate = ca.getCertificate();

		PemIo.writeX509CertificateToPem(caCertificate, caCertificateFile);

		// -- server
		X500Name serverSubject = CertificationRequestBuilder.createSubject("DE", null, null, null, null, "test-server");
		KeyPair serverRsaKeyPair = CertificationRequestBuilder.createRsaKeyPair4096Bit();
		JcaPKCS10CertificationRequest serverRequest = CertificationRequestBuilder
				.createServerCertificationRequest(serverSubject, serverRsaKeyPair, null, "localhost");

		X509Certificate serverCertificate = ca.signWebServerCertificate(serverRequest);

		CertificateWriter.toPkcs12(serverCertificateFile, serverRsaKeyPair.getPrivate(), "password".toCharArray(),
				serverCertificate, caCertificate, "test-server");
		// server --

		// -- client
		X500Name clientSubject = CertificationRequestBuilder.createSubject("DE", null, null, null, null, "test-client");
		KeyPair clientRsaKeyPair = CertificationRequestBuilder.createRsaKeyPair4096Bit();
		JcaPKCS10CertificationRequest clientRequest = CertificationRequestBuilder
				.createClientCertificationRequest(clientSubject, clientRsaKeyPair);

		X509Certificate clientCertificate = ca.signWebClientCertificate(clientRequest);

		char[] clientKeyStorePassword = "password".toCharArray();
		KeyStore clientKeyStore = CertificateHelper.toPkcs12KeyStore(clientRsaKeyPair.getPrivate(),
				new Certificate[] { clientCertificate, caCertificate }, "test-client", clientKeyStorePassword);

		CertificateWriter.toPkcs12(clientCertificateFile, clientRsaKeyPair.getPrivate(), "password".toCharArray(),
				clientCertificate, caCertificate, "client");
		// client --

		// -- external client
		X500Name externalClientSubject = CertificationRequestBuilder.createSubject("DE", null, null, null, null,
				"external-client");
		KeyPair externalClientRsaKeyPair = CertificationRequestBuilder.createRsaKeyPair4096Bit();
		JcaPKCS10CertificationRequest externalClientRequest = CertificationRequestBuilder
				.createClientCertificationRequest(externalClientSubject, externalClientRsaKeyPair);

		X509Certificate externalClientCertificate = ca.signWebClientCertificate(externalClientRequest);

		char[] externalClientKeyStorePassword = "password".toCharArray();
		KeyStore externalClientKeyStore = CertificateHelper.toPkcs12KeyStore(externalClientRsaKeyPair.getPrivate(),
				new Certificate[] { externalClientCertificate, caCertificate }, "external-client",
				externalClientKeyStorePassword);

		CertificateWriter.toPkcs12(externalClientCertificateFile, externalClientRsaKeyPair.getPrivate(),
				"password".toCharArray(), externalClientCertificate, caCertificate, "client");
		// external client --

		this.clientCertificate = new ClientCertificate(clientCertificate,
				CertificateHelper.extractTrust(clientKeyStore), clientKeyStore, clientKeyStorePassword);
		this.externalClientCertificate = new ClientCertificate(externalClientCertificate,
				CertificateHelper.extractTrust(externalClientKeyStore), externalClientKeyStore,
				externalClientKeyStorePassword);

		this.caCertificateFile = caCertificateFile;
		this.serverCertificateFile = serverCertificateFile;
		this.clientCertificateFile = clientCertificateFile;
		this.externalClientCertificateFile = externalClientCertificateFile;

		this.filesToDelete = Arrays.asList(caCertificateFile, serverCertificateFile, clientCertificateFile,
				externalClientCertificateFile);
	}

	private void deleteX509Certificates()
	{
		logger.info("Deleting certificate files {} ...", filesToDelete);
		filesToDelete.forEach(this::deleteFile);
	}

	private void deleteFile(Path file)
	{
		try
		{
			Files.delete(file);
		}
		catch (IOException e)
		{
			logger.error("Error while deleting certificate file {}, error: {}", file.toString(), e.toString());
		}
	}
}
