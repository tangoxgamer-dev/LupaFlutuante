
package com.lupaflutuante.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.media.projection.MediaProjectionManager;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int CAPTURE_REQUEST = 100;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectionManager = (MediaProjectionManager)
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(35, 60, 35, 35);

        TextView title = new TextView(this);
        title.setText("Lupa Flutuante 10X");
        title.setTextSize(25);
        layout.addView(title);

        Button start = new Button(this);
        start.setText("ATIVAR LUPA");
        layout.addView(start);

        Button stop = new Button(this);
        stop.setText("DESLIGAR LUPA");
        layout.addView(stop);

        setContentView(layout);

        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 &&
                !Settings.canDrawOverlays(this)) {

                Intent permission = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                );
                startActivity(permission);
                return;
            }

            Intent capture =
                projectionManager.createScreenCaptureIntent();

            startActivityForResult(
                capture, CAPTURE_REQUEST
            );
        });

        stop.setOnClickListener(v -> {
            stopService(new Intent(this, LupaService.class));
        });
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(
            requestCode, resultCode, data
        );

        if (requestCode == CAPTURE_REQUEST &&
            resultCode == RESULT_OK &&
            data != null) {

            Intent service = new Intent(
                this, LupaService.class
            );

            service.putExtra("resultCode", resultCode);
            service.putExtra("resultData", data);

            startForegroundService(service);
        }
    }
}
