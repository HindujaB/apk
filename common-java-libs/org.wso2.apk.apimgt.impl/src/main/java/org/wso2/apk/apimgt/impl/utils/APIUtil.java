/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.apk.apimgt.impl.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.apache.xerces.util.SecurityManager;
import org.everit.json.schema.Schema;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.wso2.apk.apimgt.api.APIManagementException;
import org.wso2.apk.apimgt.api.ErrorHandler;
import org.wso2.apk.apimgt.api.ExceptionCodes;
import org.wso2.apk.apimgt.api.LoginPostExecutor;
import org.wso2.apk.apimgt.api.NewPostLoginExecutor;
import org.wso2.apk.apimgt.api.model.APIStatus;
import org.wso2.apk.apimgt.api.model.Environment;
import org.wso2.apk.apimgt.api.model.KeyManagerConnectorConfiguration;
import org.wso2.apk.apimgt.api.model.OperationPolicyData;
import org.wso2.apk.apimgt.api.model.OperationPolicyDefinition;
import org.wso2.apk.apimgt.api.model.OperationPolicySpecification;
import org.wso2.apk.apimgt.api.model.Scope;
import org.wso2.apk.apimgt.api.model.Tier;
import org.wso2.apk.apimgt.api.model.VHost;
import org.wso2.apk.apimgt.impl.APIConstants;
import org.wso2.apk.apimgt.impl.APIManagerAnalyticsConfiguration;
import org.wso2.apk.apimgt.impl.APIManagerConfigurationServiceImpl;
import org.wso2.apk.apimgt.impl.ConfigurationHolder;
import org.wso2.apk.apimgt.impl.config.APIMConfigService;
import org.wso2.apk.apimgt.impl.config.APIMConfigServiceImpl;
import org.wso2.apk.apimgt.impl.dao.ScopesDAO;
import org.wso2.apk.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.apk.apimgt.impl.proxy.ExtendedProxyRoutePlanner;
import org.wso2.apk.apimgt.user.exceptions.UserException;
import org.wso2.apk.apimgt.user.mgt.internal.UserManagerHolder;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.net.ssl.SSLContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * This class contains the utility methods used by the implementations of APIManager, APIProvider
 * and APIConsumer interfaces.
 */
public final class APIUtil {

    private static final Log log = LogFactory.getLog(APIUtil.class);

    private static final Log audit = LogFactory.getLog("AUDIT_LOG");
    public static final String ERROR_WHILE_RETRIEVING_TENANT_DOMAIN = "Error while retrieving tenant domain values from user store";

    public static final String DISABLE_ROLE_VALIDATION_AT_SCOPE_CREATION = "disableRoleValidationAtScopeCreation";

    private static final int ENTITY_EXPANSION_LIMIT = 0;
    private static volatile Set<String> allowedScopes;
    private static boolean isPublisherRoleCacheEnabled = true;

    public static final String STRICT = "Strict";
    public static final String ALLOW_ALL = "AllowAll";
    public static final String DEFAULT_AND_LOCALHOST = "DefaultAndLocalhost";
    public static final String HOST_NAME_VERIFIER = "httpclient.hostnameVerifier";
    public static String multiGrpAppSharing = null;

    private static final String CONFIG_ELEM_OAUTH = "OAuth";
    private static final String REVOKE = "revoke";
    private static final String TOKEN = "token";

    private static final String SHA256_WITH_RSA = "SHA256withRSA";
    private static final String NONE = "NONE";
    private static final String SUPER_TENANT_SUFFIX =
            APIConstants.EMAIL_DOMAIN_SEPARATOR + APIConstants.SUPER_TENANT_DOMAIN;

    private static final int IPV4_ADDRESS_BIT_LENGTH = 32;
    private static final int IPV6_ADDRESS_BIT_LENGTH = 128;

    public static final String TENANT_IDLE_TIME = "tenant.idle.time";
    public static final String UI_PERMISSION_ACTION = "ui.execute";

    private static Schema tenantConfigJsonSchema;
    private static Schema operationPolicySpecSchema;

    private static APIMConfigService apimConfigService;

    private APIUtil() {

    }

    private static String hostAddress = null;
    private static final int timeoutInSeconds = 15;
    private static final int retries = 2;

    //constants for getting masked token
    private static final int MAX_LEN = 36;
    private static final int MAX_VISIBLE_LEN = 8;
    private static final int MIN_VISIBLE_LEN_RATIO = 5;
    private static final String MASK_CHAR = "X";

    /**
     * To initialize the publisherRoleCache configurations, based on configurations.
     */
    public static void init() throws APIManagementException {

        ConfigurationHolder apiManagerConfiguration = ServiceReferenceHolder.getInstance()
                .getAPIManagerConfigurationService().getAPIManagerConfiguration();
        String isPublisherRoleCacheEnabledConfiguration = apiManagerConfiguration
                .getFirstProperty(APIConstants.PUBLISHER_ROLE_CACHE_ENABLED);
        isPublisherRoleCacheEnabled = isPublisherRoleCacheEnabledConfiguration == null || Boolean
                .parseBoolean(isPublisherRoleCacheEnabledConfiguration);
    }

    public static APIStatus getApiStatus(String status) throws APIManagementException {

        APIStatus apiStatus = null;
        for (APIStatus aStatus : APIStatus.values()) {
            if (aStatus.getStatus().equalsIgnoreCase(status)) {
                apiStatus = aStatus;
            }
        }
        return apiStatus;
    }

    public static void handleException(String msg) throws APIManagementException {

        log.error(msg);
        throw new APIManagementException(msg);
    }

    public static void handleException(String msg, Throwable t) throws APIManagementException {

        log.error(msg, t);
        throw new APIManagementException(msg, t);
    }

    public static void handleExceptionWithCode(String msg, ErrorHandler code) throws APIManagementException {

        log.error(msg);
        throw new APIManagementException(msg, code);
    }

    public static void handleExceptionWithCode(String msg, Throwable t, ErrorHandler code) throws APIManagementException {

        log.error(msg, t);
        throw new APIManagementException(msg, t, code);
    }

    /**
     * Sorts the list of tiers according to the number of requests allowed per minute in each tier in descending order.
     *
     * @param tiers - The list of tiers to be sorted
     * @return - The sorted list.
     */
    public static List<Tier> sortTiers(Set<Tier> tiers) {

        List<Tier> tierList = new ArrayList<Tier>();
        tierList.addAll(tiers);
        Collections.sort(tierList);
        return tierList;
    }

    /**
     * Check if document visibility levels are enabled
     *
     * @return True if document visibility levels are enabled
     */
    public static boolean isDocVisibilityLevelsEnabled() {

        // checking if Doc visibility levels enabled in api-manager.xml
        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().
                getAPIManagerConfiguration().getFirstProperty(
                        APIConstants.API_PUBLISHER_ENABLE_API_DOC_VISIBILITY_LEVELS).equals("true");

    }

    /**
     * Checks whether the specified user has the specified permission.
     *
     * @param userNameWithoutChange A username
     * @param permission            A valid Carbon permission
     * @throws APIManagementException If the user does not have the specified permission or if an error occurs
     */
    public static boolean hasPermission(String userNameWithoutChange, String permission)
            throws APIManagementException {

        boolean authorized = false;
        if (userNameWithoutChange == null) {
            throw new APIManagementException(ExceptionCodes.ANON_USER_ACTION);
        }

        if (isPermissionCheckDisabled()) {
            log.debug("Permission verification is disabled by APIStore configuration");
            authorized = true;
            return authorized;
        }

        if (APIConstants.Permissions.APIM_ADMIN.equals(permission)) {
            Integer value = getValueFromCache(APIConstants.API_PUBLISHER_ADMIN_PERMISSION_CACHE, userNameWithoutChange);
            if (value != null) {
                return value == 1;
            }
        }

        try {
            String tenantDomain = getTenantDomain(userNameWithoutChange);
            //TODO fix tenant flow
//            PrivilegedCarbonContext.startTenantFlow();
//            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
            int tenantId = UserManagerHolder.getUserManager().getTenantId(tenantDomain);
            authorized = UserManagerHolder.getUserManager().isUserAuthorized(tenantId,
                    getTenantAwareUsername(userNameWithoutChange), permission,
                    UI_PERMISSION_ACTION);
            if (APIConstants.Permissions.APIM_ADMIN.equals(permission)) {
                addToRolesCache(APIConstants.API_PUBLISHER_ADMIN_PERMISSION_CACHE, userNameWithoutChange,
                        authorized ? 1 : 2);
            }

        } catch (UserException e) {
            throw new APIManagementException("Error while checking the user:" + userNameWithoutChange
                    + " authorized or not", e, ExceptionCodes.USERSTORE_INITIALIZATION_FAILED);
        }

        return authorized;
    }

    /**
     * Checks whether the disablePermissionCheck parameter enabled
     *
     * @return boolean
     */
    public static boolean isPermissionCheckDisabled() {


        String disablePermissionCheck = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                .getAPIManagerConfiguration().getFirstProperty(APIConstants.API_STORE_DISABLE_PERMISSION_CHECK);
        if (disablePermissionCheck == null) {
            return false;
        }
        return Boolean.parseBoolean(disablePermissionCheck);
    }

    /**
     * Retrieves the role list of a user
     *
     * @param username A username
     * @param username A username
     * @throws APIManagementException If an error occurs
     */
    public static String[] getListOfRoles(String username) throws APIManagementException {

        if (username == null) {
            throw new APIManagementException(ExceptionCodes.ANON_USER_ACTION);
        }

        String[] roles = getValueFromCache(APIConstants.API_USER_ROLE_CACHE, username);
        if (roles != null) {
            return roles;
        }
        try {
            String tenantDomain = getTenantDomain(username);
            int tenantId = UserManagerHolder.getUserManager().getTenantId(tenantDomain);
            roles = UserManagerHolder.getUserManager().getRoleListOfUser(tenantId,
                    getTenantAwareUsername(username));
            addToRolesCache(APIConstants.API_USER_ROLE_CACHE, username, roles);
            return roles;
        } catch (UserException e) {
            throw new APIManagementException("UserStoreException while trying the role list of the user " + username,
                    e, ExceptionCodes.USERSTORE_INITIALIZATION_FAILED);
        }
    }

    /**
     * Check whether user is exist
     *
     * @param username A username
     * @throws APIManagementException If an error occurs
     */
    public static boolean isUserExist(String username) throws APIManagementException {

        if (username == null) {
            throw new APIManagementException("Attempt to execute privileged operation as the anonymous user",
                    ExceptionCodes.ANON_USER_ACTION);
        }
        try {
            String tenantDomain = getTenantDomain(username);
            String tenantAwareUserName = getTenantAwareUsername(username);
            int tenantId = UserManagerHolder.getUserManager().getTenantId(tenantDomain);
            return UserManagerHolder.getUserManager().isExistingUser(tenantId, tenantAwareUserName);
        } catch (UserException e) {
            throw new APIManagementException("UserStoreException while trying the user existence " + username, e,
                    ExceptionCodes.USERSTORE_INITIALIZATION_FAILED);
        }
    }

    /**
     * To add the value to a cache.
     *
     * @param cacheName - Name of the Cache
     * @param key       - Key of the entry that need to be added.
     * @param value     - Value of the entry that need to be added.
     */
    protected static <T> void addToRolesCache(String cacheName, String key, T value) {

        if (isPublisherRoleCacheEnabled) {
            if (log.isDebugEnabled()) {
                log.debug("Publisher role cache is enabled, adding the roles for the " + key + " to the cache "
                        + cacheName + "'");
            }
            //TODO: APK
//            Caching.getCacheManager(APIConstants.API_MANAGER_CACHE_MANAGER).getCache(cacheName).put(key, value);
        }
    }

    /**
     * To get the value from the cache.
     *
     * @param cacheName Name of the cache.
     * @param key       Key of the cache entry.
     * @return Role list from the cache, if a values exists, otherwise null.
     */
    protected static <T> T getValueFromCache(String cacheName, String key) {

        if (isPublisherRoleCacheEnabled) {
            if (log.isDebugEnabled()) {
                log.debug("Publisher role cache is enabled, retrieving the roles for  " + key + " from the cache "
                        + cacheName + "'");
            }
            //TODO: APK
//            Cache<String, T> rolesCache = Caching.getCacheManager(APIConstants.API_MANAGER_CACHE_MANAGER)
//                    .getCache(cacheName);
//            return rolesCache.get(key);
        }
        return null;
    }

    /**
     * When an input is having '@',replace it with '-AT-' [This is required to persist API data in registry,as registry
     * paths don't allow '@' sign.]
     *
     * @param input inputString
     * @return String modifiedString
     */
    public static String replaceEmailDomain(String input) {

        if (input != null && input.contains(APIConstants.EMAIL_DOMAIN_SEPARATOR)) {
            input = input.replace(APIConstants.EMAIL_DOMAIN_SEPARATOR, APIConstants.EMAIL_DOMAIN_SEPARATOR_REPLACEMENT);
        }
        return input;
    }

    /**
     * When an input is having '-AT-',replace it with @ [This is required to persist API data between registry and database]
     *
     * @param input inputString
     * @return String modifiedString
     */
    public static String replaceEmailDomainBack(String input) {

        if (input != null && input.contains(APIConstants.EMAIL_DOMAIN_SEPARATOR_REPLACEMENT)) {
            input = input.replace(APIConstants.EMAIL_DOMAIN_SEPARATOR_REPLACEMENT,
                    APIConstants.EMAIL_DOMAIN_SEPARATOR);
        }
        return input;
    }

    private static JsonElement getFileBaseTenantConfig() throws APIManagementException {

        try {
            byte[] localTenantConfFileData = getLocalTenantConfFileData();
            String tenantConfDataStr = new String(localTenantConfFileData, Charset.defaultCharset());
            JsonParser jsonParser = new JsonParser();
            return jsonParser.parse(tenantConfDataStr);
        } catch (IOException e) {
            throw new APIManagementException("Error while retrieving file base tenant-config", e);
        }
    }

    /**
     * Gets the byte content of the local tenant-conf.json
     *
     * @return byte content of the local tenant-conf.json
     * @throws IOException error while reading local tenant-conf.json
     */
    private static byte[] getLocalTenantConfFileData() throws IOException {

        //TODO handle config files properly
        return new byte[1];
    }

    public static boolean isAnalyticsEnabled() {

        return APIManagerAnalyticsConfiguration.getInstance().isAnalyticsEnabled();
    }

    public static String removeAnySymbolFromUriTempate(String uriTemplate) {

        if (uriTemplate != null) {
            int anySymbolIndex = uriTemplate.indexOf("/*");
            if (anySymbolIndex != -1) {
                return uriTemplate.substring(0, anySymbolIndex);
            }
        }
        return uriTemplate;
    }

    /**
     * Check if tenant is available
     *
     * @param tenantDomain tenant Domain
     * @return isTenantAvailable
     * @throws UserException
     */
    public static boolean isTenantAvailable(String tenantDomain) throws UserException {

        return UserManagerHolder.getUserManager().isTenantAvailable(tenantDomain);
    }

    public static int getInternalOrganizationId(String organization) throws APIManagementException {

        //TODO handle configs
//        return getOrganizationResolver().getInternalId(organization);
        return -1234; //This is a dummy return value
    }

    /**
     * check whether given role is exist
     *
     * @param userName logged user
     * @param roleName role name need to check
     * @return true if exist and false if not
     * @throws APIManagementException If an error occurs
     */
    public static boolean isRoleNameExist(String userName, String roleName) {

        if (roleName == null || StringUtils.isEmpty(roleName.trim())) {
            return true;
        }

        //disable role validation if "disableRoleValidationAtScopeCreation" system property is set
        String disableRoleValidation = System.getProperty(DISABLE_ROLE_VALIDATION_AT_SCOPE_CREATION);
        if (Boolean.parseBoolean(disableRoleValidation)) {
            return true;
        }

        try {
            int tenantId = UserManagerHolder.getUserManager().getTenantId(getTenantDomain(userName));

            String[] roles = roleName.split(",");
            for (String role : roles) {
                if (!UserManagerHolder.getUserManager().isExistingRole(tenantId, role.trim())) {
                    return false;
                }
            }
        } catch (UserException | APIManagementException e) {
            log.error("Error when getting the list of roles", e);
            return false;
        }
        return true;
    }

    /**
     * Helper method to get tenantId from userName
     *
     * @param userName user name
     * @return tenantId
     */
    public static int getTenantId(String userName) throws APIManagementException {

        String tenantDomain = null;
        //get tenant domain from user name
        tenantDomain = getTenantDomain(userName);
        return getTenantIdFromTenantDomain(tenantDomain);
    }

    /**
     * Helper method to get tenantId from tenantDomain
     *
     * @param tenantDomain tenant Domain
     * @return tenantId
     */
    public static int getTenantIdFromTenantDomain(String tenantDomain) {

        if (tenantDomain == null) {
            return APIConstants.SUPER_TENANT_ID;
        }
        try {
            return getInternalOrganizationId(tenantDomain);
        } catch (APIManagementException e) {
            log.error(e.getMessage(), e);
        }
        return -1;
    }

    /**
     * Helper method to get tenantId from organization
     *
     * @param organization Organization
     * @return tenantId
     */
    public static int getInternalIdFromTenantDomainOrOrganization(String organization) {

        if (organization == null) {
            return APIConstants.SUPER_TENANT_ID;
        }
        try {
            return getInternalOrganizationId(organization);
        } catch (APIManagementException e) {
            log.error(e.getMessage(), e);
        }
        return -1;
    }

    /**
     * Helper method to get tenantDomain from tenantId
     *
     * @param tenantId tenant Id
     * @return tenantId
     */
    public static String getTenantDomainFromTenantId(int tenantId) {

        try {
            return UserManagerHolder.getUserManager().getTenantDomainByTenantId(tenantId);
        } catch (UserException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Returns true if sequence is set
     *
     * @param sequence
     * @return
     */
    public static boolean isSequenceDefined(String sequence) {

        return sequence != null && !"none".equals(sequence) && !StringUtils.isEmpty(sequence);
    }

    /**
     * Find scope object in a set based on the key
     *
     * @param scopes - Set of scopes
     * @param key    - Key to search with
     * @return Scope - scope object
     */
    public static Scope findScopeByKey(Set<Scope> scopes, String key) {

        for (Scope scope : scopes) {
            if (scope.getKey().equals(key)) {
                return scope;
            }
        }
        return null;
    }

    /**
     * Read the REST API group id extractor class reference from api-manager.xml.
     *
     * @return REST API group id extractor class reference.
     */
    public static String getRESTApiGroupingExtractorImplementation() {

        ConfigurationHolder config = new APIManagerConfigurationServiceImpl(new ConfigurationHolder())
                .getAPIManagerConfiguration();
        String restApiGroupingExtractor = config
                .getFirstProperty(APIConstants.API_STORE_REST_API_GROUP_EXTRACTOR_IMPLEMENTATION);
        if (StringUtils.isEmpty(restApiGroupingExtractor)) {
            restApiGroupingExtractor = getGroupingExtractorImplementation();
        }
        return restApiGroupingExtractor;
    }

    /**
     * Read the group id extractor class reference from api-manager.xml.
     *
     * @return group id extractor class reference.
     */
    public static String getGroupingExtractorImplementation() {

        ConfigurationHolder config = new APIManagerConfigurationServiceImpl(new ConfigurationHolder())
                .getAPIManagerConfiguration();
        return config.getFirstProperty(APIConstants.API_STORE_GROUP_EXTRACTOR_IMPLEMENTATION);
    }

    /**
     * Return a http client instance
     *
     * @param url - server url
     * @return
     */

    public static HttpClient getHttpClient(String url) throws APIManagementException {

        URL configUrl = null;
        try {
            configUrl = new URL(url);
        } catch (MalformedURLException e) {
            handleExceptionWithCode("URL is malformed",
                    e, ExceptionCodes.from(ExceptionCodes.URI_PARSE_ERROR, "Malformed url"));
        }
        int port = configUrl.getPort();
        String protocol = configUrl.getProtocol();
        return getHttpClient(port, protocol);
    }

    /**
     * Return a PoolingHttpClientConnectionManager instance
     *
     * @param protocol- service endpoint protocol. It can be http/https
     * @return PoolManager
     */
    private static PoolingHttpClientConnectionManager getPoolingHttpClientConnectionManager(String protocol)
            throws APIManagementException {

        PoolingHttpClientConnectionManager poolManager;
        if (APIConstants.HTTPS_PROTOCOL.equals(protocol)) {
            SSLConnectionSocketFactory socketFactory = createSocketFactory();
            org.apache.http.config.Registry<ConnectionSocketFactory> socketFactoryRegistry =
                    RegistryBuilder.<ConnectionSocketFactory>create()
                            .register(APIConstants.HTTPS_PROTOCOL, socketFactory).build();
            poolManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        } else {
            poolManager = new PoolingHttpClientConnectionManager();
        }
        return poolManager;
    }

    private static SSLConnectionSocketFactory createSocketFactory() throws APIManagementException {

        SSLContext sslContext;

        String keyStorePath = "";
        //TODO handle configuration
//                CarbonUtils.getServerConfiguration().getFirstProperty(APIConstants.TRUST_STORE_LOCATION);
        try {
            KeyStore trustStore =
                    //TODO handle configs
//                    ServiceReferenceHolder.getInstance().getTrustStore();
                    KeyStore.getInstance("JKS"); //Dummy instantiation
            sslContext = SSLContexts.custom().loadTrustMaterial(trustStore).build();

            X509HostnameVerifier hostnameVerifier;
            String hostnameVerifierOption = System.getProperty(HOST_NAME_VERIFIER);

            if (ALLOW_ALL.equalsIgnoreCase(hostnameVerifierOption)) {
                hostnameVerifier = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
            } else if (STRICT.equalsIgnoreCase(hostnameVerifierOption)) {
                hostnameVerifier = SSLSocketFactory.STRICT_HOSTNAME_VERIFIER;
            } else {
                hostnameVerifier = SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
            }

            return new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
        } catch (KeyStoreException e) {
            handleException("Failed to read from Key Store", e);
        } catch (NoSuchAlgorithmException e) {
            handleException("Failed to load Key Store from " + keyStorePath, e);
        } catch (KeyManagementException e) {
            handleException("Failed to load key from" + keyStorePath, e);
        }

        return null;
    }

    /**
     * Return a http client instance
     *
     * @param port      - server port
     * @param protocol- service endpoint protocol http/https
     * @return
     */
    public static HttpClient getHttpClient(int port, String protocol) {

        ConfigurationHolder configuration = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                .getAPIManagerConfiguration();

        String maxTotal = configuration
                .getFirstProperty(APIConstants.HTTP_CLIENT_MAX_TOTAL);
        String defaultMaxPerRoute = configuration
                .getFirstProperty(APIConstants.HTTP_CLIENT_DEFAULT_MAX_PER_ROUTE);

        String proxyEnabled = configuration.getFirstProperty(APIConstants.PROXY_ENABLE);
        String proxyHost = configuration.getFirstProperty(APIConstants.PROXY_HOST);
        String proxyPort = configuration.getFirstProperty(APIConstants.PROXY_PORT);
        String proxyUsername = configuration.getFirstProperty(APIConstants.PROXY_USERNAME);
        String proxyPassword = configuration.getFirstProperty(APIConstants.PROXY_PASSWORD);
        String nonProxyHosts = configuration.getFirstProperty(APIConstants.NON_PROXY_HOSTS);
        String proxyProtocol = configuration.getFirstProperty(APIConstants.PROXY_PROTOCOL);

        if (proxyProtocol != null) {
            protocol = proxyProtocol;
        }

        PoolingHttpClientConnectionManager pool = null;
        try {
            pool = getPoolingHttpClientConnectionManager(protocol);
        } catch (APIManagementException e) {
            log.error("Error while getting http client connection manager", e);
        }
        pool.setMaxTotal(Integer.parseInt(maxTotal));
        pool.setDefaultMaxPerRoute(Integer.parseInt(defaultMaxPerRoute));

        RequestConfig params = RequestConfig.custom().build();
        HttpClientBuilder clientBuilder = HttpClients.custom().setConnectionManager(pool)
                .setDefaultRequestConfig(params);

        if (Boolean.parseBoolean(proxyEnabled)) {
            HttpHost host = new HttpHost(proxyHost, Integer.parseInt(proxyPort), protocol);
            DefaultProxyRoutePlanner routePlanner;
            if (!StringUtils.isBlank(nonProxyHosts)) {
                routePlanner = new ExtendedProxyRoutePlanner(host, configuration);
            } else {
                routePlanner = new DefaultProxyRoutePlanner(host);
            }
            clientBuilder = clientBuilder.setRoutePlanner(routePlanner);
            if (!StringUtils.isBlank(proxyUsername) && !StringUtils.isBlank(proxyPassword)) {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(new AuthScope(proxyHost, Integer.parseInt(proxyPort)),
                        new UsernamePasswordCredentials(proxyUsername, proxyPassword));
                clientBuilder = clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }
        }
        return clientBuilder.build();
    }

    /**
     * Gets the  class given the class name.
     *
     * @param className the fully qualified name of the class.
     * @return an instance of the class with the given name
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */

    public static Object getClassInstance(String className) throws ClassNotFoundException, IllegalAccessException,
            InstantiationException {

        return getClassForName(className).newInstance();
    }

    /**
     * Gets the  class given the class name.
     *
     * @param className the fully qualified name of the class.
     * @return an instance of the class with the given name
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */

    public static Class<?> getClassForName(String className) throws ClassNotFoundException {

        return Class.forName(className);
    }

    /**
     * @param tenantDomain Tenant domain to be used to get configurations for REST API scopes
     * @return JSON object which contains configuration for REST API scopes
     * @throws APIManagementException
     */
    public static JSONObject getTenantRESTAPIScopesConfig(String tenantDomain) throws APIManagementException {

        JSONObject restAPIConfigJSON = null;
        JSONObject tenantConfJson = getTenantConfig(tenantDomain);
        if (tenantConfJson != null) {
            restAPIConfigJSON = getRESTAPIScopesFromTenantConfig(tenantConfJson);
            if (restAPIConfigJSON == null) {
                throw new APIManagementException("RESTAPIScopes config does not exist for tenant "
                        + tenantDomain, ExceptionCodes.CONFIG_NOT_FOUND);
            }
        }
        return restAPIConfigJSON;
    }

    /**
     * @param tenantDomain Tenant domain to be used to get configurations for REST API scopes
     * @return JSON object which contains configuration for REST API scopes
     * @throws APIManagementException
     */
    public static JSONObject getTenantRESTAPIScopeRoleMappingsConfig(String tenantDomain) throws APIManagementException {

        JSONObject restAPIConfigJSON = null;
        JSONObject tenantConfJson = getTenantConfig(tenantDomain);
        if (tenantConfJson != null) {
            restAPIConfigJSON = getRESTAPIScopeRoleMappingsFromTenantConfig(tenantConfJson);
            if (restAPIConfigJSON == null) {
                if (log.isDebugEnabled()) {
                    log.debug("No REST API role mappings are defined for the tenant " + tenantDomain);
                }
            }
        }
        return restAPIConfigJSON;
    }

    /**
     * Returns the tenant-conf.json in JSONObject format for the given tenant(id) from the registry.
     *
     * @param organization organization
     * @return tenant-conf.json in JSONObject format for the given tenant(id)
     * @throws APIManagementException when tenant-conf.json is not available in registry
     */
    public static JSONObject getTenantConfig(String organization) throws APIManagementException {

        //TODO implement with suitable cache
//        Cache tenantConfigCache = CacheProvider.getTenantConfigCache();
//        String cacheName = organization + "_" + APIConstants.TENANT_CONFIG_CACHE_NAME;
//        if (tenantConfigCache.containsKey(cacheName)) {
//            return (JSONObject) tenantConfigCache.get(cacheName);
//        } else {

        String tenantConfig = getAPIMConfigService().getTenantConfig(organization);
        if (StringUtils.isNotEmpty(tenantConfig)) {
            try {
                JSONObject jsonObject = (JSONObject) new JSONParser().parse(tenantConfig);
                //TODO implement with suitable cache
//                    tenantConfigCache.put(cacheName, jsonObject);
                return jsonObject;
            } catch (ParseException e) {
                throw new APIManagementException("Error occurred while converting tenant-conf to json", e,
                        ExceptionCodes.JSON_PARSE_ERROR);
            }
        }
        return new JSONObject();
//        }
    }

    private static JSONObject getRESTAPIScopesFromTenantConfig(JSONObject tenantConf) {

        return (JSONObject) tenantConf.get(APIConstants.REST_API_SCOPES_CONFIG);
    }

    private static JSONObject getRESTAPIScopeRoleMappingsFromTenantConfig(JSONObject tenantConf) {

        return (JSONObject) tenantConf.get(APIConstants.REST_API_ROLE_MAPPINGS_CONFIG);
    }

    /**
     * This method gets the RESTAPIScopes configuration from REST_API_SCOPE_CACHE if available, if not from
     * tenant-conf.json in registry.
     *
     * @param tenantDomain tenant domain name
     * @return Map of scopes which contains scope names and associated role list
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getRESTAPIScopesForTenant(String tenantDomain) {

        //TODO: APK
        Map<String, String> restAPIScopes = null;
//        restAPIScopes = (Map) Caching.getCacheManager(APIConstants.API_MANAGER_CACHE_MANAGER)
//                .getCache(APIConstants.REST_API_SCOPE_CACHE)
//                .get(tenantDomain);
        if (restAPIScopes == null) {
            try {
                restAPIScopes = APIUtil.getRESTAPIScopesFromConfig(APIUtil.getTenantRESTAPIScopesConfig(tenantDomain),
                        APIUtil.getTenantRESTAPIScopeRoleMappingsConfig(tenantDomain));
                //call load tenant config for rest API.
                //then put cache
//                Caching.getCacheManager(APIConstants.API_MANAGER_CACHE_MANAGER)
//                        .getCache(APIConstants.REST_API_SCOPE_CACHE)
//                        .put(tenantDomain, restAPIScopes);
            } catch (APIManagementException e) {
                log.error("Error while getting REST API scopes for tenant: " + tenantDomain, e);
            }
        }
        return restAPIScopes;
    }

    /**
     * This method gets the RESTAPIScopes configuration from tenant-conf.json in registry. Role Mappings (Role aliases
     * will not be substituted to the scope/role mappings)
     *
     * @param tenantDomain Tenant domain
     * @return RESTAPIScopes configuration without substituting role mappings
     * @throws APIManagementException error while getting RESTAPIScopes configuration
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getRESTAPIScopesForTenantWithoutRoleMappings(String tenantDomain)
            throws APIManagementException {

        return APIUtil.getRESTAPIScopesFromConfig(APIUtil.getTenantRESTAPIScopesConfig(tenantDomain), null);
    }

    /**
     * @param scopesConfig JSON configuration object with scopes and associated roles
     * @param roleMappings JSON Configuration object with role mappings
     * @return Map of scopes which contains scope names and associated role list
     */
    public static Map<String, String> getRESTAPIScopesFromConfig(JSONObject scopesConfig, JSONObject roleMappings) {

        Map<String, String> scopes = new HashMap<String, String>();
        JSONArray scopesArray = (JSONArray) scopesConfig.get("Scope");
        for (Object scopeObj : scopesArray) {
            JSONObject scope = (JSONObject) scopeObj;
            String scopeName = scope.get(APIConstants.REST_API_SCOPE_NAME).toString();
            String scopeRoles = scope.get(APIConstants.REST_API_SCOPE_ROLE).toString();
            if (roleMappings != null) {
                if (log.isDebugEnabled()) {
                    log.debug("REST API scope role mappings exist. Hence proceeding to swap original scope roles "
                            + "for mapped scope roles.");
                }
                //split role list string read using comma separator
                List<String> originalRoles = Arrays.asList(scopeRoles.split("\\s*,\\s*"));
                List<String> mappedRoles = new ArrayList<String>();
                for (String role : originalRoles) {
                    String mappedRole = (String) roleMappings.get(role);
                    if (mappedRole != null) {
                        if (log.isDebugEnabled()) {
                            log.debug(role + " was mapped to " + mappedRole);
                        }
                        mappedRoles.add(mappedRole);
                    } else {
                        mappedRoles.add(role);
                    }
                }
                scopeRoles = String.join(",", mappedRoles);
            }
            scopes.put(scopeName, scopeRoles);
        }
        return scopes;
    }

    /**
     * Used to get access control allowed headers according to the api-manager.xml
     *
     * @return access control allowed headers string
     */
    public static String getAllowedHeaders() {

        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_HEADERS);
    }

    /**
     * Used to get access control allowed methods define in api-manager.xml
     *
     * @return access control allowed methods string
     */
    public static String getAllowedMethods() {

       return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_METHODS);
    }

    /**
     * Used to get access control allowed origins define in api-manager.xml
     *
     * @return allow origins list defined in api-manager.xml
     */
    public static String getAllowedOrigins() {

        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_ORIGIN);
    }

    public static byte[] toByteArray(InputStream is) throws IOException {

        return IOUtils.toByteArray(is);
    }

    /**
     * Returns a secured DocumentBuilderFactory instance
     *
     * @return DocumentBuilderFactory
     */
    public static DocumentBuilderFactory getSecuredDocumentBuilder() {

        org.apache.xerces.impl.Constants Constants = null;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        try {
            dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
            dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
            dbf.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.LOAD_EXTERNAL_DTD_FEATURE, false);
        } catch (ParserConfigurationException e) {
            log.error(
                    "Failed to load XML Processor Feature " + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE + " or " +
                            Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE + " or " + Constants.LOAD_EXTERNAL_DTD_FEATURE);
        }

        SecurityManager securityManager = new SecurityManager();
        securityManager.setEntityExpansionLimit(ENTITY_EXPANSION_LIMIT);
        dbf.setAttribute(Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY, securityManager);

        return dbf;
    }

    /**
     * Logs an audit message on actions performed on entities (APIs, Applications, etc). The log is printed in the
     * following JSON format
     * {
     * "typ": "API",
     * "action": "update",
     * "performedBy": "admin@carbon.super",
     * "info": {
     * "name": "Twitter",
     * "context": "/twitter",
     * "version": "1.0.0",
     * "provider": "nuwan"
     * }
     * }
     *
     * @param entityType  - The entity type. Ex: API, Application
     * @param entityInfo  - The details of the entity. Ex: API Name, Context
     * @param action      - The type of action performed. Ex: Create, Update
     * @param performedBy - The user who performs the action.
     */
    public static void logAuditMessage(String entityType, String entityInfo, String action, String performedBy) {

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("typ", entityType);
        jsonObject.put("action", action);
        jsonObject.put("performedBy", performedBy);
        jsonObject.put("info", entityInfo);
        audit.info(StringEscapeUtils.unescapeJava(jsonObject.toString()));
    }

    /**
     * To check whether given role exist in the array of roles.
     *
     * @param userRoleList      Role list to check against.
     * @param accessControlRole Access Control Role.
     * @return true if the Array contains the role specified.
     */
    public static boolean compareRoleList(String[] userRoleList, String accessControlRole) {

        if (userRoleList != null) {
            for (String userRole : userRoleList) {
                if (userRole.equalsIgnoreCase(accessControlRole)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Used in application sharing to check if this featuer is enabled
     *
     * @return returns true if ENABLE_MULTIPLE_GROUPID is set to True
     */
    public static boolean isMultiGroupAppSharingEnabled() {

        if (multiGrpAppSharing == null) {

            ConfigurationHolder config = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().
                    getAPIManagerConfiguration();

            String groupIdExtractorClass = config.getFirstProperty(
                    APIConstants.API_STORE_GROUP_EXTRACTOR_IMPLEMENTATION);

            if (groupIdExtractorClass != null && !groupIdExtractorClass.isEmpty()) {
                try {

                    LoginPostExecutor groupingExtractor =
                            (LoginPostExecutor) APIUtil.getClassInstance(groupIdExtractorClass);

                    if (groupingExtractor instanceof NewPostLoginExecutor) {
                        multiGrpAppSharing = "true";
                    } else {
                        multiGrpAppSharing = "false";
                    }
                    // if there is a exception the default flow will work hence ingnoring the applications
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                    multiGrpAppSharing = "false";
                }
            } else {
                multiGrpAppSharing = "false";
            }
        }
        return Boolean.valueOf(multiGrpAppSharing);
    }

    /**
     * This method is used to get the authorization configurations from the tenant registry or from api-manager.xml if
     * config is not available in tenant registry
     *
     * @param organization The organization
     * @param property     The configuration to get from tenant registry or api-manager.xml
     * @return The configuration read from tenant registry or api-manager.xml
     * @throws APIManagementException Throws if the registry resource doesn't exist
     *                                or the content cannot be parsed to JSON
     */
    public static String getOAuthConfiguration(String organization, String property)
            throws APIManagementException {

        String authConfigValue = APIUtil
                .getOAuthConfigurationFromTenantRegistry(organization, property);
        if (StringUtils.isBlank(authConfigValue)) {
            authConfigValue = APIUtil.getOAuthConfigurationFromAPIMConfig(property);
        }
        return authConfigValue;
    }

    /**
     * This method is used to get the authorization configurations from the tenant registry
     *
     * @param organization organization.
     * @param property     The configuration to get from tenant registry
     * @return The configuration read from tenant registry or else null
     * @throws APIManagementException Throws if the registry resource doesn't exist
     *                                or the content cannot be parsed to JSON
     */
    public static String getOAuthConfigurationFromTenantRegistry(String organization, String property)
            throws APIManagementException {

        JSONObject tenantConfig = getTenantConfig(organization);
        //Read the configuration from the tenant registry
        String oAuthConfiguration = "";
        if (null != tenantConfig.get(property)) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(tenantConfig.get(property));
            oAuthConfiguration = stringBuilder.toString();
        }

        if (!StringUtils.isBlank(oAuthConfiguration)) {
            return oAuthConfiguration;
        }

        return null;
    }

    /**
     * This method is used to get the authorization configurations from the api manager configurations
     *
     * @param property The configuration to get from api-manager.xml
     * @return The configuration read from api-manager.xml or else null
     */
    public static String getOAuthConfigurationFromAPIMConfig(String property) {

        //If tenant registry doesn't have the configuration, then read it from api-manager.xml
        String oAuthConfiguration = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                .getAPIManagerConfiguration().getFirstProperty(APIConstants.OAUTH_CONFIGS + property);

        if (!StringUtils.isBlank(oAuthConfiguration)) {
            return oAuthConfiguration;
        }

        return null;
    }

    /**
     * Validate the input file name for invalid path elements
     *
     * @param fileName File name
     */
    public static void validateFileName(String fileName) throws APIManagementException {

        if (!fileName.isEmpty() && (fileName.contains("../") || fileName.contains("..\\"))) {
            handleException("File name contains invalid path elements. " + fileName);
        }
    }

    /**
     * Util method to call SP rest api to invoke queries.
     *
     * @param appName SP app name that the query should run against
     * @param query   query
     * @return jsonObj JSONObject of the response
     * @throws APIManagementException
     */
    public static JSONObject executeQueryOnStreamProcessor(String appName, String query) throws APIManagementException {

        String spEndpoint = APIManagerAnalyticsConfiguration.getInstance().getDasServerUrl() + "/stores/query";
        String spUserName = APIManagerAnalyticsConfiguration.getInstance().getDasServerUser();
        String spPassword = APIManagerAnalyticsConfiguration.getInstance().getDasServerPassword();
        byte[] encodedAuth = Base64
                .encodeBase64((spUserName + ":" + spPassword).getBytes(Charset.forName("ISO-8859-1")));
        String authHeader = "Basic " + new String(encodedAuth);
        URL spURL;
        try {
            spURL = new URL(spEndpoint);

            HttpClient httpClient = APIUtil.getHttpClient(spURL.getPort(), spURL.getProtocol());
            HttpPost httpPost = new HttpPost(spEndpoint);

            httpPost.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
            JSONObject obj = new JSONObject();
            obj.put("appName", appName);
            obj.put("query", query);

            if (log.isDebugEnabled()) {
                log.debug("Request from SP: " + obj.toJSONString());
            }

            StringEntity requestEntity = new StringEntity(obj.toJSONString(), ContentType.APPLICATION_JSON);

            httpPost.setEntity(requestEntity);

            HttpResponse response;
            try {
                response = httpClient.execute(httpPost);
                HttpEntity entity = response.getEntity();
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    String error = "Error while invoking SP rest api :  " + response.getStatusLine().getStatusCode()
                            + " " + response.getStatusLine().getReasonPhrase();
                    log.error(error);
                    throw new APIManagementException(error,
                            ExceptionCodes.from(ExceptionCodes.ERROR_INVOKING_SP_REST_API, error));
                }
                String responseStr = EntityUtils.toString(entity);
                if (log.isDebugEnabled()) {
                    log.debug("Response from SP: " + responseStr);
                }
                JSONParser parser = new JSONParser();
                return (JSONObject) parser.parse(responseStr);

            } catch (ClientProtocolException e) {
                String error = "Error while connecting to the server";
                handleExceptionWithCode(error, e, ExceptionCodes
                        .from(ExceptionCodes.INTERNAL_ERROR_WITH_SPECIFIC_DESC, error));
            } catch (IOException e) {
                String error = "Error while parsing the response";
                handleExceptionWithCode(error, e, ExceptionCodes
                        .from(ExceptionCodes.INTERNAL_ERROR_WITH_SPECIFIC_DESC, error));
            } catch (ParseException e) {
                handleExceptionWithCode("Error while parsing the response ", e, ExceptionCodes.JSON_PARSE_ERROR);
            } finally {
                httpPost.reset();
            }

        } catch (MalformedURLException e) {
            handleExceptionWithCode("Error while parsing the stream processor url", e,
                    ExceptionCodes.MALFORMED_SP_URL);
        }

        return null;

    }

    /**
     * This method is used to extact group ids from Extractor.
     *
     * @param response               login response String.
     * @param groupingExtractorClass extractor class.
     * @return group ids
     * @throws APIManagementException Throws is an error occured when stractoing group Ids
     */
    public static String[] getGroupIdsFromExtractor(String response, String groupingExtractorClass)
            throws APIManagementException {

        if (groupingExtractorClass != null) {
            try {
                LoginPostExecutor groupingExtractor = (LoginPostExecutor) APIUtil.getClassInstance
                        (groupingExtractorClass);
                //switching 2.1.0 and 2.2.0
                if (APIUtil.isMultiGroupAppSharingEnabled()) {
                    NewPostLoginExecutor newGroupIdListExtractor = (NewPostLoginExecutor) groupingExtractor;
                    return newGroupIdListExtractor.getGroupingIdentifierList(response);
                } else {
                    String groupId = groupingExtractor.getGroupingIdentifiers(response);
                    return new String[]{groupId};
                }

            } catch (ClassNotFoundException e) {
                String msg = groupingExtractorClass + " is not found in runtime";
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            } catch (ClassCastException e) {
                String msg = "Cannot cast " + groupingExtractorClass + " NewPostLoginExecutor";
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            } catch (IllegalAccessException e) {
                String msg = "Error occurred while invocation of getGroupingIdentifier method";
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            } catch (InstantiationException e) {
                String msg = "Error occurred while instantiating " + groupingExtractorClass + " class";
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            }
        }
        return new String[0];
    }

    public static String getStoreUrl() {

        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration()
                .getFirstProperty(APIConstants.API_STORE_URL);
    }

    /**
     * Get gateway environments defined in the configuration: api-manager.xml
     *
     * @return map of configured environments against environment name
     */
    public static Map<String, Environment> getReadOnlyEnvironments() {

        return ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                .getAPIManagerConfiguration().getApiGatewayEnvironments();
    }

    /**
     * Get default (first) vhost of the given read only environment
     *
     * @param environmentName name of the read only environment
     * @return default vhost of environment
     */
    public static VHost getDefaultVhostOfReadOnlyEnvironment(String environmentName) throws APIManagementException {

        Map<String, Environment> readOnlyEnvironments = getReadOnlyEnvironments();
        if (readOnlyEnvironments.get(environmentName) == null) {
            throw new APIManagementException("Configured read only environment not found: "
                    + environmentName, ExceptionCodes.from(ExceptionCodes.READ_ONLY_ENVIRONMENT_NOT_FOUND, environmentName));
        }
        if (readOnlyEnvironments.get(environmentName).getVhosts().isEmpty()) {
            throw new APIManagementException("VHosts not found for the environment: "
                    + environmentName, ExceptionCodes.from(ExceptionCodes.VHOST_FOR_ENVIRONMENT_NOT_FOUND, environmentName));
        }
        return readOnlyEnvironments.get(environmentName).getVhosts().get(0);
    }

    /**
     * Returns a masked token for a given token.
     *
     * @param token token to be masked
     * @return masked token.
     */
    public static String getMaskedToken(String token) {

        StringBuilder maskedTokenBuilder = new StringBuilder();
        if (token != null) {
            int allowedVisibleLen = Math.min(token.length() / MIN_VISIBLE_LEN_RATIO, MAX_VISIBLE_LEN);
            if (token.length() > MAX_LEN) {
                maskedTokenBuilder.append("...");
                maskedTokenBuilder.append(String.join("", Collections.nCopies(MAX_LEN, MASK_CHAR)));
            } else {
                maskedTokenBuilder.append(String.join("", Collections.nCopies(token.length()
                        - allowedVisibleLen, MASK_CHAR)));
            }
            maskedTokenBuilder.append(token.substring(token.length() - allowedVisibleLen));
        }
        return maskedTokenBuilder.toString();
    }

    /**
     * return skipRolesByRegex config
     */
    public static String getSkipRolesByRegex() {

        ConfigurationHolder config = ServiceReferenceHolder.getInstance()
                .getAPIManagerConfigurationService().getAPIManagerConfiguration();
        return config.getFirstProperty(APIConstants.SKIP_ROLES_BY_REGEX);
    }

    public static Map<String, Object> getUserProperties(String userNameWithoutChange) throws APIManagementException {

        Map<String, Object> properties = new HashMap<String, Object>();
        if (APIUtil.hasPermission(userNameWithoutChange, APIConstants.Permissions.APIM_ADMIN)) {
            properties.put(APIConstants.USER_CTX_PROPERTY_ISADMIN, true);
        }
        properties.put(APIConstants.USER_CTX_PROPERTY_SKIP_ROLES, APIUtil.getSkipRolesByRegex());

        return properties;
    }

    public static boolean isDevPortalAnonymous() {

        ConfigurationHolder config = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                .getAPIManagerConfiguration();
        String anonymousMode = config.getFirstProperty(APIConstants.API_DEVPORTAL_ANONYMOUS_MODE);
        if (anonymousMode == null) {
            return true;
        }
        return Boolean.parseBoolean(anonymousMode);
    }

    public static Map getTenantBasedStoreDomainMapping(String tenantDomain) throws APIManagementException {

        //TODO registry usage
        return new HashMap();
    }

    public static boolean isPerTenantServiceProviderEnabled(String tenantDomain) throws APIManagementException {

        JSONObject tenantConfig = getTenantConfig(tenantDomain);
        if (tenantConfig.containsKey(APIConstants.ENABLE_PER_TENANT_SERVICE_PROVIDER_CREATION)) {
            return (boolean) tenantConfig.get(APIConstants.ENABLE_PER_TENANT_SERVICE_PROVIDER_CREATION);
        }
        return false;
    }

    public static String getTenantAdminUserName(String tenantDomain) throws APIManagementException {

        try {
            int tenantId = UserManagerHolder.getUserManager().getTenantId(tenantDomain);
            String adminUserName = UserManagerHolder.getUserManager().getAdminUsername(tenantId);
            if (!tenantDomain.contentEquals(APIConstants.SUPER_TENANT_DOMAIN)) {
                return adminUserName.concat("@").concat(tenantDomain);
            }
            return adminUserName;
        } catch (UserException e) {
            throw new APIManagementException("Error in getting tenant admin username",
                    e, ExceptionCodes.from(ExceptionCodes.USERSTORE_INITIALIZATION_FAILED));
        }
    }

    public static Map<String, KeyManagerConnectorConfiguration> getKeyManagerConfigurations() {

        //TODO handle configs
//        return ServiceReferenceHolder.getInstance().getKeyManagerConnectorConfigurations();
        return new HashMap<>(); //Dummy return
    }

    /**
     * Get scopes for the given scope keys from authorization server.
     *
     * @param scopeKeys    Scope Keys
     * @param organization organization
     * @return Scope key to Scope object mapping
     * @throws APIManagementException if an error occurs while getting scopes using scope keys
     */
    public static Map<String, Scope> getScopes(Set<String> scopeKeys, String organization)
            throws APIManagementException {

        Map<String, Scope> scopeToKeyMap = new HashMap<>();
        for (String scopeKey : scopeKeys) {
            Scope scope = getScopeByName(scopeKey, organization);
            scopeToKeyMap.put(scopeKey, scope);
        }
        return scopeToKeyMap;
    }

    public static Scope getScopeByName(String scopeKey, String organization) throws APIManagementException {

        int tenantId = APIUtil.getInternalIdFromTenantDomainOrOrganization(organization);
        return ScopesDAO.getInstance().getScope(scopeKey, tenantId);
    }

    /**
     * Replace new RESTAPI Role mappings to tenant-conf.
     *
     * @param newScopeRoleJson New object of role-scope mapping
     * @throws APIManagementException If failed to replace the new tenant-conf.
     */
    public static void updateTenantConfOfRoleScopeMapping(JSONObject newScopeRoleJson, String username)
            throws APIManagementException {

        String tenantDomain;
        tenantDomain = getTenantDomain(username);
        //read from tenant-conf.json
        JSONObject tenantConfig = getTenantConfig(tenantDomain);
        JsonObject existingTenantConfObject = (JsonObject) new JsonParser().parse(tenantConfig.toJSONString());
        JsonElement existingTenantConfScopes = existingTenantConfObject.get(APIConstants.REST_API_SCOPES_CONFIG);
        JsonElement newTenantConfScopes = new JsonParser().parse(newScopeRoleJson.toJSONString());
        JsonObject mergedTenantConfScopes = mergeTenantConfScopes(existingTenantConfScopes, newTenantConfScopes);

        // Removing the old RESTAPIScopes config from the existing tenant-conf
        existingTenantConfObject.remove(APIConstants.REST_API_SCOPES_CONFIG);
        // Adding the merged RESTAPIScopes config to the tenant-conf
        existingTenantConfObject.add(APIConstants.REST_API_SCOPES_CONFIG, mergedTenantConfScopes);

        // Prettify the tenant-conf
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String formattedTenantConf = gson.toJson(existingTenantConfObject);
        //TODO handle configs
//        ServiceReferenceHolder.getInstance().getApimConfigService().updateTenantConfig(tenantDomain,
//                formattedTenantConf);

        if (log.isDebugEnabled()) {
            log.debug("Finalized tenant-conf.json: " + formattedTenantConf);
        }
    }

    /**
     * Merge the existing and new scope-role mappings (RESTAPIScopes config) in the tenant-conf
     *
     * @param existingTenantConfScopes Existing (old) scope-role mappings
     * @param newTenantConfScopes      Modified (new) scope-role mappings
     * @return JsonObject with merged tenant-sconf scope mappings
     */
    public static JsonObject mergeTenantConfScopes(JsonElement existingTenantConfScopes, JsonElement newTenantConfScopes) {

        JsonArray existingTenantConfScopesArray = (JsonArray) existingTenantConfScopes.getAsJsonObject().
                get(APIConstants.REST_API_SCOPE);
        JsonArray newTenantConfScopesArray = (JsonArray) newTenantConfScopes.getAsJsonObject().
                get(APIConstants.REST_API_SCOPE);
        JsonArray mergedTenantConfScopesArray = new JsonParser().parse(newTenantConfScopesArray.toString()).
                getAsJsonArray();

        // Iterating the existing (old) scope-role mappings
        for (JsonElement existingScopeRoleMapping : existingTenantConfScopesArray) {
            String existingScopeName = existingScopeRoleMapping.getAsJsonObject().get(APIConstants.REST_API_SCOPE_NAME).
                    getAsString();
            Boolean scopeRoleMappingExists = false;
            // Iterating the modified (new) scope-role mappings and add the old scope mappings
            // if those are not present in the list (merging)
            for (JsonElement newScopeRoleMapping : newTenantConfScopesArray) {
                String newScopeName = newScopeRoleMapping.getAsJsonObject().get(APIConstants.REST_API_SCOPE_NAME).
                        getAsString();
                if (StringUtils.equals(existingScopeName, newScopeName)) {
                    // If a particular mapping is already there, skip it
                    scopeRoleMappingExists = true;
                    break;
                }
            }
            // If the particular old mapping does not exist in the new list, add it to the new list
            if (!scopeRoleMappingExists) {
                mergedTenantConfScopesArray.add(existingScopeRoleMapping);
            }
        }
        JsonObject mergedTenantConfScopes = new JsonObject();
        mergedTenantConfScopes.add(APIConstants.REST_API_SCOPE, mergedTenantConfScopesArray);
        return mergedTenantConfScopes;
    }

    /**
     * Replace new RoleMappings  to tenant-conf.
     *
     * @param newRoleMappingJson New object of role-alias mapping
     * @throws APIManagementException If failed to replace the new tenant-conf.
     */
    public static void updateTenantConfRoleAliasMapping(JSONObject newRoleMappingJson, String username)
            throws APIManagementException {

        String tenantDomain = getTenantDomain(username);

        //read from tenant-conf.json
        JsonObject existingTenantConfObject = new JsonObject();
        String existingTenantConf = "conf"; //Dummy config value
        //TODO handle configs
//                ServiceReferenceHolder.getInstance().getApimConfigService().getTenantConfig(tenantDomain);

        existingTenantConfObject = new JsonParser().parse(existingTenantConf).getAsJsonObject();

        //append original role to the role mapping list
        Set<Entry<String, JsonElement>> roleMappingEntries = newRoleMappingJson.entrySet();
        for (Entry<String, JsonElement> entry : roleMappingEntries) {
            List<String> currentRoles = Arrays.asList(String.valueOf(entry.getValue()).split(","));
            boolean isOriginalRoleAlreadyInRoles = false;
            for (String role : currentRoles) {
                if (role.equals(entry.getKey())) {
                    isOriginalRoleAlreadyInRoles = true;
                    break;
                }
            }
            if (!isOriginalRoleAlreadyInRoles) {
                String newRoles = entry.getKey() + "," + entry.getValue();
                newRoleMappingJson.replace(entry.getKey(), entry.getValue(), newRoles);
            }
        }
        existingTenantConfObject.remove(APIConstants.REST_API_ROLE_MAPPINGS_CONFIG);
        JsonElement jsonElement = new JsonParser().parse(String.valueOf(newRoleMappingJson));
        existingTenantConfObject.add(APIConstants.REST_API_ROLE_MAPPINGS_CONFIG, jsonElement);

        // Prettify the tenant-conf
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String formattedTenantConf = gson.toJson(existingTenantConfObject);
        //TODO handle configs
//        ServiceReferenceHolder.getInstance().getApimConfigService().updateTenantConfig(tenantDomain,
//                formattedTenantConf);
        //TODO cache implementation
//        Cache tenantConfigCache = CacheProvider.getTenantConfigCache();
//        String cacheName = tenantDomain + "_" + APIConstants.TENANT_CONFIG_CACHE_NAME;
//        tenantConfigCache.remove(cacheName);
        if (log.isDebugEnabled()) {
            log.debug("Finalized tenant-conf.json: " + formattedTenantConf);
        }
    }

    /**
     * Check whether roles exist for the user.
     *
     * @param userName
     * @param roleName
     * @return
     * @throws APIManagementException
     */
    public static boolean isRoleExistForUser(String userName, String roleName) throws APIManagementException {

        boolean foundUserRole = false;
        String[] userRoleList = getListOfRoles(userName);
        String[] inputRoles = roleName.split(",");
        if (log.isDebugEnabled()) {
            log.debug("isRoleExistForUser(): User Roles " + Arrays.toString(userRoleList));
            log.debug("isRoleExistForUser(): InputRoles Roles " + Arrays.toString(inputRoles));
        }
        if (inputRoles != null) {
            for (String inputRole : inputRoles) {
                if (compareRoleList(userRoleList, inputRole)) {
                    foundUserRole = true;
                    break;
                }
            }
        }
        return foundUserRole;
    }

    public static void validateRestAPIScopes(String tenantConfig) throws APIManagementException {

        JsonObject fileBaseTenantConfig = (JsonObject) getFileBaseTenantConfig();
        Set<String> fileBaseScopes = getRestAPIScopes(fileBaseTenantConfig);
        Set<String> uploadedTenantConfigScopes = getRestAPIScopes((JsonObject) new JsonParser().parse(tenantConfig));
        fileBaseScopes.removeAll(uploadedTenantConfigScopes);
        if (fileBaseScopes.size() > 0) {
            throw new APIManagementException("Insufficient scopes available in tenant-config", ExceptionCodes.INVALID_TENANT_CONFIG);
        }
    }

    private static Set<String> getRestAPIScopes(JsonObject tenantConfig) {

        Set<String> scopes = new HashSet<>();
        if (tenantConfig.has(APIConstants.REST_API_SCOPES_CONFIG)) {
            JsonObject restApiScopes = (JsonObject) tenantConfig.get(APIConstants.REST_API_SCOPES_CONFIG);
            if (restApiScopes.has(APIConstants.REST_API_SCOPE)
                    && restApiScopes.get(APIConstants.REST_API_SCOPE) instanceof JsonArray) {
                JsonArray restAPIScopes = (JsonArray) restApiScopes.get(APIConstants.REST_API_SCOPE);
                if (restAPIScopes != null) {
                    for (JsonElement scopeElement : restAPIScopes) {
                        if (scopeElement instanceof JsonObject) {
                            if (((JsonObject) scopeElement).has(APIConstants.REST_API_SCOPE_NAME)
                                    && ((JsonObject) scopeElement).get(APIConstants.REST_API_SCOPE_NAME)
                                    instanceof JsonPrimitive) {
                                JsonElement name = ((JsonObject) scopeElement).get(APIConstants.REST_API_SCOPE_NAME);
                                scopes.add(name.toString());
                            }
                        }
                    }
                }

            }
        }
        return scopes;
    }

    public static Schema retrieveTenantConfigJsonSchema() {

        return tenantConfigJsonSchema;
    }

    public static Schema retrieveOperationPolicySpecificationJsonSchema() {

        return operationPolicySpecSchema;
    }

    /**
     * Return the md5 hash of the provided policy. To generate the md5 hash, policy Specification and the
     * two definitions are used
     *
     * @param policyData Operation policy data
     * @return md5 hash
     */
    public static String getMd5OfOperationPolicy(OperationPolicyData policyData) {

        String policySpecificationAsString = "";
        String synapsePolicyDefinitionAsString = "";
        String ccPolicyDefinitionAsString = "";

        if (policyData.getSpecification() != null) {
            policySpecificationAsString = new Gson().toJson(policyData.getSpecification());
        }
        if (policyData.getSynapsePolicyDefinition() != null) {
            synapsePolicyDefinitionAsString = new Gson().toJson(policyData.getSynapsePolicyDefinition());
        }
        if (policyData.getCcPolicyDefinition() != null) {
            ccPolicyDefinitionAsString = new Gson().toJson(policyData.getCcPolicyDefinition());
        }

        return DigestUtils.md5Hex(policySpecificationAsString + synapsePolicyDefinitionAsString
                + ccPolicyDefinitionAsString);
    }

    /**
     * Return the md5 hash of the policy definition string
     *
     * @param policyDefinition Operation policy definition
     * @return md5 hash of the definition content
     */
    public static String getMd5OfOperationPolicyDefinition(OperationPolicyDefinition policyDefinition) {

        String md5Hash = "";

        if (policyDefinition != null) {
            if (policyDefinition.getContent() != null) {
                md5Hash = DigestUtils.md5Hex(policyDefinition.getContent());
            }
        }
        return md5Hash;
    }

    public static OperationPolicyData generateOperationPolicyDataObject(String apiUuid, String organization,
                                                                        String policyName,
                                                                        String policyDefinitionString) {

        OperationPolicySpecification policySpecification = new OperationPolicySpecification();
        policySpecification.setCategory(OperationPolicySpecification.PolicyCategory.Mediation);
        policySpecification.setName(policyName);
        policySpecification.setDisplayName(policyName);
        policySpecification.setDescription("This is a mediation policy migrated to an operation policy.");

        ArrayList<String> gatewayList = new ArrayList<>();
        gatewayList.add(APIConstants.OPERATION_POLICY_SUPPORTED_GATEWAY_SYNAPSE);
        policySpecification.setSupportedGateways(gatewayList);

        ArrayList<String> supportedAPIList = new ArrayList<>();
        supportedAPIList.add(APIConstants.OPERATION_POLICY_SUPPORTED_API_TYPE_HTTP);
        policySpecification.setSupportedApiTypes(supportedAPIList);

        ArrayList<String> applicableFlows = new ArrayList<>();
        applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_REQUEST);
        applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_RESPONSE);
        applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_FAULT);
        policySpecification.setApplicableFlows(applicableFlows);

        OperationPolicyData policyData = new OperationPolicyData();
        policyData.setOrganization(organization);
        policyData.setSpecification(policySpecification);
        policyData.setApiUUID(apiUuid);

        if (policyDefinitionString != null) {
            OperationPolicyDefinition policyDefinition = new OperationPolicyDefinition();
            policyDefinition.setContent(policyDefinitionString);
            policyDefinition.setGatewayType(OperationPolicyDefinition.GatewayType.Synapse);
            policyDefinition.setMd5Hash(APIUtil.getMd5OfOperationPolicyDefinition(policyDefinition));
            policyData.setSynapsePolicyDefinition(policyDefinition);
        }

        policyData.setMd5Hash(APIUtil.getMd5OfOperationPolicy(policyData));

        return policyData;
    }

    public static String getTenantDomain(String userName) throws APIManagementException {
        String tenantDomain;
        try {
            tenantDomain = UserManagerHolder.getUserManager().getTenantDomain(userName);
            if (tenantDomain.isEmpty()) {
                tenantDomain = APIConstants.SUPER_TENANT_DOMAIN;
            }
        } catch (UserException e) {
            throw new APIManagementException(ERROR_WHILE_RETRIEVING_TENANT_DOMAIN, e,
                    ExceptionCodes.USERSTORE_INITIALIZATION_FAILED);
        }
        return tenantDomain;
    }

    public static String getTenantAwareUsername(String userName) throws APIManagementException {
        try {
            userName = UserManagerHolder.getUserManager().getTenantAwareUsername(userName);
        } catch (UserException e) {
            throw new APIManagementException("Error while getting tenant Aware Username of the user:" + userName,
                    e, ExceptionCodes.USERSTORE_INITIALIZATION_FAILED);
        }
        return userName;
    }

    private static APIMConfigService getAPIMConfigService() {

        if (apimConfigService == null) {
            apimConfigService = new APIMConfigServiceImpl();
        }
        return apimConfigService;
    }

    public static boolean isFalseExplicitly(String value) {
        return value == null || value.equalsIgnoreCase("false")
                || value.equals("0") || value.equalsIgnoreCase("no");
    }
}
