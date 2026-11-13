
package com.lupaflutuante.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;

import java.nio.ByteBuffer;

public class LupaService extends Service {

    private WindowManager wm;
    private LinearLayout panel;
    private WindowManager.LayoutParams params;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private ImageView preview;
    private Bitmap frame;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int zoom = 5;
    private int screenW, screenH;
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannel channel =
            new NotificationChannel(
                "lupa", "Lupa Flutuante",
                NotificationManager.IMPORTANCE_LOW
            );

        NotificationManager nm =
            getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);

        Notification notification =
            new Notification.Builder(this, "lupa")
                .setContentTitle("Lupa Flutuante")
                .setContentText("Captura de tela ativa")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .build();

        startForeground(
            1, notification,
            android.content.pm.ServiceInfo
                .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null) return START_NOT_STICKY;

        int result = intent.getIntExtra("resultCode", 0);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(
                "resultData", Intent.class);
        } else {
            data = intent.getParcelableExtra("resultData");
        }

        if (result != Activity.RESULT_OK || data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        wm = (WindowManager)
            getSystemService(WINDOW_SERVICE);

        android.util.DisplayMetrics metrics =
            getResources().getDisplayMetrics();

        screenW = metrics.widthPixels;
        screenH = metrics.heightPixels;

        createOverlay();

        MediaProjectionManager manager =
            (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        projection = manager.getMediaProjection(result, data);

        projection.registerCallback(
            new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopSelf();
                }
            }, handler
        );

        reader = ImageReader.newInstance(
            screenW, screenH,
            PixelFormat.RGBA_8888, 2
        );

        display = projection.createVirtualDisplay(
            "LupaCapture", screenW, screenH,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.getSurface(), null, handler
        );

        running = true;
        handler.postDelayed(this::updateFrame, 150);
        return START_NOT_STICKY;
    }

    private void createOverlay() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(8, 8, 8, 8);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(25, 25, 30));
        bg.setCornerRadius(22);
        panel.setBackground(bg);

        LinearLayout bar = new LinearLayout(this);

        Button five = new Button(this);
        five.setText("5X");
        bar.addView(five);

        Button ten = new Button(this);
        ten.setText("10X");
        bar.addView(ten);

        Button close = new Button(this);
        close.setText("X");
        bar.addView(close);

        panel.addView(bar);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        panel.addView(preview,
            new LinearLayout.LayoutParams(320, 280));

        params = new WindowManager.LayoutParams(
            360, WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 40;
        params.y = 180;

        wm.addView(panel, params);

        five.setOnClickListener(v -> zoom = 5);
        ten.setOnClickListener(v -> zoom = 10);
        close.setOnClickListener(v -> stopSelf());

        bar.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int oldX, oldY;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX();
                    downY = e.getRawY();
                    oldX = params.x;
                    oldY = params.y;
                    return true;
                }

                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = oldX +
                        (int)(e.getRawX() - downX);
                    params.y = oldY +
                        (int)(e.getRawY() - downY);
                    wm.updateViewLayout(panel, params);
                    return true;
                }
                return false;
            }
        });
    }

    private void updateFrame() {
        if (!running || reader == null) return;

        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image != null) {
                Image.Plane plane = image.getPlanes()[0];
                ByteBuffer buffer = plane.getBuffer();

                int pixelStride = plane.getPixelStride();
                int rowStride = plane.getRowStride();
                int paddedWidth =
                    screenW + (rowStride -
                    pixelStride * screenW) / pixelStride;

                Bitmap full = Bitmap.createBitmap(
                    paddedWidth, screenH,
                    Bitmap.Config.ARGB_8888
                );
                full.copyPixelsFromBuffer(buffer);

                int cropSize = Math.min(
                    screenW, screenH) / zoom;

                int cx = Math.max(0,
                    Math.min(screenW - cropSize,
                        params.x + 180 - cropSize / 2));

                int cy = Math.max(0,
                    Math.min(screenH - cropSize,
                        params.y + 180 - cropSize / 2));

                Bitmap cropped = Bitmap.createBitmap(
                    full, cx, cy, cropSize, cropSize
                );

                Bitmap scaled = Bitmap.createScaledBitmap(
                    cropped, 320, 280, false
                );

                preview.setImageBitmap(scaled);

                if (frame != null && !frame.isRecycled())
                    frame.recycle();

                frame = scaled;
                cropped.recycle();
                full.recycle();
            }
        } catch (Exception ignored) {
        } finally {
            if (image != null) image.close();
        }

        if (running)
            handler.postDelayed(this::updateFrame, 250);
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);

        if (display != null) display.release();
        if (reader != null) reader.close();
        if (projection != null) projection.stop();

        if (wm != null && panel != null)
            wm.removeView(panel);

        if (frame != null && !frame.isRecycled())
            frame.recycle();

        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
          }
