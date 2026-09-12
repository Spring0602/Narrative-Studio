package edu.njust.narrativestudio.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import edu.njust.narrativestudio.exception.BusinessException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class OllamaDialogueProviderTest {
    HttpServer server;
    final ObjectMapper json=new ObjectMapper();
    final AtomicReference<String> request=new AtomicReference<>();
    String response="{\"response\":\"你好，旅人。\",\"done\":true}";
    int status=200;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/generate",exchange->{
            request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.getResponseHeaders().add("Location","/forbidden-redirect");
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            try(var output=exchange.getResponseBody()) {output.write(bytes);}
        });
        server.start();
    }
    @AfterEach void stop() {server.stop(0);}
    OllamaDialogueProvider provider() {
        return new OllamaDialogueProvider(true,"http://127.0.0.1:"+server.getAddress().getPort()+"/api/generate","test-model",json);
    }
    @Test void sendsNonStreamingBoundedGenerationAndReturnsOnlyText() throws Exception {
        assertEquals("你好，旅人。",provider().generate("Selected context"));
        var body=json.readTree(request.get());
        assertFalse(body.get("stream").asBoolean());
        assertEquals(512,body.get("options").get("num_predict").asInt());
        assertEquals("Selected context",body.get("prompt").asText());
    }
    @Test void disabledDoesNotContactProvider() {
        assertThrows(BusinessException.class,()->new OllamaDialogueProvider(false,"http://127.0.0.1:1","test",json).generate("text"));
        assertNull(request.get());
    }
    @Test void insecureRemoteEndpointRejectedBeforeNetwork() {
        assertThrows(BusinessException.class,()->new OllamaDialogueProvider(true,"http://example.com/api/generate","test",json).generate("text"));
        assertNull(request.get());
    }
    @Test void unsuccessfulResponseIsUnavailable() {
        status=503;assertThrows(BusinessException.class,()->provider().generate("text"));
    }
    @Test void redirectIsNotFollowed() {
        status=302;assertThrows(BusinessException.class,()->provider().generate("text"));
    }
    @Test void malformedOrIncompleteResponseIsRejected() {
        response="not json";assertThrows(BusinessException.class,()->provider().generate("text"));
        response="{\"response\":\"partial\",\"done\":false}";assertThrows(BusinessException.class,()->provider().generate("text"));
    }
    @Test void oversizedResponseIsRejected() {
        response="x".repeat(70000);assertThrows(BusinessException.class,()->provider().generate("text"));
    }
}
