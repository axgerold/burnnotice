package burnnotice.capstone.alara;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.res.TypedArrayUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.lang.Double.NaN;

// AG: Modified from https://stackoverflow.com/questions/13450406/ ...
// ... how-to-receive-serial-data-using-android-bluetooth

public class BluetoothActivity extends Activity
{
    TextView myLabel;
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
    double dbl_data, volts, uv_index, instant_irradiance, cumulative_irradiance, int_progress, user_MED = NaN;
    String str_progress = "";
    List<Double> history_irradiance = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // AG: Changed layout to Bluetooth xml, instead of Main
        setContentView(R.layout.content_bluetooth);

        Button openButton = (Button)findViewById(R.id.open);
        Button sendButton = (Button)findViewById(R.id.send);
        Button closeButton = (Button)findViewById(R.id.close);
        myLabel = (TextView)findViewById(R.id.label);
        myTextbox = (EditText)findViewById(R.id.entry);

        //Open Button
        openButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    // AG: Changed to if statement, to handle case when BT not available
                    if (findBT()) {
                    openBT(); }
                }
                catch (IOException ex) { }
            }
        });

        //Send Button
        sendButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    sendData();
                }
                catch (IOException ex) { }
                catch (NullPointerException NPE) { } // AG: Can press send, even if nothing opened
            }
        });

        //Close button
        closeButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    closeBT();
                }
                catch (IOException ex) { }
                catch (NullPointerException NPE) { } // AG: Can press close, even if nothing opened
            }
        });
    }

    // AG: Changed void to boolean method to pass by openBT if BT not found/null
    public boolean findBT()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            myLabel.setText("No bluetooth adapter available");
            return false; // AG: Can return, even if void method; will quit method
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
            return false;
        }

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
        myLabel.setText("Bluetooth Device Found");
        return true;
    }

    void openBT() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();

        myLabel.setText("Bluetooth Opened");
    }

    void beginListenForData()
    {
        final Handler handler = new Handler();

        // TODO: Change delimiter to find data of interest
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
                                            myLabel.setText(data);
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

    // AG: Created method to take input data and calculate exposure
    void calculateExposure(String str_data) {
        dbl_data = Double.parseDouble(str_data); // AG: convert string to double
        if (dbl_data < 0) { // AG: if negative value, assume negligible and approximately 0
            dbl_data = 0;
        }

        // TODO: Change voltage as necessary
        volts = dbl_data / 4096 * 5; // AG: convert bits (bytes?) to voltage
        uv_index = volts / 10;
        instant_irradiance = uv_index * 0.0025; // AG: convert UV index to instant_irradiance (1 = 0.0025 mW/cm^2)
        // AG: UV index conversion according to https://escholarship.org/uc/item/5925w4hq
        cumulative_irradiance = cumulative_irradiance + instant_irradiance;
        // TODO: Cumulative irradiance must update progress bar (content_main.xml)
        int_progress = cumulative_irradiance / user_MED;
        str_progress = Double.toString(int_progress);
        // TODO: Must factor in time prior to skin type MED/preference time
        // TODO: Must trigger NotificationActivity
        history_irradiance.add(instant_irradiance);
        System.out.println(cumulative_irradiance);
        // TODO: Remove print statement after verifying functionality
    }

    void sendData() throws IOException
    {
        String msg = myTextbox.getText().toString();
        msg += "\n";
        mmOutputStream.write(msg.getBytes());
        myLabel.setText("Data Sent");
    }

    void closeBT() throws IOException
    {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        myLabel.setText("Bluetooth Closed");
    }
}