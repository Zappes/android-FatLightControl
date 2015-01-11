package net.bluephod.fatlightcontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.Set;


public class ConnectActivity extends ActionBarActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_connect);
	}

	@Override
	protected void onResume() {
		super.onResume();

		BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

		if(btAdapter == null) {
			Toast.makeText(getApplicationContext(), R.string.err_bluetooth_not_available, Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
		ArrayAdapter<DeviceEntry> devicesAdapter= new ArrayAdapter<DeviceEntry>(this, android.R.layout.simple_list_item_1);
		for(BluetoothDevice device : devices) {
			if(device.getName().toUpperCase().startsWith("FATLIGHT")) {
				devicesAdapter.add(new DeviceEntry(device));
			}
		}

		final ListView devicesListView = (ListView) findViewById(R.id.devicesListView);
		devicesListView.setAdapter(devicesAdapter);

		devicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				DeviceEntry entry = (DeviceEntry) devicesListView.getItemAtPosition(position);

				Intent intent = new Intent(ConnectActivity.this, ControlActivity.class);
				Bundle b = new Bundle();
				b.putString("name", entry.getName());
				b.putString("address", entry.getAddress());
				intent.putExtras(b);

				startActivity(intent);
				finish();
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_connect, menu);
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
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	public static class DeviceEntry {
		BluetoothDevice device;

		public DeviceEntry(BluetoothDevice device) {
			this.device = device;
		}

		public String getName() {
			return device.getName();
		}

		public String getAddress() {
			return device.getAddress();
		}

		@Override
		public String toString() {
			return device.getName();
		}
	}
}
