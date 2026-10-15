import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevClient;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevProperties;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Free public GETs only. No API keys, provider inference endpoint, application or database. */
public class JevNetworkDiagnostic {
    static final URI TRACE = URI.create("https://www.cloudflare.com/cdn-cgi/trace");

    static Map<String, Object> get(HttpClient client, URI uri, Map<String, String> privateTrace) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("endpoint", uri.toString());
        try {
            var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            result.put("httpStatus", response.statusCode());
            if (uri.equals(TRACE) && response.statusCode() == 200) {
                for (String line : response.body().split("\\R")) {
                    if (line.startsWith("loc=") && line.substring(4).matches("[A-Z]{2}")) {
                        result.put("country", line.substring(4));
                    }
                    // Used only for equality comparison; never printed or written.
                    if (line.startsWith("ip=")) privateTrace.put("ip", line.substring(3));
                }
            }
        } catch (Exception failure) {
            result.put("errorType", failure.getClass().getSimpleName());
        }
        return result;
    }

    static String connect(String host, int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(10000);
            socket.getOutputStream().write(("CONNECT example.com:443 HTTP/1.1\r\n"
                    + "Host: example.com:443\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            var line = new StringBuilder();
            for (int next; line.length() < 256 && (next = socket.getInputStream().read()) != -1;) {
                if (next == '\n') break;
                if (next != '\r') line.append((char) next);
            }
            String[] fields = line.toString().split(" ");
            return fields.length > 1 && fields[1].matches("[0-9]{3}") ? fields[1] : "INVALID_RESPONSE";
        } catch (Exception failure) {
            return failure.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) throws Exception {
        var properties = new JevProperties();
        properties.setConnectTimeout(Duration.ofSeconds(5));
        var result = new LinkedHashMap<String, Object>();
        result.put("timeUtc", Instant.now().toString());
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("javaUseSystemProxies", System.getProperty("java.net.useSystemProxies", "false"));
        result.put("defaultSelectorClass", ProxySelector.getDefault().getClass().getName());
        result.put("defaultJevSelection", ProxySelector.getDefault().select(URI.create("https://api.typesafe.ai"))
                .stream().map(p -> p.type().name()).toList());
        try (var defaultClient = JevClient.createHttpClient(properties);
             var directClient = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY)
                     .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()) {
            result.put("defaultClientExposesExplicitProxy", defaultClient.proxy().isPresent());
            properties.getProxy().setEnabled(true);
            properties.getProxy().setHost("127.0.0.1");
            properties.getProxy().setPort(7897);
            try (var proxyClient = JevClient.createHttpClient(properties)) {
                var privateDirect = new HashMap<String, String>();
                var privateProxy = new HashMap<String, String>();
                result.put("productionProxyJevSelection", proxyClient.proxy().orElseThrow()
                        .select(URI.create("https://api.typesafe.ai")).stream().map(p -> p.type().name()).toList());
                result.put("proxyHttp", get(proxyClient, URI.create("http://example.com/"), new HashMap<>()));
                result.put("proxyConnectStatus", connect("127.0.0.1", 7897));
                result.put("proxyHttps", get(proxyClient, URI.create("https://example.com/"), new HashMap<>()));
                result.put("noHttpProxyTrace", get(directClient, TRACE, privateDirect));
                result.put("explicitProxyTrace", get(proxyClient, TRACE, privateProxy));
                result.put("publicExitComparison", privateDirect.containsKey("ip") && privateProxy.containsKey("ip")
                        ? (privateDirect.get("ip").equals(privateProxy.get("ip")) ? "SAME" : "DIFFERENT") : "UNKNOWN");
                result.put("providerRegionAccess", "UNKNOWN");
                result.put("realJevCalls", 0);
            }
        }
        var json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result);
        Files.writeString(Path.of(args[0]), json);
        System.out.println(json);
    }
}
