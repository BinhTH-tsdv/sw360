/*
 * Copyright Siemens AG, 2021. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.db;

import com.cloudant.client.api.CloudantClient;
import com.google.gson.Gson;
import org.eclipse.sw360.datahandler.cloudantclient.DatabaseConnectorCloudant;
import org.eclipse.sw360.datahandler.cloudantclient.DatabaseInstanceCloudant;
import org.eclipse.sw360.datahandler.couchdb.lucene.NouveauLuceneAwareDatabaseConnector;
import org.eclipse.sw360.datahandler.thrift.moderation.ModerationRequest;
import org.eclipse.sw360.nouveau.designdocument.NouveauDesignDocument;
import org.eclipse.sw360.nouveau.designdocument.NouveauIndexDesignDocument;
import org.eclipse.sw360.nouveau.designdocument.NouveauIndexFunction;
import org.ektorp.http.HttpClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.eclipse.sw360.common.utils.SearchUtils.OBJ_ARRAY_TO_STRING_INDEX;
import static org.eclipse.sw360.nouveau.LuceneAwareCouchDbConnector.DEFAULT_DESIGN_PREFIX;

public class ModerationSearchHandler {

    private static final String DDOC_NAME = DEFAULT_DESIGN_PREFIX + "/lucene";

    private static final NouveauIndexDesignDocument luceneSearchView
        = new NouveauIndexDesignDocument("moderations",
            new NouveauIndexFunction(
                "function(doc) {" +
                OBJ_ARRAY_TO_STRING_INDEX +
                "    if(!doc.type || doc.type != 'moderation' || !doc.documentId) return;" +
                "    arrayToStringIndex(doc.moderators, 'moderators');" +
                "    if(doc.documentName && typeof(doc.documentName) == 'string' && doc.documentName.length > 0) {" +
                "      index('text', 'documentName', doc.documentName, {'store': true});" +
                "    }" +
                "    if(doc.componentType && typeof(doc.componentType) == 'string' && doc.componentType.length > 0) {" +
                "      index('text', 'componentType', doc.componentType, {'store': true});" +
                "    }" +
                "    if(doc.requestingUser && typeof(doc.requestingUser) == 'string' && doc.requestingUser.length > 0) {" +
                "      index('text', 'requestingUser', doc.requestingUser, {'store': true});" +
                "    }" +
                "    if(doc.componentType && typeof(doc.requestingUserDepartment) == 'string' && doc.requestingUserDepartment.length > 0) {" +
                "      index('text', 'requestingUserDepartment', doc.requestingUserDepartment, {'store': true});" +
                "    }" +
                "    if(doc.moderationState && typeof(doc.moderationState) == 'string' && doc.moderationState.length > 0) {" +
                "      index('text', 'moderationState', doc.moderationState, {'store': true});" +
                "    }" +
                "    if(doc.componentType && typeof(doc.componentType) == 'string' && doc.componentType.length > 0) {" +
                "      index('text', 'componentType', doc.componentType, {'store': true});" +
                "    }" +
                "    if(doc.timestamp) {"+
                "      var dt = new Date(doc.timestamp); "+
                "      var formattedDt = `${dt.getFullYear()}${(dt.getMonth()+1).toString().padStart(2,'0')}${dt.getDate().toString().padStart(2,'0')}`;" +
                "      index('double', 'timestamp', formattedDt, {'store': true});"+
                "    }" +
                "}"));
    private final NouveauLuceneAwareDatabaseConnector connector;

    public ModerationSearchHandler(Supplier<HttpClient> httpClient, Supplier<CloudantClient> cClient, String dbName) throws IOException {
        DatabaseConnectorCloudant db = new DatabaseConnectorCloudant(cClient, dbName);
        connector = new NouveauLuceneAwareDatabaseConnector(db, cClient, DDOC_NAME);
        Gson gson = (new DatabaseInstanceCloudant(cClient)).getClient().getGson();
        NouveauDesignDocument searchView = new NouveauDesignDocument();
        searchView.setId(DDOC_NAME);
        searchView.addNouveau(luceneSearchView, gson);
        connector.addDesignDoc(searchView);
    }

    public List<ModerationRequest> search(String text, final Map<String, Set<String>> subQueryRestrictions ) {
        return connector.searchViewWithRestrictions(ModerationRequest.class, luceneSearchView.getIndexName(),
                text, subQueryRestrictions);
    }
}
