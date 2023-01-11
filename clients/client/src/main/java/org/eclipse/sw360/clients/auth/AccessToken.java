/*
 * Copyright (c) Bosch.IO GmbH 2020.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.clients.auth;

import org.apache.commons.lang3.Validate;
import org.eclipse.sw360.http.RequestBuilder;
import org.eclipse.sw360.http.utils.HttpConstants;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * <p>
 * A class representing an access token.
 * </p>
 * <p>
 * In addition to giving some semantics to a plain string, this class offers som
 * convenience methods to add a token to a request.
 * </p>
 */
public final class AccessToken {
	/**
	 * Stores the managed access token.
	 */
	private final String token;

	/**
	 * Creates a new instance of {@code AccessToken} and initializes it with the
	 * given token string. It is checked whether the token is actually defined.
	 *
	 * @param token
	 *            the token string
	 * @throws NullPointerException
	 *             if the token is <strong>null</strong>
	 * @throws IllegalArgumentException
	 *             if the token is empty
	 */
	public AccessToken(String token) {
		this.token = Validate.notEmpty(token, "Undefined access token");
	}

	/**
	 * Returns the access token managed by this object as a plain string.
	 *
	 * @return the access token string
	 */
	public String getToken() {
		return token;
	}

	/**
	 * Adds the token managed by this object to a corresponding request header using
	 * the builder specified.
	 *
	 * @param builder
	 *            the request builder
	 * @return the same request builder for method chaining
	 */
	public RequestBuilder addToken(RequestBuilder builder) {
		return builder.header(HttpConstants.HEADER_AUTHORIZATION, HttpConstants.AUTH_BEARER + getToken());
	}

	/**
	 * Returns a {@code Consumer} that first adds the managed token to the passed in
	 * {@link RequestBuilder} and then delegates to the given {@code Consumer}. This
	 * is a convenient way to add support for access tokens to a request generated
	 * by an arbitrary producer.
	 *
	 * @param producer
	 *            the {@code Consumer} of a request builder to be wrapped
	 * @return a {@code Consumer} adding the managed access token
	 */
	public Consumer<RequestBuilder> tokenProducer(Consumer<? super RequestBuilder> producer) {
		return builder -> producer.accept(addToken(builder));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		AccessToken that = (AccessToken) o;
		return getToken().equals(that.getToken());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getToken());
	}
}
