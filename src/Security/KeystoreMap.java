package Security;

import java.io.*;
import java.security.KeyStore;
import java.util.HashMap;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class KeystoreMap extends HashMap<String, String> {
    private String keystoreFile;
    private String password;
    private KeyStore ks;

    public KeystoreMap(String keystoreFile, String password) throws Exception {
        this.keystoreFile = keystoreFile;
        this.password = password;
        ks = KeyStore.getInstance("PKCS12");
        File file = new File(keystoreFile);
        if (file.exists()) {
            ks.load(new FileInputStream(file), password.toCharArray());
            loadAll();
        } else {
            ks.load(null, password.toCharArray());
        }
    }

    private void loadAll() throws Exception {
        for (String alias : java.util.Collections.list(ks.aliases())) {
            if (ks.entryInstanceOf(alias, KeyStore.SecretKeyEntry.class)) {
                SecretKey key = ((KeyStore.SecretKeyEntry) ks.getEntry(alias,
                        new KeyStore.PasswordProtection(password.toCharArray()))).getSecretKey();
                put(alias, new String(key.getEncoded()));
            }
        }
    }

    @Override
    public String put(String key, String value) {
        try {
            SecretKey secretKey = new SecretKeySpec(value.getBytes(), "AES");
            KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(secretKey);
            KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(password.toCharArray());
            ks.setEntry(key, entry, protParam);
            ks.store(new FileOutputStream(keystoreFile), password.toCharArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return super.put(key, value);
    }

    @Override
    public String get(Object key) {
        return super.get(key);
    }
}
