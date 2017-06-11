/*
 *  Copyright (c) 2005-2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.user.core.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.base.api.ServerConfigurationService;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.UserStoreConfigConstants;
import org.wso2.carbon.user.core.UserStoreManagerFactory;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.jdbc.JDBCUserStoreManager;
import org.wso2.carbon.user.core.ldap.ActiveDirectoryUserStoreManager;
import org.wso2.carbon.user.core.ldap.ReadOnlyLDAPUserStoreManager;
import org.wso2.carbon.user.core.ldap.ReadWriteLDAPUserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.tracker.UserStoreManagerRegistry;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.carbon.user.core.claim.ClaimManagerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @scr.component name="user.store.mgt.dscomponent" immediate=true
 * @scr.reference name="user.realmservice.default"
 * interface="org.wso2.carbon.user.core.service.RealmService" cardinality="1..1"
 * policy="dynamic" bind="setRealmService"
 * unbind="unsetRealmService"
 * @scr.reference name="server.configuration.service"
 * interface="org.wso2.carbon.base.api.ServerConfigurationService" cardinality="1..1"
 * policy="dynamic"  bind="setServerConfigurationService"
 * unbind="unsetServerConfigurationService"
 * @scr.reference name="claim.mgt.component"
 * interface="org.wso2.carbon.user.core.claim.ClaimManagerFactory" cardinality="0..1"
 * policy="dynamic"  bind="setClaimManagerFactory"
 * unbind="unsetClaimManagerFactory"
 * @scr.reference name="user.store.mgt.component"
 * interface="org.wso2.carbon.user.core.UserStoreManagerFactory" cardinality="0..1"
 * policy="dynamic"  bind="setUserStoreManagerFactory"
 * unbind="unsetUserStoreManagerFactory"
 */
public class UserStoreMgtDSComponent {
    private static Log log = LogFactory.getLog(UserStoreMgtDSComponent.class);
    private static RealmService realmService;
    private static ServerConfigurationService serverConfigurationService = null;
    private static ClaimManagerFactory claimManagerFactory = null;
    private static UserStoreManagerFactory userStoreManagerFactory = null;

    public static RealmService getRealmService() {
        return realmService;
    }

    protected void setRealmService(RealmService rlmService) {
        realmService = rlmService;
    }

    public static ServerConfigurationService getServerConfigurationService() {
        return UserStoreMgtDSComponent.serverConfigurationService;
    }

    protected void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
        UserStoreMgtDSComponent.serverConfigurationService = serverConfigurationService;
    }

    protected void activate(ComponentContext ctxt) {
        try {
            // We assume this component gets activated by super tenant
            PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext
                    .getThreadLocalCarbonContext();
            carbonContext.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
            carbonContext.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

            UserStoreManager jdbcUserStoreManager = new JDBCUserStoreManager();
            ctxt.getBundleContext().registerService(UserStoreManager.class.getName(), jdbcUserStoreManager, null);

            UserStoreManager readWriteLDAPUserStoreManager = new ReadWriteLDAPUserStoreManager();
            ctxt.getBundleContext().registerService(UserStoreManager.class.getName(), readWriteLDAPUserStoreManager, null);

            UserStoreManager readOnlyLDAPUserStoreManager = new ReadOnlyLDAPUserStoreManager();
            ctxt.getBundleContext().registerService(UserStoreManager.class.getName(), readOnlyLDAPUserStoreManager, null);

            UserStoreManager activeDirectoryUserStoreManager = new ActiveDirectoryUserStoreManager();
            ctxt.getBundleContext().registerService(UserStoreManager.class.getName(), activeDirectoryUserStoreManager, null);

            UserStoreManagerRegistry.init(ctxt.getBundleContext());

            log.info("Carbon UserStoreMgtDSComponent activated successfully.");
        } catch (Exception e) {
            log.error("Failed to activate Carbon UserStoreMgtDSComponent ", e);
        }
    }

    protected void deactivate(ComponentContext ctxt) {
        if (log.isDebugEnabled()) {
            log.debug("Carbon UserStoreMgtDSComponent is deactivated ");
        }
    }

    protected void unsetRealmService(RealmService realmService) {
        realmService = null;
    }

    protected void unsetServerConfigurationService(ServerConfigurationService serverConfigurationService) {
        if (log.isDebugEnabled()) {
            log.debug("Unsetting the ServerConfigurationService");
        }
        UserStoreMgtDSComponent.serverConfigurationService = null;
    }

    public static ClaimManagerFactory getClaimManagerFactory() {
        return UserStoreMgtDSComponent.claimManagerFactory;
    }

    protected void setClaimManagerFactory(ClaimManagerFactory claimManagerFactory) {
        this.claimManagerFactory = claimManagerFactory;
        try {
            if (claimManagerFactory.createClaimManager(MultitenantConstants.SUPER_TENANT_ID) != null) {
                ClaimManager claimManager = claimManagerFactory.createClaimManager(MultitenantConstants.SUPER_TENANT_ID);
                setClaimManager(realmService.getBootstrapRealm(), claimManager);
                setClaimManager(realmService.getBootstrapRealm().getUserStoreManager(), claimManager);
                RealmConfiguration secondaryRealmConfiguration = realmService.getBootstrapRealm()
                        .getRealmConfiguration().getSecondaryRealmConfig();
                if (secondaryRealmConfiguration != null) {
                    do {
                        String userDomain = secondaryRealmConfiguration.getUserStoreProperty(UserStoreConfigConstants
                                .DOMAIN_NAME);
                        setClaimManager(realmService.getBootstrapRealm().getUserStoreManager()
                                .getSecondaryUserStoreManager(userDomain), claimManager);

                        secondaryRealmConfiguration = secondaryRealmConfiguration.getSecondaryRealmConfig();
                    } while (secondaryRealmConfiguration != null);
                }
            }
        } catch (Exception e) {
            log.error("Error while setting claim manager from claim manager factory");
        }

    }

    protected void unsetClaimManagerFactory(ClaimManagerFactory claimManagerFactory) {
        UserStoreMgtDSComponent.claimManagerFactory = null;
    }

    private void setClaimManager(Object object, ClaimManager claimManager) {
        try {
            Class<?> currentClass = object.getClass();
            Method method = null;
            while (currentClass != null && method == null) {
                try {
                    method = currentClass.getDeclaredMethod("setClaimManager", ClaimManager.class);
                } catch (NoSuchMethodException e) {
                    // method not present - try super class
                    currentClass = currentClass.getSuperclass();
                }
            }
            if (method != null) {
                method.setAccessible(true);
                method.invoke(object, claimManager);
                log.info("Claim manager set for " + object.getClass());
                method.setAccessible(false);
            } else {
                throw new NoSuchMethodException();
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.error("No claim manager setter found for " + object.getClass() + " or its supper classes");
        }

    }

    public static UserStoreManagerFactory getUserStoreManagerFactory() {
        return UserStoreMgtDSComponent.userStoreManagerFactory;
    }

    protected void setUserStoreManagerFactory(UserStoreManagerFactory userStoreManagerFactory) {
        this.userStoreManagerFactory = userStoreManagerFactory;
        try {
            org.wso2.carbon.user.core.UserStoreManager userStoreManager =
                    userStoreManagerFactory.createUserStoreManager(realmService.getBootstrapRealmConfiguration(),
                                                                   realmService.getBootstrapRealm().getClaimManager(),
                                                                   MultitenantConstants.SUPER_TENANT_ID);
            setUserStoreManager(realmService.getBootstrapRealm(), userStoreManager);
        } catch (Exception e) {
            log.error("Error while setting claim manager from claim manager factory");
        }

    }

    protected void unsetUserStoreManagerFactory(UserStoreManagerFactory userStoreManagerFactory) {
        UserStoreMgtDSComponent.userStoreManagerFactory = null;
    }

    private void setUserStoreManager(Object object, UserStoreManager userStoreManager) {
        try {
            Class<?> currentClass = object.getClass();
            Method method = null;
            try {
                method = currentClass.getDeclaredMethod("setUserStoreManager",
                                                        org.wso2.carbon.user.core.UserStoreManager.class);
            } catch (NoSuchMethodException e) {
                // method not present - try super class
            }
            if (method != null) {
                method.setAccessible(true);
                method.invoke(object, userStoreManager);
                log.info("UserStoreManager set for " + object.getClass());
                method.setAccessible(false);
            } else {
                throw new NoSuchMethodException();
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.error("No user store manager setter found for " + object.getClass());
        }

    }
}
