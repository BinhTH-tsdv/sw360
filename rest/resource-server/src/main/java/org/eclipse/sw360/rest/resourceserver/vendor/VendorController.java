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

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.rest.resourceserver.core.HalResource;
import org.eclipse.sw360.rest.resourceserver.core.RestControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.eclipse.sw360.datahandler.common.SW360Constants;
import org.eclipse.sw360.datahandler.resourcelists.PaginationResult;
import javax.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.sw360.datahandler.resourcelists.ResourceClassNotFoundException ;
import org.eclipse.sw360.datahandler.resourcelists.PaginationParameterException;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@BasePathAwareController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@RestController
@SecurityRequirement(name = "tokenAuth")
public class VendorController implements RepresentationModelProcessor<RepositoryLinksResource> {
    public static final String VENDORS_URL = "/vendors";

    @NonNull
    private final Sw360VendorService vendorService;

    @NonNull
    private final RestControllerHelper restControllerHelper;

    @GetMapping(value = VENDORS_URL)
    public ResponseEntity<CollectionModel<EntityModel<Vendor>>> getVendors(
            Pageable pageable, HttpServletRequest request
    ) throws TException, ResourceClassNotFoundException, PaginationParameterException, URISyntaxException {
        List<Vendor> vendors = vendorService.getVendors();
        PaginationResult<Vendor> paginationResult = restControllerHelper.createPaginationResult(request, pageable, vendors, SW360Constants.TYPE_VENDOR);
        List<EntityModel<Vendor>> vendorResources = new ArrayList<>();
        for (Vendor vendor : paginationResult.getResources()) {
            /*
            * Return type: vendor
            * Vendor embeddedVendor = restControllerHelper.convertToEmbeddedVendor(vendor);
            * EntityModel<Vendor> vendorResource = EntityModel.of(embeddedVendor);
            * vendorResources.add(vendorResource);
            */

            /*
            * Don't return type: vendor
            */
            Vendor embeddedVendor = restControllerHelper.convertToEmbeddedVendor(vendor);
            HalResource<Vendor> halVendor = new HalResource<>(embeddedVendor);
            vendorResources.add(halVendor);

        }
        CollectionModel<EntityModel<Vendor>> resources;
        if (vendors.size() == 0) {
            resources = restControllerHelper.emptyPageResource(Vendor.class, paginationResult);
        } else {
            resources = restControllerHelper.generatePagesResource(paginationResult, vendorResources);
        }
        HttpStatus status = resources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(resources, status);
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(linkTo(VendorController.class).slash("api" + VENDORS_URL).withRel("vendors"));
        return resource;
    }
}
