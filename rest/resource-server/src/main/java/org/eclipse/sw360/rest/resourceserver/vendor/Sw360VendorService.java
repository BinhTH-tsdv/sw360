/* Sw360VendorService */
/*
 * Copyright (C) TOSHIBA CORPORATION, 2024. Part of the SW360 Frontend Project.
 * Copyright (C) Toshiba Software Development (Vietnam) Co., Ltd., 2024. Part of the SW360 Frontend Project.
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
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.datahandler.thrift.vendors.VendorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;

@Component
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class Sw360VendorService {
    private static final String THRIFT_SERVER_URL = "http://localhost:8080/vendors/thrift" ;

    public List<Vendor> getVendors() {
        try {
            return getThriftVendorClient().getAllVendors();
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

    public Vendor getVendorById(String vendorId) {
        try {
            return getThriftVendorClient().getByID(vendorId);
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

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

    private VendorService.Iface getThriftVendorClient() throws TTransportException {
        THttpClient thriftClient = new THttpClient(THRIFT_SERVER_URL);
        TProtocol protocol = new TCompactProtocol(thriftClient);
        return new VendorService.Client(protocol);
    }
}
