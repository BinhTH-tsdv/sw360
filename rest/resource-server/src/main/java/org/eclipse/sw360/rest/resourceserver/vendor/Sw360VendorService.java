/* Sw360VendorService */
/*
 * Copyright Siemens AG, 2017. Part of the SW360 Portal Vendor.
 *
  * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.rest.resourceserver.vendor;

import lombok.RequiredArgsConstructor;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransportException;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestStatus;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestSummary;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.ThriftClients;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.datahandler.thrift.vendors.VendorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.List;

@Component
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class Sw360VendorService {
    private static final String THRIFT_SERVER_URL = "http://localhost:8080/vendors/thrift" ;

    // Retrieve all vendors
    public List<Vendor> getVendors() {
        try {
            return getThriftVendorClient().getAllVendors();
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

    // Retrieve vendor by ID
    public Vendor getVendorById(String vendorId) {
        try {
            return getThriftVendorClient().getByID(vendorId);
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

    // Retrieve vendor by full name
    public Vendor getVendorByFullName(String fullName) {
        try {
            for (Vendor vendor : getThriftVendorClient().getAllVendors()) {
                if (fullName.equals(vendor.getFullname())) {
                    return vendor;
                }
            }
            return null;
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

    // Helper method to create and return a Thrift client for the Vendor service
    private VendorService.Iface getThriftVendorClient() throws TTransportException {
        THttpClient thriftClient = new THttpClient(THRIFT_SERVER_URL);
        TProtocol protocol = new TCompactProtocol(thriftClient);
        return new VendorService.Client(protocol);
    }
}
