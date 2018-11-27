package burnnotice.capstone.alara;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import static burnnotice.capstone.alara.BluetoothService.exposure_percent;
import static burnnotice.capstone.alara.BluetoothReceiver.dbl_received_data;


public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // AG: Starts Bluetooth connection
        startService(new Intent(this, BluetoothService.class));
        BluetoothReceiver bluetoothReceiver = new BluetoothReceiver();
        bluetoothReceiver.onReceive(this, getIntent());

//        AG: Use button function to send test notifications
        final Button test_notif_button = findViewById(R.id.test_notif_button);
        test_notif_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Notifications.notifyExposure(getApplicationContext(), "", 1);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            // AG: onClick, navigates from main page to settings page
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
//        else if (id == R.id.action_bluetooth) {
//            startActivity(new Intent(this, BluetoothService.class));
//            startProgress();
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }

    boolean notification_sent = false;

    // AG: Creating method to continuously update progress bar
    private void updateProgress() {
        final ProgressBar progressBar = findViewById(R.id.progressBar);
        final TextView progressPercent = findViewById(R.id.progressPercent);

        Thread progressThread = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    // AG: Update progress bar
                    progressBar.setProgress((int) exposure_percent);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // AG: Change % value of progress TextView
                            progressPercent.setText(String.valueOf(progressBar.getProgress()) + " %");

                            // AG: If dangerous exposure, send notification
                            if (exposure_percent >= 80 & !notification_sent) {
                                Notifications.notifyExposure(getApplicationContext(), "", 1);
                                notification_sent = true;
                            }
                        }
                    });
                    try {
                        Thread.sleep(1000); // AG: Time between updates (1 second)
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        progressThread.start();
    }
}
