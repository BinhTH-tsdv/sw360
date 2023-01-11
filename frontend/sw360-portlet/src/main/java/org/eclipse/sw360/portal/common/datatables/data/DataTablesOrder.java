/*
 * Copyright Siemens AG, 2015. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.common.datatables.data;

/**
 * @author daniele.fognini@tngtech.com
 */
public class DataTablesOrder {
	private final int column;
	private final boolean ascending;

	public DataTablesOrder(int column, boolean ascending) {
		this.column = column;
		this.ascending = ascending;
	}

	public int getColumn() {
		return column;
	}

	public boolean isAscending() {
		return ascending;
	}
}
