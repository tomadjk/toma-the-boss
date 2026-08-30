package com.tomatheboss.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

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
    private PlayerView playerView;
    private ExoPlayer player;
    private SharedPreferences prefs;
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
        prefs = getSharedPreferences("toma_the_boss", MODE_PRIVATE);
        UiModeManager ui = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        tvMode = ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        if (tvMode) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        buildUi();
        rebuildCameraList();
        if (!cameras.isEmpty()) showCamera(0);
        scheduleRotation();
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
        titleView.setText("Toma the Boss");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(tvMode ? 24 : 21);
        titleView.setTypeface(null, 1);
        header.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, tvMode ? 1f : 0f));
        if (!tvMode) titleView.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;

        LinearLayout weatherBox = new LinearLayout(this);
        weatherBox.setOrientation(LinearLayout.VERTICAL);
        weatherBox.setGravity(tvMode ? Gravity.END : Gravity.START);
        weatherView = new TextView(this);
        weatherView.setTextColor(Color.WHITE);
        weatherView.setTextSize(tvMode ? 20 : 17);
        forecastView = new TextView(this);
        forecastView.setTextColor(0xFFCCCCCC);
        forecastView.setTextSize(tvMode ? 15 : 13);
        weatherBox.addView(weatherView);
        weatherBox.addView(forecastView);
        header.addView(weatherBox, new LinearLayout.LayoutParams(tvMode ? dp(620) : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(header);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER);
        addQuickButton(quick, "Split", "Split");
        addQuickButton(quick, "Đakovo", "Đakovo");
        addQuickButton(quick, "Vir", "Vir");
        addQuickButton(quick, "Imou", "Imou");
        root.addView(quick, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        viewer = new FrameLayout(this);
        viewer.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        vp.topMargin = dp(5);
        root.addView(viewer, vp);

        sourceView = new TextView(this);
        sourceView.setTextColor(0xFFAAAAAA);
        sourceView.setTextSize(12);
        sourceView.setGravity(Gravity.CENTER);
        sourceView.setPadding(4, 4, 4, 4);
        root.addView(sourceView);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = makeButton("◀");
        autoButton = makeButton("PAUZA");
        Button next = makeButton("▶");
        Button settings = makeButton("IMOU ⚙");
        prev.setOnClickListener(v -> previousCamera());
        next.setOnClickListener(v -> nextCamera());
        autoButton.setOnClickListener(v -> toggleAuto());
        settings.setOnClickListener(v -> showImouSettings());
        controls.addView(prev);
        controls.addView(autoButton);
        controls.addView(next);
        controls.addView(settings);
        root.addView(controls);

        setContentView(root);
        if (tvMode) next.requestFocus();
    }

    private void addQuickButton(LinearLayout row, String label, String city) {
        Button b = makeButton(label);
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

    private void rebuildCameraList() {
        cameras.clear();
        cameras.add(new CameraItem("Split", "Prokurative / Riva", "https://www.livecamcroatia.com/hr/kamera/split-prokurative-riva", CameraType.WEB));
        cameras.add(new CameraItem("Split", "Matejuška", "https://www.livecamcroatia.com/hr/kamera/split-matejuska", CameraType.WEB));
        cameras.add(new CameraItem("Split", "Riva Hrvatskog preporoda", "https://www.livecamcroatia.com/hr/kamera/split-riva-hrvatskog-preporoda", CameraType.WEB));
        cameras.add(new CameraItem("Đakovo", "Katedrala", "https://www.livecamcroatia.com/hr/kamera/dakovo-katedrala", CameraType.WEB));
        cameras.add(new CameraItem("Đakovo", "Korzo", "https://www.livecamcroatia.com/hr/kamera/dakovo-korzo-pjesacka-zona", CameraType.WEB));
        cameras.add(new CameraItem("Vir", "Glavni trg", "https://www.livecamcroatia.com/hr/kamera/vir-glavni-trg", CameraType.WEB));
        cameras.add(new CameraItem("Vir", "Plaža - okretna HD", "https://www.livecamcroatia.com/hr/kamera/vir-plaza-okretna-hd-kamera", CameraType.WEB));

        addPrivateCamera(1);
        addPrivateCamera(2);
    }

    private void addPrivateCamera(int slot) {
        String url = prefs.getString("imou_url_" + slot, "").trim();
        if (!url.isEmpty()) {
            String name = prefs.getString("imou_name_" + slot, "Imou " + slot).trim();
            if (name.isEmpty()) name = "Imou " + slot;
            cameras.add(new CameraItem("Imou", name, url, CameraType.RTSP));
        }
    }

    private void jumpTo(String city) {
        if ("Imou".equals(city)) {
            for (int i = 0; i < cameras.size(); i++) {
                if (cameras.get(i).type == CameraType.RTSP) { showCamera(i); return; }
            }
            showImouSettings();
            return;
        }
        for (int i = 0; i < cameras.size(); i++) {
            if (city.equals(cameras.get(i).city)) { showCamera(i); return; }
        }
    }

    private void showCamera(int newIndex) {
        if (cameras.isEmpty()) return;
        index = (newIndex + cameras.size()) % cameras.size();
        CameraItem cam = cameras.get(index);
        titleView.setText(cam.city + " — " + cam.name);
        sourceView.setText(cam.type == CameraType.RTSP ? "Privatna Imou kamera • lokalni RTSP" : "Javna live kamera • LiveCamCroatia");
        stopCurrentViewer();
        viewer.removeAllViews();
        if (cam.type == CameraType.RTSP) showRtsp(cam.url); else showWeb(cam.url);
        updateWeather(cam.city);
        handler.removeCallbacks(rotateRunnable);
        scheduleRotation();
    }

    private void showWeb(String url) {
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.setBackgroundColor(Color.BLACK);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String pageUrl) {
                String js = "javascript:(function(){" +
                        "document.body.style.background='#000';" +
                        "var h=['header','footer','nav','.navbar','.cookie','.cookies','.cookie-banner','.advertisement','.adsbygoogle'];" +
                        "h.forEach(function(s){document.querySelectorAll(s).forEach(function(e){e.style.display='none';});});" +
                        "var v=document.querySelector('video')||document.querySelector('.camera iframe')||document.querySelector('iframe');" +
                        "if(v){v.scrollIntoView({block:'center'});v.style.maxWidth='100%';}" +
                        "})();";
                view.evaluateJavascript(js, null);
            }
        });
        viewer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.loadUrl(url);
    }

    private void showRtsp(String url) {
        playerView = new PlayerView(this);
        playerView.setUseController(!tvMode);
        playerView.setBackgroundColor(Color.BLACK);
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        viewer.addView(playerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        try {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
            player.prepare();
            player.play();
        } catch (Exception e) {
            Toast.makeText(this, "Imou RTSP veza nije uspjela", Toast.LENGTH_LONG).show();
        }
    }

    private void stopCurrentViewer() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        playerView = null;
    }

    private void nextCamera() { if (!cameras.isEmpty()) showCamera(index + 1); }
    private void previousCamera() { if (!cameras.isEmpty()) showCamera(index - 1); }

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
        if (loc == null) {
            weatherView.setText("Privatna kamera");
            forecastView.setText("");
            return;
        }
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

                String now = String.format(Locale.getDefault(), "%s • %.0f°C • osjet %.0f°C • vjetar %.0f km/h",
                        weatherText(code), temp, feel, wind);
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
                    CameraItem currentCam = cameras.isEmpty() ? null : cameras.get(index);
                    if (currentCam != null && city.equals(currentCam.city)) {
                        weatherView.setText(city + " • " + now);
                        forecastView.setText(fc.toString());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    CameraItem currentCam = cameras.isEmpty() ? null : cameras.get(index);
                    if (currentCam != null && city.equals(currentCam.city)) {
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
        if ("Vir".equals(city)) return new Location(44.2979, 15.0851);
        return null;
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

    private void showImouSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), dp(10));
        scroll.addView(box);

        TextView help = new TextView(this);
        help.setText("Imou kamere se spremaju samo na ovom uređaju, ne u GitHub. Unesi puni RTSP URL. Čest primjer: rtsp://korisnik:lozinka@192.168.1.50:554/cam/realmonitor?channel=1&subtype=0");
        help.setTextSize(14);
        box.addView(help);

        EditText n1 = edit("Naziv kamere 1", prefs.getString("imou_name_1", "Imou 1"));
        EditText u1 = edit("RTSP URL kamere 1", prefs.getString("imou_url_1", ""));
        EditText n2 = edit("Naziv kamere 2", prefs.getString("imou_name_2", "Imou 2"));
        EditText u2 = edit("RTSP URL kamere 2", prefs.getString("imou_url_2", ""));
        u1.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        u2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(n1); box.addView(u1); box.addView(n2); box.addView(u2);

        new AlertDialog.Builder(this)
                .setTitle("Imou kamere")
                .setView(scroll)
                .setNegativeButton("Odustani", null)
                .setNeutralButton("Obriši", (d, w) -> {
                    prefs.edit().remove("imou_name_1").remove("imou_url_1").remove("imou_name_2").remove("imou_url_2").apply();
                    rebuildCameraList();
                    if (!cameras.isEmpty()) showCamera(0);
                })
                .setPositiveButton("Spremi", (d, w) -> {
                    prefs.edit()
                            .putString("imou_name_1", n1.getText().toString().trim())
                            .putString("imou_url_1", u1.getText().toString().trim())
                            .putString("imou_name_2", n2.getText().toString().trim())
                            .putString("imou_url_2", u2.getText().toString().trim())
                            .apply();
                    rebuildCameraList();
                    Toast.makeText(this, "Imou postavke spremljene", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private EditText edit(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setPadding(dp(8), dp(10), dp(8), dp(10));
        return e;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            nextCamera(); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            previousCamera(); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            toggleAuto(); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showImouSettings(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopCurrentViewer();
        io.shutdownNow();
        super.onDestroy();
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }

    private enum CameraType { WEB, RTSP }

    private static class CameraItem {
        final String city, name, url;
        final CameraType type;
        CameraItem(String city, String name, String url, CameraType type) {
            this.city = city; this.name = name; this.url = url; this.type = type;
        }
    }

    private static class Location {
        final double lat, lon;
        Location(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }
}
