<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<link rel="stylesheet" href="${teamcityPluginResourcesPath}codemirror.css">
<l:settingsGroup title="VSphere Instaclone build step settings">
    <tr>
        <th>
            <label for="templateNameSuffix">Template name suffix: <l:star/></label>
        </th>
        <td>
            <div class="postRel">
                <props:textProperty name="templateNameSuffix" className="longField"/>
            </div>
            <span class="smallNote">Template name suffix appended to a new instaclone name</span>
            <span class="error" id="error_templateNameSuffix"></span>
        </td>
    </tr>
    <tr>
        <th>
            <label for="cloneNumberLimit">Clone number limit: <l:star/></label>
        </th>
        <td>
            <div class="postRel">
                <props:textProperty name="cloneNumberLimit" className="smallField" maxlength="7"/>
            </div>
            <span class="smallNote">How many older clones should be preserved. Min value is 1.</span>
            <span class="error" id="error_cloneNumberLimit"></span>
        </td>
    </tr>
    <tr>
        <th>
            <label for="templateTimeout">Clone generation timeout (seconds): <l:star/></label>
        </th>
        <td>
            <div class="postRel">
                <props:textProperty name="templateTimeout" className="smallField" maxlength="7"/>
            </div>
            <span class="smallNote">How many seconds to wait to generate a new clone. '0' value means default value.</span>
            <span class="error" id="error_templateTimeout"></span>
        </td>
    </tr>
</l:settingsGroup>