/* VendorController */
/*
 * Copyright Siemens AG, 2017-2018. Part of the SW360 Portal Vendor.
 *
  * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.resourceserver.vendor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.thrift.licenses.License;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.rest.resourceserver.core.HalResource;
import org.eclipse.sw360.rest.resourceserver.core.RestControllerHelper;
import org.eclipse.sw360.rest.resourceserver.user.UserController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.eclipse.sw360.datahandler.resourcelists.PaginationParameterException;
import org.eclipse.sw360.datahandler.resourcelists.PaginationResult;
import org.eclipse.sw360.datahandler.resourcelists.ResourceClassNotFoundException;
import org.eclipse.sw360.datahandler.common.SW360Constants;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletResponse;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.eclipse.sw360.datahandler.common.WrappedException.wrapTException;

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
//    public ResponseEntity<EntityModel<Vendor>> getVendors() {
//        List<Vendor> vendors = vendorService.getVendors();
//        return new ResponseEntity<>(vendors, HttpStatus.OK);
//    }
//    public List<Vendor> getVendors() {
//        List<Vendor> vendors = vendorService.getVendors();
//        List<EntityModel<Vendor>> vendorResources = new ArrayList<>();
//        return vendorService.getVendors();
//    }
    public ResponseEntity<CollectionModel<EntityModel<Vendor>>> getVendors() throws TException {
        List<Vendor> vendors = vendorService.getVendors();

        List<EntityModel<Vendor>> vendorResources = new ArrayList<>();
        for (Vendor vendor : vendors) {
            //HalResource<Vendor> halVendor = restControllerHelper.addEmbeddedVendor(vendor);
            //HalResource halVendor = restControllerHelper.addEmbeddedVendor(vendor);
            Vendor embeddedVendor = restControllerHelper.convertToEmbeddedVendor(vendor);
            HalResource<Vendor> halVendor = new HalResource<>(embeddedVendor);

            Vendor vendorByFullName = vendorService.getVendorByFullName(vendor.getFullname());
            if(vendorByFullName != null) {
                Link vendorSelfLink = linkTo(UserController.class)
                        .slash("api" + VendorController.VENDORS_URL + "/" + vendorByFullName.getId()).withSelfRel();
                halVendor.add(vendorSelfLink);
            }

//            Vendor embeddedVendor = restControllerHelper.convertToEmbeddedVendor(vendor);
//            EntityModel<Vendor> vendorResource = EntityModel.of(embeddedVendor);
//            vendorResources.add(vendorResource);

//            Vendor vendorByFullName = Vendor vendor.fullname;

//            if(vendor.getFullname() != null) {
//                Link vendorSelfLink = linkTo(UserController.class)
//                        .slash("api" + VendorController.VENDORS_URL + "/" + vendor.getId()).withSelfRel();
//                halVendor.add(vendorSelfLink);
//            }

            vendorResources.add(halVendor);
        }

        CollectionModel<EntityModel<Vendor>> resources = CollectionModel.of(vendorResources);
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

//    public ResponseEntity<CollectionModel<EntityModel<Vendor>>> getVendors(
//            Pageable pageable,
//            HttpServletRequest request
//            ) throws TException, URISyntaxException, PaginationParameterException, ResourceClassNotFoundException {
//        List<Vendor> vendors = vendorService.getVendors();
//
//        PaginationResult<Vendor> paginationResult = restControllerHelper.createPaginationResult(request, pageable, vendors, SW360Constants.TYPE_VENDOR);
//        List<EntityModel<Vendor>> vendorResources = new ArrayList<>();
//        for (Vendor v: paginationResult.getResources()) {
//            Vendor embeddedVendor = restControllerHelper.convertToEmbeddedVendor(v);
//            vendorResources.add(EntityModel.of(embeddedVendor));
//        }
//
//        CollectionModel<EntityModel<Vendor>> resources;
//        if (vendors.size() == 0) {
//            resources = restControllerHelper.emptyPageResource(Vendor.class, paginationResult);
//        } else {
//            resources = restControllerHelper.generatePagesResource(paginationResult, vendorResources);
//        }
//
//        HttpStatus status = resources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
//        return new ResponseEntity<>(resources, status);
//    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(linkTo(VendorController.class).slash("api" + VENDORS_URL).withRel("vendors"));
        return resource;
    }
}
