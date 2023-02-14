package com.example.masterthesis;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/**
 * View of the application after successful connection via Bluetooth
 */
public class MainActivity_BT extends AppCompatActivity {

    //Variable containing the list of devices found
    private final ArrayList<String> discoveredDevices = new ArrayList<>();

    //Adapter connecting arrays with ListView
    private ArrayAdapter<String> listAdapter;

    //sockets connecting as server and client
    private BluetoothSocket socketClient = null, socketServer = null;

    //A variable stating whether the file has been selected for upload
    private boolean dataSendFromClient = false;

    //Intent to file
    private Intent fileToSend;

    //Log class reference
    private final MainActivity_Log.ListLog LOG = new MainActivity_Log.ListLog();
    private Button button_sendData, button_foundDevice, button_detect, button_disconnectBack;
    private TextView textView_connected, textView_inf, textView_percent;
    private ListView listView;
    private ProgressBar progressBar;
    private ConnectBtServerThread threadServer;
    private ConnectBtClientThread threadClient;
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_bt);
        setTitle("Bluetooth");

        button_disconnectBack = findViewById(R.id.button_disconnectBack);
        button_detect = findViewById(R.id.button_detect);
        button_foundDevice = findViewById(R.id.button_foundDevice);
        button_sendData = findViewById(R.id.button_sendData);
        textView_connected = findViewById(R.id.textView_connected);
        textView_inf = findViewById(R.id.textView_inf);
        textView_percent = findViewById(R.id.textView_percent);
        listView = findViewById(R.id.ListView);
        progressBar = findViewById(R.id.progressBar);

        textView_connected.setText("Not connected");

        //The invoked thread listening for the connection attempt
        threadServer = new ConnectBtServerThread();
        threadServer.start();

        //Button to detection by other devices
        button_detect.setOnClickListener(v -> discoverableBt());

        //Button to find device
        button_foundDevice.setOnClickListener((v -> foundDeviceBt()));

        //Button to send data
        button_sendData.setOnClickListener(v -> sendDataFile());

        //Button to disconnect or back
        button_disconnectBack.setOnClickListener(v -> {
            closeBtConnection();
            Intent intent = new Intent(MainActivity_BT.this, MainActivity.class);
            startActivity(intent);
        });

        //Select a found device for Bluetooth connection
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String deviceInfo = (String) listView.getItemAtPosition(position);
            //deviceAddress holds the 17 characters from the end of the deviceInfo string
            String deviceAddress = deviceInfo.substring(deviceInfo.length() - 17);
            BluetoothDevice device = Constants.bluetoothAdapter.getRemoteDevice(deviceAddress);
            threadClient = new ConnectBtClientThread(device);
            threadClient.start();
            threadServer.interrupt();
        });
    }

    //region BT detection

    //Calling intent enable discoverability
    private void discoverableBt() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        ActivityDiscoverableBt.launch(intent);
    }

    //Reactions to permission response received discoverableBt
    final ActivityResultLauncher<Intent> ActivityDiscoverableBt = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != 0)
                    Toast.makeText(MainActivity_BT.this, "The device is discoverable", Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(MainActivity_BT.this, "The device is undetectable", Toast.LENGTH_SHORT).show();
            });

    //endregion BT detection

    //region Searching for BT devices

    //configure discovery of Bluetooth devices
    @SuppressLint("MissingPermission") //Used to bypass validation re-verification
    private void foundDeviceBt() {
        Constants.bluetoothAdapter.startDiscovery();
        listDiscoverableDevices();
        intentActionFound();
        intentActionAclDisconnected();
    }

    //configuration of the list of discoverable devices
    private void listDiscoverableDevices()
    {
        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, discoveredDevices);
        listAdapter.clear();
        listView.setAdapter(listAdapter);
    }

    //launching the intention to detect a new Bluetooth device
    private void intentActionFound()
    {
        IntentFilter intent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, intent);
    }

    //launching the intention about losing connection with the Bluetooth device
    private void intentActionAclDisconnected()
    {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(receiver, filter);
    }

    // Create a BroadcastReceiver for ACTION_FOUND or ACTION_ACL_DISCONNECTED.
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                discoveredDevices.add(device.getName() + "\n" + device.getAddress());
            }
            else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                discoveredDevices.remove(device.getName() + "\n" + device.getAddress());
            }
            listAdapter.notifyDataSetChanged();
        }
    };

    //endregion Searching for BT devices

    //region Connect as a Client

    //client class extended by thread class
    private class ConnectBtClientThread extends Thread {
        private long fileSizeBytes; //file size in bytes
        private String fileSizeUnit = "Bytes" , deviceName;
        private OutputStream outputStream;

        //ConnectBtClientThread class constructor
        @SuppressLint("MissingPermission")
        public ConnectBtClientThread(BluetoothDevice device) {
            LOG.addLog(new Date(System.currentTimeMillis()),"A client thread has started");
            try {
                socketClient = device.createRfcommSocketToServiceRecord(Constants.MY_UUID);
            } catch (IOException e) {
                LOG.addLog(LOG.currentDate(),"Socket's create() method failed", e.getMessage());
                return;
            }
            deviceName = device.getName();
        }

        //A method that is run when the start() method is called on an object representing a thread
        @SuppressLint({"MissingPermission", "SetTextI18n"})
        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            Constants.bluetoothAdapter.cancelDiscovery();
            try {
                // Connection to the device via the socket. This call blocks until it succeeds or throws an exception
                socketClient.connect();
            } catch (IOException e) {
                // Unable to connect, close the socket and return.
                LOG.addLog(LOG.currentDate(),"Unable to connect", e.getMessage());
                closeSocketClient();
                return;
            }
            LOG.addLog(LOG.currentDate(),"The connection attempt succeeded");
            //runOnUiThread() Used to run code on the main UI thread.
            runOnUiThread(() -> {
                textView_connected.setText("Connected as a client with\n" + deviceName);
                button_sendData.setVisibility(View.VISIBLE);
                button_foundDevice.setVisibility(View.INVISIBLE);
                button_detect.setVisibility(View.INVISIBLE);
                button_disconnectBack.setText("Disconnect");
                listView.setVisibility(View.INVISIBLE);});
            if(sendNameDevice()) {
                //keep looping until the thread is stopped.
                while (!interrupted()) {
                    if (dataSendFromClient)
                        sendData();
                    if(!socketClient.isConnected()) {
                        closeSocketClient();
                        runOnUiThread(() -> Toast.makeText(MainActivity_BT.this, "Disconnected", Toast.LENGTH_SHORT).show());
                        break;
                    }
                }
            }
        }
        // Closes the client socket and causes the thread to finish.
        private void closeSocketClient() {
            try {
                socketClient.close();
                LOG.addLog(LOG.currentDate(),"Client socket closed");
            } catch (IOException e) {
                LOG.addLog(LOG.currentDate(),"Could not close the client socket", e.getMessage());
            }
        }

        //The method where the device name is sent
        @SuppressLint("MissingPermission")
        private boolean sendNameDevice()
        {
            try {
                outputStream = socketClient.getOutputStream();
                try {
                    outputStream.write(Constants.bluetoothAdapter.getName().getBytes());
                    outputStream.flush(); //flush() is used to push out all written bytes
                    LOG.addLog(LOG.currentDate(),"Device name sent");
                    return true;
                }
                catch (IOException e) {
                    LOG.addLog(LOG.currentDate(),"Failed to send device name", e.getMessage());
                    return false;
                }
            } catch (IOException ex) {
                LOG.addLog(LOG.currentDate(),"Failed to create stream to send message", ex.getMessage());
                return false;
            }
        }

        //The method where the file data and the file itself are sent
        @SuppressLint({"SetTextI18n", "Range", "Recycle"})
        private void sendData()
        {
            Uri uri = fileToSend.getData();
            double fileSize = getFileSize(uri);
            int bufferSize = (int) (fileSizeBytes * 0.1); //buffer size is 10% of file size in bytes
            byte[] buffer = new byte[bufferSize];
            int bytesRead;

            String fileName = getFileName(uri);
            try {
                //Sending file information
                String fileData = fileName + ";" + fileSizeUnit + ";" + fileSizeBytes + ";" + bufferSize;
                outputStream.write(fileData.getBytes());
                outputStream.flush();
                LOG.addLog(LOG.currentDate(),"Sending file information");

                //start counting the transfer time
                long startTime = System.currentTimeMillis();
                FileInputStream file = null;

                try {
                    file = (FileInputStream) getContentResolver().openInputStream(uri);
                    long fullBytes=0;

                    //A loop that sends a file and displays the progress percentage
                    while ((bytesRead = file.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, bytesRead);
                        fullBytes+=bytesRead;
                        int percent = (int) (fullBytes * 100.0) / (int) fileSizeBytes;
                        runOnUiThread(() -> textView_percent.setText("Sent: " + percent + " %"));
                        progressBar.setProgress(percent);
                    }
                    outputStream.flush();
                    LOG.addLog(LOG.currentDate(),"Data file sent");
                    Arrays.fill(buffer, 0, buffer.length, (byte) 0); //clearing the buffer

                    try {
                        InputStream inputStream = socketClient.getInputStream();
                        byte[] confirmBuffer = new byte[100];

                        //A loop in which it expects the server to confirm receipt of the file
                        while (true) {
                            int bytesLoad = inputStream.read(confirmBuffer);
                            String confirmMessage = new String(confirmBuffer, 0, bytesLoad);

                            if (confirmMessage.equals("Confirmed")) {
                                //end of upload time counting
                                long endTime = System.currentTimeMillis();
                                runOnUiThread(() -> Toast.makeText(MainActivity_BT.this, "File sent", Toast.LENGTH_SHORT).show());
                                double resultTime = (double) (endTime - startTime) / 1000; //time change ms to s
                                double speedSend = fileSize / resultTime;
                                String sizeUnit = setSpeedSendUnit(speedSend);
                                runOnUiThread(() -> textView_inf.setText(textView_inf.getText() + "\nFile transfer time: " +
                                        Constants.decimalFormat.format(resultTime) + " s\nSize of the uploaded file: " +
                                        Constants.decimalFormat.format(fileSize) + " " + fileSizeUnit + "\nUpload speed is: " +
                                        Constants.decimalFormat.format(speedSend) + " " + sizeUnit + "/s"));
                                Arrays.fill(confirmBuffer, 0, confirmBuffer.length, (byte) 0);
                                dataSendFromClient = false;
                                break;
                            } else if (confirmMessage.equals("NoneConfirmed")) {
                                LOG.addLog(LOG.currentDate(),"Failed to save to the server");
                                runOnUiThread(() -> textView_inf.setText(textView_inf.getText() + "\nFailed to save to the server"));
                                Arrays.fill(confirmBuffer, 0, confirmBuffer.length, (byte) 0);
                                dataSendFromClient = false;
                                break;
                            }
                        }
                    }
                    catch (IOException e)
                    {
                        LOG.addLog(LOG.currentDate(),"Failed to create stream to receive message whether file was delivered", e.getMessage());
                    }
                } catch (IOException e) {
                    LOG.addLog(LOG.currentDate(),"Failed to send file",e.getMessage());
                } finally {
                    try {
                        if (file != null) {
                            file.close();
                            LOG.addLog(LOG.currentDate(),"Stream to file closed");
                        }
                    } catch (IOException e) {
                        LOG.addLog(LOG.currentDate(),"Failed to close stream to file",e.getMessage());
                    }
                }
            }
            catch (IOException e)
            {
                LOG.addLog(LOG.currentDate(),"Failed to send basic file information", e.getMessage());
            }
        }

        //method where the filename is retrieved
        @SuppressLint({"Range", "SetTextI18n"})
        private String getFileName(Uri uri)
        {
            String fileName = null;
            if (uri.getScheme().equals("content")) {
                try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    }
                }
            }
            if(fileName != null) {
                int cut = fileName.lastIndexOf('/');
                if (cut != -1) {
                    fileName = fileName.substring(cut + 1);
                }
            }
            String finalFileName = fileName;
            runOnUiThread(() -> textView_inf.setText("The name of the uploaded file: " + finalFileName));
            return finalFileName;
        }

        //method where the file size is taken and then converted to the appropriate size
        @SuppressLint("Range")
        private double getFileSize(Uri uri)
        {
            File file = new File(uri.getPath());
            double fileSize = 0;
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    fileSize = cursor.getDouble(cursor.getColumnIndex(OpenableColumns.SIZE));
                }
            }
            if(fileSize == 0)
                fileSize = file.length();
            fileSizeBytes = (long) fileSize;
            if(fileSize > 1024) {
                fileSize /= 1024; //to KB
                fileSizeUnit = "KB";
                if(fileSize > 1024) {
                    fileSize /= 1024; //to MB
                    fileSizeUnit = "MB";
                }
            }
            return fileSize;
        }

        //method where the size of the variable data transfer rate is converted
        @SuppressWarnings("UnusedAssignment")
        private String setSpeedSendUnit(double speedSend)
        {
            String sizeUnit = fileSizeUnit;
            if(fileSizeUnit.equals("Bytes") && speedSend > 1024) {
                speedSend /= 1024; //to KB
                sizeUnit = "KB";
                if(speedSend > 1024) {
                    speedSend /= 1024; //to MB
                    sizeUnit = "MB";
                }
            }
            else if (fileSizeUnit.equals("KB") && speedSend >1024)
            {
                speedSend /= 1024; //to MB
                sizeUnit = "MB";
            }
            return sizeUnit;
        }
    }

    //endregion Connect as a Client

    //region Connect as a Server

    //server class extended by thread class
    private class ConnectBtServerThread extends Thread {
        private BluetoothServerSocket serverSocket;

        //variable containing the name of the downloaded file
        private String fileName;

        //ConnectBtServerThread class constructor
        @SuppressLint("MissingPermission")
        public ConnectBtServerThread() {
            LOG.addLog(new Date(System.currentTimeMillis()),"A server thread has started listening");
            try {
                serverSocket = Constants.bluetoothAdapter.listenUsingRfcommWithServiceRecord(Constants.NAME, Constants.MY_UUID);
            } catch (IOException e) {
                LOG.addLog(LOG.currentDate(),"Socket's listen() method failed", e.getMessage());
            }
        }
        //A method that is run when the start() method is called on an object representing a thread
        @SuppressLint("SetTextI18n")
        public void run() {
            // Keep listening until exception occurs or a socket is returned.
            try {
                socketServer = serverSocket.accept();
            } catch (IOException e) {
                LOG.addLog(LOG.currentDate(),"Socket's accept() method failed", e.getMessage());
            }
            if (socketServer != null) {
                LOG.addLog(LOG.currentDate(),"The connection attempt succeeded");
                try {
                    InputStream inputStream = socketServer.getInputStream();
                    getData(inputStream);

                    //if the connection is broken
                    if(!socketServer.isConnected()) {
                        try {
                            runOnUiThread(() -> {
                                textView_connected.setText("Disconnected");
                                Toast.makeText(MainActivity_BT.this, "Disconnected", Toast.LENGTH_SHORT).show();
                                button_disconnectBack.setText("Back");
                                button_disconnectBack.setVisibility(View.VISIBLE);});
                            inputStream.close();
                            socketServer.close();
                        } catch (IOException ex) {
                            LOG.addLog(LOG.currentDate(),"Error closing input stream and socket's", ex.getMessage());
                        }
                    }
                } catch (IOException e) {
                    LOG.addLog(LOG.currentDate(),"Failed to create stream to write data", e.getMessage());
                }
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    LOG.addLog(LOG.currentDate(),"Error closing output stream:", e.getMessage());
                }
            }
        }

        //The method where the file is downloaded and saved
        @SuppressLint("SetTextI18n")
        private void getData(InputStream inputStream)
        {
            byte[] buffer = new byte[1024];
            int bytes;

            try {
                bytes = inputStream.read(buffer);
                String incomingMessage = new String(buffer, 0, bytes);
                //runOnUiThread() Used to run code on the main UI thread.
                runOnUiThread(() -> {
                    textView_connected.setText("Connected as a server with\n" + incomingMessage);
                    button_foundDevice.setVisibility(View.INVISIBLE);
                    button_detect.setVisibility(View.INVISIBLE);
                    button_disconnectBack.setVisibility(View.INVISIBLE);
                    listView.setVisibility(View.INVISIBLE);});

                try {
                    OutputStream outputStream = socketServer.getOutputStream();

                    //The loop will be sent until it is stopped
                    while(!interrupted()) {
                        try {
                            bytes = inputStream.read(buffer);
                            if (bytes > 0) {

                                //File information is being retrieved
                                String fileFirstData = new String(buffer, 0, bytes);
                                String[] dataArray = fileFirstData.split(";");
                                fileName = dataArray[0];
                                String fileUnit = dataArray[1];
                                String fileSizeString = dataArray[2];
                                String bufferSize = dataArray[3];
                                LOG.addLog(LOG.currentDate(),"File information is being retrieved");
                                Arrays.fill(buffer, 0, buffer.length, (byte) 0);

                                double fileSize = conversionFileSize(Long.parseLong(fileSizeString),fileUnit);
                                String confirmMessage;
                                FileOutputStream fileToSave = null;
                                File file = setFilePlace();
                                LOG.addLog(LOG.currentDate(),"The file name has been set");

                                try {
                                    fileToSave = new FileOutputStream(file);
                                    long fullBytes = 0;
                                    byte[] bufferData = new byte[Integer.parseInt(bufferSize)];

                                    //loop where the file is fetched and the percentage of the file's saved data is displayed
                                    while ((bytes = inputStream.read(bufferData)) > 0) {
                                        fileToSave.write(bufferData, 0, bytes);
                                        fullBytes+=bytes;
                                        int percent = (int) (fullBytes * 100.0) / (int) Long.parseLong(fileSizeString);
                                        runOnUiThread(() -> textView_percent.setText("Download: " + percent + " %"));
                                        progressBar.setProgress(percent);
                                        if(percent == 100) {
                                            Arrays.fill(bufferData, 0, bufferData.length, (byte) 0);
                                            break;
                                        }
                                    }
                                    fileToSave.flush();
                                    runOnUiThread(() -> Toast.makeText(MainActivity_BT.this, "Downloaded File", Toast.LENGTH_SHORT).show());
                                    LOG.addLog(new Date(System.currentTimeMillis()),"The file has been downloaded and saved");
                                    confirmMessage= "Confirmed";
                                } catch (IOException e) {
                                    LOG.addLog(LOG.currentDate(),"Error downloaded and saving file", e.getMessage());
                                    confirmMessage= "NoneConfirmed";
                                } finally {
                                    try {
                                        if (fileToSave != null) {
                                            fileToSave.close();
                                            LOG.addLog(LOG.currentDate(),"Stream to file closed");
                                        }
                                    } catch (IOException e) {
                                        LOG.addLog(LOG.currentDate(),"Error closing output stream:", e.getMessage());
                                    }
                                }
                                //sending response to download and save file
                                outputStream.write(confirmMessage.getBytes());
                                outputStream.flush();
                                LOG.addLog(LOG.currentDate(),"Sending response to download and save file");

                                if(confirmMessage.equals("Confirmed")) {
                                    runOnUiThread(() -> textView_inf.setText("The name of the received file: " +
                                            fileName + "\nFile size: " + Constants.decimalFormat.format(fileSize) +
                                            " " + fileUnit));
                                }
                                else
                                {
                                    runOnUiThread(() -> textView_inf.setText("Error downloaded and saving file"));
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            LOG.addLog(LOG.currentDate(),"The data could not be loaded", e.getMessage());
                            break;
                        }
                    }
                    outputStream.close();
                } catch (IOException e) {
                    LOG.addLog(LOG.currentDate(),"Failed to create stream to send data",e.getMessage());
                }
            } catch (IOException e) {
                LOG.addLog(LOG.currentDate(),"The first data could not be loaded",e.getMessage());
            }
        }

        //method where the location to save the downloaded file is chosen
        //and the name is set if it already exists in the given place
        private File setFilePlace()
        {
            File file = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) +
                            "/" + fileName);
            int i = 1;
            while (file.exists()) {
                int dotIndex = fileName.lastIndexOf(".");
                String baseName = fileName.substring(0, dotIndex);
                String extension = fileName.substring(dotIndex);
                fileName = baseName + "(" + i + ")" + extension;
                file = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) +
                                "/" + fileName);
                i++;
            }
            return file;
        }

        //method where the file size is converted according to the unit
        private double conversionFileSize(long fileSizeLong, String fileUnit)
        {
            double fileSize = (double) fileSizeLong;
            switch (fileUnit) {
                case "MB":
                    fileSize /= 1024; //to KB
                    fileSize /= 1024; //to MB
                    break;
                case "KB":
                    fileSize /= 1024; //to KB
                    break;
            }
            return fileSize;
        }
    }

    //endregion Connect as a Server

    //The method where the intent to select the file to be sent is triggered
    private void sendDataFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent,Constants.REQUEST_BT_SEND_DATA_FILE);
    }

    //Reactions to permission response received openFile
    @SuppressLint("MissingPermission")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_BT_SEND_DATA_FILE && resultCode == RESULT_OK) {
            LOG.addLog(LOG.currentDate(),"You have selected a file to upload");
            fileToSend = data;
            dataSendFromClient = true;
        }
    }

    //method where the relevant threads, streams and other elements working in the background are closed
    @SuppressLint("MissingPermission")
    private void closeBtConnection()
    {
        if(threadClient != null)
            if(threadClient.isAlive()) {
                threadClient.interrupt();
                LOG.addLog(LOG.currentDate(),"Thread client was stopped");
            }
        if(threadServer != null)
            if(threadServer.isAlive()) {
                threadServer.interrupt();
                LOG.addLog(LOG.currentDate(),"Thread server was stopped");
            }
        if(receiver.isOrderedBroadcast()) {
            unregisterReceiver(receiver);
            LOG.addLog(LOG.currentDate(),"Broadcast was closed");
        }
        if(Constants.bluetoothAdapter.isDiscovering()) {
            Constants.bluetoothAdapter.cancelDiscovery();
            LOG.addLog(LOG.currentDate(),"bluetoothAdapter was closed");
        }
        if(socketClient != null)
            if(socketClient.isConnected()) {
                try {
                    socketClient.close();
                    LOG.addLog(LOG.currentDate(),"Socket client was closed");
                } catch (IOException e) {
                    LOG.addLog(LOG.currentDate(),"Error closing socket client", e.getMessage());
                }
            }
        if(socketServer != null)
            if(socketServer.isConnected()) {
                try {
                    socketServer.close();
                    LOG.addLog(LOG.currentDate(),"Socket server was closed");
                } catch (IOException e) {
                    LOG.addLog(LOG.currentDate(),"Error closing socket server", e.getMessage());
                }
            }
    }

    //The method that is run when the application is closed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeBtConnection();
    }

    //Create a menu for your current activity
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        MenuItem itemShowLog = menu.findItem(R.id.show_log);
        itemShowLog.setTitle("Show Log");
        return true;
    }
    //Create interactions for selecting items from the menu
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.show_log) {
            Intent intent = new Intent(this, MainActivity_Log.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}