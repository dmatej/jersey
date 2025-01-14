/*
 * Copyright (c) 2013, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.oauth1.signature;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import jakarta.inject.Inject;

import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.uri.UriComponent;

/**
 * Injectable class used for processing an OAuth signature (signing or verifying).
 * <p>
 * Example of usage:
 *
 * <pre>
 * // inject the OAuth1Signature
 * &#64;Inject
 * OAuth1Signature oAuthSignature;
 * ...
 *
 * // wrap an existing request with some concrete implementation
 * OAuth1Request request = new ConcreteOAuthRequestImplementation();
 *
 * // establish the parameters that will be used to sign the request
 * OAuth1Parameters params = new OAuth1Parameters().consumerKey("dpf43f3p2l4k3l03").
 *  token("nnch734d00sl2jdk").signatureMethod(HmaSha1Method.NAME).
 *  timestamp().nonce().version();
 *
 * // establish the secrets that will be used to sign the request
 * OAuth1Secrets secrets = new OAuth1Secrets().consumerSecret("kd94hf93k423kf44").
 *  tokenSecret("pfkkdhi9sl3r4s00");
 *
 * // generate the digital signature and set in the request
 * oAuthSignature.sign(request, params, secrets);
 * </pre>
 *
 * @author Hubert A. Le Van Gong <hubert.levangong at Sun.COM>
 * @author Paul C. Bryan <pbryan@sun.com>
 */
public class OAuth1Signature {

    private final HashMap<String, OAuth1SignatureMethod> methods;

    /**
     * Create a new instance of the OAuth signature configured with injected {@code ServiceLocator}.
     * @param injectionManager injection manager
     */
    @Inject
    public OAuth1Signature(final InjectionManager injectionManager) {
        methods = new HashMap<String, OAuth1SignatureMethod>();
        final List<OAuth1SignatureMethod> methodList = injectionManager.getAllInstances(OAuth1SignatureMethod.class);
        for (final OAuth1SignatureMethod oAuth1SignatureMethod : methodList) {
            methods.put(oAuth1SignatureMethod.name(), oAuth1SignatureMethod);
        }
    }

    /**
     * Generates and returns an OAuth signature for the given request,
     * parameters and secrets.
     *
     * @param request the request to generate signature for.
     * @param params the OAuth authorization parameters.
     * @param secrets the secrets used to generate the OAuth signature.
     * @return the OAuth digital signature.
     * @throws OAuth1SignatureException if an error occurred generating the signature.
     */
    public String generate(final OAuth1Request request,
                           final OAuth1Parameters params, final OAuth1Secrets secrets) throws OAuth1SignatureException {
        return getSignatureMethod(params).sign(baseString(request, params), secrets);
    }

    /**
     * Generates an OAuth signature for the given request, parameters and
     * secrets, and stores it as a signature parameter, and writes the
     * OAuth parameters to the request as an Authorization header.
     *
     * @param request the request to generate signature for and write header to.
     * @param params the OAuth authorization parameters.
     * @param secrets the secrets used to generate the OAuth signature.
     * @throws OAuth1SignatureException if an error occurred generating the signature.
     */
    public void sign(final OAuth1Request request,
                     OAuth1Parameters params, final OAuth1Secrets secrets) throws OAuth1SignatureException {
        params = params.clone(); // don't modify caller's parameters
        params.setSignature(generate(request, params, secrets));
        params.writeRequest(request);
    }

    /**
     * Verifies the OAuth signature for a given request, parameters and
     * secrets.
     *
     * @param request the request to verify the signature from.
     * @param params the OAuth authorization parameters
     * @param secrets the secrets used to verify the OAuth signature.
     * @return true if the signature is verified.
     * @throws OAuth1SignatureException if an error occurred generating the signature.
     */
    public boolean verify(final OAuth1Request request,
                          final OAuth1Parameters params, final OAuth1Secrets secrets) throws OAuth1SignatureException {
        return getSignatureMethod(params).verify(baseString(request, params), secrets, params.getSignature());
    }

    /**
     * Collects, sorts and concetenates the request parameters into a
     * normalized string, per section 9.1.1. of the OAuth 1.0 specification.
     *
     * @param request the request to retreive parameters from.
     * @param params the OAuth authorization parameters to retrieve parameters from.
     * @return the normalized parameters string.
     */
    static String normalizeParameters(final OAuth1Request request, final OAuth1Parameters params) {

        final ArrayList<String[]> list = new ArrayList<String[]>();

        // parameters in the OAuth HTTP authorization header
        for (final String key : params.keySet()) {

            // exclude realm and oauth_signature parameters from OAuth HTTP authorization header
            if (key.equals(OAuth1Parameters.REALM) || key.equals(OAuth1Parameters.SIGNATURE)) {
                continue;
            }

            final String value = params.get(key);

            // Encode key and values as per section 3.6 http://tools.ietf.org/html/draft-hammer-oauth-10#section-3.6
            if (value != null) {
                addParam(key, value, list);
            }
        }

        // parameters in the HTTP POST request body and HTTP GET parameters in the query part
        for (final String key : request.getParameterNames()) {

            // ignore parameter if an OAuth-specific parameter that appears in the OAuth parameters
            if (key.startsWith("oauth_") && params.containsKey(key)) {
                continue;
            }

            // the same parameter name can have multiple values
            final List<String> values = request.getParameterValues(key);

            // Encode key and values as per section 3.6 http://tools.ietf.org/html/draft-hammer-oauth-10#section-3.6
            if (values != null) {
                for (final String value : values) {
                    addParam(key, value, list);
                }
            }
        }

        // sort name-value pairs by name
        Collections.sort(list, new Comparator<String[]>() {
            @Override
            public int compare(final String[] t, final String[] t1) {
                final int c = t[0].compareTo(t1[0]);
                return c == 0 ? t[1].compareTo(t1[1]) : c;
            }
        });

        final StringBuilder buf = new StringBuilder();

        // append each name-value pair, delimited with ampersand
        for (final Iterator<String[]> i = list.iterator(); i.hasNext(); ) {
            final String[] param = i.next();
            buf.append(param[0]).append("=").append(param[1]);
            if (i.hasNext()) {
                buf.append('&');
            }
        }

        return buf.toString();
    }

    /**
     * Constructs the request URI, per section 9.1.2 of the OAuth 1.0
     * specification.
     *
     * @param request the incoming request to construct the URI from.
     * @return the constructed URI.
     */
    private URI constructRequestURL(final OAuth1Request request) throws OAuth1SignatureException {
        try {
            final URL url = request.getRequestURL();
            if (url == null) {
                throw new OAuth1SignatureException();
            }
            final StringBuilder builder = new StringBuilder(url.getProtocol()).append("://")
                    .append(url.getHost().toLowerCase(Locale.ROOT));
            final int port = url.getPort();
            if (port > 0 && port != url.getDefaultPort()) {
                builder.append(':').append(port);
            }
            builder.append(url.getPath());
            return new URI(builder.toString());

        } catch (final URISyntaxException mue) {
            throw new OAuth1SignatureException(mue);
        }
    }

    /**
     * Assembles request base string for which a digital signature is to be
     * generated/verified, per section 9.1.3 of the OAuth 1.0 specification.
     *
     * @param request the request from which to assemble baseString.
     * @param params the OAuth authorization parameters from which to assemble baseString.
     * @return the concatenated baseString, ready to sign/verify
     */
    private String baseString(final OAuth1Request request,
                              final OAuth1Parameters params) throws OAuth1SignatureException {
        // HTTP request method
        final StringBuilder builder = new StringBuilder(request.getRequestMethod().toUpperCase(Locale.ROOT));

        // request URL, see section 3.4.1.2 http://tools.ietf.org/html/draft-hammer-oauth-10#section-3.4.1.2
        builder.append('&').append(UriComponent.encode(constructRequestURL(request).toASCIIString(),
                UriComponent.Type.UNRESERVED));

        // normalized request parameters, see section 3.4.1.3.2 http://tools.ietf.org/html/draft-hammer-oauth-10#section-3.4.1.3.2
        builder.append('&').append(UriComponent.encode(normalizeParameters(request, params),
                UriComponent.Type.UNRESERVED));

        return builder.toString();
    }

    /**
     * Retrieves an instance of a signature method that can be used to generate
     * or verify signatures for data.
     *
     * @return the retrieved signature method.
     * @throws UnsupportedSignatureMethodException if signature method not supported.
     */
    private OAuth1SignatureMethod getSignatureMethod(final OAuth1Parameters params)
            throws UnsupportedSignatureMethodException {
        final OAuth1SignatureMethod method = methods.get(params.getSignatureMethod());
        if (method == null) {
            throw new UnsupportedSignatureMethodException(params.getSignatureMethod());
        }
        return method;
    }

    private static void addParam(final String key, final String value, final List<String[]> list) {
        list.add(new String[] {
                UriComponent.encode(key, UriComponent.Type.UNRESERVED),
                value == null ? "" : UriComponent.encode(value, UriComponent.Type.UNRESERVED)
        });
    }

}

