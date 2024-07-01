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
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestStatus;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestSummary;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.datahandler.thrift.vendors.VendorService;
import org.eclipse.sw360.rest.resourceserver.Sw360ResourceServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

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

    public Vendor createVendor(Vendor vendor) {
        try {
            VendorService.Iface sw360VendorClient = getThriftVendorClient();
            if (CommonUtils.isNullEmptyOrWhitespace(vendor.getFullname()) || CommonUtils.isNullEmptyOrWhitespace(vendor.getShortname())
                    || CommonUtils.isNullEmptyOrWhitespace(vendor.getUrl())) {
                throw new HttpMessageNotReadableException("A Vendor cannot have null or empty 'Full Name' or 'Short Name' or 'URL'!");
            }
            AddDocumentRequestSummary summary = sw360VendorClient.addVendor(vendor);
            if (AddDocumentRequestStatus.SUCCESS.equals(summary.getRequestStatus())) {
                vendor.setId(summary.getId());
                return vendor;
            } else if (AddDocumentRequestStatus.DUPLICATE.equals(summary.getRequestStatus())) {
                throw new DataIntegrityViolationException("A Vendor with same full name '" + vendor.getFullname() + "' and URL already exists!");
            } else if (AddDocumentRequestStatus.FAILURE.equals(summary.getRequestStatus())) {
                throw new HttpMessageNotReadableException(summary.getMessage());
            }
            return null;
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

    public RequestStatus updateVendor(Vendor vendor, User sw360User) throws TException {
        VendorService.Iface sw360VendorClient = getThriftVendorClient();
        RequestStatus requestStatus;
        requestStatus = sw360VendorClient.updateVendor(vendor, sw360User);
        if (requestStatus == RequestStatus.INVALID_INPUT) {
            throw new HttpMessageNotReadableException("Dependent document Id/ids not valid.");
        } else if (requestStatus == RequestStatus.NAMINGERROR) {
            throw new HttpMessageNotReadableException("Vendor name field cannot be empty or contain only whitespace character");
        } else if (requestStatus != RequestStatus.SUCCESS && requestStatus != RequestStatus.SENT_TO_MODERATOR) {
            throw new RuntimeException("sw360 vendor with name '" + vendor.getFullname() + " cannot be updated.");
        }
        return requestStatus;
    }

    public RequestStatus deleteVendor(String vendorId, User sw360User) throws TException {
        VendorService.Iface sw360VendorClient = getThriftVendorClient();
        return sw360VendorClient.deleteVendor(vendorId, sw360User);
    }

    private VendorService.Iface getThriftVendorClient() throws TTransportException {
        THttpClient thriftClient = new THttpClient(THRIFT_SERVER_URL);
        TProtocol protocol = new TCompactProtocol(thriftClient);
        return new VendorService.Client(protocol);
    }
}
