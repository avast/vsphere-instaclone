<%--
  ~ Copyright (c) 2006, JetBrains, s.r.o. All Rights Reserved.
  --%>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>


<div class="parameter">
    Template name suffix: <props:displayValue name="templateNameSuffix" emptyValue="" showInPopup="false" popupTitle="Template name suffix" popupLinkText="view template name suffix"/>
</div>
<div class="parameter">
    Clone number limit: <props:displayValue name="cloneNumberLimit" emptyValue="" showInPopup="false" popupTitle="Clone number limit" popupLinkText="view clone number limit"/>
</div>
<div class="parameter">
    Timeout: <props:displayValue name="templateTimeout" emptyValue="" showInPopup="false" popupTitle="Template timeout" popupLinkText="view template timeout"/>
</div>