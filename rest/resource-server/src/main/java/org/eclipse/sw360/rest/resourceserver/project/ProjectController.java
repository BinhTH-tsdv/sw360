/*
 * Copyright Siemens AG, 2017-2018.
 * Copyright Bosch Software Innovations GmbH, 2017.
 * Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.resourceserver.project;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TSimpleJSONProtocol;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.SW360Constants;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.thrift.MainlineState;
import org.eclipse.sw360.datahandler.thrift.ProjectReleaseRelationship;
import org.eclipse.sw360.datahandler.thrift.ReleaseRelationship;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentContent;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentType;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentUsage;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.components.ReleaseLink;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseInfo;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseInfoFile;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseInfoParsingResult;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseNameWithText;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.OutputFormatInfo;
import org.eclipse.sw360.datahandler.thrift.licenses.License;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectLink;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectRelationship;
import org.eclipse.sw360.datahandler.thrift.Source;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.VulnerabilityDTO;
import org.eclipse.sw360.rest.resourceserver.attachment.Sw360AttachmentService;
import org.eclipse.sw360.rest.resourceserver.component.Sw360ComponentService;
import org.eclipse.sw360.rest.resourceserver.core.HalResource;
import org.eclipse.sw360.rest.resourceserver.core.RestControllerHelper;
import org.eclipse.sw360.rest.resourceserver.license.Sw360LicenseService;
import org.eclipse.sw360.rest.resourceserver.licenseinfo.Sw360LicenseInfoService;
import org.eclipse.sw360.rest.resourceserver.release.Sw360ReleaseService;
import org.eclipse.sw360.rest.resourceserver.user.Sw360UserService;
import org.eclipse.sw360.rest.resourceserver.vulnerability.Sw360VulnerabilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.GsonJsonParser;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceProcessor;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.eclipse.sw360.datahandler.common.WrappedException.wrapTException;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;

@BasePathAwareController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ProjectController implements ResourceProcessor<RepositoryLinksResource> {
    public static final String PROJECTS_URL = "/projects";
    public static final String SW360_ATTACHMENT_USAGES = "sw360:attachmentUsages";
    private static final Logger log = Logger.getLogger(ProjectController.class);
    private static final TSerializer THRIFT_JSON_SERIALIZER = new TSerializer(new TSimpleJSONProtocol.Factory());
    private static final ImmutableMap<Project._Fields, String> mapOfFieldsTobeEmbedded = ImmutableMap.<Project._Fields, String>builder()
            .put(Project._Fields.CLEARING_TEAM, "clearingTeam")
            .put(Project._Fields.HOMEPAGE, "homepage")
            .put(Project._Fields.WIKI, "wiki")
            .put(Project._Fields.MODERATORS, "sw360:moderators")
            .put(Project._Fields.CONTRIBUTORS,"sw360:contributors")
            .put(Project._Fields.ATTACHMENTS,"sw360:attachments").build();

    @NonNull
    private final Sw360ProjectService projectService;

    @NonNull
    private final Sw360UserService userService;

    @NonNull
    private final Sw360ReleaseService releaseService;

    @NonNull
    private final Sw360LicenseService licenseService;

    @NonNull
    private final Sw360VulnerabilityService vulnerabilityService;

    @NonNull
    private final Sw360AttachmentService attachmentService;

    @NonNull
    private final Sw360LicenseInfoService licenseInfoService;

    @NonNull
    private final RestControllerHelper restControllerHelper;

    @NonNull
    private final Sw360ComponentService componentService;

    @RequestMapping(value = PROJECTS_URL, method = RequestMethod.GET)
    public ResponseEntity<Resources<Resource<Project>>> getProjectsForUser(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "type", required = false) String projectType,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "allDetails", required = false) boolean allDetails) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        Map<String, Project> mapOfProjects = new HashMap<>();
        boolean isSearchByName = name != null && !name.isEmpty();
        List<Project> sw360Projects = new ArrayList<>();
        if (isSearchByName) {
            sw360Projects.addAll(projectService.searchProjectByName(name, sw360User));
        } else {
            sw360Projects.addAll(projectService.getProjectsForUser(sw360User));
        }
        sw360Projects.stream().forEach(prj -> mapOfProjects.put(prj.getId(), prj));
        List<Resource<Project>> projectResources = new ArrayList<>();
        sw360Projects.stream()
                .filter(project -> projectType == null || projectType.equals(project.projectType.name()))
                .filter(project -> group == null || group.isEmpty() || group.equals(project.getBusinessUnit()))
                .filter(project -> tag == null || tag.isEmpty() || tag.equals(project.getTag()))
                .forEach(p -> {
                    Resource<Project> embeddedProjectResource = null;
                    if (!allDetails) {
                        Project embeddedProject = restControllerHelper.convertToEmbeddedProject(p);
                        embeddedProjectResource = new Resource<>(embeddedProject);
                    } else {
                        embeddedProjectResource = createHalProjectResourceWithAllDetails(p, sw360User, mapOfProjects,
                                !isSearchByName);
                        if (embeddedProjectResource == null) {
                            return;
                        }
                    }
                    projectResources.add(embeddedProjectResource);
                });

        Resources<Resource<Project>> resources = restControllerHelper.createResources(projectResources);
        HttpStatus status = resources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(resources, status);
    }

    @RequestMapping(value = PROJECTS_URL + "/{id}", method = RequestMethod.GET)
    public ResponseEntity<Resource<Project>> getProject(
            @PathVariable("id") String id) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        Project sw360Project = projectService.getProjectForUserById(id, sw360User);
        HalResource<Project> userHalResource = createHalProject(sw360Project, sw360User);
        return new ResponseEntity<>(userHalResource, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = PROJECTS_URL, method = RequestMethod.POST)
    public ResponseEntity createProject(
            @RequestBody Project project) throws URISyntaxException, TException {
        if (project.getReleaseIdToUsage() != null) {

            Map<String, ProjectReleaseRelationship> releaseIdToUsage = new HashMap<>();
            Map<String, ProjectReleaseRelationship> oriReleaseIdToUsage = project.getReleaseIdToUsage();
            for (String releaseURIString : oriReleaseIdToUsage.keySet()) {
                URI releaseURI = new URI(releaseURIString);
                String path = releaseURI.getPath();
                String releaseId = path.substring(path.lastIndexOf('/') + 1);
                releaseIdToUsage.put(releaseId, oriReleaseIdToUsage.get(releaseURIString));
            }
            project.setReleaseIdToUsage(releaseIdToUsage);
        }

        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        project = projectService.createProject(project, sw360User);
        HalResource<Project> halResource = createHalProject(project, sw360User);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(project.getId()).toUri();

        return ResponseEntity.created(location).body(halResource);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = PROJECTS_URL + "/{id}/releases", method = RequestMethod.POST)
    public ResponseEntity linkReleases(
            @PathVariable("id") String id,
            @RequestBody List<String> releaseURIs) throws URISyntaxException, TException {
        addOrPatchReleasesToProject(id, releaseURIs, false);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = PROJECTS_URL + "/{id}/releases", method = RequestMethod.PATCH)
    public ResponseEntity patchReleases(@PathVariable("id") String id, @RequestBody List<String> releaseURIs)
            throws URISyntaxException, TException {
        addOrPatchReleasesToProject(id, releaseURIs, true);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @RequestMapping(value = PROJECTS_URL + "/{id}/releases", method = RequestMethod.GET)
    public ResponseEntity<Resources<Resource<Release>>> getProjectReleases(
            @PathVariable("id") String id,
            @RequestParam(value = "transitive", required = false) String transitive) throws TException {

        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Set<String> releaseIds = projectService.getReleaseIds(id, sw360User, transitive);
        final Set<String> releaseIdsInBranch = new HashSet<>();
        boolean isTransitive = Boolean.parseBoolean(transitive);
        final List<Resource<Release>> releaseResources = new ArrayList<>();
        for (final String releaseId : releaseIds) {
            final Release sw360Release = releaseService.getReleaseForUserById(releaseId, sw360User);
            final Release embeddedRelease = restControllerHelper.convertToEmbeddedRelease(sw360Release);
            final HalResource<Release> releaseResource = new HalResource<>(embeddedRelease);
            if (isTransitive) {
                projectService.addEmbeddedlinkedRelease(sw360Release, sw360User, releaseResource, releaseService,
                        releaseIdsInBranch);
            }
            releaseResources.add(releaseResource);
        }

        final Resources<Resource<Release>> resources = restControllerHelper.createResources(releaseResources);
        HttpStatus status = resources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(resources, status);
    }

    @RequestMapping(value = PROJECTS_URL + "/{id}/releases/ecc", method = RequestMethod.GET)
    public ResponseEntity<Resources<Resource<Release>>> getECCsOfReleases(
            @PathVariable("id") String id,
            @RequestParam(value = "transitive", required = false) String transitive) throws TException {

        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Set<String> releaseIds = projectService.getReleaseIds(id, sw360User, transitive);

        final List<Resource<Release>> releaseResources = new ArrayList<>();
        for (final String releaseId : releaseIds) {
            final Release sw360Release = releaseService.getReleaseForUserById(releaseId, sw360User);
            Release embeddedRelease = restControllerHelper.convertToEmbeddedRelease(sw360Release);
            embeddedRelease.setEccInformation(sw360Release.getEccInformation());
            final Resource<Release> releaseResource = new Resource<>(embeddedRelease);
            releaseResources.add(releaseResource);
        }

        final Resources<Resource<Release>> resources = restControllerHelper.createResources(releaseResources);
        HttpStatus status = resources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(resources, status);
    }

    @RequestMapping(value = PROJECTS_URL + "/{id}/vulnerabilities", method = RequestMethod.GET)
    public ResponseEntity<Resources<Resource<VulnerabilityDTO>>> getVulnerabilitiesOfReleases(@PathVariable("id") String id) {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final List<VulnerabilityDTO> allVulnerabilityDTOs = vulnerabilityService.getVulnerabilitiesByProjectId(id, sw360User);

        final List<Resource<VulnerabilityDTO>> vulnerabilityResources = new ArrayList<>();
        for (final VulnerabilityDTO vulnerabilityDTO : allVulnerabilityDTOs) {
            final Resource<VulnerabilityDTO> vulnerabilityDTOResource = new Resource<>(vulnerabilityDTO);
            vulnerabilityResources.add(vulnerabilityDTOResource);
        }

        final Resources<Resource<VulnerabilityDTO>> resources = restControllerHelper
                .createResources(vulnerabilityResources);
        HttpStatus status = resources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(resources, status);
    }

    @RequestMapping(value = PROJECTS_URL + "/{id}/licenses", method = RequestMethod.GET)
    public ResponseEntity<Resources<Resource<License>>> getLicensesOfReleases(@PathVariable("id") String id) throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Project project = projectService.getProjectForUserById(id, sw360User);
        final List<Resource<License>> licenseResources = new ArrayList<>();
        final Set<String> allLicenseIds = new HashSet<>();

        final Set<String> releaseIdToUsage = project.getReleaseIdToUsage().keySet();
        for (final String releaseId : releaseIdToUsage) {
            final Release sw360Release = releaseService.getReleaseForUserById(releaseId, sw360User);
            final Set<String> licenseIds = sw360Release.getMainLicenseIds();
            if (licenseIds != null && !licenseIds.isEmpty()) {
                allLicenseIds.addAll(licenseIds);
            }
        }
        for (final String licenseId : allLicenseIds) {
            final License sw360License = licenseService.getLicenseById(licenseId);
            final License embeddedLicense = restControllerHelper.convertToEmbeddedLicense(sw360License);
            final Resource<License> licenseResource = new Resource<>(embeddedLicense);
            licenseResources.add(licenseResource);
        }

        final Resources<Resource<License>> resources = restControllerHelper.createResources(licenseResources);
        HttpStatus status = resources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(resources, status);
    }

    @RequestMapping(value = PROJECTS_URL + "/{id}/licenseinfo", method = RequestMethod.GET)
    public void downloadLicenseInfo(@PathVariable("id") String id,
                                    @RequestParam("generatorClassName") String generatorClassName,
                                    @RequestParam("variant") String variant,
                                    @RequestParam(value = "externalIds", required=false) String externalIds,
                                    HttpServletResponse response) throws TException, IOException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Project sw360Project = projectService.getProjectForUserById(id, sw360User);

        List<ProjectLink> mappedProjectLinks = projectService.createLinkedProjects(sw360Project,
                projectService.filterAndSortAttachments(SW360Constants.LICENSE_INFO_ATTACHMENT_TYPES), true, sw360User);

        List<AttachmentUsage> attchmntUsg = attachmentService.getAttachemntUsages(id);

        Map<Source, Set<String>> releaseIdToExcludedLicenses = attchmntUsg.stream()
                .collect(Collectors.toMap(AttachmentUsage::getOwner,
                        x -> x.getUsageData().getLicenseInfo().getExcludedLicenseIds(), (li1, li2) -> li1));

        Set<String> usedAttachmentContentIds = attchmntUsg.stream()
                .map(AttachmentUsage::getAttachmentContentId).collect(Collectors.toSet());

        final Map<String, Set<String>> selectedReleaseAndAttachmentIds = new HashMap<>();
        final Map<String, Set<LicenseNameWithText>> excludedLicensesPerAttachments = new HashMap<>();


        mappedProjectLinks.forEach(projectLink -> wrapTException(() ->
                projectLink.getLinkedReleases().stream().filter(ReleaseLink::isSetAttachments).forEach(releaseLink -> {
            String releaseLinkId = releaseLink.getId();
            Set<String> excludedLicenseIds = releaseIdToExcludedLicenses.get(Source.releaseId(releaseLinkId));

            if (!selectedReleaseAndAttachmentIds.containsKey(releaseLinkId)) {
                selectedReleaseAndAttachmentIds.put(releaseLinkId, new HashSet<>());
            }
            final List<Attachment> attachments = releaseLink.getAttachments();
            Release release = componentService.getReleaseById(releaseLinkId, sw360User);
            for (final Attachment attachment : attachments) {
                String attachemntContentId = attachment.getAttachmentContentId();
                if (usedAttachmentContentIds.contains(attachemntContentId)) {
                    List<LicenseInfoParsingResult> licenseInfoParsingResult = licenseInfoService
                            .getLicenseInfoForAttachment(release, sw360User, attachemntContentId);
                    excludedLicensesPerAttachments.put(attachemntContentId,
                            getExcludedLicenses(excludedLicenseIds, licenseInfoParsingResult));
                    selectedReleaseAndAttachmentIds.get(releaseLinkId).add(attachemntContentId);
                }
            }
        })));

        final String projectName = sw360Project.getName();
        final String projectVersion = sw360Project.getVersion();
        final String timestamp = SW360Utils.getCreatedOnTime().replaceAll("\\s", "_").replace(":", "_");
        String outputGeneratorClassNameWithVariant = generatorClassName+"::"+variant;
        final OutputFormatInfo outputFormatInfo = licenseInfoService.getOutputFormatInfoForGeneratorClass(generatorClassName);
        final String filename = String.format("%s-%s%s-%s.%s", Strings.nullToEmpty(variant).equals("DISCLOSURE") ? "LicenseInfo" : "ProjectClearingReport", projectName,
			StringUtils.isBlank(projectVersion) ? "" : "-" + projectVersion, timestamp,
			outputFormatInfo.getFileExtension());

        final LicenseInfoFile licenseInfoFile = licenseInfoService.getLicenseInfoFile(sw360Project, sw360User, outputGeneratorClassNameWithVariant, selectedReleaseAndAttachmentIds, excludedLicensesPerAttachments, externalIds);
        byte[] byteContent = licenseInfoFile.bufferForGeneratedOutput().array();
        response.setContentType(outputFormatInfo.getMimeType());
        response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename));
        FileCopyUtils.copy(byteContent, response.getOutputStream());
    }

    private Set<LicenseNameWithText> getExcludedLicenses(Set<String> excludedLicenseIds,
            List<LicenseInfoParsingResult> licenseInfoParsingResult) {

        Predicate<LicenseNameWithText> filteredLicense = licenseNameWithText -> excludedLicenseIds
                .contains(licenseNameWithText.getLicenseName());
        Function<LicenseInfo, Stream<LicenseNameWithText>> streamLicenseNameWithTexts = licenseInfo -> licenseInfo
                .getLicenseNamesWithTexts().stream();
        return licenseInfoParsingResult.stream().map(LicenseInfoParsingResult::getLicenseInfo)
                .flatMap(streamLicenseNameWithTexts).filter(filteredLicense).collect(Collectors.toSet());
    }

    @RequestMapping(value = PROJECTS_URL + "/{id}/attachments", method = RequestMethod.GET)
    public ResponseEntity<Resources<Resource<Attachment>>> getProjectAttachments(
            @PathVariable("id") String id) throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Project sw360Project = projectService.getProjectForUserById(id, sw360User);
        final Resources<Resource<Attachment>> resources = attachmentService.getResourcesFromList(sw360Project.getAttachments());
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    @RequestMapping(value = PROJECTS_URL + "/{projectId}/attachments/{attachmentId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void downloadAttachmentFromProject(
            @PathVariable("projectId") String projectId,
            @PathVariable("attachmentId") String attachmentId,
            HttpServletResponse response) throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Project project = projectService.getProjectForUserById(projectId, sw360User);
        this.attachmentService.downloadAttachmentWithContext(project, attachmentId, response, sw360User);
    }

    @RequestMapping(value = PROJECTS_URL + "/{projectId}/attachments/clearingReports", method = RequestMethod.GET, produces = "application/zip")
    public void downloadClearingReports(
            @PathVariable("projectId") String projectId,
            HttpServletResponse response) throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Project project = projectService.getProjectForUserById(projectId, sw360User);
        final String filename = "Clearing-Reports-" + project.getName() + ".zip";


        final Set<Attachment> attachments = project.getAttachments();
        final Set<AttachmentContent> clearingAttachments = new HashSet<>();
        for (final Attachment attachment : attachments) {
            if (attachment.getAttachmentType().equals(AttachmentType.CLEARING_REPORT)) {
                clearingAttachments.add(attachmentService.getAttachmentContent(attachment.getAttachmentContentId()));
            }
        }

        try (InputStream attachmentStream = attachmentService.getStreamToAttachments(clearingAttachments, sw360User, project)) {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename));
            FileCopyUtils.copy(attachmentStream, response.getOutputStream());
        } catch (final TException | IOException e) {
            log.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = PROJECTS_URL + "/{id}", method = RequestMethod.PATCH)
    public ResponseEntity<Resource<Project>> patchProject(
            @PathVariable("id") String id,
            @RequestBody Project updateProject) throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        Project sw360Project = projectService.getProjectForUserById(id, user);
        sw360Project = this.restControllerHelper.updateProject(sw360Project, updateProject);
        projectService.updateProject(sw360Project, user);
        HalResource<Project> userHalResource = createHalProject(sw360Project, user);
        return new ResponseEntity<>(userHalResource, HttpStatus.OK);
    }

    @RequestMapping(value = PROJECTS_URL + "/{projectId}/attachments", method = RequestMethod.POST, consumes = {"multipart/mixed", "multipart/form-data"})
    public ResponseEntity<HalResource> addAttachmentToProject(@PathVariable("projectId") String projectId,
                                                              @RequestPart("file") MultipartFile file,
                                                              @RequestPart("attachment") Attachment newAttachment) throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();

        Attachment attachment;
        try {
            attachment = attachmentService.uploadAttachment(file, newAttachment, sw360User);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }

        final Project project = projectService.getProjectForUserById(projectId, sw360User);
        project.addToAttachments(attachment);
        projectService.updateProject(project, sw360User);

        final HalResource<Project> halResource = createHalProject(project, sw360User);
        return new ResponseEntity<>(halResource, HttpStatus.OK);
    }

    @RequestMapping(value = PROJECTS_URL + "/searchByExternalIds", method = RequestMethod.GET)
    public ResponseEntity searchByExternalIds(@RequestParam MultiValueMap<String, String> externalIdsMultiMap) throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        return restControllerHelper.searchByExternalIds(externalIdsMultiMap, projectService, sw360User);
    }

    @RequestMapping(value = PROJECTS_URL + "/usedBy" + "/{id}", method = RequestMethod.GET)
    public ResponseEntity<Resources<Resource<Project>>> getUsedByProjectDetails(@PathVariable("id") String id) throws TException{
        User user = restControllerHelper.getSw360UserFromAuthentication();
        //Project sw360Project = projectService.getProjectForUserById(id, user);
        Set<Project> sw360Projects = projectService.searchLinkingProjects(id, user);

        List<Resource<Project>> projectResources = new ArrayList<>();
        sw360Projects.forEach(p -> {
                    Project embeddedProject = restControllerHelper.convertToEmbeddedProject(p);
                    projectResources.add(new Resource<>(embeddedProject));
                });

        Resources<Resource<Project>> resources = restControllerHelper.createResources(projectResources);
        HttpStatus status = resources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(resources, status);
    }

    @RequestMapping(value = PROJECTS_URL + "/{id}/attachmentUsage", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Map<String, Object>> getAttachmentUsage(@PathVariable("id") String id)
            throws TException {
        List<AttachmentUsage> attachmentUsages = attachmentService.getAllAttachmentUsage(id);
        String prefix = "{\"" + SW360_ATTACHMENT_USAGES + "\":[";
        String serializedUsages = attachmentUsages.stream()
                .map(usage -> wrapTException(() -> THRIFT_JSON_SERIALIZER.toString(usage)))
                .collect(Collectors.joining(",", prefix, "]}"));
        GsonJsonParser parser = new GsonJsonParser();
        Map<String, Object> attachmentUsageMap = parser.parseMap(serializedUsages);
        List<Map<String, Object>> listOfAttachmentUsages = (List<Map<String, Object>>) attachmentUsageMap
                .get(SW360_ATTACHMENT_USAGES);
        for (Map<String, Object> attachmentUsage : listOfAttachmentUsages) {
            attachmentUsage.remove("revision");
            attachmentUsage.remove("type");
        }

        if (listOfAttachmentUsages.isEmpty()) {
            attachmentUsageMap = null;
        }

        HttpStatus status = attachmentUsageMap == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(attachmentUsageMap, status);
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(linkTo(ProjectController.class).slash("api" + PROJECTS_URL).withRel("projects"));
        return resource;
    }

    private HalResource<Project> createHalProject(Project sw360Project, User sw360User) throws TException {
        HalResource<Project> halProject = new HalResource<>(sw360Project);
        User projectCreator = restControllerHelper.getUserByEmail(sw360Project.getCreatedBy());
        restControllerHelper.addEmbeddedUser(halProject, projectCreator, "createdBy");

        Map<String, ProjectReleaseRelationship> releaseIdToUsage = sw360Project.getReleaseIdToUsage();
        if (releaseIdToUsage != null) {
            restControllerHelper.addEmbeddedReleases(halProject, releaseIdToUsage.keySet(), releaseService, sw360User);
        }

        Map<String, ProjectRelationship> linkedProjects = sw360Project.getLinkedProjects();
        if (linkedProjects != null) {
            restControllerHelper.addEmbeddedProject(halProject, linkedProjects.keySet(), projectService, sw360User);
        }

        if (sw360Project.getModerators() != null) {
            Set<String> moderators = sw360Project.getModerators();
            restControllerHelper.addEmbeddedModerators(halProject, moderators);
        }

        if (sw360Project.getAttachments() != null) {
            restControllerHelper.addEmbeddedAttachments(halProject, sw360Project.getAttachments());
        }

        if(sw360Project.getLeadArchitect() != null) {
            restControllerHelper.addEmbeddedLeadArchitect(halProject, sw360Project.getLeadArchitect());
        }

        if (sw360Project.getContributors() != null) {
            Set<String> contributors = sw360Project.getContributors();
            restControllerHelper.addEmbeddedContributors(halProject, contributors);
        }

        return halProject;
    }

    private void addOrPatchReleasesToProject(String id, List<String> releaseURIs, boolean patch)
            throws URISyntaxException, TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        Project project = projectService.getProjectForUserById(id, sw360User);
        Map<String, ProjectReleaseRelationship> releaseIdToUsage = new HashMap<>();
        if (patch) {
            releaseIdToUsage = project.getReleaseIdToUsage();
        }

        for (String releaseURIString : releaseURIs) {
            URI releaseURI = new URI(releaseURIString);
            String path = releaseURI.getPath();
            String releaseId = path.substring(path.lastIndexOf('/') + 1);
            releaseIdToUsage.put(releaseId,
                    new ProjectReleaseRelationship(ReleaseRelationship.CONTAINED, MainlineState.OPEN));
        }
        project.setReleaseIdToUsage(releaseIdToUsage);
        projectService.updateProject(project, sw360User);
    }

    private HalResource<Project> createHalProjectResourceWithAllDetails(Project sw360Project, User sw360User,
            Map<String, Project> mapOfProjects, boolean isAllAccessibleProjectFetched) {
        Map<String, ProjectRelationship> linkedProjects = sw360Project.getLinkedProjects();
        if (!isLinkedProjectsVisible(linkedProjects, sw360User, mapOfProjects, isAllAccessibleProjectFetched)) {
            return null;
        }
        HalResource<Project> halProject = new HalResource<>(sw360Project);
        halProject.addEmbeddedResource("createdBy", sw360Project.getCreatedBy());

        for (Entry<Project._Fields, String> field : mapOfFieldsTobeEmbedded.entrySet()) {
            restControllerHelper.addEmbeddedFields(field.getValue(), sw360Project.getFieldValue(field.getKey()),
                    halProject);
        }

        return halProject;
    }

    private boolean isLinkedProjectsVisible(Map<String, ProjectRelationship> linkedProjects, User sw360User,
            Map<String, Project> mapOfProjects, boolean isAllAccessibleProjectFetched) {
        if (isAllAccessibleProjectFetched && !CommonUtils.isNullOrEmptyMap(linkedProjects)) {
            for (String linkedProjectId : linkedProjects.keySet()) {
                if (!mapOfProjects.containsKey(linkedProjectId)) {
                    return false;
                }
            }
        } else if (!CommonUtils.isNullOrEmptyMap(linkedProjects)) {
            for (String linkedProjectId : linkedProjects.keySet()) {
                if (!mapOfProjects.containsKey(linkedProjectId)) {
                    try {
                        projectService.getProjectForUserById(linkedProjectId, sw360User);
                    } catch (TException exp) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
