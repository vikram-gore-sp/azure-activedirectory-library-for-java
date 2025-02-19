/*******************************************************************************
 * Copyright © Microsoft Open Technologies, Inc.
 * 
 * All Rights Reserved
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * THIS CODE IS PROVIDED *AS IS* BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION
 * ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE, FITNESS FOR A
 * PARTICULAR PURPOSE, MERCHANTABILITY OR NON-INFRINGEMENT.
 * 
 * See the Apache License, Version 2.0 for the specific language
 * governing permissions and limitations under the License.
 ******************************************************************************/
package com.microsoft.aad.adal4j;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class WSTrustRequest {

    private final static Logger log = LoggerFactory
            .getLogger(WSTrustRequest.class);

    private final static int MAX_EXPECTED_MESSAGE_SIZE = 1024;
    private final static String DEFAULT_APPLIES_TO = "urn:federation:MicrosoftOnline";    
    
    static WSTrustResponse execute(String url, String username, String password)
            throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/soap+xml; charset=utf-8");


        BindingPolicy policy = MexParser.getWsTrustEndpointFromMexEndpoint(url);
        String soapAction = "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue"; // default value (WSTrust 1.3)

        // only change it if version is wsTrust2005, otherwise default to wsTrust13
        if (policy.getVersion() == WsTrustVersion.WSTRUST2005)
        {
            soapAction = "http://schemas.xmlsoap.org/ws/2005/02/trust/RST/Issue"; // wsTrust2005 soap value
        }

        headers.put("SOAPAction", soapAction);
        String body = buildMessage(policy.getUrl(), username, password, policy.getVersion()).toString();
        String response = HttpHelper.executeHttpPost(log, policy.getUrl(), body, headers);
        return WSTrustResponse.parse(response, policy.getVersion());
    }

    private static StringBuilder buildMessage(String address, String username,
            String password, WsTrustVersion addressVersion) {

        StringBuilder securityHeaderBuilder = new StringBuilder(
                MAX_EXPECTED_MESSAGE_SIZE);
        buildSecurityHeader(securityHeaderBuilder, username, password, addressVersion);
        String guid = UUID.randomUUID().toString();
        StringBuilder messageBuilder = new StringBuilder(
                MAX_EXPECTED_MESSAGE_SIZE);
        
        String schemaLocation = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
        String soapAction = "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue";
        String rstTrustNamespace = "http://docs.oasis-open.org/ws-sx/ws-trust/200512";
        String keyType = "http://docs.oasis-open.org/ws-sx/ws-trust/200512/Bearer";
        String requestType = "http://docs.oasis-open.org/ws-sx/ws-trust/200512/Issue";
        
        if (addressVersion == WsTrustVersion.WSTRUST2005)
        {
            soapAction = "http://schemas.xmlsoap.org/ws/2005/02/trust/RST/Issue";
            rstTrustNamespace = "http://schemas.xmlsoap.org/ws/2005/02/trust";
            keyType = "http://schemas.xmlsoap.org/ws/2005/05/identity/NoProofKey";
            requestType = "http://schemas.xmlsoap.org/ws/2005/02/trust/Issue";
        }


        // Example WSTrust 1.3 request
        // <s:Envelope xmlns:wst='http://schemas.xmlsoap.org/ws/2005/02/trust' xmlns:wssc='http://schemas.xmlsoap.org/ws/2005/02/sc' xmlns:wsa='http://www.w3.org/2005/08/addressing' xmlns:wsu='http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd' 
        //          xmlns:wsp='http://schemas.xmlsoap.org/ws/2004/09/policy' xmlns:saml='urn:oasis:names:tc:SAML:1.0:assertion' xmlns:wsse='http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd' xmlns:ps='http://schemas.microsoft.com/Passport/SoapServices/PPCRL'
        //          mlns:s='http://www.w3.org/2003/05/soap-envelope'>
        //  <s:Header>
        //      <wsa:Action s:mustUnderstand='1'>http://schemas.xmlsoap.org/ws/2005/02/trust/RST/Issue</wsa:Action>
        //      <wsa:To s:mustUnderstand='1'>https://corp.sts.microsoft.com:443/adfs/services/trust/2005/windowstransport</wsa:To>
        //      <wsa:MessageID>1303795308</wsa:MessageID>-<wsse:Security>-<wsu:Timestamp Id="Timestamp"><wsu:Created>2011-04-26T05:21:50Z</wsu:Created><wsu:Expires>2011-04-26T05:26:50Z</wsu:Expires></wsu:Timestamp></wsse:Security></s:Header>-<s:Body>-<wst:RequestSecurityToken Id="RST0"><wst:RequestType>http://schemas.xmlsoap.org/ws/2005/02/trust/Issue</wst:RequestType>-<wsp:AppliesTo>-<wsa:EndpointReference><wsa:Address>urn:federation:MicrosoftOnline</wsa:Address></wsa:EndpointReference></wsp:AppliesTo><wst:KeyType>http://schemas.xmlsoap.org/ws/2005/05/identity/NoProofKey</wst:KeyType></wst:RequestSecurityToken></s:Body></s:Envelope>
        messageBuilder
                .append(String
                        .format("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope' xmlns:a='http://www.w3.org/2005/08/addressing' xmlns:u='%s'>"
                                        + "<s:Header>"
                                        + "<a:Action s:mustUnderstand='1'>%s</a:Action>"
                                        + "<a:messageID>urn:uuid:"
                                        + "%s"
                                        + // guid
                                        "</a:messageID>"
                                        + "<a:ReplyTo>"
                                        + "<a:Address>http://www.w3.org/2005/08/addressing/anonymous</a:Address>"
                                        + "</a:ReplyTo>"
                                        + "<a:To s:mustUnderstand='1'>"
                                        + "%s"
                                        + // resource
                                        "</a:To>"
                                        + "%s"
                                        + // securityHeader
                                        "</s:Header>"
                                        + "<s:Body>"
                                        + "<trust:RequestSecurityToken xmlns:trust='%s'>"
                                        + "<wsp:AppliesTo xmlns:wsp='http://schemas.xmlsoap.org/ws/2004/09/policy'>"
                                        + "<a:EndpointReference>"
                                        + "<a:Address>"
                                        + "%s"
                                        + // appliesTo like
                                        // urn:federation:MicrosoftOnline. Either
                                        // wst:TokenType or wst:AppliesTo should be
                                        // defined in the token request message. If
                                        // both are specified, the wst:AppliesTo field
                                        // takes precedence.
                                        "</a:Address>"
                                        + "</a:EndpointReference>"
                                        + "</wsp:AppliesTo>"
                                        + "<trust:KeyType>%s</trust:KeyType>"
                                        + "<trust:RequestType>%s</trust:RequestType>"
                                        + // If we dont specify tokentype, it will
                                        // return samlv1.1
                                        "</trust:RequestSecurityToken>"
                                        + "</s:Body>"
                                        + "</s:Envelope>", schemaLocation, soapAction,
                                guid, address, securityHeaderBuilder.toString(),
                                rstTrustNamespace, DEFAULT_APPLIES_TO, keyType,
                                requestType));

        return messageBuilder;
    }

    private static StringBuilder buildSecurityHeader(StringBuilder securityHeaderBuilder, String username, String password, WsTrustVersion version) {

        StringBuilder messageCredentialsBuilder = new StringBuilder(
                MAX_EXPECTED_MESSAGE_SIZE);
        String guid = UUID.randomUUID().toString();
        username = StringEscapeUtils.escapeXml10(username);
        password = StringEscapeUtils.escapeXml10(password);

        DateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = new Date();
        String currentTimeString = dateFormat.format(date);

        // Expiry is 10 minutes after creation
        int toAdd = 60 * 1000 * 10;
        date = new Date(date.getTime() + toAdd);
        String expiryTimeString = dateFormat.format(date);

        messageCredentialsBuilder.append(String.format(
                "<o:UsernameToken u:Id='uuid-"
                + "%s'>" + // guid
                "<o:Username>%s</o:Username>" + //username
                "<o:Password>%s</o:Password>" + //password
                "</o:UsernameToken>",
                guid,
                username,
                password));

        securityHeaderBuilder.append("<o:Security s:mustUnderstand='1' xmlns:o='http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd'>");
        securityHeaderBuilder.append(String.format(
                "<u:Timestamp u:Id='_0'>"
                + "<u:Created>%s</u:Created>" + // created
                "<u:Expires>%s</u:Expires>" + // Expires
                "</u:Timestamp>",
                currentTimeString, expiryTimeString));
        securityHeaderBuilder.append(messageCredentialsBuilder.toString());
        securityHeaderBuilder.append("</o:Security>");

        return securityHeaderBuilder;
    }
}
