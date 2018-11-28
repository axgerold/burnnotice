package burnnotice.capstone.alara;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.EditText;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

// AG: Modified from https://stackoverflow.com/questions/13450406/ ...
// ... how-to-receive-serial-data-using-android-bluetooth

// AG: Changed from Activity to Service

public class BluetoothService extends Service
{
    private static final String TAG = BluetoothService.class.getSimpleName();
//    TextView myLabel;
    EditText myTextbox;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;

    // AG: Initialize variables
    private static double incoming_data, exposure_percentage, volts, uv_index, instant_irradiance,
            cumul_irradiance, exposure_percent, user_MED = 0;
    private int user_input_skin_type = 0;

    // AG: Created for Service
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // AG: Don't need to bind
    }

    // AG: Changed Bluetooth to Service, no longer need GUI buttons in onCreate
    public void onCreate()
    {
        super.onCreate();
        Log.d(TAG, "onCreate running");

        // AG: Connect to device via BT
            try
            {
                // AG: Changed to if statement, to handle case when BT not available
                if (findBT()) {
                openBT();
                }
            }
            catch (IOException ex) { ex.printStackTrace();}

    }

    // AG: Created for Service, runs until app is closed
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // AG: Created for Service
    @Override
    public void onDestroy() {
        try {
            closeBT();
        } catch (IOException e) {
            e.printStackTrace();
        }

        super.onDestroy();

    }

    // AG: Changed void to boolean method to pass by openBT if BT not found/null
    public boolean findBT()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            Log.d(TAG,"No bluetooth adapter available");
            return false; // AG: Can return, even if void method; will quit method
        }

//        if(!mBluetoothAdapter.isEnabled())
//        {
//            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBluetooth, 0);
//            return false;
//        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("Burn Notice BLE1"))
                {
                    mmDevice = device;
                    break; // AG: ends for loop
                }
            }
        }
        Log.d(TAG,"Bluetooth Device Found");
        return true;
    }

    /** Nordic UART Service UUID */
    private final static UUID UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    /** RX characteristic UUID */
    private final static UUID UART_RX_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    /** TX characteristic UUID */
    private final static UUID UART_TX_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");


    void openBT() throws IOException
    {
        // AG : Imported UART UUIDs
        UUID uuid = UART_SERVICE_UUID;
        mmSocket = mmDevice.createInsecureRfcommSocketToServiceRecord(uuid);
        try {
            mmSocket.connect();
        } catch (IOException e) {
            try {
                mmSocket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();

        Log.d(TAG,"Bluetooth Opened");
    }

    void beginListenForData()
    {
        final Handler handler = new Handler();

        // TODO: Change delimiter, if necessary
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024]; // AG: Up to 1024 characters (kilobyte)
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String incoming_data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            Log.d(TAG, incoming_data);

                                            // TODO: Create splash page so we don't have to call getSkinType() every time

                                            getSkinType();
                                            exposure_percentage = calculateExposure(incoming_data);
                                            // AG: Added broadcast of received data
                                            Intent intent = new Intent("Alara.Broadcast.EXPOSURE");
                                            intent.putExtra("CALCULATED_EXPOSURE", exposure_percentage);
                                            BluetoothService.this.sendBroadcast(intent);
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    // AG: Created method to get skin type from user
    void getSkinType() {

        AlaraSharedPreferences sharedPreferences = new AlaraSharedPreferences();
        user_input_skin_type = sharedPreferences.getSkinType(getApplicationContext());

        // TODO: Change MEDs as necessary

        switch (user_input_skin_type){
            case 1:
                user_MED = 15;
                break;
            case 2:
                user_MED = 25;
                break;
            case 3:
                user_MED = 30;
                break;
            case 4:
                user_MED = 40;
                break;
            case 5:
                user_MED = 60;
                break;
            case 6:
                user_MED = 90;
                break;
        }

        Log.d("User MED is: %d", String.valueOf(user_MED));
    }

    // AG: Created method to take input data and calculate exposure
    double calculateExposure(String raw_data) {
        incoming_data = Double.parseDouble(raw_data); // AG: convert string to double
        if (incoming_data < 0) { // AG: if negative value, assume negligible and approximately 0
            incoming_data = 0;
        }

        // TODO: Change voltage as necessary
        volts = incoming_data / 4095 * 5; // AG: convert bytes to voltage
        uv_index = volts / 10;
        instant_irradiance = uv_index * 0.0025; // AG: convert UV index to instant_irradiance (1 = 0.0025 mW/cm^2)
        // AG: UV index conversion according to https://escholarship.org/uc/item/5925w4hq
        cumul_irradiance = cumul_irradiance + instant_irradiance;
        exposure_percent = cumul_irradiance / user_MED;

        return exposure_percent;

        // TODO: Verify calibration
    }

    void sendData() throws IOException
    {
        String msg = myTextbox.getText().toString();
        msg += "\n";
        mmOutputStream.write(msg.getBytes());
        Log.d(TAG,"Data Sent");
    }

    void closeBT() throws IOException
    {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        Log.d(TAG,"Bluetooth Closed");
    }
}