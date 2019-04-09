<%@ page import="co.elastic.webapp.TestDAO" %>
<%@ page import="co.elastic.webapp.Constants" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.sql.SQLException" %>
<html>
<body>
<%
    boolean causeDbError = request.getParameter(Constants.CAUSE_DB_ERROR) != null;
    boolean causeServletError = request.getParameter(Constants.CAUSE_TRANSACTION_ERROR) != null;
    Exception cause = null;
    try {
        String content;
        try {
            content = TestDAO.instance().queryDb(causeDbError);
        } catch (SQLException e) {
            cause = e;
            content = Constants.DB_ERROR;
        }
        response.getWriter().append(content);
    } catch (IOException e) {
        cause = e;
    }

    if (causeServletError) {
        throw new ServletException(Constants.TRANSACTION_FAILURE, cause);
    }
%>
</body>
</html>
