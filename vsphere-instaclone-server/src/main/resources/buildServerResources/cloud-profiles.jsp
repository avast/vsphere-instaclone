<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" session="true" errorPage="/runtimeError.html"
%><%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"
%><%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"
%><%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"
%><%@ taglib prefix="bs" tagdir="/WEB-INF/tags"
%><%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout"
%><%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms"
%><%@ taglib prefix="authz" tagdir="/WEB-INF/tags/authz"
%><%@ taglib prefix="afn" uri="/WEB-INF/functions/authz"
%><%@ taglib prefix="graph" tagdir="/WEB-INF/tags/graph"
%><%@ taglib prefix="props" tagdir="/WEB-INF/tags/props"
%><%@ taglib prefix="util" uri="/WEB-INF/functions/util"
%>

<bs:page isExperimentalUI="false">

<jsp:attribute name="head_include">
    <script type="text/javascript">
        // ReactUI.setIsExperimentalUI();
    </script>
</jsp:attribute>
    <jsp:attribute name="body_include">
    <div id="app"></div>
    <%@ include file="profiles-table.jsp" %>
</jsp:attribute>
</bs:page>