package com.linagora.tmail.module;


import java.util.Optional;

import javax.inject.Singleton;

import org.apache.james.modules.TestJMAPServerModule;

import com.google.inject.Provides;
import com.linagora.tmail.james.jmap.jwt.JwtPrivateKeyConfiguration;
import com.linagora.tmail.james.jmap.jwt.JwtPrivateKeyProvider;

import scala.jdk.javaapi.OptionConverters;

public class LinagoraTestJMAPServerModule extends TestJMAPServerModule {
    public static final String JWT_PUBLIC_PEM_KEY =
        """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA6SkmZr94bvC8qN58Wy9/
            7vOjdFtAOWEfeWuHl+4Tz1Inpubh+9/ZSkgcMp36W7R1O1EjYIbctrkC8Et+HaGu
            CgvKyqsUZgNZDa2VPljaWzjzAjgBEWKM8p06dAfrombcl26tw3vnv1fRL7y1Ajip
            g41aVfrwmXTZOJeuFxDUhHs5eppQj5xEmkMiCyFettdvMJZUq3xJOfUaqu0cgKQU
            D355VO+tKmCnMjWC1W7YSUGoL/l6DtE2deG2WRWvN2bpjgXqwYoELGkqMw03Ww4D
            z6gjBXjeb9Ka/05hhh31vdTvS0rY+eUgQ+UOTT6siHrMwv4rFZq+lafJ7hqU1VQC
            CwIDAQAB
            -----END PUBLIC KEY-----
            """;

    public static final String JWT_PRIVATE_PEM_KEY =
        """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpQIBAAKCAQEA6SkmZr94bvC8qN58Wy9/7vOjdFtAOWEfeWuHl+4Tz1Inpubh
            +9/ZSkgcMp36W7R1O1EjYIbctrkC8Et+HaGuCgvKyqsUZgNZDa2VPljaWzjzAjgB
            EWKM8p06dAfrombcl26tw3vnv1fRL7y1Ajipg41aVfrwmXTZOJeuFxDUhHs5eppQ
            j5xEmkMiCyFettdvMJZUq3xJOfUaqu0cgKQUD355VO+tKmCnMjWC1W7YSUGoL/l6
            DtE2deG2WRWvN2bpjgXqwYoELGkqMw03Ww4Dz6gjBXjeb9Ka/05hhh31vdTvS0rY
            +eUgQ+UOTT6siHrMwv4rFZq+lafJ7hqU1VQCCwIDAQABAoIBAFMnbt+kF8KRLueW
            +YjXxuukjr33sU8FeWEnXWNs8Dm3VhbrLttSeT9Jumy+9MPx9wFhrZlGX772+rpS
            YjcVK2m/zOI843iCZyc+qgRjUfTIubon2RGnMRdxxaAOFxaDUtbbDTOzo/IU0rEQ
            vwl8xc/6AKa7aUWBa9sIFXl6ciCQ15fMs6x+gsFWM/mwpaS1Bmll1UX8GC3SjijI
            UHcK83UuJhO5FTlLjdumpApw1Bbj+Pdhnt3fo5JmySKsCU3fnjaIRjA0bDhYW5r5
            JVZrk6oEczWYcTzv1NXpAoFK/5mqZkMqTNthkcleJ15R9sw+UTcFsehWm4LiLD4I
            DPNIKNkCgYEA+2Nk9R/JdPHRfqEptlN4Vk2oLixxfns6UgBc6t0VmFaumLi6FFwx
            48ynfsMAHris0uc6uBl/f7kiY6EQ7beG8BzFy74BLNmJlsy4gxTqGAm3k9WijI24
            n4qgKOs/4RqQqajxOfpHs+JRUo7pRKXchMsOXzMBglmFJB6T1qs6t08CgYEA7XAn
            KDrzxsFJCzcCFMjD59L9wwDAku4w6Pv7MzvtcEv01E/EmOv3PebQeFAyZBeVxATG
            y1bkaS4VRl637BL2BX0jz6qaG+mqUBSEIF878If6eBkMV0de0rmhUzpYiMCN88yv
            /qRUXcVTMPz1SFxbhMvzgpxJb01u6/NATjZPWoUCgYEAi0Jvff8i6b7AEAcVhWRO
            CHkyjomeQbPgBecfkhfxS5fRVtcWdgrwtEH+E5HQsjQZwSfI9o1hfQ7BBzIFn7qI
            bOFzjT9vhTnpJ3m3SR4/5BsV8DZrurMTsIXp3WEc3QWLWAE3yKdmKzdXV4XFoXrE
            Y2fdSU2HK3+N6wlpWoU2nK8CgYEAojfYmNTGDkmpxN69LkQIDE+Ljfnql7fidJsL
            gXJ1Ax7x00f4Ul4Mmh8i2MA53UZ7zONSikQAY5fXcy9tSv2dVhysJcox5dYbxQBv
            UMqf4fKU/g5m7w5Uy3WFsZ4QNMYRdbqnlzrgZPxWnQaF8f0fjbfl05tBVKi7mrqj
            eYwDZR0CgYEA26t/ukwT2nwoy1uOQDA2H+Ntr3+p+/v7F/9okdudzMZTWG1Xt0CB
            htsUgGktQ0L9EqD+oJYCT5A86Xpzs8crXdnGWb3Nsd5pNvRTMbgjdFxEhya1lzDD
            MY+QgMz8y6XlPlFM9z+ULNifAe3iM5JPBZQ+xx/MSi/0G94NNeeAFQs=
            -----END RSA PRIVATE KEY-----""";

    @Provides
    @Singleton
    JwtPrivateKeyProvider jwtPrivateKeyProvider() {
        return new JwtPrivateKeyProvider(new JwtPrivateKeyConfiguration(OptionConverters.toScala(Optional.of(JWT_PRIVATE_PEM_KEY))));
    }
}