package com.migym.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * La app es la misma página web que se prueba en Claude, guardada dentro del APK (assets/web).
 * Se sirve desde https://appassets.androidplatform.net para que el guardado del navegador
 * (localStorage) quede en el celular y no se pierda al actualizar la app.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final int ELEGIR_FOTO = 1;
    private static final int IMPORTAR_FITNOTES = 2;

    private WebView web;
    private ValueCallback<Uri[]> fotoCallback;
    private volatile String importacion;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        configurarBarras();

        web = new WebView(this);
        web.setBackgroundColor(0xFFF3F5F7);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(true);

        web.addJavascriptInterface(new Puente(), "AppNativo");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String ruta = u.getPath();
                if (ruta == null || ruta.equals("/") || ruta.isEmpty()) ruta = "/index.html";
                try {
                    InputStream in = getAssets().open("web" + ruta);
                    return new WebResourceResponse(tipo(ruta), "utf-8", in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "No encontrado", null, null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                // Enlaces de afuera (por ejemplo la lista de pruebas) se abren en el navegador
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) {
                    // Sin navegador instalado: no hacer nada
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fotoCallback != null) fotoCallback.onReceiveValue(null);
                fotoCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Elegir foto"), ELEGIR_FOTO);
                } catch (Exception e) {
                    fotoCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.loadUrl("https://" + HOST + "/index.html");
    }

    private void configurarBarras() {
        Window w = getWindow();
        w.setStatusBarColor(0xFFF3F5F7);
        w.setNavigationBarColor(0xFFFFFFFF);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(flags);
    }

    private static String tipo(String ruta) {
        if (ruta.endsWith(".html")) return "text/html";
        if (ruta.endsWith(".css")) return "text/css";
        if (ruta.endsWith(".js")) return "application/javascript";
        if (ruta.endsWith(".woff2")) return "font/woff2";
        if (ruta.endsWith(".png")) return "image/png";
        if (ruta.endsWith(".svg")) return "image/svg+xml";
        if (ruta.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent datos) {
        if (pedido == ELEGIR_FOTO && fotoCallback != null) {
            Uri[] elegida = null;
            if (resultado == RESULT_OK && datos != null && datos.getData() != null) {
                elegida = new Uri[] { datos.getData() };
            }
            fotoCallback.onReceiveValue(elegida);
            fotoCallback = null;
            return;
        }
        if (pedido == IMPORTAR_FITNOTES) {
            if (resultado == RESULT_OK && datos != null && datos.getData() != null) leerRespaldo(datos.getData());
            else avisarImportacion("{\"cancelado\":true}");
            return;
        }
        super.onActivityResult(pedido, resultado, datos);
    }

    /** Copia el respaldo elegido a la caché y lo lee fuera del hilo de la pantalla. */
    private void leerRespaldo(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File copia = new File(getCacheDir(), "fitnotes-respaldo.db");
                String resultado;
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new IOException("sin datos");
                    OutputStream out = new FileOutputStream(copia);
                    try {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    } finally {
                        in.close();
                        out.close();
                    }
                    resultado = LectorFitNotes.leer(copia);
                } catch (Exception e) {
                    resultado = LectorFitNotes.error("No pude leer ese archivo. Elige el respaldo que guardaste desde FitNotes (termina en .fitnotes).");
                } finally {
                    copia.delete();
                    new File(copia.getPath() + "-journal").delete();
                }
                avisarImportacion(resultado);
            }
        }).start();
    }

    /** Deja el resultado listo y avisa a la página, que lo pide con resultadoImportacion(). */
    private void avisarImportacion(String json) {
        importacion = json;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript("window.onFitNotesListo && window.onFitNotesListo()", null);
            }
        });
    }

    /** Botón atrás: primero navega dentro de la app; si ya está en el inicio, sale. */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.appBack ? String(window.appBack()) : 'false'", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                if (!"\"true\"".equals(v)) finish();
            }
        });
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    /** Lo que la página puede pedirle al celular. */
    private class Puente {
        @JavascriptInterface
        public void pantallaEncendida(final boolean encendida) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (encendida) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface
        public void importarFitNotes() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    try {
                        startActivityForResult(Intent.createChooser(i, "Elegir respaldo de FitNotes"), IMPORTAR_FITNOTES);
                    } catch (Exception e) {
                        avisarImportacion(LectorFitNotes.error("No encontré una app para elegir archivos en el celular."));
                    }
                }
            });
        }

        @JavascriptInterface
        public String resultadoImportacion() {
            String r = importacion;
            importacion = null;
            return r;
        }

        @JavascriptInterface
        public void vibrar(long ms) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        }
    }
}
