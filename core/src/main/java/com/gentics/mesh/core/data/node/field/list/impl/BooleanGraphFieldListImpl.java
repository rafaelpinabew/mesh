package com.gentics.mesh.core.data.node.field.list.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.generic.MeshVertexImpl;
import com.gentics.mesh.core.data.node.field.BooleanGraphField;
import com.gentics.mesh.core.data.node.field.impl.BooleanGraphFieldImpl;
import com.gentics.mesh.core.data.node.field.list.AbstractBasicGraphFieldList;
import com.gentics.mesh.core.data.node.field.list.BooleanGraphFieldList;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.rest.node.field.list.impl.BooleanFieldListImpl;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.util.CompareUtils;

/**
 * @see BooleanGraphFieldList
 */
public class BooleanGraphFieldListImpl extends AbstractBasicGraphFieldList<BooleanGraphField, BooleanFieldListImpl, Boolean>
		implements BooleanGraphFieldList {

	public static void init(Database database) {
		database.addVertexType(BooleanGraphFieldListImpl.class, MeshVertexImpl.class);
	}

	@Override
	public BooleanGraphField getBoolean(int index) {
		return getField(index);
	}

	@Override
	public BooleanGraphField createBoolean(Boolean flag) {
		BooleanGraphField field = createField();
		field.setBoolean(flag);
		return field;
	}

	@Override
	protected BooleanGraphField createField(String key) {
		return new BooleanGraphFieldImpl(key, getImpl());
	}

	@Override
	public Class<? extends BooleanGraphField> getListType() {
		return BooleanGraphFieldImpl.class;
	}

	@Override
	public void delete(SearchQueueBatch batch) {
		getElement().remove();
	}

	@Override
	public BooleanFieldListImpl transformToRest(InternalActionContext ac, String fieldKey, List<String> languageTags, int level) {
		BooleanFieldListImpl restModel = new BooleanFieldListImpl();
		for (BooleanGraphField item : getList()) {
			restModel.add(item.getBoolean());
		}
		return restModel;
	}

	@Override
	public List<Boolean> getValues() {
		return getList().stream().map(BooleanGraphField::getBoolean).collect(Collectors.toList());
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof BooleanFieldListImpl) {
			BooleanFieldListImpl restField = (BooleanFieldListImpl) obj;
			List<Boolean> restList = restField.getItems();
			List<? extends BooleanGraphField> graphList = getList();
			List<Boolean> graphStringList = graphList.stream().map(e -> e.getBoolean()).collect(Collectors.toList());
			return CompareUtils.equals(restList, graphStringList);
		}
		return super.equals(obj);
	}
}
