package burnnotice.capstone.alara;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.LinkedList;

public class BluetoothReceiver extends BroadcastReceiver {

    public static double dbl_received_data = 0;

    @Override
    public void onReceive(Context context, Intent intent) {

        Bundle bundle = intent.getExtras();
        String str_data = bundle.toString();
        dbl_received_data = Double.parseDouble(str_data); // AG: find double

    }

}
