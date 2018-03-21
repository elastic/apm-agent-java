package co.elastic.apm.servlet;

import javax.servlet.http.HttpServletRequest;

/**
 * Utility class which helps to determine the real IP of a {@link HttpServletRequest}
 * <p>
 * This implementation is based on
 * <a href="https://github.com/stagemonitor/stagemonitor/blob/0.88.2/stagemonitor-web-servlet/src/main/java/org/stagemonitor/web/servlet/MonitoredHttpRequest.java">stagemonitor</a>
 * </p>
 */
public class ClientIpUtils {

    /**
     * This method returns the first IP from common HTTP header names used by reverse proxies.
     * If there is no such header, it returns {@link HttpServletRequest#getRemoteAddr()}
     *
     * @param request The HTTP request.
     * @return the
     */
    public static String getRealIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        ip = getIpFromHeaderIfNotAlreadySet("X-Real-IP", request, ip);
        ip = getIpFromHeaderIfNotAlreadySet("Proxy-Client-IP", request, ip);
        ip = getIpFromHeaderIfNotAlreadySet("WL-Proxy-Client-IP", request, ip);
        ip = getIpFromHeaderIfNotAlreadySet("HTTP_CLIENT_IP", request, ip);
        ip = getIpFromHeaderIfNotAlreadySet("HTTP_X_FORWARDED_FOR", request, ip);
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return getFirstIp(ip);
    }

    /*
     * Can be a comma separated list if there are multiple devices in the forwarding chain
     */
    private static String getFirstIp(String ip) {
        if (ip != null) {
            final int indexOfFirstComma = ip.indexOf(',');
            if (indexOfFirstComma != -1) {
                ip = ip.substring(0, indexOfFirstComma);
            }
        }
        return ip;
    }

    private static String getIpFromHeaderIfNotAlreadySet(String header, HttpServletRequest request, String ip) {
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader(header);
        }
        return ip;
    }
}
