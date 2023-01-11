/*
 * Copyright Siemens AG, 2014-2015. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.couchdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import static org.eclipse.sw360.datahandler.common.SW360Constants.KEY_ID;
import static org.eclipse.sw360.datahandler.common.SW360Constants.KEY_REV;

/**
 * Mixin class for Jackson serialization into CouchDB. Allows to use objects
 * generated by Thrift directly into CouchDB via Ektorp.
 *
 * @author cedric.bodet@tngtech.com
 */
@JsonIgnoreProperties({KEY_ID, KEY_REV})
@SuppressWarnings("unused")
public class DatabaseNestedMixIn {

	@JsonProperty("issetBitfield")
	private byte __isset_bitfield = 0;

}
