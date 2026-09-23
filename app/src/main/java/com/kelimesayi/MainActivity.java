package com.kelimesayi;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    // !!! KENDI GITHUB RAW LINKINIZ !!!
    private final String GITHUB_RAW_URL = "https://raw.githubusercontent.com/mevafeyzasavas-byte/countdown/main/app/src/main/assets/index.html";
    private final String LOCAL_FILE_NAME = "index.html";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        // Hem "files" (guncellenen dosya) hem "assets" (yerel yedek) icin path handler
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/files/", new WebViewAssetLoader.InternalStoragePathHandler(this, getFilesDir()))
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        setContentView(webView);

        // ÖNCE yerel dosyayı yükle (kullanıcı hemen oyunu görsün)
        loadLocalFile();

        // SONRA arka planda güncellemeyi kontrol et
        checkForUpdates();
    }

    private void loadLocalFile() {
        File file = new File(getFilesDir(), LOCAL_FILE_NAME);
        if (file.exists()) {
            webView.loadUrl("https://appassets.androidplatform.net/files/" + LOCAL_FILE_NAME);
        } else {
            webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        }
    }

    private void checkForUpdates() {
        new Thread(() -> {
            try {
                URL url = new URL(GITHUB_RAW_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.connect();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = conn.getInputStream();
                    File localFile = new File(getFilesDir(), LOCAL_FILE_NAME);

                    String remoteHash = getHash(inputStream);

                    String localHash = "";
                    if (localFile.exists()) {
                        localHash = getHash(new java.io.FileInputStream(localFile));
                    }

                    // Hash farklıysa yeni sürümü indir
                    if (!remoteHash.equals(localHash)) {
                        conn = (HttpURLConnection) url.openConnection();
                        conn.connect();
                        InputStream newInputStream = conn.getInputStream();
                        FileOutputStream fos = new FileOutputStream(localFile);
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = newInputStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                        newInputStream.close();

                        // Güncelleme indirildi → UI thread'inde WebView'i yeniden yükle
                        new Handler(Looper.getMainLooper()).post(() -> {
                            webView.loadUrl("https://appassets.androidplatform.net/files/" + LOCAL_FILE_NAME);
                        });
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String getHash(InputStream inputStream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] byteArray = new byte[1024];
        int bytesCount;
        while ((bytesCount = inputStream.read(byteArray)) != -1) {
            digest.update(byteArray, 0, bytesCount);
        }
        inputStream.close();
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
