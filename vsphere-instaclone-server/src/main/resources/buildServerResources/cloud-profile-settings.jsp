<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>

<tr>
    <th>
        <label for="vmwareInstacloneSdkUrl">vCenter SDK URL:</label><l:star/>
    </th>
    <td>
        <props:textProperty name="vmwareInstacloneSdkUrl" className="longField" />
    </td>
</tr>
<tr>
    <th>
        <label for="vmwareInstacloneUsername">Username:</label><l:star/>
    </th>
    <td>
        <props:textProperty name="vmwareInstacloneUsername" className="longField" />
    </td>
</tr>
<tr>
    <th>
        <label for="vmwareInstaclonePassword">Password:</label><l:star/>
    </th>
    <td>
        <props:passwordProperty name="vmwareInstaclonePassword" className="longField" />
    </td>
</tr>
<tr>
    <th>
        <label for="vmwareInstacloneImages">Images:</label><l:star/>
    </th>
    <td>
        <props:multilineProperty name="vmwareInstacloneImages" linkTitle="" cols="120" rows="25" />
    </td>
</tr>

<props:hiddenProperty name="vmwareInstacloneProfileUuid" />
