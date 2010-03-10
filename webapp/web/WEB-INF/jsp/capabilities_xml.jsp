<%@include file="xml_header.jsp"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%
response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
response.setHeader("Pragma","no-cache"); //HTTP 1.0
response.setDateHeader ("Expires", 0); //prevents caching at the proxy server
%>
<%-- Displays the Capabilities document in XML. --%>
<WMS_Capabilities
        version="1.3.0"
        updateSequence="${lastUpdate}"
        xmlns="http://www.opengis.net/wms"
        xmlns:xlink="http://www.w3.org/1999/xlink"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.opengis.net/wms http://schemas.opengis.net/wms/1.3.0/capabilities_1_3_0.xsd">
        
    <Service>
        <Name>WMS</Name>
        <Title>${title}</Title>
        <Abstract>${abstract}</Abstract>
        <KeywordList>
            <%-- TODO --%>
        </KeywordList>
        <OnlineResource xlink:type="simple" xlink:href="<c:out value="${config.server.url}"/>"/>
        <ContactInformation>
            <ContactPersonPrimary>
                <ContactPerson><c:out value="${config.contact.name}"/></ContactPerson>
                <ContactOrganization><c:out value="${config.contact.org}"/></ContactOrganization>
            </ContactPersonPrimary>
            <ContactVoiceTelephone><c:out value="${config.contact.tel}"/></ContactVoiceTelephone>
            <ContactElectronicMailAddress><c:out value="${config.contact.email}"/></ContactElectronicMailAddress>
        </ContactInformation>
        <Fees>none</Fees>
        <AccessConstraints>none</AccessConstraints>
        <LayerLimit>${layerLimit}</LayerLimit>
        <MaxWidth>${maxImageSize}</MaxWidth>
        <MaxHeight>${maxImageSize}</MaxHeight>
    </Service>
    <Capability>
        <Request>
            <GetCapabilities>
                <Format>text/xml</Format>
                <DCPType><HTTP><Get><OnlineResource xlink:type="simple" xlink:href="<c:out value="${wmsBaseUrl}"/>"/></Get></HTTP></DCPType>
            </GetCapabilities>
            <GetMap>
                <c:forEach var="mimeType" items="${supportedImageFormats}">
                <Format>${mimeType}</Format>
                </c:forEach>
                <DCPType><HTTP><Get><OnlineResource xlink:type="simple" xlink:href="<c:out value="${wmsBaseUrl}"/>"/></Get></HTTP></DCPType>
            </GetMap>
        </Request>
        <Exception>
            <Format>XML</Format>
        </Exception>
        <Layer>
        </Layer>
    </Capability>
    
</WMS_Capabilities>