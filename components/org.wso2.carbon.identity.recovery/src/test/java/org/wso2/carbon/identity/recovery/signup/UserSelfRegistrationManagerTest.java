
/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.recovery.signup;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.carbon.consent.mgt.core.ConsentManager;
import org.wso2.carbon.consent.mgt.core.ConsentManagerImpl;
import org.wso2.carbon.consent.mgt.core.exception.ConsentManagementException;
import org.wso2.carbon.consent.mgt.core.model.AddReceiptResponse;
import org.wso2.carbon.consent.mgt.core.model.ConsentManagerConfigurationHolder;
import org.wso2.carbon.consent.mgt.core.model.ReceiptInput;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.application.common.model.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.User;
import org.wso2.carbon.identity.auth.attribute.handler.AuthAttributeHandlerManager;
import org.wso2.carbon.identity.auth.attribute.handler.exception.AuthAttributeHandlerClientException;
import org.wso2.carbon.identity.auth.attribute.handler.exception.AuthAttributeHandlerException;
import org.wso2.carbon.identity.auth.attribute.handler.model.ValidationResult;
import org.wso2.carbon.identity.common.testng.WithCarbonHome;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.carbon.identity.governance.IdentityGovernanceException;
import org.wso2.carbon.identity.governance.IdentityGovernanceService;
import org.wso2.carbon.identity.governance.service.notification.NotificationChannels;
import org.wso2.carbon.identity.governance.service.otp.OTPGenerator;
import org.wso2.carbon.identity.recovery.IdentityRecoveryConstants;
import org.wso2.carbon.identity.recovery.IdentityRecoveryException;
import org.wso2.carbon.identity.recovery.RecoveryScenarios;
import org.wso2.carbon.identity.recovery.RecoverySteps;
import org.wso2.carbon.identity.recovery.bean.NotificationResponseBean;
import org.wso2.carbon.identity.recovery.exception.SelfRegistrationException;
import org.wso2.carbon.identity.recovery.internal.IdentityRecoveryServiceDataHolder;
import org.wso2.carbon.identity.recovery.model.Property;
import org.wso2.carbon.identity.recovery.model.UserRecoveryData;
import org.wso2.carbon.identity.recovery.store.JDBCRecoveryDataStore;
import org.wso2.carbon.identity.recovery.store.UserRecoveryDataStore;
import org.wso2.carbon.identity.recovery.util.Utils;
import org.wso2.carbon.idp.mgt.IdentityProviderManager;
import org.wso2.carbon.user.api.Claim;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import static org.wso2.carbon.identity.auth.attribute.handler.AuthAttributeHandlerConstants.ErrorMessages.ERROR_CODE_AUTH_ATTRIBUTE_HANDLER_NOT_FOUND;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ConnectorConfig.ENABLE_SELF_SIGNUP;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ConnectorConfig.SELF_REGISTRATION_SEND_OTP_IN_EMAIL;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ConnectorConfig.SELF_REGISTRATION_USE_LOWERCASE_CHARACTERS_IN_OTP;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ConnectorConfig.SELF_REGISTRATION_USE_NUMBERS_IN_OTP;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ConnectorConfig.SELF_REGISTRATION_USE_UPPERCASE_CHARACTERS_IN_OTP;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ConnectorConfig.SELF_REGISTRATION_OTP_LENGTH;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ConnectorConfig.SELF_REGISTRATION_SMSOTP_VERIFICATION_CODE_EXPIRY_TIME;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ConnectorConfig.SELF_REGISTRATION_SMS_OTP_REGEX;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ConnectorConfig.SELF_REGISTRATION_VERIFICATION_CODE_EXPIRY_TIME;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ConnectorConfig.SIGN_UP_NOTIFICATION_INTERNALLY_MANAGE;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_INVALID_REGISTRATION_OPTION;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_INVALID_USER_ATTRIBUTES_FOR_REGISTRATION;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_MULTIPLE_REGISTRATION_OPTIONS;
import static org.wso2.carbon.identity.recovery.IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_UNEXPECTED_ERROR_VALIDATING_ATTRIBUTES;

/**
 * Test class for UserSelfRegistrationManager class.
 */
@WithCarbonHome
public class UserSelfRegistrationManagerTest {

    @InjectMocks
    private UserSelfRegistrationManager userSelfRegistrationManager;

    @Mock
    private IdentityRecoveryServiceDataHolder identityRecoveryServiceDataHolder;

    @Mock
    private UserRecoveryDataStore userRecoveryDataStore;

    @Mock
    private IdentityEventService identityEventService;

    @Mock
    private UserStoreManager userStoreManager;

    @Mock
    private UserRealm userRealm;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private AuthAttributeHandlerManager authAttributeHandlerManager;

    @Mock
    private IdentityGovernanceService identityGovernanceService;

    @Mock
    private ReceiptInput resultReceipt;

    @Mock
    private RealmService realmService;

    @Mock
    private OTPGenerator otpGenerator;

    @Mock
    private PrivilegedCarbonContext privilegedCarbonContext;

    private MockedStatic<IdentityRecoveryServiceDataHolder> mockedServiceDataHolder;
    private MockedStatic<IdentityUtil> mockedIdentityUtil;
    private MockedStatic<JDBCRecoveryDataStore> mockedJDBCRecoveryDataStore;
    private MockedStatic<IdentityProviderManager> mockedIdentityProviderManager;
    private MockedStatic<IdentityTenantUtil> mockedIdentityTenantUtil;
    private MockedStatic<PrivilegedCarbonContext> mockedPrivilegedCarbonContext;
    private MockedStatic<FrameworkUtils> mockedFrameworkUtils;

    private final String TEST_TENANT_DOMAIN_NAME = "carbon.super";
    private final int TEST_TENANT_ID = 12;
    private final String TEST_USERSTORE_DOMAIN = "PRIMARY";
    private final String TEST_USER_NAME = "dummyUser";
    private final String TEST_CLAIM_URI = "ttp://wso2.org/claims/emailaddress";
    private final String TEST_CLAIM_VALUE = "dummyuser@wso2.com";
    private final String TEST_MOBILE_CLAIM_VALUE = "0775553443";
    private final String TEST_PRIMARY_USER_STORE_DOMAIN = "PRIMARY";
    private final String TEST_RECOVERY_DATA_STORE_SECRET = "secret";

    private static final Log LOG = LogFactory.getLog(UserSelfRegistrationManagerTest.class);

    @BeforeMethod
    public void setUp() throws UserStoreException {

        MockitoAnnotations.openMocks(this);

        userSelfRegistrationManager = UserSelfRegistrationManager.getInstance();

        mockedServiceDataHolder = mockStatic(IdentityRecoveryServiceDataHolder.class);
        mockedIdentityUtil = mockStatic(IdentityUtil.class);
        mockedJDBCRecoveryDataStore = mockStatic(JDBCRecoveryDataStore.class);
        mockedIdentityProviderManager = mockStatic(IdentityProviderManager.class);
        mockedIdentityTenantUtil = mockStatic(IdentityTenantUtil.class);
        mockedPrivilegedCarbonContext = mockStatic(PrivilegedCarbonContext.class);
        mockedFrameworkUtils = mockStatic(FrameworkUtils.class);

        mockedIdentityProviderManager.when(IdentityProviderManager::getInstance).thenReturn(identityProviderManager);
        mockedServiceDataHolder.when(IdentityRecoveryServiceDataHolder::getInstance)
                .thenReturn(identityRecoveryServiceDataHolder);
        mockedPrivilegedCarbonContext.when(PrivilegedCarbonContext::getThreadLocalCarbonContext)
                .thenReturn(privilegedCarbonContext);
        mockedJDBCRecoveryDataStore.when(JDBCRecoveryDataStore::getInstance).thenReturn(userRecoveryDataStore);
        mockedIdentityTenantUtil.when(() -> IdentityTenantUtil.getTenantId(any())).thenReturn(TEST_TENANT_ID);
        mockedIdentityUtil.when(IdentityUtil::getPrimaryDomainName).thenReturn(TEST_PRIMARY_USER_STORE_DOMAIN);
        mockedIdentityUtil.when(() -> IdentityUtil.addDomainToName(eq(TEST_USER_NAME), anyString()))
                .thenReturn(String.format("%s/%s", TEST_USER_NAME, TEST_USERSTORE_DOMAIN));
        mockedFrameworkUtils.when(FrameworkUtils::getMultiAttributeSeparator).thenReturn(",");

        when(identityRecoveryServiceDataHolder.getIdentityEventService()).thenReturn(identityEventService);
        when(identityRecoveryServiceDataHolder.getIdentityGovernanceService()).thenReturn(identityGovernanceService);
        when(identityRecoveryServiceDataHolder.getOtpGenerator()).thenReturn(otpGenerator);
        when(identityRecoveryServiceDataHolder.getAuthAttributeHandlerManager()).thenReturn(authAttributeHandlerManager);
        when(identityRecoveryServiceDataHolder.getRealmService()).thenReturn(realmService);

        when(realmService.getTenantUserRealm(anyInt())).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
    }

    @AfterMethod
    public void tearDown() {

        mockedIdentityUtil.close();
        mockedJDBCRecoveryDataStore.close();
        mockedIdentityProviderManager.close();
        mockedIdentityTenantUtil.close();
        mockedPrivilegedCarbonContext.close();
        mockedServiceDataHolder.close();
        mockedFrameworkUtils.close();
    }

    String consentData =
            "{\"jurisdiction\":\"someJurisdiction\",\"collectionMethod\":\"Web Form - Self Registration\"," +
                    "\"language\":\"en\",\"piiPrincipalId\":\"DOMAIN/testuser\",\"services\":" +
                    "[{\"tenantDomain\":\"wso2.com\",\"serviceDisplayName\":\"Resident IDP\"," +
                    "\"serviceDescription\":\"Resident IDP\",\"purposes\":[{\"purposeId\":3,\"purposeCategoryId\":[1]," +
                    "\"consentType\":\"EXPLICIT\",\"piiCategory\":[{\"piiCategoryId\":1," +
                    "\"validity\":\"DATE_UNTIL:INDEFINITE\"}],\"primaryPurpose\":true," +
                    "\"termination\":\"DATE_UNTIL:INDEFINITE\",\"thirdPartyDisclosure\":false}],\"tenantId\":1}]," +
                    "\"policyURL\":\"somePolicyUrl\",\"tenantId\":1,\"properties\":{}}";

    /**
     * Testing ResendConfirmationCode for user self registration.
     *
     * @param username                             Username
     * @param userstore                            Userstore domain
     * @param tenantDomain                         Tenant domain
     * @param preferredChannel                     Preferred Notification channel
     * @param errorMsg                             Error scenario
     * @param enableInternalNotificationManagement Manage notifications internally
     * @param expectedChannel                      Expected notification channel
     * @throws Exception If an error occurred while testing.
     */
    @Test(dataProvider = "userDetailsForResendingAccountConfirmation")
    public void testResendConfirmationCode(String username, String userstore, String tenantDomain,
                                           String preferredChannel, String errorMsg,
                                           String enableInternalNotificationManagement, String expectedChannel)
            throws Exception {

        // Build recovery user.
        User user = new User();
        user.setUserName(username);
        user.setUserStoreDomain(userstore);
        user.setTenantDomain(tenantDomain);

        UserRecoveryData userRecoveryData = new UserRecoveryData(user, TEST_RECOVERY_DATA_STORE_SECRET, RecoveryScenarios
                .SELF_SIGN_UP, RecoverySteps.CONFIRM_SIGN_UP);
        // Storing preferred notification channel in remaining set ids.
        userRecoveryData.setRemainingSetIds(preferredChannel);
        userRecoveryData.setTimeCreated(new Timestamp(System.currentTimeMillis()));

        mockConfigurations("true", enableInternalNotificationManagement);
        mockJDBCRecoveryDataStore(userRecoveryData);
        mockEmailTrigger();
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        when(userStoreManager.getUserClaimValue(any(), eq(IdentityRecoveryConstants.MOBILE_NUMBER_CLAIM), any()))
                .thenReturn(TEST_MOBILE_CLAIM_VALUE);
        when(userStoreManager.getUserClaimValue(any(), eq(IdentityRecoveryConstants.EMAIL_ADDRESS_CLAIM), any()))
                .thenReturn(TEST_CLAIM_VALUE);
        mockedIdentityTenantUtil.when(() -> IdentityTenantUtil.getTenantId(anyString())).thenReturn(-1234);

        NotificationResponseBean responseBean =
                userSelfRegistrationManager.resendConfirmationCode(user, null);
        assertEquals(responseBean.getNotificationChannel(), expectedChannel, errorMsg);
    }

    /**
     * Contains user data related resending self registration notification.
     *
     * @return Object[][]
     */
    @DataProvider(name = "userDetailsForResendingAccountConfirmation")
    private Object[][] userDetailsForResendingAccountConfirmation() {

        String username = "sominda";
        // Notification channel types.
        String EMAIL = NotificationChannels.EMAIL_CHANNEL.getChannelType();
        String SMS = NotificationChannels.SMS_CHANNEL.getChannelType();
        String EXTERNAL = NotificationChannels.EXTERNAL_CHANNEL.getChannelType();

        /* ArrayOrder: Username, Userstore, Tenant domain, Preferred channel, Error message, Manage notifications
        internally, excepted channel */
        return new Object[][]{
                {username, TEST_USERSTORE_DOMAIN, TEST_TENANT_DOMAIN_NAME, EMAIL, "User with EMAIL as Preferred " +
                        "Notification Channel : ", "TRUE", EMAIL},
                {username, TEST_USERSTORE_DOMAIN, TEST_TENANT_DOMAIN_NAME, EMAIL, "User with EMAIL as Preferred " +
                        "Notification Channel but notifications are externally managed : ", "FALSE", EXTERNAL},
                {username, TEST_USERSTORE_DOMAIN, TEST_TENANT_DOMAIN_NAME, SMS, "User with SMS as Preferred " +
                        "Notification Channel : ", "TRUE", SMS},
                {username, TEST_USERSTORE_DOMAIN, TEST_TENANT_DOMAIN_NAME, StringUtils.EMPTY,
                        "User no preferred channel specified : ", "TRUE", null}
        };
    }

    /**
     * Mock user self registration configurations for resend account confirmation.
     *
     * @param enableSelfSignUp            Enable user self registration
     * @param enableInternalNotifications Enable notifications internal management.
     * @throws Exception If an error occurred while mocking configurations.
     */
    private void mockConfigurations(String enableSelfSignUp, String enableInternalNotifications) throws Exception {

        org.wso2.carbon.identity.application.common.model.Property signupConfig =
                new org.wso2.carbon.identity.application.common.model.Property();
        signupConfig.setName(ENABLE_SELF_SIGNUP);
        signupConfig.setValue(enableSelfSignUp);

        org.wso2.carbon.identity.application.common.model.Property notificationConfig =
                new org.wso2.carbon.identity.application.common.model.Property();
        notificationConfig.setName(SIGN_UP_NOTIFICATION_INTERNALLY_MANAGE);
        notificationConfig.setValue(enableInternalNotifications);

        org.wso2.carbon.identity.application.common.model.Property sendOtpInEmailConfig =
                new org.wso2.carbon.identity.application.common.model.Property();
        sendOtpInEmailConfig.setName(SELF_REGISTRATION_SEND_OTP_IN_EMAIL);
        sendOtpInEmailConfig.setValue("false");

        org.wso2.carbon.identity.application.common.model.Property useLowerCaseConfig =
                new org.wso2.carbon.identity.application.common.model.Property();
        useLowerCaseConfig.setName(SELF_REGISTRATION_USE_LOWERCASE_CHARACTERS_IN_OTP);
        useLowerCaseConfig.setValue("true");

        org.wso2.carbon.identity.application.common.model.Property useNumbersConfig =
                new org.wso2.carbon.identity.application.common.model.Property();
        useNumbersConfig.setName(SELF_REGISTRATION_USE_NUMBERS_IN_OTP);
        useNumbersConfig.setValue("true");

        org.wso2.carbon.identity.application.common.model.Property useUpperCaseConfig =
                new org.wso2.carbon.identity.application.common.model.Property();
        useUpperCaseConfig.setName(SELF_REGISTRATION_USE_UPPERCASE_CHARACTERS_IN_OTP);
        useUpperCaseConfig.setValue("true");

        org.wso2.carbon.identity.application.common.model.Property otpLengthConfig =
                new org.wso2.carbon.identity.application.common.model.Property();
        otpLengthConfig.setName(SELF_REGISTRATION_OTP_LENGTH);
        otpLengthConfig.setValue("6");

        org.wso2.carbon.identity.application.common.model.Property smsOTPConfig =
                new org.wso2.carbon.identity.application.common.model.Property();
        smsOTPConfig.setName(SELF_REGISTRATION_SMS_OTP_REGEX);
        smsOTPConfig.setValue("");

        org.wso2.carbon.identity.application.common.model.Property selfRegistrationCodeExpiryConfig =
                new org.wso2.carbon.identity.application.common.model.Property();
        selfRegistrationCodeExpiryConfig.setName(SELF_REGISTRATION_VERIFICATION_CODE_EXPIRY_TIME);
        selfRegistrationCodeExpiryConfig.setValue("1440");

        org.wso2.carbon.identity.application.common.model.Property selfRegistrationSMSCodeExpiryConfig =
                new org.wso2.carbon.identity.application.common.model.Property();
        selfRegistrationSMSCodeExpiryConfig.setName(SELF_REGISTRATION_SMSOTP_VERIFICATION_CODE_EXPIRY_TIME);
        selfRegistrationSMSCodeExpiryConfig.setValue("1");

        when(identityGovernanceService
                .getConfiguration(new String[]{ENABLE_SELF_SIGNUP}, TEST_TENANT_DOMAIN_NAME))
                .thenReturn(new org.wso2.carbon.identity.application.common.model.Property[]{signupConfig});
        when(identityGovernanceService
                .getConfiguration(new String[]{SIGN_UP_NOTIFICATION_INTERNALLY_MANAGE}, TEST_TENANT_DOMAIN_NAME))
                .thenReturn(new org.wso2.carbon.identity.application.common.model.Property[]{notificationConfig});
        when(identityGovernanceService
                .getConfiguration(new String[]{SELF_REGISTRATION_SEND_OTP_IN_EMAIL}, TEST_TENANT_DOMAIN_NAME))
                .thenReturn(new org.wso2.carbon.identity.application.common.model.Property[]{sendOtpInEmailConfig});
        when(identityGovernanceService
                .getConfiguration(new String[]{SELF_REGISTRATION_USE_LOWERCASE_CHARACTERS_IN_OTP},
                        TEST_TENANT_DOMAIN_NAME))
                .thenReturn(new org.wso2.carbon.identity.application.common.model.Property[]{useLowerCaseConfig});
        when(identityGovernanceService
                .getConfiguration(new String[]{SELF_REGISTRATION_USE_NUMBERS_IN_OTP}, TEST_TENANT_DOMAIN_NAME))
                .thenReturn(new org.wso2.carbon.identity.application.common.model.Property[]{useNumbersConfig});
        when(identityGovernanceService
                .getConfiguration(new String[]{SELF_REGISTRATION_USE_UPPERCASE_CHARACTERS_IN_OTP},
                        TEST_TENANT_DOMAIN_NAME))
                .thenReturn(new org.wso2.carbon.identity.application.common.model.Property[]{useUpperCaseConfig});
        when(identityGovernanceService
                .getConfiguration(new String[]{SELF_REGISTRATION_OTP_LENGTH}, TEST_TENANT_DOMAIN_NAME))
                .thenReturn(new org.wso2.carbon.identity.application.common.model.Property[]{otpLengthConfig});
        when(identityGovernanceService
                .getConfiguration(new String[]{SELF_REGISTRATION_SMS_OTP_REGEX}, TEST_TENANT_DOMAIN_NAME))
                .thenReturn(new org.wso2.carbon.identity.application.common.model.Property[]{smsOTPConfig});
        when(identityGovernanceService
                .getConfiguration(
                        new String[]{SELF_REGISTRATION_VERIFICATION_CODE_EXPIRY_TIME}, TEST_TENANT_DOMAIN_NAME))
                .thenReturn(new org.wso2.carbon.identity.application.common.model.Property[]
                        {selfRegistrationCodeExpiryConfig});
        when(identityGovernanceService
                .getConfiguration(
                        new String[]{SELF_REGISTRATION_SMSOTP_VERIFICATION_CODE_EXPIRY_TIME}, TEST_TENANT_DOMAIN_NAME))
                .thenReturn(new org.wso2.carbon.identity.application.common.model.Property[]
                        {selfRegistrationSMSCodeExpiryConfig});
        when(otpGenerator.generateOTP(anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), anyString()))
                .thenReturn("1234-4567-890");
        mockedIdentityUtil.when(IdentityUtil::getPrimaryDomainName).thenReturn(TEST_USERSTORE_DOMAIN);
    }

    /**
     * Mock JDBCRecoveryDataStore to store user recovery data.
     *
     * @param userRecoveryData User recovery data to be mocked.
     * @throws IdentityRecoveryException If an error occurred while mocking JDBCRecoveryDataStore.
     */
    private void mockJDBCRecoveryDataStore(UserRecoveryData userRecoveryData) throws IdentityRecoveryException {

        mockedJDBCRecoveryDataStore.when(JDBCRecoveryDataStore::getInstance).thenReturn(userRecoveryDataStore);
        when(userRecoveryDataStore.loadWithoutCodeExpiryValidation(ArgumentMatchers.anyObject(), ArgumentMatchers.anyObject())).
                thenReturn(userRecoveryData);
        doNothing().when(userRecoveryDataStore).invalidate(ArgumentMatchers.anyString());
        doNothing().when(userRecoveryDataStore).store(ArgumentMatchers.any(UserRecoveryData.class));
    }

    /**
     * Mock email triggering.
     *
     * @throws IdentityEventException If an error occurred while mocking identityEventService.
     */
    private void mockEmailTrigger() throws IdentityEventException {

        IdentityRecoveryServiceDataHolder.getInstance().setIdentityEventService(identityEventService);
        doNothing().when(identityEventService).handleEvent(ArgumentMatchers.any(Event.class));
    }

    @Test
    public void testAddConsent() throws Exception {

        IdentityProvider identityProvider = new IdentityProvider();
        when(identityProviderManager.getResidentIdP(ArgumentMatchers.anyString())).thenReturn(identityProvider);
        ConsentManager consentManager = new MyConsentManager(new ConsentManagerConfigurationHolder());
        when(identityRecoveryServiceDataHolder.getConsentManager()).thenReturn(consentManager);
        userSelfRegistrationManager.addUserConsent(consentData, "wso2.com");
        Assert.assertEquals(IdentityRecoveryConstants.Consent.COLLECTION_METHOD_SELF_REGISTRATION,
                resultReceipt.getCollectionMethod());
        Assert.assertEquals("someJurisdiction", resultReceipt.getJurisdiction());
        Assert.assertEquals("en", resultReceipt.getLanguage());
        Assert.assertNotNull(resultReceipt.getServices());
        Assert.assertEquals(1, resultReceipt.getServices().size());
        Assert.assertNotNull(resultReceipt.getServices().get(0).getPurposes());
        Assert.assertEquals(1, resultReceipt.getServices().get(0).getPurposes().size());
        Assert.assertEquals(new Integer(3), resultReceipt.getServices().get(0).getPurposes().get(0).getPurposeId());
        Assert.assertEquals(IdentityRecoveryConstants.Consent.EXPLICIT_CONSENT_TYPE,
                resultReceipt.getServices().get(0).getPurposes().get(0).getConsentType());
        Assert.assertEquals(IdentityRecoveryConstants.Consent.INFINITE_TERMINATION,
                resultReceipt.getServices().get(0).getPurposes().get(0).getTermination());
        Assert.assertEquals(new Integer(3),
                resultReceipt.getServices().get(0).getPurposes().get(0).getPurposeId());
    }

    @Test
    public void testAttributeVerification() throws Exception {

        ValidationResult validationResult = new ValidationResult();
        validationResult.setValid(true);

        when(authAttributeHandlerManager.validateAuthAttributes(anyString(), anyMap())).thenReturn(validationResult);

        User user = new User();
        user.setUserName(TEST_USER_NAME);

        Claim claim = new Claim();
        claim.setClaimUri(TEST_CLAIM_URI);
        claim.setValue(TEST_CLAIM_VALUE);

        Property property = new Property("registrationOption", "MagicLinkAuthAttributeHandler");

        Boolean response = userSelfRegistrationManager.verifyUserAttributes(user, "password", new Claim[]{claim},
                new Property[]{property});

        Assert.assertTrue(response);
    }

    @DataProvider(name = "attributeVerificationFailureData")
    private Object[][] attributeVerificationFailureData() {

        String scenario1 = "Multiple registration options defined in the request.";
        String scenario2 = "Invalid registration option defined in the request.";
        String scenario3 = "Exceptions while obtaining the validation result.";
        String scenario4 = "Attribute requirements not satisfied.";
        String scenario5 = "Validation result being null.";

        Property property1 = new Property("registrationOption", "MagicLinkAuthAttributeHandler");
        Property property2 = new Property("registrationOption", "BasicAuthAuthAttributeHandler");

        // ArrayOrder: scenario, propertiesMap, thrownException, expectedException, validationResult.
        return new Object[][]{
                {scenario1, new Property[]{property1, property2}, null,
                        ERROR_CODE_MULTIPLE_REGISTRATION_OPTIONS.getCode(), null},
                {scenario2, new Property[]{property1},
                        new AuthAttributeHandlerClientException(ERROR_CODE_AUTH_ATTRIBUTE_HANDLER_NOT_FOUND.getCode(),
                                ERROR_CODE_AUTH_ATTRIBUTE_HANDLER_NOT_FOUND.getMessage()),
                        ERROR_CODE_INVALID_REGISTRATION_OPTION.getCode(), null},
                {scenario3, new Property[]{property1}, new AuthAttributeHandlerException("error-code", "message"),
                        ERROR_CODE_UNEXPECTED_ERROR_VALIDATING_ATTRIBUTES.getCode(), null},
                {scenario4, new Property[]{property1}, null,
                        ERROR_CODE_INVALID_USER_ATTRIBUTES_FOR_REGISTRATION.getCode(), new ValidationResult(false)},
                {scenario5, new Property[]{property1}, null,
                        ERROR_CODE_UNEXPECTED_ERROR_VALIDATING_ATTRIBUTES.getCode(), null}
        };
    }

    @Test(dataProvider = "attributeVerificationFailureData")
    public void testAttributeVerificationFailures(String scenario, Property[] properties, Exception thrownException,
                                                  String expectedErrorCode, ValidationResult validationResult)
            throws Exception {

        LOG.debug("Attribute verification during self registration test scenario: " + scenario);

        if (thrownException != null) {
            when(authAttributeHandlerManager.validateAuthAttributes(anyString(), anyMap())).thenThrow(thrownException);
        } else {
            when(authAttributeHandlerManager.validateAuthAttributes(anyString(), anyMap())).thenReturn(validationResult);
        }

        User user = new User();
        user.setUserName(TEST_USER_NAME);

        Claim claim = new Claim();
        claim.setClaimUri(TEST_CLAIM_URI);
        claim.setValue(TEST_CLAIM_VALUE);

        try {
            userSelfRegistrationManager.verifyUserAttributes(user, "password", new Claim[]{claim}, properties);
        } catch (SelfRegistrationException e) {
            Assert.assertEquals(e.getErrorCode(), expectedErrorCode);
        }
    }

    @Test
    public void testConfirmVerificationCodeMe()
            throws IdentityRecoveryException, UserStoreException {

        // Case 1: Multiple email and mobile per user is enabled.
        String code = "test-code";
        String verificationPendingMobileNumber = "0700000000";
        User user = getUser();
        UserRecoveryData userRecoveryData = new UserRecoveryData(user, TEST_RECOVERY_DATA_STORE_SECRET,
                RecoveryScenarios.MOBILE_VERIFICATION_ON_UPDATE, RecoverySteps.VERIFY_MOBILE_NUMBER);
        userRecoveryData.setRemainingSetIds(verificationPendingMobileNumber);

        when(userRecoveryDataStore.load(eq(code))).thenReturn(userRecoveryData);
        when(privilegedCarbonContext.getUsername()).thenReturn(TEST_USER_NAME);
        when(privilegedCarbonContext.getTenantDomain()).thenReturn(TEST_TENANT_DOMAIN_NAME);

        mockMultiAttributeEnabled(true);
        mockGetUserClaimValue(IdentityRecoveryConstants.MOBILE_NUMBER_CLAIM, StringUtils.EMPTY);
        mockGetUserClaimValue(IdentityRecoveryConstants.VERIFIED_MOBILE_NUMBERS_CLAIM, StringUtils.EMPTY);

        userSelfRegistrationManager.confirmVerificationCodeMe(code, new HashMap<>());

        ArgumentCaptor<Map<String, String>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(userStoreManager, atLeastOnce()).setUserClaimValues(anyString(), claimsCaptor.capture(), isNull());

        Map<String, String> capturedClaims = claimsCaptor.getValue();
        String updatedVerifiedMobileNumbers =
                capturedClaims.get(IdentityRecoveryConstants.VERIFIED_MOBILE_NUMBERS_CLAIM);
        String updatedVerificationPendingMobile =
                capturedClaims.get(IdentityRecoveryConstants.MOBILE_NUMBER_PENDING_VALUE_CLAIM);

        assertEquals(updatedVerificationPendingMobile, StringUtils.EMPTY);
        assertTrue(StringUtils.contains(updatedVerifiedMobileNumbers, verificationPendingMobileNumber));

        // Case 2: Multiple email and mobile per user is disabled.
        mockMultiAttributeEnabled(false);
        ArgumentCaptor<Map<String, String>> claimsCaptor2 = ArgumentCaptor.forClass(Map.class);

        userSelfRegistrationManager.confirmVerificationCodeMe(code, new HashMap<>());

        verify(userStoreManager, atLeastOnce()).setUserClaimValues(anyString(), claimsCaptor2.capture(), isNull());
        Map<String, String> capturedClaims2 = claimsCaptor2.getValue();
        String mobileNumberClaims =
                capturedClaims2.get(IdentityRecoveryConstants.MOBILE_NUMBER_CLAIM);
        String updatedVerificationPendingMobile2 =
                capturedClaims.get(IdentityRecoveryConstants.MOBILE_NUMBER_PENDING_VALUE_CLAIM);

        assertEquals(updatedVerificationPendingMobile2, StringUtils.EMPTY);
        assertEquals(mobileNumberClaims, verificationPendingMobileNumber);
    }

    @Test
    public void testGetConfirmedSelfRegisteredUserVerifyEmail()
            throws IdentityRecoveryException, UserStoreException, IdentityGovernanceException {

        String code = "test-code";
        String verifiedChannelType = "EMAIL";
        String verifiedChannelClaim = "http://wso2.org/claims/emailaddress";
        String verificationPendingEmail = "pasindu@gmail.com";
        Map<String, String> metaProperties = new HashMap<>();

        User user = getUser();
        UserRecoveryData userRecoveryData = new UserRecoveryData(user, TEST_RECOVERY_DATA_STORE_SECRET,
                RecoveryScenarios.EMAIL_VERIFICATION_ON_UPDATE, RecoverySteps.VERIFY_EMAIL);
        // Setting verification pending email claim value.
        userRecoveryData.setRemainingSetIds(verificationPendingEmail);

        when(userRecoveryDataStore.load(eq(code))).thenReturn(userRecoveryData);
        when(userRecoveryDataStore.load(eq(code), anyBoolean())).thenReturn(userRecoveryData);
        when(privilegedCarbonContext.getUsername()).thenReturn(TEST_USER_NAME);
        when(privilegedCarbonContext.getTenantDomain()).thenReturn(TEST_TENANT_DOMAIN_NAME);

        mockMultiAttributeEnabled(true);
        mockGetUserClaimValue(IdentityRecoveryConstants.VERIFIED_EMAIL_ADDRESSES_CLAIM, StringUtils.EMPTY);
        mockGetUserClaimValue(IdentityRecoveryConstants.EMAIL_ADDRESSES_CLAIM, StringUtils.EMPTY);

        org.wso2.carbon.identity.application.common.model.Property property =
                new org.wso2.carbon.identity.application.common.model.Property();
        org.wso2.carbon.identity.application.common.model.Property[] testProperties =
                new org.wso2.carbon.identity.application.common.model.Property[]{property};

        when(identityGovernanceService.getConfiguration(any(), anyString())).thenReturn(testProperties);

        userSelfRegistrationManager.getConfirmedSelfRegisteredUser(code, verifiedChannelType, verifiedChannelClaim,
                metaProperties);

        ArgumentCaptor<Map<String, String>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(userStoreManager, atLeastOnce()).setUserClaimValues(anyString(), claimsCaptor.capture(), isNull());

        Map<String, String> capturedClaims = claimsCaptor.getValue();
        String updatedVerifiedEmailAddresses =
                capturedClaims.get(IdentityRecoveryConstants.VERIFIED_EMAIL_ADDRESSES_CLAIM);
        String verificationPendingEmailAddress =
                capturedClaims.get(IdentityRecoveryConstants.EMAIL_ADDRESS_PENDING_VALUE_CLAIM);

        assertTrue(StringUtils.contains(updatedVerifiedEmailAddresses, verificationPendingEmail));
        assertEquals(verificationPendingEmailAddress, StringUtils.EMPTY);
    }

    @Test
    public void testGetConfirmedSelfRegisteredUserVerifyMobile()
            throws IdentityRecoveryException, UserStoreException, IdentityGovernanceException {

        String code = "test-code";
        String verifiedChannelType = "SMS";
        String verifiedChannelClaim = "http://wso2.org/claims/mobile";
        String verificationPendingMobileNumber = "077888888";
        Map<String, String> metaProperties = new HashMap<>();

        User user = getUser();
        UserRecoveryData userRecoveryData = new UserRecoveryData(user, TEST_RECOVERY_DATA_STORE_SECRET,
                RecoveryScenarios.MOBILE_VERIFICATION_ON_UPDATE, RecoverySteps.VERIFY_MOBILE_NUMBER);
        // Setting verification pending email claim value.
        userRecoveryData.setRemainingSetIds(verificationPendingMobileNumber);

        when(userRecoveryDataStore.load(eq(code))).thenReturn(userRecoveryData);
        when(userRecoveryDataStore.load(eq(code), anyBoolean())).thenReturn(userRecoveryData);
        when(privilegedCarbonContext.getUsername()).thenReturn(TEST_USER_NAME);
        when(privilegedCarbonContext.getTenantDomain()).thenReturn(TEST_TENANT_DOMAIN_NAME);

        mockGetUserClaimValue(IdentityRecoveryConstants.VERIFIED_MOBILE_NUMBERS_CLAIM, StringUtils.EMPTY);
        mockGetUserClaimValue(IdentityRecoveryConstants.MOBILE_NUMBERS_CLAIM, StringUtils.EMPTY);

        org.wso2.carbon.identity.application.common.model.Property property =
                new org.wso2.carbon.identity.application.common.model.Property();
        org.wso2.carbon.identity.application.common.model.Property[] testProperties =
                new org.wso2.carbon.identity.application.common.model.Property[]{property};

        when(identityGovernanceService.getConfiguration(any(), anyString())).thenReturn(testProperties);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::isMultiEmailsAndMobileNumbersPerUserEnabled).thenReturn(true);
            mockedUtils.when(() -> Utils.getConnectorConfig(
                            eq(IdentityRecoveryConstants.ConnectorConfig.ENABLE_MOBILE_VERIFICATION_BY_PRIVILEGED_USER),
                            anyString()))
                    .thenReturn("true");
            userSelfRegistrationManager.getConfirmedSelfRegisteredUser(code, verifiedChannelType, verifiedChannelClaim,
                    metaProperties);
        }

        ArgumentCaptor<Map<String, String>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(userStoreManager, atLeastOnce()).setUserClaimValues(anyString(), claimsCaptor.capture(), isNull());

        Map<String, String> capturedClaims = claimsCaptor.getValue();
        String updatedVerifiedMobileNumbers =
                capturedClaims.get(IdentityRecoveryConstants.VERIFIED_MOBILE_NUMBERS_CLAIM);
        String verificationPendingMobileNumberClaim =
                capturedClaims.get(IdentityRecoveryConstants.MOBILE_NUMBER_PENDING_VALUE_CLAIM);
        String updatedMobileNumberClaimValue =
                capturedClaims.get(IdentityRecoveryConstants.MOBILE_NUMBER_CLAIM);

        assertTrue(StringUtils.contains(updatedVerifiedMobileNumbers, verificationPendingMobileNumber));
        assertEquals(verificationPendingMobileNumberClaim, StringUtils.EMPTY);
        assertEquals(updatedMobileNumberClaimValue, verificationPendingMobileNumber);
    }

    private User getUser() {

        User user = new User();
        user.setUserName(TEST_USER_NAME);
        user.setUserStoreDomain(TEST_USERSTORE_DOMAIN);
        user.setTenantDomain(TEST_TENANT_DOMAIN_NAME);
        return user;
    }

    private void mockMultiAttributeEnabled(Boolean isEnabled) {

        mockedIdentityUtil.when(() -> IdentityUtil.getProperty(
                eq(IdentityRecoveryConstants.ConnectorConfig.SUPPORT_MULTI_EMAILS_AND_MOBILE_NUMBERS_PER_USER)))
                .thenReturn(isEnabled.toString());
    }

    private void mockGetUserClaimValue(String claimUri, String claimValue) throws UserStoreException {

        when(userStoreManager.getUserClaimValue(any(), eq(claimUri), any())).thenReturn(claimValue);
    }

    /**
     * Sample consent manager class.
     */
    class MyConsentManager extends ConsentManagerImpl {

        public MyConsentManager(ConsentManagerConfigurationHolder configHolder) {

            super(configHolder);
        }

        public AddReceiptResponse addConsent(ReceiptInput receiptInput) throws ConsentManagementException {

            resultReceipt = receiptInput;
            return null;
        }
    }
}