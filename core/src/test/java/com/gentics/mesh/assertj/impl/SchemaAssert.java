package com.gentics.mesh.assertj.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.gentics.mesh.assertj.AbstractMeshAssert;
import com.gentics.mesh.core.data.schema.SchemaContainer;
import com.gentics.mesh.core.data.schema.SchemaContainerVersion;
import com.gentics.mesh.core.rest.schema.Schema;

public class SchemaAssert extends AbstractMeshAssert<SchemaAssert, Schema> {

	public SchemaAssert(Schema actual) {
		super(actual, SchemaAssert.class);
	}

	public SchemaAssert matches(Schema schema) {
		assertNotNull(schema);
		assertNotNull(actual);
		assertEquals("The name of the schemas do not match.", actual.getName(), schema.getName());
		assertEquals("The description of the schemas do not match.", actual.getDescription(), schema.getDescription());
		assertEquals("The displayField of the schemas do not match.", actual.getDisplayField(), schema.getDisplayField());
		assertEquals("The segmentField of the schemas do not match.", actual.getSegmentField(), schema.getSegmentField());
		// TODO assert for schema properties
		return this;
	}

	public SchemaAssert isValid() {
		actual.validate();
		return this;
	}

	public SchemaAssert matches(SchemaContainer schema) {
		// TODO make schemas extends generic nodes?
		// assertGenericNode(schema, restSchema);
		assertNotNull(schema);
		assertNotNull(actual);

		String creatorUuid = schema.getCreator().getUuid();
		String editorUuid = schema.getEditor().getUuid();
		assertEquals("The editor of the schema did not match up.", editorUuid, actual.getEditor().getUuid());
		assertEquals("The creator of the schema did not match up.", creatorUuid, actual.getCreator().getUuid());
		assertEquals("The creation date did not match up", schema.getCreationDate(), actual.getCreated());
		assertEquals("The edited date did not match up", schema.getLastEditedDate(), actual.getEdited());
		// assertEquals("Name does not match with the requested name.", schema.getName(), restSchema.getName());
		// assertEquals("Description does not match with the requested description.", schema.getDescription(), restSchema.getDescription());
		// assertEquals("Display names do not match.", schema.getDisplayName(), restSchema.getDisplayName());
		// TODO verify other fields
		return this;
	}

	public SchemaAssert matches(SchemaContainerVersion version) {
		assertNotNull(version);
		assertNotNull(actual);

		Schema storedSchema = version.getSchema();
		matches(storedSchema);
		SchemaContainer container = version.getSchemaContainer();
		matches(container);
		return this;
	}

}
