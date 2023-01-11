/*
 * Copyright (c) Bosch Software Innovations GmbH 2018.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.clients.rest.resource.projects;

import org.eclipse.sw360.clients.rest.resource.LinkObjects;
import org.eclipse.sw360.clients.rest.resource.SW360HalResource;

public class SW360ProjectList extends SW360HalResource<LinkObjects, SW360ProjectListEmbedded> {

	@Override
	public LinkObjects createEmptyLinks() {
		return new LinkObjects();
	}

	@Override
	public SW360ProjectListEmbedded createEmptyEmbedded() {
		return new SW360ProjectListEmbedded();
	}
}
