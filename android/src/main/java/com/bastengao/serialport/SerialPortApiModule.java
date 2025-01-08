package com.bastengao.serialport;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.DocumentsContract;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SerialPortApiModule extends ReactContextBaseJavaModule implements EventSender {
    private final ReactApplicationContext reactContext;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private volatile boolean keepReading = false;
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{(?:[^{}]*|\\{.*?\\})*\\}");

    public SerialPortApiModule(final ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        backgroundThread = new HandlerThread("FileReaderThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public String getName() {
        return "SerialPortAPI";
    }

    @ReactMethod
    public void startReading(final String filePath) {
        keepReading = true;
        System.out.println("Start Reading...");
        File device = new File(filePath); // Replace with your device file
        if (!device.exists()) {
            System.out.println("Device file not found!");
            return;
        }

        backgroundHandler.post(() -> {
            try (FileInputStream inputStream = new FileInputStream(device)) {
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
                // Thread to continuously read from the serial device
            } catch (IOException e) {
                System.err.println("TESTAPP Error: " + e.getMessage());
            }
        });
    }

    @ReactMethod
    public void stopReading() {
        keepReading = false;
        System.out.println("Stopped reading from serial port.");
    }

    @ReactMethod
    public void writeToCsv(String uri, String fileName, String data, Promise promise) {
        try {
            JSONObject jsonObject = new JSONObject(data);

            Iterator<String> keys = jsonObject.keys();

            Uri fileUri = Uri.parse(uri);
            ContentResolver contentResolver = reactContext.getContentResolver();
            Uri documentUri = createOrGetDocumentUri(contentResolver, fileUri, fileName);

            if (documentUri == null) {
                promise.reject("Error creating or resolving file URI");
                return;
            }

            boolean isNewFile = isNewFile(contentResolver, documentUri);

            try (OutputStream fos = contentResolver.openOutputStream(documentUri, "wa")) {
                OutputStreamWriter writer = new OutputStreamWriter(fos);
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withRecordSeparator("\n"));

                if (isNewFile) {
                    while (keys.hasNext()) {
                        String key = keys.next();
                        csvPrinter.print(key);
                    }
                    csvPrinter.println();
                }

                keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    csvPrinter.print(jsonObject.get(key));
                }
                csvPrinter.println();
                csvPrinter.flush();

                promise.resolve("CSV written successfully to: " + fileUri.toString());
            } catch (IOException e) {
                e.printStackTrace();
                promise.reject("FILE_WRITE_ERROR", "Error writing to file", e);
            }
            promise.resolve("File written successfully");
        } catch (Exception e) {
            e.printStackTrace();
            promise.reject("Error writing file", e);
        }
    }

    @ReactMethod
    public void readFromCsv(String uri, String fileName, Promise promise) {
        Uri fileUri = Uri.parse(uri);

        ContentResolver contentResolver = reactContext.getContentResolver();
        Uri documentUri = createOrGetDocumentUri(contentResolver, fileUri, fileName);

        if (documentUri == null) {
            promise.reject("Error creating or resolving file URI");
            return;
        }

        try (InputStream inputStream = contentResolver.openInputStream(documentUri)) {
            // Use InputStreamReader for parsing the InputStream
            InputStreamReader reader = new InputStreamReader(inputStream);

            // Parse the CSV file
            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader() // Use first row as header
                    .parse(reader);

            // Create a WritableArray to hold the results
            WritableArray resultArray = Arguments.createArray();

            // Loop through the records
            for (CSVRecord record : parser) {
                WritableMap rowMap = Arguments.createMap();

                // Loop through header names and add each column to the row map
                for (String header : parser.getHeaderNames()) {
                    rowMap.putString(header, record.get(header));
                }

                // Add the row map to the result array
                resultArray.pushMap(rowMap);
            }

            // Close the InputStream and Reader
            reader.close();
            inputStream.close();

            // Resolve the promise with the result array
            promise.resolve(resultArray);
        } catch (IOException e) {
            e.printStackTrace();
            promise.reject("FILE_READ_ERROR", "Error reading CSV file", e);
        }
    }

    private boolean isNewFile(ContentResolver contentResolver, Uri fileUri) {
        try (FileInputStream fileInputStream = (FileInputStream) contentResolver.openInputStream(fileUri)) {
            return fileInputStream == null || fileInputStream.available() == 0;
        } catch (FileNotFoundException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Uri createOrGetDocumentUri(ContentResolver contentResolver, Uri treeUri, String fileName) {
        try {
            // Build the document URI for the file
            Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
            );

            // Query the directory to see if the file already exists
            Uri existingFileUri = findExistingFileUri(contentResolver, documentUri, fileName);
            if (existingFileUri != null) {
                return existingFileUri; // File already exists
            }

            // File does not exist, create it
            return DocumentsContract.createDocument(
                    contentResolver,
                    documentUri,
                    "text/csv", // MIME type for CSV
                    fileName
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Uri findExistingFileUri(ContentResolver contentResolver, Uri directoryUri, String fileName) {
        try (Cursor cursor = contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        directoryUri,
                        DocumentsContract.getTreeDocumentId(directoryUri)
                ),
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String existingFileName = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                    String documentId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID));

                    // Perform a strict match on file name
                    if (fileName.equals(existingFileName)) {
                        return DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
    public void sendEvent(final String eventName, final WritableMap event) {
        reactContext.runOnUiQueueThread(new Runnable() {
            @Override
            public void run() {
                reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit(eventName, event);
            }
        });
    }
}
