package com.whitestein.securestorage;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.security.KeyChain;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;


public class PasswordStorageHelper {

    private static final String LOG_TAG = PasswordStorageHelper.class.getSimpleName();
    private static final String PREFERENCES_FILE = "cap_sec";

    private final PasswordStorage passwordStorage = new PasswordStorageHelper_SDK18();

    private final Context context;

    public static final ReentrantLock GLOBAL_BIOMETRICS_LOCK = new ReentrantLock();

    public PasswordStorageHelper(Context context) {
        this.context = context;
        try {
            passwordStorage.init(context);
        } catch (Exception ex) {
            Log.e(LOG_TAG, "PasswordStorage initialisation error:" + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }

    }

    public void setData(String key, byte[] data) {
        passwordStorage.setData(key, data);
    }

    public byte[] getData(String key, FragmentActivity activity) {
        return passwordStorage.getData(key, activity);
    }

    public String[] keys() {
        return passwordStorage.keys();
    }

    public void remove(String key) {
        passwordStorage.remove(key);
    }

    public void clear() {
        passwordStorage.clear();
    }

    private interface PasswordStorage {
        boolean init(Context context);

        void setData(String key, byte[] data);

        byte[] getData(String key, FragmentActivity activity);

        String[] keys();

        void remove(String key);

        void clear();
    }

    private static class PasswordStorageHelper_SDK28 implements PasswordStorage {

        private static final String KEY_ALGORITHM_RSA = "RSA";

        private static final String KEYSTORE_PROVIDER_ANDROID_KEYSTORE = "AndroidKeyStore";
        private static final String RSA_ECB_PKCS1_PADDING = "RSA/ECB/PKCS1Padding";

        private SharedPreferences preferences;
        private String alias = null;

        @SuppressWarnings("deprecation")
        @SuppressLint({"NewApi", "TrulyRandom"})
        @Override
        public boolean init(Context context) {
            preferences = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
            alias = context.getPackageName() + "_cap_sec";

            KeyStore ks;

            try {
                ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID_KEYSTORE);

                ks.load(null);

                // Check if Private and Public already keys exists. If so we don't need to generate them again
                PrivateKey privateKey = (PrivateKey) ks.getKey(alias, null);
                if (privateKey != null && ks.getCertificate(alias) != null) {
                    PublicKey publicKey = ks.getCertificate(alias).getPublicKey();
                    if (publicKey != null) {
                        Log.i("SecureStorage", "init: KeyAlreadyPresent");
                        return true;
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            AlgorithmParameterSpec spec;
            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                spec = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                        .setUnlockedDeviceRequired(true)
                        .setInvalidatedByBiometricEnrollment(false)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationValidityDurationSeconds(300)
                        .setUserConfirmationRequired(false)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                        .build();
            } else {
                spec = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                        .setUnlockedDeviceRequired(true)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationParameters(300, KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL)
                        .setInvalidatedByBiometricEnrollment(false)
                        .setUserConfirmationRequired(false)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                        .build();
            }

            KeyPairGenerator kpGenerator;
            try {
                kpGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER_ANDROID_KEYSTORE);
                kpGenerator.initialize(spec);
                kpGenerator.generateKeyPair();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // Check if device support Hardware-backed keystore
            try {
                PrivateKey privateKey = (PrivateKey) ks.getKey(alias, null);
                KeyChain.isBoundKeyAlgorithm(KeyProperties.KEY_ALGORITHM_RSA);
                KeyFactory keyFactory = KeyFactory.getInstance(privateKey.getAlgorithm(), KEYSTORE_PROVIDER_ANDROID_KEYSTORE);
                KeyInfo keyInfo = keyFactory.getKeySpec(privateKey, KeyInfo.class);
                boolean isHardwareBackedKeystoreSupported = keyInfo.isInsideSecureHardware();
                Log.i(LOG_TAG, "Hardware-Backed Keystore Supported: " + isHardwareBackedKeystoreSupported);
            } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | InvalidKeySpecException | NoSuchProviderException e) {
                return false;
            }

            return true;
        }

        @Override
        public void setData(String key, byte[] data) {
            try {
                KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID_KEYSTORE);

                ks.load(null);
                if (ks.getCertificate(alias) == null) return;

                PublicKey publicKey = ks.getCertificate(alias).getPublicKey();

                if (publicKey == null) {
                    Log.i(LOG_TAG, "Error: Public key was not found in Keystore");
                    throw new RuntimeException("Error: Public key was not found in Keystore");
                }

                String value = encrypt(publicKey, data);

                Editor editor = preferences.edit();
                editor.putString(key, value);
                editor.apply();
            } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException
                    | IllegalBlockSizeException | BadPaddingException | NoSuchProviderException
                    | InvalidKeySpecException | KeyStoreException | CertificateException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        public byte[] getData(String key, FragmentActivity activity) {
            KeyStore ks = null;
            try {
                ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID_KEYSTORE);
                ks.load(null);
                PrivateKey privateKey = (PrivateKey) ks.getKey(alias, null);
                return decrypt(privateKey, preferences.getString(key, null), activity);
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException
                    | UnrecoverableEntryException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String[] keys() {
            Set<String> keySet = preferences.getAll().keySet();
            return keySet.toArray(new String[keySet.size()]);
        }

        @Override
        public void remove(String key) {
            Editor editor = preferences.edit();
            editor.remove(key);
            editor.commit();
        }

        @Override
        public void clear() {
            Editor editor = preferences.edit();
            editor.clear();
            editor.commit();
        }

        private static final int KEY_LENGTH = 2048;

        @SuppressLint("TrulyRandom")
        private static String encrypt(PublicKey encryptionKey, byte[] data) throws NoSuchAlgorithmException,
                NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
                NoSuchProviderException, InvalidKeySpecException {

            if (data.length <= KEY_LENGTH / 8 - 11) {
                Cipher cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING);
                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
                byte[] encrypted = cipher.doFinal(data);
                return Base64.encodeToString(encrypted, Base64.DEFAULT);
            } else {
                Cipher cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING);
                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
                int limit = KEY_LENGTH / 8 - 11;
                int position = 0;
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                while (position < data.length) {
                    if (data.length - position < limit)
                        limit = data.length - position;
                    byte[] tmpData = cipher.doFinal(data, position, limit);
                    try {
                        byteArrayOutputStream.write(tmpData);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    position += limit;
                }

                return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);
            }
        }


        @RequiresApi(api = Build.VERSION_CODES.M)
        private static boolean bioMetricsRequired(PrivateKey decryptionKey) {
            try {
                GLOBAL_BIOMETRICS_LOCK.lock();
                Cipher cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING);
                cipher.init(Cipher.DECRYPT_MODE, decryptionKey);
            } catch (UserNotAuthenticatedException e) {
                return true;
            } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
                throw new RuntimeException(e);
            }finally {
                GLOBAL_BIOMETRICS_LOCK.unlock();
            }
            return false;
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        private static CompletableFuture<Boolean> displayBioMetrics(FragmentActivity activity) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();


            BiometricPrompt.PromptInfo promptInfo;
            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Biometric Auth")
                        .setSubtitle("Log in using your biometric credential")
                        .setDeviceCredentialAllowed(true)
                        .build();
            } else {
                promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Biometric Auth")
                        .setSubtitle("Log in using your biometric or device credential")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build();
            }

            BiometricPrompt prompt = new BiometricPrompt(activity, Executors.newSingleThreadExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    GLOBAL_BIOMETRICS_LOCK.unlock();
                    future.completeExceptionally(new RuntimeException(errString.toString()));
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    GLOBAL_BIOMETRICS_LOCK.unlock();
                    try {
                        future.complete(true);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    GLOBAL_BIOMETRICS_LOCK.unlock();
                    future.completeExceptionally(new RuntimeException("failed to authenticate using biometric auth"));
                }
            });
            activity.runOnUiThread(() -> {
                GLOBAL_BIOMETRICS_LOCK.lock();
                prompt.authenticate(promptInfo);
            });
            return future;
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        private static byte[] decrypt(PrivateKey decryptionKey, String encryptedData, FragmentActivity activity) throws IllegalBlockSizeException, BadPaddingException, InvalidKeyException {

            if (bioMetricsRequired(decryptionKey)) {
                return displayBioMetrics(activity).thenApply(state -> {
                    try {
                        return _decrypt(decryptionKey, encryptedData, activity);
                    } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
                        throw new RuntimeException(e);
                    }
                }).join();
            } else {
                return _decrypt(decryptionKey, encryptedData, activity);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        private static byte[] _decrypt(PrivateKey decryptionKey, String encryptedData, FragmentActivity activity) throws
                InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

            try {
                if (encryptedData == null)
                    return null;
                byte[] encryptedBuffer = Base64.decode(encryptedData, Base64.DEFAULT);
                Cipher cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING);
                cipher.init(Cipher.DECRYPT_MODE, decryptionKey);
                if (encryptedBuffer.length <= KEY_LENGTH / 8) {
                    return (cipher.doFinal(encryptedBuffer));
                } else {
                    int limit = KEY_LENGTH / 8;
                    int position = 0;
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    while (position < encryptedBuffer.length) {
                        if (encryptedBuffer.length - position < limit)
                            limit = encryptedBuffer.length - position;
                        byte[] tmpData = cipher.doFinal(encryptedBuffer, position, limit);
                        try {
                            byteArrayOutputStream.write(tmpData);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        position += limit;
                    }
                    return (byteArrayOutputStream.toByteArray());
                }

            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                throw new RuntimeException(e);
            }


        }
    }
}
