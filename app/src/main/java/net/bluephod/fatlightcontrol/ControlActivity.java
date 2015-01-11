package net.bluephod.fatlightcontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.common.base.Splitter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * This activity does everything.
 * <p/>
 * Inspired by https://bellcode.wordpress.com/2012/01/02/android-and-arduino-bluetooth-communication/
 */
public class ControlActivity extends ActionBarActivity {
	private static final String TAG = "ControlActivity";

	private TextView connectionTextView;
	private TextView resultTextView;
	private Button onButton;
	private Button offButton;
	private Spinner modeSpinner;
	private EditText trgbEditText;
	private Button trgbButton;
	private EditText delayEditText;
	private Button delayButton;

	private BluetoothDevice device;
	private BluetoothSocket socket;
	private OutputStreamWriter out;
	private InputStream in;
	private boolean stopWorker = false;

	private String lastCommand = "";
	private Map<String, String> status;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		status = new HashMap<String, String>();
		setContentView(R.layout.activity_control);

		connectionTextView = (TextView) findViewById(R.id.connectionTextView);
		resultTextView = (TextView) findViewById(R.id.resultTextView);
		onButton = (Button) findViewById(R.id.onButton);
		offButton = (Button) findViewById(R.id.offButton);
		modeSpinner = (Spinner) findViewById(R.id.modeSpinner);
		trgbEditText = (EditText) findViewById(R.id.trgbEditText);
		trgbButton = (Button) findViewById(R.id.trgbButton);
		delayEditText = (EditText) findViewById(R.id.delayEditText);
		delayButton = (Button) findViewById(R.id.delayButton);

		setInputsEnabled(false);

		connectionTextView.setText(R.string.err_fatlight_not_connected);
		resultTextView.setText("");

		onButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setActive(true);
			}
		});
		offButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setActive(false);
			}
		});
		trgbButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setTrgb(trgbEditText.getText().toString());
			}
		});
		delayButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setDelay(delayEditText.getText().toString());
			}
		});

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
	}

	private void setInputsEnabled(boolean enabled) {
		onButton.setEnabled(enabled);
		offButton.setEnabled(enabled);
		modeSpinner.setEnabled(enabled);
		trgbButton.setEnabled(enabled);
		trgbEditText.setEnabled(enabled);
		delayButton.setEnabled(enabled);
		delayEditText.setEnabled(enabled);
	}

	private void sendCommand(String command) {
		if (socket.isConnected()) {
			setInputsEnabled(false);
			resultTextView.setText("");

			try {
				out.write(command + "\r\n");
				out.flush();
			} catch (IOException e) {
				Log.e(TAG, "Error writing command", e);
				setInputsEnabled(true);
			}
		} else {
			Log.e(TAG, "Command can't be written to unconnected socket.");
		}
	}

	private void connect(String name, String address) throws IOException {
		device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
		socket = device.createRfcommSocketToServiceRecord(Constants.UUID_SPP);
		socket.connect();
		out = new OutputStreamWriter(socket.getOutputStream());
		in = socket.getInputStream();

		receiveData();

		getStatus();

		connectionTextView.setText(name + " " + address);
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
		Thread workerThread = new Thread(new Runnable() {
			public void run() {
				int readBufferPosition = 0;
				byte[] readBuffer = new byte[1024];

				while (!Thread.currentThread().isInterrupted() && !stopWorker) {
					try {
						int bytesAvailable = in.available();
						if (bytesAvailable > 0) {
							byte[] packetBytes = new byte[bytesAvailable];
							bytesAvailable = in.read(packetBytes);
							for (int i = 0; i < bytesAvailable; i++) {
								byte b = packetBytes[i];
								if (b == delimiter) {
									byte[] encodedBytes = new byte[readBufferPosition];
									System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
									final String data = new String(encodedBytes, "US-ASCII");
									readBufferPosition = 0;

									handler.post(new Runnable() {
										public void run() {
											handleResult(data);
										}
									});
								} else {
									readBuffer[readBufferPosition++] = b;
								}
							}
						}
					} catch (IOException ex) {
						stopWorker = true;
					}
				}
			}
		});

		workerThread.start();
	}

	private void setMode(int mode) {
		lastCommand = "MODE";
		sendCommand("MODE " + mode);
	}

	private void setActive(boolean active) {
		lastCommand = active ? "ON" : "OFF";
		sendCommand(lastCommand);
	}

	private void setTrgb(String trgbString) {
		lastCommand = "SET";
		sendCommand(lastCommand + " " +trgbString);
	}

	private void setDelay(String delayString) {
		lastCommand = "DELAY";
		sendCommand(lastCommand + " " +delayString);
	}

	private void getStatus() {
		lastCommand = "STATUS";
		sendCommand(lastCommand);
	}

	private void handleStatus(String result) {
		if(result.startsWith("YEAH")) {
			status = Splitter.on(" ").trimResults().withKeyValueSeparator(":").split(result.substring(5));

			trgbEditText.setText(status.get("TRGB"));
			delayEditText.setText(status.get("DELAY"));
			modeSpinner.setSelection(Integer.parseInt(status.get("MODE")));
		}
	}

	private void handleResult(String result) {
		if (lastCommand.equals("STATUS")) {
			handleStatus(result);
		}

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
		} else {
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
