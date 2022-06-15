
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'dart:io' show Platform;


class Wifirtt {
  static const MethodChannel _channel = MethodChannel('wifirtt');

  static Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
  static Future<bool> hasWiFiRTTFeature() async{
    if(!Platform.isAndroid) {
      return Future<bool>.value(false);
    }
    return await _channel.invokeMethod('hasWiFiRTTFeature');
  }

  static Future<List<String>?> get accessPoints async {
    return await _channel.invokeListMethod<String>('getAPs');
  }

  // static Future<Map?> runRangingRequest() async{
  static Future<Map<String, List<int>>?> runRangingRequest({List<String>? accessPoints = null}) async{
    var map = await _channel.invokeMapMethod<String, List<Object?>>('runRangingRequest', <String, dynamic>{
      'accessPoints': accessPoints
    });
    return map?.map((key, value) => MapEntry(key, value.cast<int>())).cast<String, List<int>>();
  }

  static Future<bool?> scanNetwork() async{
    return await _channel.invokeMethod<bool>('scanNetwork');
  }

  static Future<List<double>?> trilaterate(
      List<List<double>>? positions,
      List<double>? distances,
      List<double>? positionsStdDev,
      List<double>? distancesStdDev,
      [int weightsType = 0]) async{
    return await _channel.invokeListMethod<double>("trilaterate", <String, dynamic>{
      'positions': positions,
      'distances': distances,
      'positionsStdDev': positionsStdDev,
      'distancesStdDev': distancesStdDev,
      'weightsType': weightsType
    });
  }

  static Future<Map<String, int>?> getRSS() async{
    return await _channel.invokeMapMethod<String, int>("getRSS");
  }
}
