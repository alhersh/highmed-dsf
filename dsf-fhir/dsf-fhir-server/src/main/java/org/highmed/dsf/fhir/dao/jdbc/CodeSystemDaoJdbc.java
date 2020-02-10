package org.highmed.dsf.fhir.dao.jdbc;

import java.sql.SQLException;
import java.util.Optional;

import org.apache.commons.dbcp2.BasicDataSource;
import org.highmed.dsf.fhir.dao.CodeSystemDao;
import org.highmed.dsf.fhir.search.parameters.CodeSystemIdentifier;
import org.highmed.dsf.fhir.search.parameters.CodeSystemStatus;
import org.highmed.dsf.fhir.search.parameters.CodeSystemUrl;
import org.highmed.dsf.fhir.search.parameters.CodeSystemVersion;
import org.hl7.fhir.r4.model.CodeSystem;

import ca.uhn.fhir.context.FhirContext;

public class CodeSystemDaoJdbc extends AbstractResourceDaoJdbc<CodeSystem> implements CodeSystemDao
{
	private final ReadByUrlDaoJdbc<CodeSystem> readByUrl;

	public CodeSystemDaoJdbc(BasicDataSource dataSource, FhirContext fhirContext)
	{
		super(dataSource, fhirContext, CodeSystem.class, "code_systems", "code_system", "code_system_id",
				with(CodeSystemIdentifier::new, CodeSystemStatus::new, CodeSystemUrl::new, CodeSystemVersion::new),
				with());

		readByUrl = new ReadByUrlDaoJdbc<>(this::getDataSource, this::getResource, getResourceTable(),
				getResourceColumn(), getResourceIdColumn());
	}

	@Override
	protected CodeSystem copy(CodeSystem resource)
	{
		return resource.copy();
	}

	@Override
	public Optional<CodeSystem> readByUrl(String urlAndVersion) throws SQLException
	{
		return readByUrl.readByUrl(urlAndVersion);
	}
}
