/*
 * Copyright Siemens AG, 2017-2018. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.couchdb.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.collect.Maps;
import org.eclipse.sw360.datahandler.thrift.attachments.LicenseInfoUsage;
import org.eclipse.sw360.datahandler.thrift.attachments.ManuallySetUsage;
import org.eclipse.sw360.datahandler.thrift.attachments.SourcePackageUsage;
import org.eclipse.sw360.datahandler.thrift.attachments.UsageData;

import java.io.IOException;
import java.util.Map;

/**
 * Since {@link UsageData} is a union type, Jackson is not able to determine the
 * exact type of the inner value. This is caused by the type {@link Object} of
 * the value_ field inside the {@link UsageData} object.
 *
 * Therefore an own deserializer is needed that tells Jackson the correct type
 * to use on deserializing.
 */
public class UsageDataDeserializer extends StdDeserializer<UsageData> {

	/**
	 * A mapping between field types of usage data and the class types for the
	 * value. Unfortunately such a mapping is not generated by Thrift and has to be
	 * created manually here.
	 */
	private static Map<UsageData._Fields, Class<?>> typeMap;
	static {
		typeMap = Maps.newHashMap();
		typeMap.put(UsageData._Fields.LICENSE_INFO, LicenseInfoUsage.class);
		typeMap.put(UsageData._Fields.SOURCE_PACKAGE, SourcePackageUsage.class);
		typeMap.put(UsageData._Fields.MANUALLY_SET, ManuallySetUsage.class);
	}

	private static class GenericUsageData {
		String setField_;
		JsonNode value_;
	}

	public UsageDataDeserializer() {
		this(null);
	}

	public UsageDataDeserializer(Class<?> vc) {
		super(vc);
	}

	@Override
	public UsageData deserialize(JsonParser parser, DeserializationContext context)
			throws IOException, JsonProcessingException {
		GenericUsageData genericUsageData = parser.readValueAs(GenericUsageData.class);

		UsageData._Fields type = UsageData._Fields.valueOf(genericUsageData.setField_);
		if (type == null) {
			throw new IllegalArgumentException(
					"Type " + genericUsageData.setField_ + " is not registered and cannot be deserialized.");
		}

		Class<?> valueType = typeMap.get(type);
		if (valueType == null) {
			throw new IllegalArgumentException(
					"No class registered for type " + genericUsageData.setField_ + ". Could not deserialize field.");
		}

		Object value = genericUsageData.value_.traverse(parser.getCodec()).readValueAs(valueType);
		return new UsageData(type, value);
	}

}
