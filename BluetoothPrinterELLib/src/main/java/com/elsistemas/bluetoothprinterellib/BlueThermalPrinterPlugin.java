package com.elsistemas.bluetoothprinterellib;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlueThermalPrinterPlugin {
    private final Context context;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static ConnectedThread THREAD = null;
    private BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private final Object initializationLock = new Object();
    private BluetoothManager mBluetoothManager;

    private void setup() {
        synchronized (initializationLock) {
            mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();
        }
    }

    public BlueThermalPrinterPlugin(Context context) {
        this.context = context;

        setup();
    }

    public void detach() {
        mBluetoothAdapter = null;
        mBluetoothManager = null;
    }

    public int state() throws Exception {
        try {
            switch (mBluetoothAdapter.getState()) {
                case BluetoothAdapter.STATE_OFF:
                    return BluetoothAdapter.STATE_OFF;
                case BluetoothAdapter.STATE_ON:
                    return BluetoothAdapter.STATE_ON;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    return BluetoothAdapter.STATE_TURNING_OFF;
                case BluetoothAdapter.STATE_TURNING_ON:
                    return BluetoothAdapter.STATE_TURNING_ON;
                default:
                    return 0;
            }
        } catch (SecurityException e) {
            throw new Exception("Argument 'address' not found");
        }
    }

    private static String convertListaDeHashMapsParaJson(List<Map<String, Object>> listaDeHashMaps) throws JSONException {
        // Criar um array JSON para armazenar os objetos JSON
        JSONArray jsonArray = new JSONArray();

        // Converter cada HashMap para um objeto JSON e adicionar ao array
        for (Map<String, Object> hashMap : listaDeHashMaps) {
            JSONObject jsonObject = new JSONObject(hashMap);
            jsonArray.put(jsonObject);
        }

        // Retornar a representação em string do JSON
        return jsonArray.toString();
    }

    @SuppressLint("MissingPermission")
    public String getPairedDevices() throws JSONException {

        List<Map<String, Object>> list = new ArrayList<>();

        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            Map<String, Object> ret = new HashMap<>();
            ret.put("address", device.getAddress());
            ret.put("name", device.getName());
            ret.put("type", device.getType());
            list.add(ret);
        }

        return convertListaDeHashMapsParaJson(list);
    }

    public String exceptionToString(Exception ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }

    @SuppressLint("MissingPermission")
    public boolean connect(String address, int widthPaper) {
        try {
            if (THREAD != null) {
                // Já conectado
                return false;
            }

            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

            if (device == null) {
                // Dispositivo não encontrado
                return false;
            }

            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(MY_UUID);

            if (socket == null) {
                // Falha na criação do socket
                return false;
            }

            // Cancela a descoberta do Bluetooth, mesmo que não tenha sido iniciada
            mBluetoothAdapter.cancelDiscovery();

            try {
                socket.connect();
                THREAD = new ConnectedThread(socket);
                THREAD.start();

                byte[] command = {0x1B, 0x40, 0x1B, 0x57, (byte)(widthPaper / 8), 0x0A}; // set width paper

                THREAD.write(command);

                return true;  // Conexão estabelecida com sucesso
            } catch (Exception ex) {
                // Erro durante a conexão
                return false;
            }
        } catch (Exception ex) {
            // Outro erro
            return false;
        }
    }

    public boolean disconnect() throws Exception {
        try {
            if (THREAD == null) {
                throw new Exception("not connected");
            }

            AsyncTask.execute(() -> {
                THREAD.cancel();
                THREAD = null;
            });

            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean write(String message) throws Exception {
        if (THREAD == null) {
            throw new Exception("not connected");
        }

        try {
            THREAD.write(message.getBytes());
            return true;
        } catch (Exception ex) {
            throw new Exception(ex.getMessage());
        }
    }

    private boolean writeBytes(byte[] message) throws Exception {
        if (THREAD == null) {
            throw new Exception("not connected");
        }

        try {
            THREAD.write(message);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean printText(String message, int size, int align) throws Exception {
        // Print config "mode"
        byte[] cc = new byte[]{0x1B, 0x21, 0x03}; // 0- normal size text
        // byte[] cc1 = new byte[]{0x1B,0x21,0x00}; // 0- normal size text
        byte[] bb = new byte[]{0x1B, 0x21, 0x08}; // 1- only bold text
        byte[] bb2 = new byte[]{0x1B, 0x21, 0x20}; // 2- bold with medium text
        byte[] bb3 = new byte[]{0x1B, 0x21, 0x10}; // 3- bold with large text
        byte[] bb4 = new byte[]{0x1B, 0x21, 0x30}; // 4- strong text
        byte[] bb5 = new byte[]{0x1B, 0x21, 0x50}; // 5- extra strong text
        if (THREAD == null) {
            throw new Exception("not connected");
        }

        try {
            switch (size) {
                case 0:
                    THREAD.write(cc);
                    break;
                case 1:
                    THREAD.write(bb);
                    break;
                case 2:
                    THREAD.write(bb2);
                    break;
                case 3:
                    THREAD.write(bb3);
                    break;
                case 4:
                    THREAD.write(bb4);
                    break;
                case 5:
                    THREAD.write(bb5);
            }

            switch (align) {
                case 0:
                    // left align
                    THREAD.write(PrinterCommands.ESC_ALIGN_LEFT);
                    break;
                case 1:
                    // center align
                    THREAD.write(PrinterCommands.ESC_ALIGN_CENTER);
                    break;
                case 2:
                    // right align
                    THREAD.write(PrinterCommands.ESC_ALIGN_RIGHT);
                    break;
            }

            THREAD.write(message.getBytes("UTF-8"));
            THREAD.write(PrinterCommands.FEED_LINE);
            THREAD.write(PrinterCommands.FEED_LINE);
            THREAD.write(PrinterCommands.FEED_LINE);
            THREAD.write(PrinterCommands.FEED_LINE);
            THREAD.write(PrinterCommands.FEED_LINE);
            THREAD.write(PrinterCommands.FEED_LINE);
            THREAD.write(PrinterCommands.FEED_LINE);
            return true;
        } catch (Exception ex) {
            throw new Exception(ex.getMessage());
        }
    }

//    public boolean printLeftRight(String msg1, String msg2, int size, String charset, String format) throws Exception {
//        byte[] cc = new byte[]{0x1B, 0x21, 0x03}; // 0- normal size text
//        // byte[] cc1 = new byte[]{0x1B,0x21,0x00}; // 0- normal size text
//        byte[] bb = new byte[]{0x1B, 0x21, 0x08}; // 1- only bold text
//        byte[] bb2 = new byte[]{0x1B, 0x21, 0x20}; // 2- bold with medium text
//        byte[] bb3 = new byte[]{0x1B, 0x21, 0x10}; // 3- bold with large text
//        byte[] bb4 = new byte[]{0x1B, 0x21, 0x30}; // 4- strong text
//        if (THREAD == null) {
//            throw new Exception("not connected");
//        }
//        try {
//            switch (size) {
//                case 0:
//                    THREAD.write(cc);
//                    break;
//                case 1:
//                    THREAD.write(bb);
//                    break;
//                case 2:
//                    THREAD.write(bb2);
//                    break;
//                case 3:
//                    THREAD.write(bb3);
//                    break;
//                case 4:
//                    THREAD.write(bb4);
//                    break;
//            }
//            THREAD.write(PrinterCommands.ESC_ALIGN_CENTER);
//            String line = String.format("%-15s %15s %n", msg1, msg2);
//            if (format != null) {
//                line = String.format(format, msg1, msg2);
//            }
//            if (charset != null) {
//                THREAD.write(line.getBytes(charset));
//            } else {
//                THREAD.write(line.getBytes());
//            }
//            return true;
//        } catch (Exception ex) {
//            throw new Exception(ex.getMessage());
//        }
//    }

    public boolean printNewLine(int count) throws Exception {
        if (THREAD == null) {
            throw new Exception("not connected");
        }

        try {
            if (count > 1) {
                for (int i = 0; i < count; i++) {
                    THREAD.write(PrinterCommands.FEED_LINE);
                }
            } else {
                THREAD.write(PrinterCommands.FEED_LINE);
            }

            return true;
        } catch (Exception ex) {
            throw new Exception(ex.getMessage());
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    //readSink.success(new String(buffer, 0, bytes));
                } catch (NullPointerException e) {
                    break;
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cancel() {
            try {
                outputStream.flush();
                outputStream.close();

                inputStream.close();

                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean printImage(Bitmap image) throws Exception {
        if (THREAD == null) {
            throw new Exception("not connected");
        }

        try {
            if (image != null) {
                byte[] command = Utils.decodeBitmap(image);
                THREAD.write(PrinterCommands.ESC_ALIGN_CENTER);
                THREAD.write(command);
            }

            return true;
        } catch (Exception ex) {
            throw new Exception(ex.getMessage());
        }
    }

    public boolean printImageBytes(byte[] bytes, int align) throws Exception {
        if (THREAD == null) {
            throw new Exception("not connected");
        }
        try {
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp != null) {
                byte[] command = Utils.decodeBitmap(bmp);
                byte[] paperWidth = new byte[]{0x1D, 0x67, 0x50, 0x38, 0x30};

                switch (align) {
                    case 0:
                        THREAD.write(PrinterCommands.ESC_ALIGN_LEFT);
                        break;
                    case 1:
                        THREAD.write(PrinterCommands.ESC_ALIGN_CENTER);
                        break;
                    case 2:
                        THREAD.write(PrinterCommands.ESC_ALIGN_RIGHT);
                        break;
                }

                THREAD.write(command);

                THREAD.write(PrinterCommands.FEED_LINE);
                THREAD.write(PrinterCommands.FEED_LINE);
                THREAD.write(PrinterCommands.FEED_LINE);
                THREAD.write(PrinterCommands.FEED_LINE);
                THREAD.write(PrinterCommands.FEED_LINE);
                THREAD.write(PrinterCommands.FEED_LINE);
                THREAD.write(PrinterCommands.FEED_LINE);
                return true;

            } else {
                Log.e("Print Photo error", "the file isn't exists");
            }
            return false;
        } catch (Exception ex) {
            Log.e("Print image", ex.getMessage(), ex);
            throw new Exception("not connected");
        }
    }

    public boolean bluetoothActivated() {
        return mBluetoothAdapter.isEnabled();
    }

    @SuppressLint("MissingPermission")
    public boolean enableBluetooth(Activity activity) {
        try {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}