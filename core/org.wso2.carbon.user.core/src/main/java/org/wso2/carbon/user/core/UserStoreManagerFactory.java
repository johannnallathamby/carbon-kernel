package org.wso2.carbon.user.core;

import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;

import java.util.Map;

/**
 * Created by johann on 6/10/17.
 */
public interface UserStoreManagerFactory {

    UserStoreManager createUserStoreManager(RealmConfiguration realmConfiguration, ClaimManager claimManager, int
            tenantId);

}
