/*
 * Copyright 2014 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springdata.cassandra.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.springdata.cassandra.mapping.CassandraPersistentEntity;
import org.springdata.cql.core.DefaultExecuteOperation;
import org.springdata.cql.core.DefaultIngestOperation;
import org.springdata.cql.core.ExecuteOperation;
import org.springdata.cql.core.IngestOperation;
import org.springdata.cql.generator.AlterTableCqlGenerator;
import org.springdata.cql.generator.CreateIndexCqlGenerator;
import org.springdata.cql.generator.CreateTableCqlGenerator;
import org.springdata.cql.generator.DropIndexCqlGenerator;
import org.springdata.cql.generator.DropTableCqlGenerator;
import org.springdata.cql.spec.AlterTableSpecification;
import org.springdata.cql.spec.CreateIndexSpecification;
import org.springdata.cql.spec.CreateTableSpecification;
import org.springdata.cql.spec.DropIndexSpecification;
import org.springdata.cql.spec.DropTableSpecification;
import org.springdata.cql.spec.WithNameSpecification;
import org.springframework.data.mapping.model.MappingException;
import org.springframework.util.Assert;

import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.TableMetadata;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Iterators;

/**
 * SchemaOperations implementation
 * 
 * @author Alex Shvid
 * 
 */
public class DefaultSchemaOperations implements SchemaOperations {

	private CassandraTemplate cassandraTemplate;

	protected DefaultSchemaOperations(CassandraTemplate cassandraTemplate) {
		Assert.notNull(cassandraTemplate);

		this.cassandraTemplate = cassandraTemplate;
	}

	@Override
	public ExecuteOperation createTable(String tableName, Class<?> entityClass) {

		Assert.notNull(entityClass);

		final CassandraPersistentEntity<?> entity = cassandraTemplate.getPersistentEntity(entityClass);
		CreateTableSpecification spec = cassandraTemplate.getConverter().getCreateTableSpecification(entity);
		spec.name(tableName);

		CreateTableCqlGenerator generator = new CreateTableCqlGenerator(spec);

		String cql = generator.toCql();

		return new DefaultExecuteOperation(cassandraTemplate.cqlTemplate(), cql);

	}

	@Override
	public Optional<ExecuteOperation> alterTable(String tableName, Class<?> entityClass,
			boolean dropRemovedAttributeColumns) {

		Assert.notNull(entityClass);

		String cql = alterTableCql(tableName, entityClass, dropRemovedAttributeColumns);

		if (cql != null) {

			return Optional.<ExecuteOperation> of(new DefaultExecuteOperation(cassandraTemplate.cqlTemplate(), cql));

		} else {

			return Optional.absent();
		}
	}

	@Override
	public String validateTable(String tableName, Class<?> entityClass) {

		Assert.notNull(entityClass);

		return alterTableCql(tableName, entityClass, true);

	}

	/**
	 * Service method to generate cql query for the given table
	 * 
	 * @param tableName
	 * @param entityClass
	 * @param dropRemovedAttributeColumns
	 * @return Cql query string or null if no changes
	 */
	protected String alterTableCql(String tableName, Class<?> entityClass, boolean dropRemovedAttributeColumns) {

		final CassandraPersistentEntity<?> entity = cassandraTemplate.getPersistentEntity(entityClass);

		TableMetadata tableMetadata = cassandraTemplate.getCqlOperations().getSchemaOperations()
				.getTableMetadata(tableName);

		AlterTableSpecification spec = cassandraTemplate.getConverter().getAlterTableSpecification(entity, tableMetadata,
				dropRemovedAttributeColumns);

		if (!spec.hasChanges()) {
			return null;
		}

		AlterTableCqlGenerator generator = new AlterTableCqlGenerator(spec);

		String cql = generator.toCql();

		return cql;

	}

	@Override
	public ExecuteOperation dropTable(String tableName) {

		DropTableSpecification spec = new DropTableSpecification().name(tableName);
		String cql = new DropTableCqlGenerator(spec).toCql();

		return new DefaultExecuteOperation(cassandraTemplate.cqlTemplate(), cql);

	}

	@Override
	public IngestOperation createIndexes(String tableName, Class<?> entityClass) {

		Assert.notNull(entityClass);

		CassandraPersistentEntity<?> entity = cassandraTemplate.getPersistentEntity(entityClass);

		List<CreateIndexSpecification> specList = cassandraTemplate.getConverter().getCreateIndexSpecifications(entity);

		Iterator<Statement> queryIterator = Iterators.transform(specList.iterator(),
				new Function<CreateIndexSpecification, Statement>() {

					@Override
					public Statement apply(CreateIndexSpecification spec) {
						String cql = new CreateIndexCqlGenerator(spec).toCql();
						return new SimpleStatement(cql);
					}

				});

		return new DefaultIngestOperation(cassandraTemplate.cqlTemplate(), queryIterator);

	}

	@Override
	public IngestOperation alterIndexes(String tableName, Class<?> entityClass) {

		Assert.notNull(entityClass);

		List<String> cqlList = alterIndexesCql(tableName, entityClass);

		Iterator<Statement> queryIterator = Iterators.transform(cqlList.iterator(), new Function<String, Statement>() {

			@Override
			public Statement apply(String cql) {
				return new SimpleStatement(cql);
			}

		});

		return new DefaultIngestOperation(cassandraTemplate.cqlTemplate(), queryIterator);

	}

	@Override
	public List<String> validateIndexes(String tableName, Class<?> entityClass) {

		Assert.notNull(entityClass);

		return alterIndexesCql(tableName, entityClass);
	}

	/**
	 * Service method to generate cql queries to alter indexes in table
	 * 
	 * @param tableName
	 * @param entityClass
	 * @return List of cql queries
	 */

	protected List<String> alterIndexesCql(String tableName, Class<?> entityClass) {

		CassandraPersistentEntity<?> entity = cassandraTemplate.getPersistentEntity(entityClass);

		TableMetadata tableMetadata = cassandraTemplate.getCqlOperations().getSchemaOperations()
				.getTableMetadata(tableName);

		List<WithNameSpecification<?>> specList = cassandraTemplate.getConverter().getIndexChangeSpecifications(entity,
				tableMetadata);

		if (specList.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> result = new ArrayList<String>(specList.size());

		for (WithNameSpecification<?> spec : specList) {

			if (spec instanceof CreateIndexSpecification) {
				result.add(new CreateIndexCqlGenerator((CreateIndexSpecification) spec).toCql());
			} else if (spec instanceof DropIndexSpecification) {
				result.add(new DropIndexCqlGenerator((DropIndexSpecification) spec).toCql());
			} else {
				throw new MappingException("unexpected index operation " + spec + " for " + entityClass);
			}

		}

		return result;
	}

}
