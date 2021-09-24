package com.linagora.tmail.module;


import java.util.Optional;

import javax.inject.Singleton;

import org.apache.james.modules.TestJMAPServerModule;

import com.google.inject.Provides;
import com.linagora.tmail.james.jmap.jwt.JwtPrivateKeyConfiguration;
import com.linagora.tmail.james.jmap.jwt.JwtPrivateKeyProvider;

import scala.jdk.javaapi.OptionConverters;

public class LinagoraTestJMAPServerModule extends TestJMAPServerModule {
    private static final String JWT_PUBLIC_PEM_KEY =
        "-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCVnxAOpup/rtGzn+xUaBRFSe34\n" +
            "H7YyiM6YBD1bh5rkoi9pB6fvs1vDlXzBmR0Zl6kn3g+2ChW0lqMkmv73Y2Lv3WZK\n" +
            "NZ3DUR3lfBFbvYGQyFyib+e4MY1yWkj3sumMl1wdUB4lKLHLIRv9X1xCqvbSHEtq\n" +
            "zoZF4vgBYx0VmuJslwIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    private static final String JWT_PRIVATE_PEM_KEY =
        "-----BEGIN PRIVATE KEY-----\n" +
            "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAJWfEA6m6n+u0bOf\n" +
            "7FRoFEVJ7fgftjKIzpgEPVuHmuSiL2kHp++zW8OVfMGZHRmXqSfeD7YKFbSWoySa\n" +
            "/vdjYu/dZko1ncNRHeV8EVu9gZDIXKJv57gxjXJaSPey6YyXXB1QHiUoscshG/1f\n" +
            "XEKq9tIcS2rOhkXi+AFjHRWa4myXAgMBAAECgYA0FsxgTXwWN6aKAoMkX5evB63x\n" +
            "VBj6PuatxcwWsX8bWdtnlWLB8I9h6Akm3UdkQYiCeKy/k4M6+7aQZ+Wb+t3WW9H9\n" +
            "pNE/WlrkvQ9bKGluy26ZMDFRYbofEM3u7AoFkZzZrojaW0T0kH41fE3i+0sKfI3Z\n" +
            "863jtl6qLDb+Dx/sAQJBAMd15r/mPisRaGWMms+Mnwd27gA7mfjXHSLhmE8D7HdX\n" +
            "hjsItIdS2Vk+s9clngz8R3ogzXA8GH5HCTKQaMTIRxECQQDACISYYJWI2d+OktgA\n" +

            "N3dxk8paAv1Vqnm3b/4oA/JlSWZ7vheKZk6IdiyCyjgVBlNBT1gOImt15hotThX3\n" +
            "4AknAkEAxKkZ13GDMGGchiuI5ESo8+ouJaqeWHx4bNDzpEyhFNYGMiSWIqrsRBMP\n" +
            "rHyZhgIj02WOSS/nknIlvmYl9oflkQJAc0rp6N5cCPzd9qh9HKwwfzU/EQmoda1T\n" +
            "RGntyrKL7nnCGNsJISPJVK62jJPCVgUlKRntARdzMybCYp72G4sbkwJAKk7ggvsc\n" +
            "RBTITDA7nbywahtKAnPgjbK/qHeJMVH2ePL9HveFAf3YRpa07zVlienbv/kjULKP\n" +
            "HJGoXJNY/TNbMQ==\n" +
            "-----END PRIVATE KEY-----";

    @Provides
    @Singleton
    JwtPrivateKeyProvider jwtPrivateKeyProvider() {
        return new JwtPrivateKeyProvider(new JwtPrivateKeyConfiguration(OptionConverters.toScala(Optional.of(JWT_PRIVATE_PEM_KEY))));
    }
}