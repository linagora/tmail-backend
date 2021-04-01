# GPG key pairs for testing purposes

Keys information:

```
pub   rsa3072 2021-04-01 [SC]
      3BA423385C8C80D453D7E6F95BF4E866E9CC6FA2
uid           [ultimate] TMail test key 1 <key1@linagora.com>
sub   rsa3072 2021-04-01 [E]

pub   rsa3072 2021-04-01 [SC]
      12522CF961A95474431BADD676E1BC47187D6CEF
uid           [ultimate] TMail test key 2 <key2@linagora.com>
sub   rsa3072 2021-04-01 [E]
```

Associated password: `123456`

## GPG memo

Generating the keys:

```
gpg --full-gen-key
```

Exporting the keys:

```
gpg --armor --export key1@linagora.com > openpaas-james/mailbox/encrypted/src/test/resources/gpg1.pub 
gpg --armor --export-secret-keys key1@linagora.com >  openpaas-james/mailbox/encrypted/src/test/resources/gpg.private
```

Decrypting stuff:

```
gpg --decrypt {file}
```