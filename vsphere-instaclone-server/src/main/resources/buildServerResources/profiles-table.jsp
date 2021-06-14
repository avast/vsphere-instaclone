<%--<%@include file="/include.jsp" %>--%>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%--<%@ taglib prefix="clouds" tagdir="/WEB-INF/tags/clouds" %>--%>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="authz" tagdir="/WEB-INF/tags/authz" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>

<jsp:useBean id="profiles"
             type="java.util.List<com.avast.teamcity.plugins.instaclone.web.service.CloudProfilesService.ProfileItem>"
             scope="request"/>

<bs:linkCSS dynamic="${true}">
    /css/visibleProjects.css
    /css/settingsTable.css
    /css/profilePage.css
    /css/settingsBlock.css
    /css/tags.css
    /css/admin/adminMain.css
    /plugins/vsphere-instaclone2/css/instaProfiles.css
</bs:linkCSS>

<bs:linkScript>
    /plugins/vsphere-instaclone2/js/instaProfiles.js
</bs:linkScript>

<h1>vSphere-Instaclone profiles</h1>

<table class="highlightable parametersTable">
    <tr class="header">
        <th>Project</th>
        <th>Project Id</th>
        <th>Profile Name</th>
        <th>Profile Id</th>
        <th></th>
        <th>VCenterAccount</th>
        <th>SDK Url</th>
        <th>Templates</th>
    </tr>

    <c:forEach var="item" items="${profiles}">
        <tr>
            <td>
                <bs:projectLinkFull project="${item.project}"/>
            </td>
            <td>
                <code><c:out value="${item.project.externalId}"/></code>
            </td>
            <td>
                <authz:authorize projectId="${item.project.projectId}" allPermissions="MANAGE_AGENT_CLOUDS">
  <span style="font-weight:normal;"><a title="Click to edit cloud profile"
                                       href="<c:url value='/admin/editProject.html?projectId=${item.project.externalId}&tab=clouds&action=edit&profileId=${item.profile.profileId}&showEditor=true'/>"
  ><c:out value="${item.profile.profileName}"/>
  </a></span>
                </authz:authorize>
            </td>
            <td>
                <code><c:out value="${item.profile.profileId}"/></code>
            </td>
            <td class="editable">
                <c:if test="${not item.project.readOnly}">
                    <c:set value="${util:forJS(item.profile.profileName, true, true)}" var="escapedName"/>
                    <c:if test="${item.profile.enabled}">
                        <a href="#" onclick="enableOrDisableProfile('<bs:forJs>${item.project.externalId}</bs:forJs>',
                                '<bs:forJs>${item.profile.profileId}</bs:forJs>','disable'); return false">Disable</a>
                    </c:if>
                    <c:if test="${not item.profile.enabled}">
                        <a href="#" onclick="enableOrDisableProfile('<bs:forJs>${item.project.externalId}</bs:forJs>',
                                '<bs:forJs>${item.profile.profileId}</bs:forJs>', 'enable'); return false">Enable</a>
                    </c:if>
                </c:if>
            </td>
            <td>
                <c:if test="${item.accountId != null}">
                    <code><c:out value="${item.accountId}"/></code>
                </c:if>
            </td>
            <td>
                <c:if test="${item.sdkUrl != null}">
                    <code><c:out value="${item.sdkUrl}"/></code>
                </c:if>
            </td>

            <td>
                <c:if test="${item.templates.size() > 1}">
                    <ul class="listable">
                        <c:forEach var="template" items="${item.templates}">
                            <li>
                                <code>
                                    <c:out value="${template}"/>
                                </code>
                            </li>
                        </c:forEach>
                    </ul>
                </c:if>
                <c:if test="${item.templates.size() == 1}">
                    <c:forEach var="template" items="${item.templates}">
                        <code>
                            <c:out value="${template}"/>
                        </code>
                    </c:forEach>
                </c:if>

            </td>


        </tr>

    </c:forEach>

</table>