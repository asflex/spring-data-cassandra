/*
 * Copyright 2011-2013 the original author or authors.
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
package org.springframework.cassandra.core.cql.generator;

import static org.springframework.cassandra.core.PrimaryKey.CLUSTERED;
import static org.springframework.cassandra.core.PrimaryKey.PARTITIONED;
import static org.springframework.cassandra.core.cql.CqlStringUtils.noNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.cassandra.core.keyspace.ColumnSpecification;
import org.springframework.cassandra.core.keyspace.CreateTableSpecification;
import org.springframework.cassandra.core.keyspace.Option;

/**
 * CQL generator for generating a <code>CREATE TABLE</code> statement.
 * 
 * @author Alex Shvid
 * @author Matthew T. Adams
 */
public class CreateTableCqlGenerator extends TableCqlGenerator<CreateTableSpecification> {

	public CreateTableCqlGenerator(CreateTableSpecification specification) {
		super(specification);
	}

	public StringBuilder toCql(StringBuilder cql) {

		cql = noNull(cql);

		preambleCql(cql);
		columnsAndOptionsCql(cql);

		cql.append(";");

		return cql;
	}

	protected StringBuilder preambleCql(StringBuilder cql) {
		return noNull(cql).append("CREATE TABLE ").append(spec().getIfNotExists() ? "IF NOT EXISTS " : "")
				.append(spec().getNameAsIdentifier());
	}

	@SuppressWarnings("unchecked")
	protected StringBuilder columnsAndOptionsCql(StringBuilder cql) {

		cql = noNull(cql);

		// begin columns
		cql.append(" (");

		List<ColumnSpecification> partitionKeys = new ArrayList<ColumnSpecification>();
		List<ColumnSpecification> clusteredKeys = new ArrayList<ColumnSpecification>();
		for (ColumnSpecification col : spec().getAllColumns()) {
			col.toCql(cql).append(", ");

			if (col.getPrimary() == PARTITIONED) {
				partitionKeys.add(col);
			} else if (col.getPrimary() == CLUSTERED) {
				clusteredKeys.add(col);
			}
		}

		// begin primary key clause
		cql.append("PRIMARY KEY (");

		if (partitionKeys.size() > 1) {
			// begin partition key clause
			cql.append("(");
		}

		Collections.sort(partitionKeys, ordinalBasedColumnComparator);
		appendColumnNames(cql, partitionKeys);

		if (partitionKeys.size() > 1) {
			cql.append(")");
			// end partition key clause
		}

		if (!clusteredKeys.isEmpty()) {
			cql.append(", ");
		}

		Collections.sort(clusteredKeys, ordinalBasedColumnComparator);
		appendColumnNames(cql, clusteredKeys);

		cql.append(")");
		// end primary key clause

		cql.append(")");
		// end columns

		StringBuilder ordering = createOrderingClause(clusteredKeys);
		// begin options
		// begin option clause
		Map<String, Object> options = spec().getOptions();

		if (ordering != null || !options.isEmpty()) {

			// option preamble
			boolean first = true;
			cql.append(" WITH ");
			// end option preamble

			if (ordering != null) {
				cql.append(ordering);
				first = false;
			}
			if (!options.isEmpty()) {
				for (String name : options.keySet()) {
					// append AND if we're not on first option
					if (first) {
						first = false;
					} else {
						cql.append(" AND ");
					}

					// append <name> = <value>
					cql.append(name);

					Object value = options.get(name);
					if (value == null) { // then assume string-only, valueless option like "COMPACT STORAGE"
						continue;
					}

					cql.append(" = ");

					if (value instanceof Map) {
						optionValueMap((Map<Option, Object>) value, cql);
						continue; // end non-empty value map
					}

					// else just use value as string
					cql.append(value.toString());
				}
			}
		}
		// end options

		return cql;
	}

	private static StringBuilder createOrderingClause(List<ColumnSpecification> columns) {
		StringBuilder ordering = null;
		boolean first = true;
		for (ColumnSpecification col : columns) {

			if (col.getOrdering() != null) { // then ordering specified
				if (ordering == null) { // then initialize ordering clause
					ordering = new StringBuilder().append("CLUSTERING ORDER BY (");
				}
				if (first) {
					first = false;
				} else {
					ordering.append(", ");
				}
				ordering.append(col.getName()).append(" ").append(col.getOrdering().cql());
			}
		}
		if (ordering != null) { // then end ordering option
			ordering.append(")");
		}
		return ordering;
	}

	private static void appendColumnNames(StringBuilder str, List<ColumnSpecification> columns) {

		boolean first = true;
		for (ColumnSpecification col : columns) {
			if (first) {
				first = false;
			} else {
				str.append(", ");
			}
			str.append(col.getName());

		}

	}

	/**
	 * Ordinal based column comparator is used for column ordering in partitioned and clustered parts of the primary key
	 * 
	 * @author Alex Shvid
	 * 
	 */

	private static class OrdinalBasedColumnComparator implements Comparator<ColumnSpecification> {

		@Override
		public int compare(ColumnSpecification o1, ColumnSpecification o2) {

			Integer ordinal1 = o1.getOrdinal();
			Integer ordinal2 = o1.getOrdinal();

			if (ordinal1 == null) {
				if (ordinal2 == null) {
					return 0;
				}
				return -1;
			}

			if (ordinal2 == null) {
				return 1;
			}

			return ordinal1.compareTo(ordinal2);
		}

	}

	private final static OrdinalBasedColumnComparator ordinalBasedColumnComparator = new OrdinalBasedColumnComparator();

}
