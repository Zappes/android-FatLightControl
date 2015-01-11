package net.bluephod.fatlightcontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * This activity does everything.
 *
 * Inspired by https://bellcode.wordpress.com/2012/01/02/android-and-arduino-bluetooth-communication/
 */
public class ControlActivity extends ActionBarActivity {
	private static final String TAG = "ControlActivity";

	private TextView connectionTextView;
	private TextView resultTextView;
	private Button onButton;
	private Button offButton;
	private Spinner modeSpinner;
	private BluetoothDevice device;
	private BluetoothSocket socket;
	private OutputStreamWriter out;
	private InputStream in;
	private boolean stopWorker = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_control);

		connectionTextView = (TextView) findViewById(R.id.connectionTextView);
		resultTextView = (TextView) findViewById(R.id.resultTextView);
		onButton = (Button) findViewById(R.id.onButton);
		offButton = (Button) findViewById(R.id.offButton);
		modeSpinner = (Spinner) findViewById(R.id.modeSpinner);

		connectionTextView.setText(R.string.err_fatlight_not_connected);
		resultTextView.setText("");

		onButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setActive(true);
			}
		});
		onButton.setEnabled(false);

		offButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setActive(false);
			}
		});
		offButton.setEnabled(false);

		ArrayAdapter<String> modeAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
		modeAdapter.add(getString(R.string.control_mode_direct));
		modeAdapter.add(getString(R.string.control_mode_fading));
		modeAdapter.add(getString(R.string.control_mode_disco));

		modeSpinner = (Spinner) findViewById(R.id.modeSpinner);
		modeSpinner.setEnabled(false);
		modeSpinner.setAdapter(modeAdapter);
		modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				setMode(position);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {

			}
		});
		modeSpinner.setEnabled(true);
	}

	private void setInputsEnabled(boolean enabled) {
		onButton.setEnabled(enabled);
		offButton.setEnabled(enabled);
		modeSpinner.setEnabled(enabled);
	}

	private void sendCommand(String command) {
		setInputsEnabled(false);
		resultTextView.setText("");

		try {
			out.write(command + "\r\n");
			out.flush();
		} catch (IOException e) {
			Log.e(TAG, "Error writing command", e);
			setInputsEnabled(true);
		}
	}

	private void connect(String name, String address) throws IOException
	{
		device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
		socket = device.createRfcommSocketToServiceRecord(Constants.UUID_SPP);
		socket.connect();
		out = new OutputStreamWriter(socket.getOutputStream());
		in = socket.getInputStream();

		receiveData();

		connectionTextView.setText(name + " " + address);
		setInputsEnabled(true);
	}

	private void disconnect() throws IOException {
		setInputsEnabled(false);

		stopWorker = true;
		out.close();
		in.close();
		socket.close();
		connectionTextView.setText(R.string.err_fatlight_not_connected);
	}

	private void receiveData() {
		final Handler handler = new Handler();
		final byte delimiter = 10; //This is the ASCII code for a newline character

		stopWorker = false;
		Thread workerThread = new Thread(new Runnable()
		{
			public void run()
			{
				int readBufferPosition = 0;
				byte[] readBuffer = new byte[1024];

				while(!Thread.currentThread().isInterrupted() && !stopWorker)
				{
					try
					{
						int bytesAvailable = in.available();
						if(bytesAvailable > 0)
						{
							byte[] packetBytes = new byte[bytesAvailable];
							bytesAvailable = in.read(packetBytes);
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
											handleResult(data);
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


	private void setMode(int mode) {
		sendCommand("MODE " + mode);
	}

	private void setActive(boolean active) {
		sendCommand(active ? "ON" : "OFF");
	}

	private void getStatus() {
		sendCommand("STATUS");
	}

	private void handleResult(String result) {
		resultTextView.setText(result);
		setInputsEnabled(true);
	}

	@Override
	protected void onResume() {
		super.onResume();

		Bundle b = getIntent().getExtras();

		if (b != null) {
			try {
				connect(b.getString("name"), b.getString("address"));
			} catch (IOException e) {
				Log.e(TAG, "Exception on connect", e);
			}
		}
		else {
			connectionTextView.setText(R.string.err_fatlight_not_specified);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		try {
			disconnect();
		} catch (IOException e) {
			Log.e(TAG, "Exception on disconnect", e);
		}
	}
}
