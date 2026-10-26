# Provider-token master-key rotation

`TokenCryptoService` encrypts provider tokens with AES-256-GCM under one of the keys configured in
`holonet.crypto.keys.<version>` (Base64-encoded 256-bit keys), using whichever version
`holonet.crypto.active-key-version` currently names for new encryptions. Every ciphertext stores the key
version it was encrypted under (`EncryptedSecret.keyVersion`), so `holonet.crypto.keys` is allowed to hold
more than one version at a time: old versions stay available for decrypting ciphertexts that still reference
them, independently of which version is active for new encryptions.

Set both through the deployment's secret mechanism (environment variables or a protected file translated
into environment variables), never through the database and never committed anywhere:

```
HOLONET_CRYPTO_ACTIVE_KEY_VERSION=v1
HOLONET_CRYPTO_KEYS_V1=<base64-encoded-256-bit-key>
```

## Rotation procedure

1. Generate a new random 256-bit key and assign it the next version identifier (for example `v2`).
2. Add it alongside the existing one(s), without removing the old entry:
   ```
   HOLONET_CRYPTO_ACTIVE_KEY_VERSION=v2
   HOLONET_CRYPTO_KEYS_V1=<the previous v1 key, unchanged>
   HOLONET_CRYPTO_KEYS_V2=<the new key>
   ```
3. Restart the application. New encryptions use `v2` immediately; existing `v1`-encrypted tokens keep
   decrypting exactly as before, since `TokenCryptoService.decrypt` looks the key up by the version recorded
   on that particular `EncryptedSecret`, not by whichever version is currently active.
4. At leisure (not required immediately), re-encrypt still-`v1` tokens under `v2`: decrypt with the stored
   `TokenEncryptionContext`, re-encrypt (which now uses the active `v2` key), and persist the resulting
   `EncryptedSecret`. There is no bulk/background job for this in this work package; it is a per-token
   operation the code that owns the token (the provider-connection entity introduced in a later work
   package) performs whenever it already needs to touch that row.
5. Once nothing references `v1` anymore, remove `HOLONET_CRYPTO_KEYS_V1`. Removing it earlier makes any
   still-`v1` ciphertext permanently undecryptable (`MasterKeyUnavailableException`), by design: there is no
   fallback key.

This procedure is exercised by
`TokenCryptoServiceTest#oldCiphertextRemainsDecryptableAfterKeyRotation`.
