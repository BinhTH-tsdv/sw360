/*
 * Copyright Siemens AG, 2013-2018. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.users;

import com.liferay.portal.kernel.exception.NoSuchUserException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.model.Organization;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.search.Indexer;
import com.liferay.portal.kernel.search.IndexerRegistryUtil;
import com.liferay.portal.kernel.service.*;
import com.liferay.portal.kernel.service.persistence.RoleUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import org.eclipse.sw360.portal.common.ErrorMessages;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.portlet.PortletRequest;
import javax.servlet.http.HttpServletRequest;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author alex.borodin@evosoft.com
 */
public class UserPortletUtils {
    private static final Logger log = LogManager.getLogger(UserPortletUtils.class);
    private static final OrganizationHelper orgHelper = new OrganizationHelper();
    private UserPortletUtils() {
        // Utility class with only static functions
    }

    public static User addLiferayUser(HttpServletRequest request, String firstName, String lastName, String emailAddress, String organizationName, String roleName, boolean male, String externalId, String password, boolean passwordEncrypted, boolean activateImmediately) throws SystemException, PortalException {
        HttpServletRequestAdapter requestAdapter = new HttpServletRequestAdapter(request);
        return addLiferayUser(requestAdapter, firstName, lastName, emailAddress, organizationName, roleName, male, externalId, password, passwordEncrypted, activateImmediately);
    }

    public static User addLiferayUser(PortletRequest request, String firstName, String lastName, String emailAddress, String organizationName, String roleName, boolean male, String externalId, String password, boolean passwordEncrypted, boolean activateImmediately) throws SystemException, PortalException {
        PortletRequestAdapter requestAdapter = new PortletRequestAdapter(request);
        return addLiferayUser(requestAdapter, firstName, lastName, emailAddress, organizationName, roleName, male, externalId, password, passwordEncrypted, activateImmediately);
    }

    public static User updateLiferayUser(PortletRequest request, User user, String department, String primaryRole,
            String oldEmailId, String oldExternalId) {
        PortletRequestAdapter requestAdapter = new PortletRequestAdapter(request);
        return updateLiferayUser(requestAdapter, user, department, primaryRole, oldEmailId, oldExternalId);
    }

    public static User deactivateLiferayUser(User user, boolean deactivate) {
        user.setStatus(!deactivate ? WorkflowConstants.STATUS_APPROVED : WorkflowConstants.STATUS_INACTIVE);
        return UserLocalServiceUtil.updateUser(user);
    }

    private static User updateLiferayUser(RequestAdapter requestAdapter, User user, String department,
            String primaryRole, String oldEmailId, String oldExternalId) {
        long companyId = requestAdapter.getCompanyId();
        Consumer<String> errorMessagesConsumer = requestAdapter.getErrorMessagesConsumer();
        try {
            if (!user.getEmailAddress().equals(oldEmailId)) {
                boolean sameEmailExists = userByFieldExists(user.getEmailAddress(),
                        UserLocalServiceUtil::getUserByEmailAddress, companyId);
                if (sameEmailExists) {
                    log.info(ErrorMessages.EMAIL_ALREADY_EXISTS);
                    errorMessagesConsumer.accept(ErrorMessages.EMAIL_ALREADY_EXISTS);
                    return null;
                }
            }
            if (!user.getScreenName().equals(oldExternalId)) {
                boolean sameExternalIdExists = userByFieldExists(user.getScreenName(),
                        UserLocalServiceUtil::getUserByScreenName, companyId);
                if (sameExternalIdExists) {
                    log.info(ErrorMessages.EXTERNAL_ID_ALREADY_EXISTS);
                    errorMessagesConsumer.accept(ErrorMessages.EXTERNAL_ID_ALREADY_EXISTS);
                    return null;
                }
            }
        } catch (PortalException | SystemException e) {
            log.error(e);
            // won't try to create user if even checking for existing user failed
            return null;
        }

        try {
            String organizationName = orgHelper.mapOrganizationName(department);
            Organization organization = orgHelper.addOrGetOrganization(organizationName, companyId);
            log.info(String.format("Mapped orgcode %s to %s", department, organizationName));

            Role role = RoleLocalServiceUtil.getRole(companyId, primaryRole);
            final Role userRole = RoleLocalServiceUtil.getRole(companyId, "USER");
            long roleIdNew = role.getRoleId();
            long userRoleId = userRole.getRoleId();
            long[] roleIds = user.getRoleIds();
            if (roleIds != null) {
                for (long roleId : roleIds) {
                    if (userRoleId == roleId) {
                        continue;
                    }
                    UserLocalServiceUtil.deleteRoleUser(roleId, user.getUserId());
                }
            }
            UserGroupRoleLocalServiceUtil.deleteUserGroupRolesByUserId(user.getUserId());
            if (primaryRole.equalsIgnoreCase("Administrator") || primaryRole.equalsIgnoreCase("User")) {
                UserLocalServiceUtil.addRoleUser(roleIdNew, user.getUserId());
                RoleUtil.addUser(role.getRoleId(), user.getUserId());
            } else {
                ThemeDisplay themeDisplay = (ThemeDisplay) requestAdapter.getServiceContext().get()
                        .getLiferayPortletRequest().getAttribute(WebKeys.THEME_DISPLAY);
                UserGroupRoleLocalServiceUtil.addUserGroupRole(user.getUserId(), themeDisplay.getSiteGroupId(),
                        roleIdNew);
            }

            User userUpdated = UserLocalServiceUtil.updateUser(user);
            orgHelper.reassignUserToOrganizationIfNecessary(userUpdated, organization);

            Indexer indexer = IndexerRegistryUtil.getIndexer(User.class);
            indexer.reindex(userUpdated);
            return userUpdated;
        } catch (PortalException | SystemException e) {
            log.error(e);
            return null;
        }
    }

    private static User addLiferayUser(RequestAdapter requestAdapter, String firstName, String lastName, String emailAddress, String organizationName, String roleName, boolean male, String externalId, String password, boolean passwordEncrypted, boolean activateImmediately) throws SystemException, PortalException {
        long companyId = requestAdapter.getCompanyId();

        long organizationId = OrganizationLocalServiceUtil.getOrganizationId(companyId, organizationName);
        final Role role = RoleLocalServiceUtil.getRole(companyId, roleName);
        long roleId = role.getRoleId();

        try {
            if (userAlreadyExists(requestAdapter.getErrorMessagesConsumer(), emailAddress, externalId, companyId)){
                return null;
            }
        } catch (PortalException | SystemException e) {
            log.error(e);
            // won't try to create user if even checking for existing user failed
            return null;
        }

        try {
            long[] roleIds = roleId == 0 ? new long[]{} : new long[]{roleId};
            long[] organizationIds = organizationId == 0 ? new long[]{} : new long[]{organizationId};
            long[] userGroupIds = null;
            Optional<ServiceContext> serviceContextOpt = requestAdapter.getServiceContext();
            final ServiceContext serviceContext;
            if (!serviceContextOpt.isPresent()){
                return null;
            } else {
                serviceContext = serviceContextOpt.get();
            }
            User defaultUser = UserLocalServiceUtil.loadGetDefaultUser(companyId);
            User user = UserLocalServiceUtil.addUser(
                    defaultUser.getUserId()/*creator*/,
                    companyId,
                    false,/*autoPassword*/
                    password,/*password1*/
                    password,/*password2*/
                    false,/*autoScreenName*/
                    "",/*screenName*/
                    emailAddress,
                    defaultUser.getLocale(),
                    firstName,
                    ""/*middleName*/,
                    lastName,
                    0/*prefixId*/,
                    0/*suffixId*/,
                    male,
                    4/*birthdayMonth*/,
                    12/*birthdayDay*/,
                    1959/*birthdayYear*/,
                    ""/*jobTitle*/,
                    null/*groupIds*/,
                    organizationIds,
                    roleIds,
                    userGroupIds,
                    false/*sendEmail*/,
                    serviceContext);
            user.setPasswordReset(false);

            if (passwordEncrypted) {
                user.setPassword(password);
                user.setPasswordEncrypted(true);
            }

            RoleUtil.addUser(role.getRoleId(), user.getUserId());
            UserLocalServiceUtil.updateUser(user);
            RoleLocalServiceUtil.updateRole(role);

            User updatedUser = UserLocalServiceUtil.updateStatus(user.getUserId(), activateImmediately ? WorkflowConstants.STATUS_APPROVED : WorkflowConstants.STATUS_INACTIVE, serviceContext);
            Indexer indexer = IndexerRegistryUtil.getIndexer(User.class);
            indexer.reindex(user);
            return updatedUser;
        } catch (PortalException | SystemException e) {
            log.error(e);
            return null;
        }
    }

    private static boolean userAlreadyExists(Consumer<String> errorMessageConsumer, String emailAddress, String externalId, long companyId) throws PortalException, SystemException {
        boolean sameEmailExists = userByFieldExists(emailAddress, UserLocalServiceUtil::getUserByEmailAddress, companyId);
        boolean sameExternalIdExists = userByFieldExists(externalId, UserLocalServiceUtil::getUserByScreenName, companyId);
        boolean alreadyExists = sameEmailExists || sameExternalIdExists;

        if(alreadyExists) {
            String errorMessage;
            if(sameEmailExists) {
                errorMessage = ErrorMessages.EMAIL_ALREADY_EXISTS;
            } else {
                errorMessage = ErrorMessages.EXTERNAL_ID_ALREADY_EXISTS;
            }
            log.info(errorMessage);
            errorMessageConsumer.accept(errorMessage);
        }
        return alreadyExists;
    }

    private static boolean userByFieldExists(String searchParameter, UserSearchFunction searchFunction, long companyId) throws PortalException, SystemException {
        try {
            searchFunction.apply(companyId, searchParameter);
        } catch (NoSuchUserException nsue) {
            return false;
        }
        return true;
    }

    @FunctionalInterface
    interface UserSearchFunction {
        User apply(long companyId, String searchParameter) throws PortalException, SystemException;
    }
}
