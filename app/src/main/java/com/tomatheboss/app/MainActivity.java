package com.tomatheboss.app;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final long ROTATE_MS = 45_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<CameraItem> cameras = new ArrayList<>();

    private FrameLayout viewer;
    private TextView titleView;
    private TextView weatherView;
    private TextView forecastView;
    private TextView sourceView;
    private Button autoButton;
    private WebView webView;
    private int index = 0;
    private boolean autoRotate = true;
    private boolean tvMode;

    private final Runnable rotateRunnable = new Runnable() {
        @Override public void run() {
            if (autoRotate) nextCamera();
            scheduleRotation();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiModeManager ui = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        tvMode = ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        if (tvMode) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        buildUi();
        buildCameraList();
        showCamera(0);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(tvMode ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(tvMode ? 24 : 21);
        titleView.setTypeface(null, 1);
        header.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, tvMode ? 1f : 0f));
        if (!tvMode) titleView.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;

        LinearLayout weatherBox = new LinearLayout(this);
        weatherBox.setOrientation(LinearLayout.VERTICAL);
        weatherView = new TextView(this);
        weatherView.setTextColor(Color.WHITE);
        weatherView.setTextSize(tvMode ? 18 : 16);
        forecastView = new TextView(this);
        forecastView.setTextColor(0xFFCCCCCC);
        forecastView.setTextSize(tvMode ? 14 : 12);
        weatherBox.addView(weatherView);
        weatherBox.addView(forecastView);
        header.addView(weatherBox, new LinearLayout.LayoutParams(tvMode ? dp(620) : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(header);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        addQuickButton(quick, "Split");
        addQuickButton(quick, "Đakovo");
        addQuickButton(quick, "Vir");
        root.addView(quick, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        viewer = new FrameLayout(this);
        viewer.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        vp.topMargin = dp(5);
        root.addView(viewer, vp);

        sourceView = new TextView(this);
        sourceView.setText("Javna live kamera • LiveCamCroatia");
        sourceView.setTextColor(0xFFAAAAAA);
        sourceView.setTextSize(12);
        sourceView.setGravity(Gravity.CENTER);
        root.addView(sourceView);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        Button prev = makeButton("◀");
        autoButton = makeButton("PAUZA");
        Button next = makeButton("▶");
        prev.setOnClickListener(v -> previousCamera());
        next.setOnClickListener(v -> nextCamera());
        autoButton.setOnClickListener(v -> toggleAuto());
        controls.addView(prev);
        controls.addView(autoButton);
        controls.addView(next);
        root.addView(controls);

        setContentView(root);
        if (tvMode) next.requestFocus();
    }

    private void addQuickButton(LinearLayout row, String city) {
        Button b = makeButton(city);
        b.setTextSize(tvMode ? 17 : 13);
        b.setOnClickListener(v -> jumpTo(city));
        row.addView(b, new LinearLayout.LayoutParams(0, dp(tvMode ? 54 : 46), 1f));
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(tvMode ? 18 : 14);
        b.setAllCaps(false);
        b.setBackgroundColor(0xFF242424);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(tvMode ? 58 : 50));
        lp.setMargins(dp(4), dp(3), dp(4), dp(3));
        b.setLayoutParams(lp);
        return b;
    }

    private void buildCameraList() {
        cameras.clear();
        cameras.add(new CameraItem("Split", "Prokurative / Riva", "https://www.livecamcroatia.com/hr/kamera/split-prokurative-riva"));
        cameras.add(new CameraItem("Split", "Matejuška", "https://www.livecamcroatia.com/hr/kamera/split-matejuska"));
        cameras.add(new CameraItem("Split", "Riva Hrvatskog preporoda", "https://www.livecamcroatia.com/hr/kamera/split-riva-hrvatskog-preporoda"));
        cameras.add(new CameraItem("Đakovo", "Katedrala", "https://www.livecamcroatia.com/hr/kamera/dakovo-katedrala"));
        cameras.add(new CameraItem("Đakovo", "Korzo", "https://www.livecamcroatia.com/hr/kamera/dakovo-korzo-pjesacka-zona"));
        cameras.add(new CameraItem("Vir", "Glavni trg", "https://www.livecamcroatia.com/hr/kamera/vir-glavni-trg"));
        cameras.add(new CameraItem("Vir", "Plaža - okretna HD", "https://www.livecamcroatia.com/hr/kamera/vir-plaza-okretna-hd-kamera"));
    }

    private void jumpTo(String city) {
        for (int i = 0; i < cameras.size(); i++) {
            if (city.equals(cameras.get(i).city)) {
                showCamera(i);
                return;
            }
        }
    }

    private void showCamera(int newIndex) {
        if (cameras.isEmpty()) return;
        index = (newIndex + cameras.size()) % cameras.size();
        CameraItem cam = cameras.get(index);
        titleView.setText("Toma the Boss • " + cam.city + " — " + cam.name);
        stopWeb();
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.setBackgroundColor(Color.BLACK);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                String js = "javascript:(function(){" +
                        "document.body.style.background='#000';" +
                        "['header','footer','nav','.navbar','.cookie','.cookies','.cookie-banner','.advertisement','.adsbygoogle'].forEach(function(s){document.querySelectorAll(s).forEach(function(e){e.style.display='none';});});" +
                        "var v=document.querySelector('video')||document.querySelector('.camera iframe')||document.querySelector('iframe');" +
                        "if(v){v.scrollIntoView({block:'center'});v.style.maxWidth='100%';}" +
                        "})();";
                view.evaluateJavascript(js, null);
            }
        });
        viewer.removeAllViews();
        viewer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.loadUrl(cam.url);
        updateWeather(cam.city);
        handler.removeCallbacks(rotateRunnable);
        scheduleRotation();
    }

    private void stopWeb() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
    }

    private void nextCamera() { showCamera(index + 1); }
    private void previousCamera() { showCamera(index - 1); }

    private void toggleAuto() {
        autoRotate = !autoRotate;
        autoButton.setText(autoRotate ? "PAUZA" : "NASTAVI");
        handler.removeCallbacks(rotateRunnable);
        scheduleRotation();
    }

    private void scheduleRotation() {
        handler.removeCallbacks(rotateRunnable);
        if (autoRotate) handler.postDelayed(rotateRunnable, ROTATE_MS);
    }

    private void updateWeather(String city) {
        Location loc = locationFor(city);
        weatherView.setText(city + " • učitavanje vremena…");
        forecastView.setText("");
        io.execute(() -> {
            try {
                String u = "https://api.open-meteo.com/v1/forecast?latitude=" + loc.lat +
                        "&longitude=" + loc.lon +
                        "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                        "&forecast_days=5&timezone=auto";
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(7000);
                c.setReadTimeout(7000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONObject current = root.getJSONObject("current");
                double temp = current.getDouble("temperature_2m");
                double feel = current.getDouble("apparent_temperature");
                double wind = current.getDouble("wind_speed_10m");
                int code = current.getInt("weather_code");
                JSONObject daily = root.getJSONObject("daily");
                JSONArray days = daily.getJSONArray("time");
                JSONArray max = daily.getJSONArray("temperature_2m_max");
                JSONArray min = daily.getJSONArray("temperature_2m_min");
                JSONArray codes = daily.getJSONArray("weather_code");

                String now = String.format(Locale.getDefault(), "%s • %.0f°C • osjet %.0f°C • vjetar %.0f km/h", weatherText(code), temp, feel, wind);
                StringBuilder fc = new StringBuilder();
                SimpleDateFormat src = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat dst = new SimpleDateFormat("EEE", new Locale("hr", "HR"));
                for (int i = 0; i < Math.min(5, days.length()); i++) {
                    Date d = src.parse(days.getString(i));
                    if (i > 0) fc.append("  •  ");
                    fc.append(dst.format(d)).append(" ")
                            .append(Math.round(max.getDouble(i))).append("/")
                            .append(Math.round(min.getDouble(i))).append("° ")
                            .append(weatherIcon(codes.getInt(i)));
                }
                runOnUiThread(() -> {
                    if (!cameras.isEmpty() && city.equals(cameras.get(index).city)) {
                        weatherView.setText(city + " • " + now);
                        forecastView.setText(fc.toString());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!cameras.isEmpty() && city.equals(cameras.get(index).city)) {
                        weatherView.setText(city + " • prognoza trenutno nedostupna");
                        forecastView.setText("");
                    }
                });
            }
        });
    }

    private Location locationFor(String city) {
        if ("Split".equals(city)) return new Location(43.5081, 16.4402);
        if ("Đakovo".equals(city)) return new Location(45.3074, 18.4120);
        return new Location(44.2979, 15.0851);
    }

    private String weatherText(int code) {
        if (code == 0) return "Vedro";
        if (code <= 3) return "Djelomice oblačno";
        if (code == 45 || code == 48) return "Magla";
        if (code >= 51 && code <= 67) return "Kiša";
        if (code >= 71 && code <= 77) return "Snijeg";
        if (code >= 80 && code <= 82) return "Pljuskovi";
        if (code >= 95) return "Grmljavina";
        return "Promjenjivo";
    }

    private String weatherIcon(int code) {
        if (code == 0) return "☀";
        if (code <= 3) return "☁";
        if (code >= 71 && code <= 77) return "❄";
        if (code >= 95) return "⚡";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "☂";
        return "•";
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            nextCamera();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            previousCamera();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            toggleAuto();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopWeb();
        io.shutdownNow();
        super.onDestroy();
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class CameraItem {
        final String city;
        final String name;
        final String url;
        CameraItem(String city, String name, String url) {
            this.city = city;
            this.name = name;
            this.url = url;
        }
    }

    private static class Location {
        final double lat;
        final double lon;
        Location(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }
}
