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
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.datahandler.thrift.vendors.VendorDTO;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import org.eclipse.sw360.datahandler.resourcelists.ResourceClassNotFoundException ;
import org.eclipse.sw360.datahandler.resourcelists.PaginationParameterException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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

    @PostMapping(value = VENDORS_URL)
    public ResponseEntity<?> createVendor(
            @RequestBody Vendor vendor
    ) {
        vendor = vendorService.createVendor(vendor);
        HalResource<Vendor> halResource = new HalResource<>(vendor);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(vendor.getId()).toUri();

        return ResponseEntity.created(location).body(halResource);
    }

    @GetMapping(value = VENDORS_URL + "/{id}")
    public ResponseEntity<EntityModel<Vendor>> getVendor(
            @PathVariable("id") String id
    ) {
        Vendor vendor = vendorService.getVendorById(id);
        HalResource<Vendor> halResource = new HalResource<>(vendor);
        return new ResponseEntity<>(halResource, HttpStatus.OK);
    }

    @PatchMapping(value = VENDORS_URL + "/{id}")
    public ResponseEntity<EntityModel<Vendor>> patchVendor(
            @PathVariable("id") String id,
            @RequestBody VendorDTO updateVendorDto
    ) throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        Vendor sw360Vendor = vendorService.getVendorById(id);
        sw360Vendor = this.restControllerHelper.updateVendor(sw360Vendor, updateVendorDto);
        vendorService.updateVendor(sw360Vendor, user);
        HalResource<Vendor> halResource = new HalResource<>(sw360Vendor);
        return new ResponseEntity<>(halResource, HttpStatus.OK);
    }

    @DeleteMapping(value = VENDORS_URL + "/{id:.+}")
    public ResponseEntity deleteVendor(
            @PathVariable("id") String id
    ) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        vendorService.deleteVendor(id, sw360User);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(linkTo(VendorController.class).slash("api" + VENDORS_URL).withRel("vendors"));
        return resource;
    }
}
