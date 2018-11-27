package burnnotice.capstone.alara;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static android.support.constraint.Constraints.TAG;
import static burnnotice.capstone.alara.BluetoothReceiver.dbl_received_data;

// AG: Modified from https://stackoverflow.com/questions/13450406/ ...
// ... how-to-receive-serial-data-using-android-bluetooth

public class BluetoothService extends Service
{
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
    public static double volts, uv_index, instant_irradiance, cumul_irradiance, exposure_percent, user_MED = 0;
    LinkedList<Double> history_irradiance = new LinkedList<>();
    String user_input_MED;

    // AG: Created for Service
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // AG: Don't need to bind
    }

    // AG: Created for Service, runs until app is closed
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // AG: Changed Bluetooth to Service, no longer need GUI buttons in onCreate
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate();

        // AG: Connect to device via BT
            try
            {
                // AG: Changed to if statement, to handle case when BT not available
                if (findBT()) {
                openBT();
                }
            }
            catch (IOException ex) { ex.printStackTrace();}

//        setContentView(R.layout.content_bluetooth);
//
//        Button openButton = (Button)findViewById(R.id.open);
//        Button sendButton = (Button)findViewById(R.id.send);
//        Button closeButton = (Button)findViewById(R.id.close);
//        myLabel = (TextView)findViewById(R.id.label);
//        myTextbox = (EditText)findViewById(R.id.entry);
//
//        //Open Button
//        openButton.setOnClickListener(new View.OnClickListener()
//        {
//            public void onClick(View v)
//            {
//                try
//                {
//                    // AG: Changed to if statement, to handle case when BT not available
//                    if (findBT()) {
//                    openBT();
//                    }
//                }
//                catch (IOException ex) { }
//            }
//        });

        //Send Button
//        sendButton.setOnClickListener(new View.OnClickListener()
//        {
//            public void onClick(View v)
//            {
//                try
//                {
//                    sendData();
//                }
//                catch (IOException ex) { }
//                catch (NullPointerException NPE) { } // AG: Can press send, even if nothing opened
//            }
//        });

        //Close button
//        closeButton.setOnClickListener(new View.OnClickListener()
//        {
//            public void onClick(View v)
//            {
//                try
//                {
//                    closeBT();
//                }
//                catch (IOException ex) { }
//                catch (NullPointerException NPE) { } // AG: Can press close, even if nothing opened
//            }
//        });
    }

    // AG: Created for Service
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
            Log.d("STATUS","No bluetooth adapter available");
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
        Log.d("STATUS","Bluetooth Device Found");
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

        Log.d("STATUS","Bluetooth Opened");
    }

    void beginListenForData()
    {
        final Handler handler = new Handler();

        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
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
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            Log.d("Data: ",data);
                                            getSkinType();
                                            calculateExposure(data);
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

        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("User Entered Skin Type", Context.MODE_PRIVATE);
        String defaultValue = getResources().getStringArray(R.array.list_skin_types)[0];
        user_input_MED = sharedPref.getString(getString(R.string.skin_types), defaultValue);

        if (user_input_MED.equals("Type I: Always burns, doesn't tan")) {
            user_MED = 15;
        }
        else if (user_input_MED.equals("Type II: Burns easily")) {
            user_MED = 25;
        }
        else if (user_input_MED.equals("Type III: Tans after initial burn")) {
            user_MED = 30;
        }
        else if (user_input_MED.equals("Type IV: Burns minimally, tans easily")) {
            user_MED = 40;
        }
        else if (user_input_MED.equals("Type V: Rarely burns, tans well")) {
            user_MED = 60;
        }
        else if (user_input_MED.equals("Type VI: Never burns, always tan")) {
            user_MED = 90;
        }

        Log.d("User MED is: %d", String.valueOf(user_MED));
    }

    // AG: Created method to take input data and calculate exposure
    void calculateExposure(String str_data) {
        dbl_received_data = Double.parseDouble(str_data); // AG: convert string to double
        if (dbl_received_data < 0) { // AG: if negative value, assume negligible and approximately 0
            dbl_received_data = 0;
        }

        // TODO: Change voltage as necessary
        volts = dbl_received_data / 4096 * 5; // AG: convert bits (bytes?) to voltage
        uv_index = volts / 10;
        instant_irradiance = uv_index * 0.0025; // AG: convert UV index to instant_irradiance (1 = 0.0025 mW/cm^2)
        // AG: UV index conversion according to https://escholarship.org/uc/item/5925w4hq
        cumul_irradiance = cumul_irradiance + instant_irradiance;
        exposure_percent = cumul_irradiance / user_MED;

        // TODO: Verify output

        history_irradiance.add(instant_irradiance);
    }

    void sendData() throws IOException
    {
        String msg = myTextbox.getText().toString();
        msg += "\n";
        mmOutputStream.write(msg.getBytes());
        Log.d("STATUS","Data Sent");
    }

    void closeBT() throws IOException
    {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        Log.d("STATUS","Bluetooth Closed");
    }
}