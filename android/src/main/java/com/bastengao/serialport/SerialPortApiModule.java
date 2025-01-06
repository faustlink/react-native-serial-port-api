package com.bastengao.serialport;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SerialPortApiModule extends ReactContextBaseJavaModule implements EventSender {
    private final ReactApplicationContext reactContext;
    private volatile boolean keepReading = true;
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^{}]*\\}");
    // private final SerialPortFinder finder = new SerialPortFinder();
    // private final Map<String, SerialPortWrapper> serialPorts = new HashMap<String, SerialPortWrapper>();

    public SerialPortApiModule(final ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "SerialPortAPI";
    }

    // @ReactMethod
    // public void deviceNames(final Callback callback) {
    //     WritableArray a = Arguments.createArray();
    //     String[] names = finder.getAllDevices();
    //     for (String name : names) {
    //         a.pushString(name);
    //     }
    //     callback.invoke(a);
    // }

    @ReactMethod
    public void startReading(final String filePath) {
        keepReading = true;
        System.out.println("Start Reading...");
        File device = new File(filePath); // Replace with your device file
        if (!device.exists()) {
            System.out.println("Device file not found!");
            return;
        }

        try (FileInputStream inputStream = new FileInputStream(device)) {
            // Thread to continuously read from the serial device
            Thread readerThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (keepReading) {
                    try {
                        int bytesRead = inputStream.read(buffer);
                        if (bytesRead > 10) {
                            String receivedData = new String(buffer, 0, bytesRead);
                            System.out.println("Received: " + receivedData);
                            WritableMap event = Arguments.createMap();
                            String clean = cleanData(receivedData);
                            event.putString("data", clean);
                            sendEvent("onDataReceived", event);
                        } else if (bytesRead == -1) {
                            System.err.println("End of stream reached, stopping reader...");
                            keepReading = false; // Stop loop if EOF is reached
                        }
                    } catch (IOException e) {
                        System.err.println("Error reading serial data: " + e.getMessage());
                        keepReading = false; // Stop the loop on error
                    }
                }
            });

            readerThread.start();
        } catch (IOException e) {
            System.err.println("TESTAPP Error: " + e.getMessage());
        }
    }

    @ReactMethod
    public void stopReading() {
        keepReading = false;
        System.out.println("Stopped reading from serial port.");
    }

    private String cleanData(String input) {
        Matcher matcher = JSON_PATTERN.matcher(input);

        String validPayload = null;

        // Iterate through all JSON matches and take the last one (the valid one)
        while (matcher.find()) {
            validPayload = matcher.group();
        }

        // Return the valid payload (the last JSON object found)
        return validPayload;
    }

    // @ReactMethod
    // public void devicePaths(final Callback callback) {
    //     WritableArray p = Arguments.createArray();
    //     String[] paths = finder.getAllDevicesPath();
    //     for (String path : paths) {
    //         p.pushString(path);
    //     }    
    //     callback.invoke(p);
    // }

//    @ReactMethod
//    public void setSuPath() {
//        try {
//            Process process = Runtime.getRuntime().exec("/system/xbin/su");
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

    // @ReactMethod
    // public void getSuPath(final Callback callback) {
    //     callback.invoke(SerialPort.getSuPath());
    // }

    // @ReactMethod
    // public void open(final String path, int baudRate, int parity, int dataBits, int stopBits, int readBufferSize, Promise promise) {
    //     if (serialPorts.containsKey(path)) {
    //         promise.resolve(serialPorts.get(path).toJS());
    //         return;
    //     }

    //     Remover remover = new Remover() {
    //         @Override
    //         public void remove() {
    //             serialPorts.remove(path);
    //         }
    //     };

    //     try {
    //         SerialPort serialPort = SerialPort.newBuilder(path, baudRate)
    //                 .parity(parity)
    //                 .dataBits(dataBits)
    //                 .stopBits(stopBits)
    //                 .build();

    //         SerialPortWrapper wrapper = new SerialPortWrapper(path, readBufferSize, serialPort, this, remover);

    //         // TODO: handle previous value
    //         serialPorts.put(path, wrapper);
    //         promise.resolve(wrapper.toJS());
    //     } catch (IOException e) {
    //         System.out.println("error: " + e.getMessage());
    //         e.printStackTrace();
    //         promise.reject(null, e.getMessage());
    //     } catch (SecurityException e) {
    //         System.out.println("error: " + e.getMessage());
    //         e.printStackTrace();
    //         promise.reject(null, "no permission to read or write this serial port");
    //     }
    // }

    // @ReactMethod
    // public void send(String path, String hexStr, Promise promise) {
    //     SerialPortWrapper wrapper = serialPorts.get(path);
    //     if (wrapper == null) {
    //         promise.reject(null, "serialport not open");
    //     }

    //     byte[] buffer = hexStringToByteArray(hexStr);
    //     try {
    //         wrapper.write(buffer);
    //         promise.resolve(true);
    //         Log.i("serialport", "send: " + hexStr);
    //     } catch (IOException e) {
    //         e.printStackTrace();
    //         promise.reject(null, e.getMessage());
    //     }
    // }

    // @ReactMethod
    // public void close(String path, Promise promise) {
    //     SerialPortWrapper wrapper = serialPorts.get(path);
    //     if (wrapper == null) {
    //         promise.reject(null, "serialport not open");
    //     }

    //     wrapper.close();
    //     promise.resolve(true);
    // }

    public void sendEvent(final String eventName, final WritableMap event) {
        reactContext.runOnUiQueueThread(new Runnable() {
            @Override
            public void run() {
                reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit(eventName, event);
            }
        });
    }

    // public static byte[] hexStringToByteArray(String s) {
    //     int len = s.length();
    //     byte[] data = new byte[len / 2];
    //     for (int i = 0; i < len; i += 2) {
    //         data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
    //                 + Character.digit(s.charAt(i + 1), 16));
    //     }
    //     return data;
    // }

    // private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    // public static String bytesToHex(byte[] bytes, int length) {
    //     char[] hexChars = new char[length * 2];
    //     for (int j = 0; j < length; j++) {
    //         int v = bytes[j] & 0xFF;
    //         hexChars[j * 2] = HEX_ARRAY[v >>> 4];
    //         hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    //     }
    //     return new String(hexChars);
    // }
}
