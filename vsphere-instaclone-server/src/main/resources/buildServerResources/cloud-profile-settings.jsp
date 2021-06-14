<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="vCenterAccounts" scope="request"
             type="java.util.Collection<com.avast.teamcity.plugins.instaclone.web.service.profile.VCenterAccount>"/>
<jsp:useBean id="accountsException" scope="request" type="java.lang.String"/>

<l:settingsGroup title="VMWare Instaclone profile settings">
    <tr>
        <th>
            <label for="vmwareInstacloneVCenterAccount">vCenter Account:</label><l:star/>
        </th>
        <td>
            <props:selectProperty name="vmwareInstacloneVCenterAccount" enableFilter="true" className="longField">
                <c:forEach items="${vCenterAccounts}" var="account">
                    <props:option value="${account.id}">
                        <c:out value="${account.id} (${account.url})"/>
                    </props:option>
                </c:forEach>
            </props:selectProperty>
            <span class="error" id="error_accountsException">
                <c:out value="${accountsException}"/>
            </span>
            <span class="error" id="error_vmwareInstacloneVCenterAccount"></span>
            <span class="error" id="error_vmwareInstacloneConnectionInfo"></span>
        </td>
    </tr>
    <%--    <tr>--%>
    <%--        <th>--%>
    <%--            <label for="vmwareInstacloneSdkUrl">vCenter SDK URL:</label><l:star/>--%>
    <%--        </th>--%>
    <%--        <td>--%>

    <%--            <props:textProperty name="vmwareInstacloneSdkUrl" className="longField"/>--%>
    <%--            <span class="error" id="error_vmwareInstacloneSdkUrl"></span>--%>
    <%--            <span class="error" id="error_vmwareInstacloneConnectionInfo"></span>--%>
    <%--        </td>--%>
    <%--    </tr>--%>
    <%--    <tr>--%>
    <%--        <th>--%>
    <%--            <label for="vmwareInstacloneUsername">Username:</label><l:star/>--%>
    <%--        </th>--%>
    <%--        <td>--%>
    <%--            <props:textProperty name="vmwareInstacloneUsername" className="longField"/>--%>
    <%--            <span class="error" id="error_vmwareInstacloneUsername"></span>--%>
    <%--        </td>--%>
    <%--    </tr>--%>
    <%--    <tr>--%>
    <%--        <th>--%>
    <%--            <label for="vmwareInstaclonePassword">Password:</label><l:star/>--%>
    <%--        </th>--%>
    <%--        <td>--%>
    <%--            <props:passwordProperty name="vmwareInstaclonePassword" className="longField"/>--%>
    <%--            <span class="error" id="error_vmwareInstaclonePassword"></span>--%>
    <%--        </td>--%>
    <%--    </tr>--%>
    <tr>
        <th>
            <label for="vmwareInstacloneImages">Images:</label><l:star/>
        </th>
        <td>
            <props:multilineProperty name="vmwareInstacloneImages" linkTitle="" cols="120" rows="25"/>
            <span class="error" id="error_vmwareInstacloneImages"></span>
            <span class="smallNote">
For each image, <code>template</code> specifies the path to the virtual machine to clone.
On vSphere, this path always starts with the name of the datacenter, followed by "vm".
<br/>
The key <code>instanceFolder</code> specifies the vSphere folder in which cloned machines should
be placed. This can be the folder your template resides in.
The name of the image is used as a base for the names of the cloned images.
<br/>
The plugin will not allow more than <code>maxInstances</code> instances to run simultaneously.
<br/>
If <code>agentPool</code> is unspecified, the image will be assigned to the default pool.
You can set an explicit pool by specifying either the pool's name or its
numeric identifier.
<br/>
Optionally, you can set the network to which the cloned machine's ethernet card should
be connected. If your image contains multiple network cards, set this field to an
array of network names.
    </span>
        </td>
    </tr>
    <props:hiddenProperty name="vmwareInstacloneProfileUuid"/>
</l:settingsGroup>