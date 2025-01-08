import { NativeEventEmitter, NativeModules, Platform } from 'react-native';

const { SerialPortAPI } = NativeModules;
const eventEmitter = Platform.OS === 'android' ? new NativeEventEmitter(SerialPortAPI) : null;

export default class API {
  static startReading(filePath: string) {
      console.log(`Start reading from ${filePath}...`);
      SerialPortAPI.startReading(filePath);
  };

  static stopReading() {
    console.log("Stop reading...");
    SerialPortAPI.stopReading();
  }

  static async writeToCsv(uri: string, fileName: string, data: string) {
    console.log("Writing to file...");
    await SerialPortAPI.writeToCsv(uri, fileName, data)
    console.log("Successfully written to file...");
  }

  static async readFromCsv(uri: string, fileName: string) {
    console.log("Reading from CSV...");
    const data = await SerialPortAPI.readFromCsv(uri, fileName);
    console.log("Successfully read from CSV...");
    return data;
  }

  static onDataReceived(listener: (data: any) => void) {
    return eventEmitter?.addListener('onDataReceived', listener);
  }

  static removeListener(listener: (data: any) => void) {
    return eventEmitter?.removeListener('onDataReceived', listener);
  }

  static removeAllListeners() {
    return eventEmitter?.removeAllListeners('onDataReceived');
  }
}
