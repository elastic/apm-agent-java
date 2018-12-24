<%@ page import="co.elastic.webapp.TestDAO" %>
<%@ page import="static co.elastic.webapp.Constants.CAUSE_DB_ERROR" %>
<%@ page import="static co.elastic.webapp.Constants.CAUSE_TRANSACTION_ERROR" %>
<%@ page import="static co.elastic.webapp.Constants.DB_ERROR" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="static co.elastic.webapp.Constants.TRANSACTION_FAILURE" %>
<html>
<body>
<%
    boolean causeDbError = request.getParameter(CAUSE_DB_ERROR) != null;
    boolean causeServletError = request.getParameter(CAUSE_TRANSACTION_ERROR) != null;
    Exception cause = null;
    try {
        String content;
        try {
            content = TestDAO.instance().queryDb(causeDbError);
        } catch (SQLException e) {
            cause = e;
            content = DB_ERROR;
        }
        response.getWriter().append(content);
    } catch (IOException e) {
        cause = e;
    }

    if (causeServletError) {
        throw new ServletException(TRANSACTION_FAILURE, cause);
    }
%>
</body>
</html>
