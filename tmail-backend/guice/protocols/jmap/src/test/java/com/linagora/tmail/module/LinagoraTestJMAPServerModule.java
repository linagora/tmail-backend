package com.linagora.tmail.module;


import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

import javax.inject.Singleton;

import org.apache.james.modules.TestJMAPServerModule;

import com.google.inject.Provides;
import com.linagora.tmail.james.jmap.jwt.JwtPrivateKeyConfiguration;
import com.linagora.tmail.james.jmap.jwt.JwtPrivateKeyProvider;
import com.linagora.tmail.james.jmap.oidc.WebFingerConfiguration;

import scala.Some;
import scala.jdk.javaapi.OptionConverters;

public class LinagoraTestJMAPServerModule extends TestJMAPServerModule {
    public static final String JWT_PUBLIC_PEM_KEY =
        "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA6SkmZr94bvC8qN58Wy9/\n" +
            "7vOjdFtAOWEfeWuHl+4Tz1Inpubh+9/ZSkgcMp36W7R1O1EjYIbctrkC8Et+HaGu\n" +
            "CgvKyqsUZgNZDa2VPljaWzjzAjgBEWKM8p06dAfrombcl26tw3vnv1fRL7y1Ajip\n" +
            "g41aVfrwmXTZOJeuFxDUhHs5eppQj5xEmkMiCyFettdvMJZUq3xJOfUaqu0cgKQU\n" +
            "D355VO+tKmCnMjWC1W7YSUGoL/l6DtE2deG2WRWvN2bpjgXqwYoELGkqMw03Ww4D\n" +
            "z6gjBXjeb9Ka/05hhh31vdTvS0rY+eUgQ+UOTT6siHrMwv4rFZq+lafJ7hqU1VQC\n" +
            "CwIDAQAB\n" +
            "-----END PUBLIC KEY-----\n";

    public static final String JWT_PRIVATE_PEM_KEY =
        "-----BEGIN RSA PRIVATE KEY-----\n" +
            "MIIEpQIBAAKCAQEA6SkmZr94bvC8qN58Wy9/7vOjdFtAOWEfeWuHl+4Tz1Inpubh\n" +
            "+9/ZSkgcMp36W7R1O1EjYIbctrkC8Et+HaGuCgvKyqsUZgNZDa2VPljaWzjzAjgB\n" +
            "EWKM8p06dAfrombcl26tw3vnv1fRL7y1Ajipg41aVfrwmXTZOJeuFxDUhHs5eppQ\n" +
            "j5xEmkMiCyFettdvMJZUq3xJOfUaqu0cgKQUD355VO+tKmCnMjWC1W7YSUGoL/l6\n" +
            "DtE2deG2WRWvN2bpjgXqwYoELGkqMw03Ww4Dz6gjBXjeb9Ka/05hhh31vdTvS0rY\n" +
            "+eUgQ+UOTT6siHrMwv4rFZq+lafJ7hqU1VQCCwIDAQABAoIBAFMnbt+kF8KRLueW\n" +
            "+YjXxuukjr33sU8FeWEnXWNs8Dm3VhbrLttSeT9Jumy+9MPx9wFhrZlGX772+rpS\n" +
            "YjcVK2m/zOI843iCZyc+qgRjUfTIubon2RGnMRdxxaAOFxaDUtbbDTOzo/IU0rEQ\n" +
            "vwl8xc/6AKa7aUWBa9sIFXl6ciCQ15fMs6x+gsFWM/mwpaS1Bmll1UX8GC3SjijI\n" +
            "UHcK83UuJhO5FTlLjdumpApw1Bbj+Pdhnt3fo5JmySKsCU3fnjaIRjA0bDhYW5r5\n" +
            "JVZrk6oEczWYcTzv1NXpAoFK/5mqZkMqTNthkcleJ15R9sw+UTcFsehWm4LiLD4I\n" +
            "DPNIKNkCgYEA+2Nk9R/JdPHRfqEptlN4Vk2oLixxfns6UgBc6t0VmFaumLi6FFwx\n" +
            "48ynfsMAHris0uc6uBl/f7kiY6EQ7beG8BzFy74BLNmJlsy4gxTqGAm3k9WijI24\n" +
            "n4qgKOs/4RqQqajxOfpHs+JRUo7pRKXchMsOXzMBglmFJB6T1qs6t08CgYEA7XAn\n" +
            "KDrzxsFJCzcCFMjD59L9wwDAku4w6Pv7MzvtcEv01E/EmOv3PebQeFAyZBeVxATG\n" +
            "y1bkaS4VRl637BL2BX0jz6qaG+mqUBSEIF878If6eBkMV0de0rmhUzpYiMCN88yv\n" +
            "/qRUXcVTMPz1SFxbhMvzgpxJb01u6/NATjZPWoUCgYEAi0Jvff8i6b7AEAcVhWRO\n" +
            "CHkyjomeQbPgBecfkhfxS5fRVtcWdgrwtEH+E5HQsjQZwSfI9o1hfQ7BBzIFn7qI\n" +
            "bOFzjT9vhTnpJ3m3SR4/5BsV8DZrurMTsIXp3WEc3QWLWAE3yKdmKzdXV4XFoXrE\n" +
            "Y2fdSU2HK3+N6wlpWoU2nK8CgYEAojfYmNTGDkmpxN69LkQIDE+Ljfnql7fidJsL\n" +
            "gXJ1Ax7x00f4Ul4Mmh8i2MA53UZ7zONSikQAY5fXcy9tSv2dVhysJcox5dYbxQBv\n" +
            "UMqf4fKU/g5m7w5Uy3WFsZ4QNMYRdbqnlzrgZPxWnQaF8f0fjbfl05tBVKi7mrqj\n" +
            "eYwDZR0CgYEA26t/ukwT2nwoy1uOQDA2H+Ntr3+p+/v7F/9okdudzMZTWG1Xt0CB\n" +
            "htsUgGktQ0L9EqD+oJYCT5A86Xpzs8crXdnGWb3Nsd5pNvRTMbgjdFxEhya1lzDD\n" +
            "MY+QgMz8y6XlPlFM9z+ULNifAe3iM5JPBZQ+xx/MSi/0G94NNeeAFQs=\n" +
            "-----END RSA PRIVATE KEY-----";

    @Provides
    @Singleton
    JwtPrivateKeyProvider jwtPrivateKeyProvider() {
        return new JwtPrivateKeyProvider(new JwtPrivateKeyConfiguration(OptionConverters.toScala(Optional.of(JWT_PRIVATE_PEM_KEY))));
    }
}