package com.ismile.core.docs.security;

import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocsAuthenticationGrpcInterceptorTest {

    private DocsAuthenticationGrpcInterceptor interceptor;

    @Mock
    private JwtUtil jwtUtil;

    private DocsSecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        securityProperties = new DocsSecurityProperties();
        interceptor = new DocsAuthenticationGrpcInterceptor(securityProperties, jwtUtil);
    }

    @Test
    void allowsCallWhenTokenIsValid() {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer token-value");
        when(jwtUtil.validateToken("token-value")).thenReturn(true);

        TestServerCallHandler<Object, Object> handler = new TestServerCallHandler<>();
        TestServerCall<Object, Object> call = new TestServerCall<>("docs.DocsService/CreateCategory");

        interceptor.interceptCall(call, headers, handler);

        assertThat(handler.proceeded).isTrue();
        assertThat(call.isClosed()).isFalse();
        verify(jwtUtil).validateToken("token-value");
    }

    @Test
    void blocksCallWhenTokenMissing() {
        Metadata headers = new Metadata();
        TestServerCallHandler<Object, Object> handler = new TestServerCallHandler<>();
        TestServerCall<Object, Object> call = new TestServerCall<>("docs.DocsService/CreateCategory");

        interceptor.interceptCall(call, headers, handler);

        assertThat(handler.proceeded).isFalse();
        assertThat(call.isClosed()).isTrue();
        assertThat(call.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void skipsAuthForPublicEndpoint() {
        securityProperties.getPublicEndpoints().add("docs.DocsService/Ping");
        Metadata headers = new Metadata();
        TestServerCallHandler<Object, Object> handler = new TestServerCallHandler<>();
        TestServerCall<Object, Object> call = new TestServerCall<>("docs.DocsService/Ping");

        interceptor.interceptCall(call, headers, handler);

        assertThat(handler.proceeded).isTrue();
        assertThat(call.isClosed()).isFalse();
        verifyNoInteractions(jwtUtil);
    }

    private static final class TestServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {

        private final MethodDescriptor<ReqT, RespT> descriptor;
        private boolean closed;
        private Status status;

        private TestServerCall(String fullMethodName) {
            this.descriptor = MethodDescriptor.<ReqT, RespT>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(fullMethodName)
                    .setRequestMarshaller(TestMarshaller.create())
                    .setResponseMarshaller(TestMarshaller.create())
                    .build();
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(RespT message) {
        }

        @Override
        public void close(Status status, Metadata trailers) {
            this.closed = true;
            this.status = status;
        }

        @Override
        public boolean isCancelled() {
            return closed && status != null && !status.isOk();
        }

        @Override
        public boolean isReady() {
            return !closed;
        }

        @Override
        public Attributes getAttributes() {
            return Attributes.EMPTY;
        }

        @Override
        public String getAuthority() {
            return "test";
        }

        @Override
        public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
            return descriptor;
        }

        boolean isClosed() {
            return closed;
        }

        Status getStatus() {
            return status;
        }
    }

    private static final class TestServerCallHandler<ReqT, RespT> implements ServerCallHandler<ReqT, RespT> {

        private boolean proceeded;

        @Override
        public ServerCall.Listener<ReqT> startCall(ServerCall<ReqT, RespT> call, Metadata headers) {
            proceeded = true;
            return new ServerCall.Listener<>() {
            };
        }
    }

    private static final class TestMarshaller<T> implements MethodDescriptor.Marshaller<T> {

        private static final TestMarshaller<Object> INSTANCE = new TestMarshaller<>();

        @SuppressWarnings("unchecked")
        static <T> TestMarshaller<T> create() {
            return (TestMarshaller<T>) INSTANCE;
        }

        @Override
        public InputStream stream(T value) {
            return InputStream.nullInputStream();
        }

        @Override
        public T parse(InputStream stream) {
            return null;
        }
    }
}
